/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2ManyIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2OneIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2SizeIndex;
import org.eclipse.mat.parser.index.IndexManager;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.index.IndexReader.SizeIndexReader;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IndexWriter.IntIndexStreamer;
import org.eclipse.mat.parser.index.IndexWriter.LongIndexStreamer;
import org.eclipse.mat.parser.internal.snapshot.ObjectMarker;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SilentProgressListener;

/* package */class GarbageCleaner
{

    private final static int PARALLEL_CHUNK_SIZE = 16*1024*1024;
    public static int[] clean(final PreliminaryIndexImpl idx, final SnapshotImplBuilder builder,
                    Map<String, String> arguments, IProgressListener listener)
            throws IOException, InterruptedException, ExecutionException
    {
        IndexManager idxManager = new IndexManager();
        ExecutorService es = Executors.newWorkStealingPool();

        try
        {
            listener.beginTask(Messages.GarbageCleaner_RemovingUnreachableObjects, 11);
            listener.subTask(Messages.GarbageCleaner_SearchingForUnreachableObjects);

            final int oldNoOfObjects = idx.identifiers.size();

            // determine reachable objects
            boolean[] reachable = new boolean[oldNoOfObjects];
            int newNoOfObjects = 0;
            int[] newRoots = idx.gcRoots.getAllKeys();

            IOne2LongIndex identifiers = idx.identifiers;
            IOne2ManyIndex preOutbound = idx.outbound;
            IOne2OneIndex object2classId = idx.object2classId;
            HashMapIntObject<ClassImpl> classesById = idx.classesById;

            /*
             * START - marking objects use ObjectMarker to mark the reachable
             * objects if more than 1 CPUs are available - use multithreading
             */
            ObjectMarker marker = new ObjectMarker(newRoots, reachable, preOutbound, new SilentProgressListener(
                            listener));
            int numProcessors = Runtime.getRuntime().availableProcessors();
            if (numProcessors > 1)
            {
                try
                {
                    marker.markMultiThreaded(numProcessors);
                }
                catch (InterruptedException e)
                {
                    IOException ioe = new IOException(e.getMessage());
                    ioe.initCause(e);
                    throw ioe;
                }

                // find the number of new objects. It's not returned by marker
                for (boolean b : reachable)
                    if (b)
                        newNoOfObjects++;

            }
            else
            {
                try
                {
                    newNoOfObjects = marker.markSingleThreaded();
                }
                catch (OperationCanceledException e)
                {
                    // $JL-EXC$
                    return null;
                }
            }
            marker = null;
            /* END - marking objects */

            // check if unreachable objects exist, then either mark as GC root
            // unreachable (keep objects) or store a histogram of unreachable
            // objects
            if (newNoOfObjects < oldNoOfObjects)
            {
                Object un = idx.getSnapshotInfo().getProperty("keep_unreachable_objects"); //$NON-NLS-1$
                if (un instanceof Integer)
                {
                    int newRoot;
                    newRoot = (Integer)un;
                    newNoOfObjects = markUnreachableAsGCRoots(idx, reachable, newNoOfObjects, newRoot, listener);
                }
                if (newNoOfObjects < oldNoOfObjects)
                {
                    createHistogramOfUnreachableObjects(es, idx, reachable);
                }
            }

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1); // 3
            listener.subTask(Messages.GarbageCleaner_ReIndexingObjects);

            // create re-index map
            final int[] map = new int[oldNoOfObjects];
            final long[] id2a = new long[newNoOfObjects];

            List<ClassImpl> classes2remove = new ArrayList<ClassImpl>();

            final IOne2SizeIndex preA2size = idx.array2size;
            long memFree = 0;
            for (int ii = 0, jj = 0; ii < oldNoOfObjects; ii++)
            {
                if (reachable[ii])
                {
                    map[ii] = jj;
                    id2a[jj++] = identifiers.get(ii);
                }
                else
                {
                    map[ii] = -1;
                }
            }

            ArrayList<Callable<CleanupWrapper>> tasks = new ArrayList<Callable<CleanupWrapper>>();

            for(int i = 0; i < oldNoOfObjects; i += PARALLEL_CHUNK_SIZE) {
                final int start = i;
                final int length = Math.min(PARALLEL_CHUNK_SIZE, reachable.length - start);
                tasks.add(new CalculateGarbageCleanupForClass(idx, reachable, start, length));
            }

            List<Future<CleanupWrapper>> wrappers = null;
            wrappers = es.invokeAll(tasks);

            for(Future<CleanupWrapper> wrapper : wrappers) {
                for(CleanupResult cr : wrapper.get().results.values()) {
                    long totalmem = cr.size;
                    for (int i = cr.count; i > 0; --i)
                    {
                        // Remove one by one as removeInstanceBulk is not yet API
                        long instsize = totalmem / i;
                        totalmem -= instsize;
                        cr.clazz.removeInstance(instsize);
                    }
                    memFree += cr.size;
                }
                for(ClassImpl c : wrapper.get().classes2remove)
                {
                    classes2remove.add(c);
                }
            }

            if (newNoOfObjects < oldNoOfObjects)
            {
                listener.sendUserMessage(Severity.INFO, MessageUtil.format(
                                Messages.GarbageCleaner_RemovedUnreachableObjects, oldNoOfObjects
                                                - newNoOfObjects, memFree), null);
            }

            es.shutdown();

            // classes cannot be removed right away
            // as they are needed to remove instances of this class
            for (ClassImpl c : classes2remove)
            {
                classesById.remove(c.getObjectId());

                ClassImpl superclass = classesById.get(c.getSuperClassId());
                if (superclass != null)
                    superclass.removeSubClass(c);
            }

            reachable = null; // early gc...

            identifiers.close();
            identifiers.delete();
            identifiers = null;

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1); // 4
            listener.subTask(Messages.GarbageCleaner_ReIndexingClasses);

            // fix classes
            HashMapIntObject<ClassImpl> classesByNewId = new HashMapIntObject<ClassImpl>(classesById.size());
            for (Iterator<ClassImpl> iter = classesById.values(); iter.hasNext();)
            {
                ClassImpl clazz = iter.next();
                int index = map[clazz.getObjectId()];
                clazz.setObjectId(index);

                if (clazz.getSuperClassId() >= 0) // java.lang.Object
                    clazz.setSuperClassIndex(map[clazz.getSuperClassId()]);
                clazz.setClassLoaderIndex(map[clazz.getClassLoaderId()]);

                classesByNewId.put(index, clazz);
            }

            idx.getSnapshotInfo().setNumberOfClasses(classesByNewId.size());

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1); // 5

            // //////////////////////////////////////////////////////////////
            // identifiers
            // //////////////////////////////////////////////////////////////

            File indexFile = Index.IDENTIFIER.getFile(idx.snapshotInfo.getPrefix());
            listener.subTask(MessageUtil.format(Messages.GarbageCleaner_Writing, indexFile.getAbsolutePath()));
            idxManager.setReader(Index.IDENTIFIER, new LongIndexStreamer().writeTo(indexFile, id2a));

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1); // 6

            // //////////////////////////////////////////////////////////////
            // object 2 class Id
            // //////////////////////////////////////////////////////////////

            indexFile = Index.O2CLASS.getFile(idx.snapshotInfo.getPrefix());
            listener.subTask(MessageUtil.format(Messages.GarbageCleaner_Writing, indexFile.getAbsolutePath()));
            idxManager.setReader(Index.O2CLASS, new IntIndexStreamer().writeTo(indexFile,
                            new NewObjectIntIterator()
                            {
                                @Override
                                int doGetNextInt(int index)
                                {
                                    return map[idx.object2classId.get(nextIndex)];
                                    // return
                                    // map[object2classId.get(nextIndex)];
                                }

                                @Override
                                int[] getMap()
                                {
                                    return map;
                                }
                            }));

            object2classId.close();
            object2classId.delete();
            object2classId = null;

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1); // 7

            // //////////////////////////////////////////////////////////////
            // array size
            // //////////////////////////////////////////////////////////////

            indexFile = Index.A2SIZE.getFile(idx.snapshotInfo.getPrefix());
            listener.subTask(MessageUtil.format(Messages.GarbageCleaner_Writing, new Object[] { indexFile
                            .getAbsolutePath() }));
            final BitField arrayObjects = new BitField(newNoOfObjects);
            // arrayObjects
            IOne2OneIndex newIdx = new IntIndexStreamer().writeTo(indexFile,
                            new NewObjectIntIterator()
                            {
                                IOne2SizeIndex a2size = preA2size;
                                int newIndex = 0;

                                @Override
                                int doGetNextInt(int index)
                                {
                                    int size = a2size.get(nextIndex);
                                    // Get the compressed size, 0 means 0
                                    if (size != 0)
                                        arrayObjects.set(newIndex);
                                    newIndex++;
                                    return size;
                                }

                                @Override
                                int[] getMap()
                                {
                                    return map;
                                }
                            });

            idxManager.setReader(Index.A2SIZE, new SizeIndexReader(newIdx)); 

            preA2size.close();
            preA2size.delete();

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1); // 9

            // //////////////////////////////////////////////////////////////
            // inbound, outbound
            // //////////////////////////////////////////////////////////////

            listener.subTask(Messages.GarbageCleaner_ReIndexingOutboundIndex);

            IndexWriter.IntArray1NSortedWriter w_out = new IndexWriter.IntArray1NSortedWriter(newNoOfObjects,
                            IndexManager.Index.OUTBOUND.getFile(idx.snapshotInfo.getPrefix()));
            IndexWriter.InboundWriter w_in = new IndexWriter.InboundWriter(newNoOfObjects, IndexManager.Index.INBOUND
                            .getFile(idx.snapshotInfo.getPrefix()));

            for (int ii = 0; ii < oldNoOfObjects; ii++)
            {
                int k = map[ii];
				if (k < 0) continue;

				int[] a = preOutbound.get(ii);
				int[] tl = new int[a.length];
				for (int jj = 0; jj < a.length; jj++)
				{
					int t = map[a[jj]];

					/* No check if the referenced objects are alive */
					/* The garbage can't be reached from a live object */
					// removed if (t >= 0) ...
					tl[jj] = t;
					w_in.log(t, k, jj == 0);
				}

				w_out.log(k, tl);
            }

            preOutbound.close();
            preOutbound.delete();
            preOutbound = null;

            if (listener.isCanceled())
            {
                w_in.cancel();
                w_out.cancel();
                throw new IProgressListener.OperationCanceledException();
            }
            listener.worked(1); // 10

            listener
                            .subTask(MessageUtil.format(Messages.GarbageCleaner_Writing, w_in.getIndexFile()
                                            .getAbsolutePath()));

            idxManager.setReader(Index.INBOUND, w_in.flush(listener, new KeyWriterImpl(classesByNewId)));
            w_in = null;
            if (listener.isCanceled())
            {
                w_out.cancel();
                throw new IProgressListener.OperationCanceledException();
            }

            listener.worked(1); // 11

            listener.subTask(MessageUtil.format(Messages.GarbageCleaner_Writing, new Object[] { w_out.getIndexFile()
                            .getAbsolutePath() }));
            idxManager.setReader(Index.OUTBOUND, w_out.flush());
            w_out = null;
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1); // 12

            // fix roots
            HashMapIntObject<XGCRootInfo[]> roots = fix(idx.gcRoots, map);
            idx.getSnapshotInfo().setNumberOfGCRoots(roots.size());

            // fix threads 2 objects 2 roots
            HashMapIntObject<HashMapIntObject<XGCRootInfo[]>> rootsPerThread = new HashMapIntObject<HashMapIntObject<XGCRootInfo[]>>();
            for (IteratorInt iter = idx.thread2objects2roots.keys(); iter.hasNext();)
            {
                int threadId = iter.next();
                int fixedThreadId = map[threadId];
                if (fixedThreadId < 0)
                    continue;

                rootsPerThread.put(fixedThreadId, fix(idx.thread2objects2roots.get(threadId), map));
            }

            // fill stuff into builder
            builder.setIndexManager(idxManager);
            builder.setClassCache(classesByNewId);
            builder.setArrayObjects(arrayObjects);
            builder.setRoots(roots);
            builder.setRootsPerThread(rootsPerThread);

            return map;
        }
        finally
        {
            // delete all temporary indices
            idx.delete();

            if (idxManager != null && listener.isCanceled())
                idxManager.delete();

        }
    }

    private static HashMapIntObject<XGCRootInfo[]> fix(HashMapIntObject<List<XGCRootInfo>> roots, final int[] map)
    {
        HashMapIntObject<XGCRootInfo[]> answer = new HashMapIntObject<XGCRootInfo[]>(roots.size());
        for (Iterator<List<XGCRootInfo>> iter = roots.values(); iter.hasNext();)
        {
            List<XGCRootInfo> r = iter.next();
            XGCRootInfo[] a = new XGCRootInfo[r.size()];
            for (int ii = 0; ii < a.length; ii++)
            {
                a[ii] = r.get(ii);
                a[ii].setObjectId(map[a[ii].getObjectId()]);
                if (a[ii].getContextAddress() != 0)
                    a[ii].setContextId(map[a[ii].getContextId()]);
            }

            answer.put(a[0].getObjectId(), a);
        }
        return answer;
    }

    private static abstract class NewObjectIterator
    {
        int nextIndex = -1;
        int[] $map;

        public NewObjectIterator()
        {
            $map = getMap();
            findNext();
        }

        protected void findNext()
        {
            nextIndex++;
            while (nextIndex < $map.length && $map[nextIndex] < 0)
                nextIndex++;
        }

        public boolean hasNext()
        {
            return nextIndex < $map.length;
        }

        abstract int[] getMap();
    }

    private static abstract class NewObjectIntIterator extends NewObjectIterator implements IteratorInt
    {
        public int next()
        {
            int answer = doGetNextInt(nextIndex);
            findNext();
            return answer;
        }

        abstract int doGetNextInt(int nextIndex);

    }

    private static class KeyWriterImpl implements IndexWriter.KeyWriter
    {
        HashMapIntObject<ClassImpl> classesByNewId;

        KeyWriterImpl(HashMapIntObject<ClassImpl> classesByNewId)
        {
            this.classesByNewId = classesByNewId;
        }

        public void storeKey(int index, Serializable key)
        {
            ClassImpl impl = classesByNewId.get(index);
            impl.setCacheEntry(key);
        }
    }

    // //////////////////////////////////////////////////////////////
    // create histogram of unreachable objects
    // //////////////////////////////////////////////////////////////

    private static final class Record
    {
        ClassImpl clazz;
        int objectCount;
        long size;

        public Record(ClassImpl clazz)
        {
            this.clazz = clazz;
        }
    }

    private static void createHistogramOfUnreachableObjects(final ExecutorService es,
                    final PreliminaryIndexImpl idx, final boolean[] reachable)
                                    throws InterruptedException, ExecutionException
    {
        ArrayList<Callable<HashMap<Integer, Record>>> tasks = new ArrayList<Callable<HashMap<Integer, Record>>>();

        for(int i = 0; i < reachable.length; i += PARALLEL_CHUNK_SIZE) {
            final int start = i;
            final int length = Math.min(PARALLEL_CHUNK_SIZE, reachable.length - start);
            tasks.add(new CreateHistogramOfUnreachableObjectsChunk(idx, reachable, start, length));
        }

        List<Future<HashMap<Integer, Record>>> results = null;
        results = es.invokeAll(tasks);

        final HashMap<Integer, Record> histogram = new HashMap<Integer, Record>(reachable.length);

        for(Future<HashMap<Integer, Record>> subhistogram : results) {
            histogram.putAll(subhistogram.get());
        }

        /*
         * The parser might have already discarded some objects, so merge the histograms.
         */
        Serializable parserDeadObjects = idx.getSnapshotInfo().getProperty(UnreachableObjectsHistogram.class.getName());
        HashMap<Long, UnreachableObjectsHistogram.Record>parserRecords = new HashMap<Long, UnreachableObjectsHistogram.Record>();
        if (parserDeadObjects instanceof UnreachableObjectsHistogram)
        {
            UnreachableObjectsHistogram parserDeadObjectHistogram = (UnreachableObjectsHistogram)parserDeadObjects;
            for (UnreachableObjectsHistogram.Record r : parserDeadObjectHistogram.getRecords())
            {
                parserRecords.put(r.getClassAddress(), r);
            }
        }
        List<UnreachableObjectsHistogram.Record> records = new ArrayList<UnreachableObjectsHistogram.Record>();
        for(Record r : histogram.values()) {
            UnreachableObjectsHistogram.Record r2 = parserRecords.get(r.clazz.getObjectAddress());
            int existingCount = 0;
            long existingSize = 0L;
            if (r2 != null && r.clazz.getName().equals(r2.getClassName()))
            {
                existingCount = r2.getObjectCount();
                existingSize = r2.getShallowHeapSize();
                parserRecords.remove(r.clazz.getObjectAddress());
            }
            records.add(new UnreachableObjectsHistogram.Record(
                            r.clazz.getName(),
                            r.clazz.getObjectAddress(),
                            r.objectCount + existingCount,
                            r.size + existingSize));
        }
        records.addAll(parserRecords.values());

        UnreachableObjectsHistogram deadObjectHistogram = new UnreachableObjectsHistogram(records);
        idx.getSnapshotInfo().setProperty(UnreachableObjectsHistogram.class.getName(), deadObjectHistogram);
    }


    private static class CreateHistogramOfUnreachableObjectsChunk implements Callable<HashMap<Integer, Record>>
    {
        final PreliminaryIndexImpl idx;
        final boolean[] reachable;
        final int start;
        final int length;

        public CreateHistogramOfUnreachableObjectsChunk(PreliminaryIndexImpl idx,
                        boolean[] reachable, int start, int length)
        {
            this.idx = idx;
            this.reachable = reachable;
            this.start = start;
            this.length = length;
        }

        public HashMap<Integer, Record> call() {
            IOne2SizeIndex array2size = idx.array2size;

            HashMap<Integer, Record> histogram = new HashMap<Integer, Record>(length);

            for (int ii = start; ii < (start + length); ii++)
            {
                if (!reachable[ii])
                {
                    final int classId = idx.object2classId.get(ii);
                    Record r = histogram.get(classId);
                    if (r == null)
                    {
                        r = new Record(idx.classesById.get(classId));
                        histogram.put(classId, r);
                    }

                    long s = array2size.getSize(ii);
                    if (s > 0)
                    {
                        // Already got the size
                    }
                    else if (IClass.JAVA_LANG_CLASS.equals(r.clazz.getName()))
                    {
                        ClassImpl classImpl = idx.classesById.get(ii);
                        if (classImpl == null)
                        {
                            s = r.clazz.getHeapSizePerInstance();
                        }
                        else
                        {
                            s = classImpl.getUsedHeapSize();
                        }
                    }
                    else
                    {
                        s = r.clazz.getHeapSizePerInstance();
                    }

                    r.size += s;
                    r.objectCount += 1;
                }
            }

            return histogram;
        }
    }

    // //////////////////////////////////////////////////////////////
    // calculate garbage cleanup
    // //////////////////////////////////////////////////////////////

    private static class CleanupWrapper {
        final HashMap<Integer, CleanupResult> results;
        final List<ClassImpl> classes2remove;
        public CleanupWrapper(final HashMap<Integer, CleanupResult> results, final List<ClassImpl> classes2remove)
        {
            this.results = results;
            this.classes2remove = classes2remove;
        }
    }

    private static class CleanupResult {
        int count = 0;
        long size = 0;
        final ClassImpl clazz;
        public CleanupResult(ClassImpl clazz)
        {
            this.clazz = clazz;
        }
    }

    private static class CalculateGarbageCleanupForClass implements Callable<CleanupWrapper>
    {
        final PreliminaryIndexImpl idx;
        final boolean[] reachable;
        final int start;
        final int length;

        public CalculateGarbageCleanupForClass(PreliminaryIndexImpl idx,
                        boolean[] reachable, int start, int length)
        {
            this.idx = idx;
            this.reachable = reachable;
            this.start = start;
            this.length = length;
        }

        public CleanupWrapper call() throws Exception
        {
            HashMap<Integer, CleanupResult> results = new HashMap<Integer, CleanupResult>();
            List<ClassImpl> classes2remove = new ArrayList<ClassImpl>();
            for (int ii = start; ii < (start + length); ii++)
            {
                if (reachable[ii])
                    continue;

                int classId = idx.object2classId.get(ii);
                ClassImpl clazz = idx.classesById.get(classId);

                CleanupResult cr = results.get(classId);
                if (cr == null)
                {
                    cr = new CleanupResult(clazz);
                    results.put(classId, cr);
                }

                long arraySize = idx.array2size.getSize(ii);
                if (arraySize > 0)
                {
                    cr.count += 1;
                    cr.size += arraySize;
                }
                else
                {
                    // [INFO] some instances of java.lang.Class are not
                    // reported as HPROF_GC_CLASS_DUMP but as
                    // HPROF_GC_INSTANCE_DUMP
                    ClassImpl c = idx.classesById.get(ii);

                    if (c == null)
                    {
                        cr.count += 1;
                        cr.size += clazz.getHeapSizePerInstance();
                    }
                    else
                    {
                        cr.count += 1;
                        cr.size += c.getUsedHeapSize();
                        classes2remove.add(c);
                    }
                }
            }
            return new CleanupWrapper(results, classes2remove);
        }
    }

    // //////////////////////////////////////////////////////////////
    // mark unreachable objects as GC unknown
    // //////////////////////////////////////////////////////////////

    private static int markUnreachableAsGCRoots(final PreliminaryIndexImpl idx, //
                    boolean[] reachable, //
                    int noReachableObjects, //
                    int extraRootType, IProgressListener listener)
    {
        final int noOfObjects = reachable.length;
        final IOne2LongIndex identifiers = idx.identifiers;
        final IOne2ManyIndex preOutbound = idx.outbound;

        // find objects not referenced by any other object
        byte inbounds[] = new byte[noOfObjects];
        for (int ii = 0; ii < noOfObjects; ++ii)
        {
            if (!reachable[ii])
            {
                // We only need search the unreachable objects as
                // the reachable ones will have already marked
                // its outbound refs.
                for (int out : preOutbound.get(ii))
                {
                    // Exclude objects pointing to themselves
                    if (out != ii)
                    {
                        // Avoid overflow
                        if (inbounds[out] != -1) inbounds[out]++;
                    }
                }
            }
        }

        // First pass mark only the unreferenced objects
        ArrayInt unref = new ArrayInt();
        for (int ii = 0; ii < noOfObjects; ++ii)
        {
            // Do the objects with no inbounds first
            if (!reachable[ii] && inbounds[ii] == 0)
            {
                // Identify this unreachable object as a root,
                // No need to mark it as the marker will do that
                unref.add(ii);

                XGCRootInfo xgc = new XGCRootInfo(identifiers.get(ii), 0, extraRootType);
                xgc.setObjectId(ii);

                List<XGCRootInfo> xgcs = Collections.singletonList(xgc);
                idx.gcRoots.put(ii, xgcs);
            }
        }
        // See what else is now reachable
        ObjectMarker marker2 = new ObjectMarker(unref.toArray(), reachable, preOutbound, new SilentProgressListener(listener));
        int numProcessors = Runtime.getRuntime().availableProcessors();
        if (numProcessors > 1 && unref.size() > 1)
        {
            try
            {
                marker2.markMultiThreaded(numProcessors);
            }
            catch (InterruptedException e)
            {
                OperationCanceledException oc = new IProgressListener.OperationCanceledException();
                oc.initCause(e);
                throw oc;
            }

            // find the number of new objects. It's not returned by marker
            int newNoOfObjects = 0;
            for (boolean b : reachable)
                if (b)
                    newNoOfObjects++;
            noReachableObjects = newNoOfObjects;
        }
        else
        {
            int marked2 = marker2.markSingleThreaded();
            noReachableObjects += marked2;
        }

        // find remaining unreachable objects
        unref.clear();
        for (int ii = 0; ii < noOfObjects; ++ii)
        {
            if (!reachable[ii])
            {
                // Add to list
                unref.add(ii);
            }
        }

        int root[] = new int[1];
        ObjectMarker marker = new ObjectMarker(root, reachable, preOutbound, new SilentProgressListener(listener));
        int passes = 10;
        for (int pass = 0; pass < passes; ++pass)
        {
            // find remaining unreachable objects
            ArrayInt unref2 = new ArrayInt();
            byte outbounds[] = new byte[noOfObjects];
            for (IteratorInt it = unref.iterator(); it.hasNext();)
            {
                int ii = it.next();
                if (!reachable[ii])
                {
                    // We only need search the unreachable objects as
                    // the reachable ones will have already marked
                    // its outbound refs.
                    unref2.add(ii);
                    for (int out : preOutbound.get(ii))
                    {
                        // Exclude objects pointing to themselves
                        // and only count unreachable refs
                        // We only need to recount outbound refs as the
                        // inbound ref count will be unchanged.
                        if (out != ii && !reachable[out])
                        {
                            // Avoid overflow
                            if (outbounds[ii] != -1) outbounds[ii]++;
                        }
                    }
                }
            }
            unref = unref2;

            // choose some of the remaining unreachable objects as roots
            for (IteratorInt it = unref.iterator(); it.hasNext() && noReachableObjects < noOfObjects;)
            {
                int ii = it.next();
                ii = selectRoot(ii, pass, passes, reachable, preOutbound, outbounds, inbounds);

                if (ii >= 0) {
                    // Identify this unreachable object as a root,
                    // and see what else is now reachable
                    // No need to mark it as the marker will do that
                    root[0] = ii;

                    XGCRootInfo xgc = new XGCRootInfo(identifiers.get(ii), 0, extraRootType);
                    xgc.setObjectId(ii);

                    List<XGCRootInfo> xgcs = Collections.singletonList(xgc);
                    idx.gcRoots.put(ii, xgcs);

                    int marked = marker.markSingleThreaded();
                    noReachableObjects += marked;
                }
            }
        }

        // update GC root information
        idx.setGcRoots(idx.gcRoots);
        idx.getSnapshotInfo().setNumberOfGCRoots(idx.gcRoots.size());
        return noReachableObjects;
    }

    /**
     * Decide on next root to choose.
     * A sophisticated version would convert the object graph to a DAG
     * of strongly connected components and choose an example member from
     * each (currently unreached) source strongly connected component.
     * This version is not minimal but covers the object graph.
     * @param ii candidate root
     * @param pass from 0 to passes -1
     * @param passes number of passes
     * @param reachable which object have already been marked
     * @param preOutbound the outbound refs for each object
     * @param outbounds count of outbounds (as 0..255)
     * @param inbounds count of inbounds (as 0..255)
     * @return candidate root or -1
     */
    private static int selectRoot(int ii, int pass, int passes, boolean[] reachable, final IOne2ManyIndex preOutbound,
                    byte[] outbounds, byte[] inbounds)
    {
        if (reachable[ii])
            return -1;

        // Check for objects with 1 inbound, pointing to another object 
        // with 1 inbound, pointing back to this.
        // The cycle has no entry, so must be a root.
        if (pass == 0)
        {
            if ((inbounds[ii] & 0xff) == 1)
            {
                for (int out : preOutbound.get(ii))
                {
                    // Exclude objects pointing to themselves
                    // and only count unreachable refs
                    if (out != ii && !reachable[out])
                    {
                        if ((inbounds[out] & 0xff) != 1)
                            continue;
                        for (int out2 : preOutbound.get(out))
                        {
                            if (out2 == ii)
                            {
                                // Choose as a root as this cycle of objects
                                // has no external entry
                                return ii;
                            }
                        }
                    }
                }
            }
            return -1;
        }

        // Don't do objects with only one inbound until the end as perhaps the
        // predecessor will get marked.
        // Don't choose an object with no unref outbounds as we should choose a
        // predecessor of this object instead.
        // Choose objects with lots of outbounds first as they might mark
        // a lot of objects.
        boolean chooseAsRoot = (outbounds[ii] & 0xff) > 0
                        && (pass == passes - 1 || (inbounds[ii] & 0xff) > 1
                                        && (outbounds[ii] & 0xff) - (inbounds[ii] & 0xff) >= passes - pass - 2);

        return chooseAsRoot ? ii : -1;
    }
}

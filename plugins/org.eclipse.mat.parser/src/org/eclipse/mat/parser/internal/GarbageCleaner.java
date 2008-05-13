/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.parser.index.IndexManager;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2ManyIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2OneIndex;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.internal.snapshot.ObjectMarker;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;

/* package */class GarbageCleaner
{

    public static int[] clean(final PrelimanryIndexImpl idx, final SnapshotImplBuilder builder,
                    IProgressListener listener) throws IOException
    {
        IndexManager idxManager = new IndexManager();

        try
        {
            listener.beginTask("Removing unreachable objects", 11);
            listener.subTask("Searching for unreachable objects");

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
            int numProcessors = Runtime.getRuntime().availableProcessors();
            ObjectMarker marker = new ObjectMarker(newRoots, reachable, preOutbound, new VoidProgressListener());
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

            if (listener.isCanceled())
                return null;
            listener.worked(1); // 3
            listener.subTask("Re-indexing objects");

            // create re-index map
            final int[] map = new int[oldNoOfObjects];
            final long[] id2a = new long[newNoOfObjects];

            List<ClassImpl> classes2remove = new ArrayList<ClassImpl>();

            final IOne2OneIndex preA2size = idx.array2size;

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

                    int classId = object2classId.get(ii);
                    ClassImpl clazz = classesById.get(classId);

                    if (clazz.isArrayType())
                    {
                        clazz.removeInstance(preA2size.get(ii));
                    }
                    else
                    {
                        // [INFO] some instances of java.lang.Class are not
                        // reported as HPROF_GC_CLASS_DUMP but as
                        // HPROF_GC_INSTANCE_DUMP
                        ClassImpl c = classesById.get(ii);

                        if (c == null)
                        {
                            clazz.removeInstance(clazz.getHeapSizePerInstance());
                        }
                        else
                        {
                            clazz.removeInstance(c.getUsedHeapSize());
                            classes2remove.add(c);
                        }
                    }
                }
            }

            // classes cannot be removed right away
            // as they are needed to remove instances of this class
            for (ClassImpl c : classes2remove)
            {
                classesById.remove(c.getObjectId());

                ClassImpl superclass = (ClassImpl) classesById.get(c.getSuperClassId());
                if (superclass != null)
                    superclass.removeSubClass(c);
            }

            reachable = null; // early gc...

            identifiers.close();
            identifiers.delete();
            identifiers = null;

            if (listener.isCanceled())
                return null;
            listener.worked(1); // 4
            listener.subTask("Re-indexing classes");

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
                return null;
            listener.worked(1); // 5

            // //////////////////////////////////////////////////////////////
            // identifiers
            // //////////////////////////////////////////////////////////////

            File indexFile = Index.IDENTIFIER.getFile(idx.snapshotInfo.getPrefix());
            listener.subTask(MessageFormat.format("Writing {0}", indexFile.getAbsolutePath()));
            idxManager.setReader(Index.IDENTIFIER, new IndexWriter.LongIndexStreamer().writeTo(indexFile, id2a));

            if (listener.isCanceled())
                return null;
            listener.worked(1); // 6

            // //////////////////////////////////////////////////////////////
            // object 2 class Id
            // //////////////////////////////////////////////////////////////

            indexFile = Index.O2CLASS.getFile(idx.snapshotInfo.getPrefix());
            listener.subTask(MessageFormat.format("Writing {0}", indexFile.getAbsolutePath()));
            idxManager.setReader(Index.O2CLASS, new IndexWriter.IntIndexStreamer().writeTo(indexFile,
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
                return null;
            listener.worked(1); // 7

            // //////////////////////////////////////////////////////////////
            // array size
            // //////////////////////////////////////////////////////////////

            indexFile = Index.A2SIZE.getFile(idx.snapshotInfo.getPrefix());
            listener.subTask(MessageFormat.format("Writing {0}", new Object[] { indexFile.getAbsolutePath() }));
            final BitField arrayObjects = new BitField(newNoOfObjects);
            // arrayObjects
            idxManager.setReader(Index.A2SIZE, new IndexWriter.IntIndexStreamer().writeTo(indexFile,
                            new NewObjectIntIterator()
                            {
                                IOne2OneIndex a2size = preA2size;
                                int newIndex = 0;

                                @Override
                                int doGetNextInt(int index)
                                {
                                    int size = a2size.get(nextIndex);
                                    if (size > 0)
                                        arrayObjects.set(newIndex);
                                    newIndex++;
                                    return size;
                                }

                                @Override
                                int[] getMap()
                                {
                                    return map;
                                }
                            }));

            preA2size.close();
            preA2size.delete();
            
            if (listener.isCanceled())
                return null;
            listener.worked(1); // 9

            // //////////////////////////////////////////////////////////////
            // inbound, outbound
            // //////////////////////////////////////////////////////////////

            listener.subTask("Re-indexing outbound index");

            IndexWriter.IntArray1NSortedWriter w_out = new IndexWriter.IntArray1NSortedWriter(newNoOfObjects,
                            IndexManager.Index.OUTBOUND.getFile(idx.snapshotInfo.getPrefix()));
            IndexWriter.InboundWriter w_in = new IndexWriter.InboundWriter(newNoOfObjects, IndexManager.Index.INBOUND
                            .getFile(idx.snapshotInfo.getPrefix()));

            for (int ii = 0; ii < oldNoOfObjects; ii++)
            {
                int k = map[ii];
                if (k < 0)
                    continue;

                int[] a = preOutbound.get(ii);
                ArrayInt tl = new ArrayInt(a.length);
                for (int jj = 0; jj < a.length; jj++)
                {
                    int t = map[a[jj]];

                    if (t >= 0)
                    {
                        tl.add(t);
                        w_in.log(t, k, jj == 0);
                    }
                }

                w_out.log(k, tl.toArray());
            }

            preOutbound.close();
            preOutbound.delete();
            preOutbound = null;

            if (listener.isCanceled())
            {
                w_in.cancel();
                w_out.cancel();
                return null;
            }
            listener.worked(1); // 10

            listener.subTask(MessageFormat.format("Writing {0}", w_in.getIndexFile().getAbsolutePath()));

            idxManager.setReader(Index.INBOUND, w_in.flush(listener, new KeyWriterImpl(classesByNewId)));
            w_in = null;
            if (listener.isCanceled())
            {
                w_out.cancel();
                return null;
            }

            listener.worked(1); // 11

            listener.subTask(MessageFormat.format("Writing {0}",
                            new Object[] { w_out.getIndexFile().getAbsolutePath() }));
            idxManager.setReader(Index.OUTBOUND, w_out.flush());
            w_out = null;
            if (listener.isCanceled())
                return null;
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
            // delete all temporary indeces
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
}

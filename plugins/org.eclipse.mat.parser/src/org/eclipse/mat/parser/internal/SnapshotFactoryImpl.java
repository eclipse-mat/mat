/*******************************************************************************
 * Copyright (c) 2008, 2011 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - validation of indices
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.parser.IIndexBuilder;
import org.eclipse.mat.parser.internal.oql.OQLQueryImpl;
import org.eclipse.mat.parser.internal.util.ParserRegistry;
import org.eclipse.mat.parser.internal.util.ParserRegistry.Parser;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.SnapshotFormat;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.mat.util.MessageUtil;

public class SnapshotFactoryImpl implements SnapshotFactory.Implementation
{
    private static class SnapshotEntry
    {
        private int usageCount;
        private WeakReference<ISnapshot> snapshot;

        public SnapshotEntry(int usageCount, ISnapshot snapshot)
        {
            this.usageCount = usageCount;
            this.snapshot = new WeakReference<ISnapshot>(snapshot);
        }
    }

    private Map<File, SnapshotEntry> snapshotCache = new HashMap<File, SnapshotEntry>();

    public ISnapshot openSnapshot(File file, Map<String, String> args, IProgressListener listener) throws SnapshotException
    {
        ISnapshot answer = null;

        // lookup in cache
        SnapshotEntry entry = snapshotCache.get(file);
        if (entry != null)
        {
            answer = entry.snapshot.get();

            if (answer != null)
            {
                entry.usageCount++;
                return answer;
            }
        }

        String name = file.getAbsolutePath();

        int p = name.lastIndexOf('.');
        String prefix = p >= 0 ? name.substring(0, p + 1) : name + ".";//$NON-NLS-1$

        try
        {
            File indexFile = new File(prefix + "index");//$NON-NLS-1$
            if (indexFile.exists())
            {
                // check if hprof file is newer than index file
                if (file.lastModified() < indexFile.lastModified())
                {
                    answer = SnapshotImpl.readFromFile(file, prefix, listener);
                }
                else
                {
                    String message = MessageUtil.format(Messages.SnapshotFactoryImpl_ReparsingHeapDumpAsIndexOutOfDate,
                                    file.getPath(), new Date(file.lastModified()),
                                    indexFile.getPath(), new Date(indexFile.lastModified()));
                    listener.sendUserMessage(Severity.INFO, message, null);
                    listener.subTask(Messages.SnapshotFactoryImpl_ReparsingHeapDumpWithOutOfDateIndex);
                }
            }
        }
        catch (IOException ignore_and_reparse)
        {
            String text = ignore_and_reparse.getMessage() != null ? ignore_and_reparse.getMessage()
                            : ignore_and_reparse.getClass().getName();
            String message = MessageUtil.format(Messages.SnapshotFactoryImpl_Error_ReparsingHeapDump, text);
            listener.sendUserMessage(Severity.WARNING, message, ignore_and_reparse);
            listener.subTask(message);
        }

        if (answer == null)
        {
            deleteIndexFiles(file);
            answer = parse(file, prefix, args, listener);
        }

        entry = new SnapshotEntry(1, answer);

        snapshotCache.put(file, entry);

        return answer;
    }

    public synchronized void dispose(ISnapshot snapshot)
    {

        for (Iterator<SnapshotEntry> iter = snapshotCache.values().iterator(); iter.hasNext();)
        {
            SnapshotEntry entry = iter.next();

            ISnapshot s = entry.snapshot.get();
            if (s == null)
            {
                iter.remove();
            }
            else if (s == snapshot)
            {
                entry.usageCount--;
                if (entry.usageCount == 0)
                {
                    snapshot.dispose();
                    iter.remove();
                }
                return;
            }
        }

        // just in case the snapshot is not stored anymore
        if (snapshot != null)
            snapshot.dispose();
    }

    public IOQLQuery createQuery(String queryString) throws SnapshotException
    {
        return new OQLQueryImpl(queryString);
    }

    public List<SnapshotFormat> getSupportedFormats()
    {
        List<SnapshotFormat> answer = new ArrayList<SnapshotFormat>();

        for (Parser parser : ParserPlugin.getDefault().getParserRegistry().delegates())
            answer.add(parser.getSnapshotFormat());

        return answer;
    }

    // //////////////////////////////////////////////////////////////
    // Internal implementations
    // //////////////////////////////////////////////////////////////

    private final ISnapshot parse(File file, String prefix, Map<String, String> args, IProgressListener listener) throws SnapshotException
    {
        ParserRegistry registry = ParserPlugin.getDefault().getParserRegistry();

        List<ParserRegistry.Parser> parsers = registry.matchParser(file.getName());
        if (parsers.isEmpty())
            parsers.addAll(registry.delegates()); // try all...

        List<IOException> errors = new ArrayList<IOException>();

        for (Parser parser : parsers)
        {
            IIndexBuilder indexBuilder = parser.create(IIndexBuilder.class, ParserRegistry.INDEX_BUILDER);

			if (indexBuilder == null)
				continue;
            
            try
            {
                indexBuilder.init(file, prefix);

                XSnapshotInfo snapshotInfo = new XSnapshotInfo();
                snapshotInfo.setPath(file.getAbsolutePath());
                snapshotInfo.setPrefix(prefix);
                snapshotInfo.setProperty("$heapFormat", parser.getId());//$NON-NLS-1$
                if (Boolean.parseBoolean(args.get("keep_unreachable_objects")))//$NON-NLS-1$
                {
                    snapshotInfo.setProperty("keep_unreachable_objects", GCRootInfo.Type.UNREACHABLE);//$NON-NLS-1$
                }
                PreliminaryIndexImpl idx = new PreliminaryIndexImpl(snapshotInfo);

                indexBuilder.fill(idx, listener);

                validateIndices(idx, listener);

                SnapshotImplBuilder builder = new SnapshotImplBuilder(idx.getSnapshotInfo());

                int[] purgedMapping = GarbageCleaner.clean(idx, builder, args, listener);

                indexBuilder.clean(purgedMapping, listener);

                SnapshotImpl snapshot = builder.create(parser, listener);

                snapshot.calculateDominatorTree(listener);

                return snapshot;
            }
            catch (IOException ioe)
            {
                errors.add(ioe);
                indexBuilder.cancel();
            }
            catch (Exception e)
            {
                indexBuilder.cancel();

                throw SnapshotException.rethrow(e);
            }
        }

        if (errors.size() > 1)
        {
            MultiStatus status = new MultiStatus(ParserPlugin.PLUGIN_ID, 0,
                            MessageUtil.format(Messages.SnapshotFactoryImpl_ErrorOpeningHeapDump, file.getName()), null);
            for (IOException error : errors)
                status.add(new Status(IStatus.ERROR, ParserPlugin.PLUGIN_ID, 0, error.getLocalizedMessage(), error));
            // Create a CoreException so that all the errors will be logged
            CoreException ce = new CoreException(status);

            throw new SnapshotException(MessageUtil.format(Messages.SnapshotFactoryImpl_Error_OpeningHeapDump, file
                            .getName()), ce);
        }
        else if (errors.size() == 1)
        {
            throw new SnapshotException(MessageUtil.format(Messages.SnapshotFactoryImpl_Error_OpeningHeapDump, file
                            .getName()), errors.get(0));
        }
        else
        {
            throw new SnapshotException(MessageUtil.format(Messages.SnapshotFactoryImpl_Error_NoParserRegistered, file
                            .getName()));
        }
    }

    /**
     * Check that indices look valid
     * 
     * @param listener
     */
    private void validateIndices(PreliminaryIndexImpl pidx, IProgressListener listener)
    {
        final int maxIndex = pidx.identifiers.size();
        listener.beginTask(Messages.SnapshotFactoryImpl_ValidatingIndices, maxIndex / 1000 + 1);
        long prevAddress = -1;
        int nObjs = 0;
        int nObjsFromClass = 0;
        int nCls = 0;
        // Look at each object
        for (int i = 0; i < maxIndex; ++i)
        {
            if (i % 1000 == 0)
            {
                if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
                listener.worked(1);
            }
            // Check addresses are in ascending order
            long addr = pidx.identifiers.get(i);
            if (prevAddress == addr)
            {
                String desc = objDesc(pidx, i);
                int j = pidx.identifiers.reverse(addr);
                String desc2 = objDesc(pidx, j);
                listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                Messages.SnapshotFactoryImpl_IndexAddressHasSameAddressAsPrevious, i, desc, format(addr),
                                desc2), null);
            }
            if (prevAddress > addr)
            {
                String desc = objDesc(pidx, i);
                listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                Messages.SnapshotFactoryImpl_IndexAddressIsSmallerThanPrevious, i, desc, format(addr),
                                format(prevAddress)), null);
            }
            prevAddress = addr;
            int j = pidx.identifiers.reverse(addr);
            if (i != j)
            {
                String desc1 = objDesc(pidx, i);
                String desc2 = objDesc(pidx, j);
                listener.sendUserMessage(Severity.ERROR,
                                MessageUtil.format(Messages.SnapshotFactoryImpl_IndexAddressFoundAtOtherID, i,
                                                format(addr), j, desc1, desc2), null);
            }
            // Check the type of each object
            int clsId = pidx.object2classId.get(i);
            if (clsId < 0)
            {
                listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                Messages.SnapshotFactoryImpl_ClassIDNotFound, i, format(addr), clsId), null);
            }
            else
            {
                ClassImpl ci = pidx.classesById.get(clsId);
                if (ci == null)
                {
                    listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_ClassImplNotFound, i, format(addr), clsId), null);
                }
            }
            // Check the outbounds of each object
            int outs[] = pidx.outbound.get(i);
            if (outs == null)
            {
                String desc = objDesc(pidx, i);
                listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                Messages.SnapshotFactoryImpl_NoOutbounds, i, format(addr), desc), null);
            }
            else
            {
                if (outs.length == 0)
                {
                    String desc = objDesc(pidx, i);
                    listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_EmptyOutbounds, i, format(addr), desc), null);
                }
                else 
                {
                    for (int k = 0; k < outs.length; ++k)
                    {
                        if (outs[k] < 0 || outs[k] >= maxIndex)
                        {
                            String desc = objDesc(pidx, i);
                            listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                            Messages.SnapshotFactoryImpl_InvalidOutbound, i, format(addr), desc, k, outs[k]), null);

                        }
                    }
                    if (outs[0] != clsId) {
                        long address = outs[0] >= 0 && outs[0] < maxIndex ? pidx.identifiers.get(outs[0]) : -1;
                        String desc = objDesc(pidx, i);
                        listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_InvalidFirstOutbound, i, format(addr), desc, outs[0], format(address), clsId), null);
                    }
                }
            }
            // Check the object itself, and do special checks for plain objects or class objects
            ClassImpl ci = pidx.classesById.get(i);
            if (ci == null)
            {
                ++nObjs;
                // Ordinary object
                long size = pidx.array2size.getSize(i);
                if (size < 0)
                {
                    ci = pidx.classesById.get(clsId);
                    listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_IndexAddressNegativeArraySize, i, format(addr), size, ci
                                                    .getTechnicalName()), null);
                }
            }
            else
            {
                ++nCls;
                long addr2 = ci.getObjectAddress();
                if (addr != addr2)
                {
                    listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_ClassIndexAddressNotEqualClassObjectAddress, i,
                                    format(addr), format(addr2), ci.getTechnicalName()), null);
                }
                int id = ci.getObjectId();
                if (i != id)
                {
                    listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_ClassIndexNotEqualClassObjectID, i, format(addr), id, ci
                                                    .getTechnicalName()), null);
                }
                int clsId2 = ci.getClassId();
                if (clsId != clsId2)
                {
                    listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_ClassIndexAddressTypeIDNotEqualClassImplClassId, i,
                                    format(addr), clsId, clsId2, ci.getTechnicalName()), null);
                }
                long ldrAddr = ci.getClassLoaderAddress();
                int ldr = ci.getClassLoaderId();
                if (ldr < 0)
                {
                    listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                    Messages.SnapshotFactoryImpl_ClassIndexAddressNoLoaderID, i, format(addr), clsId, ldr,
                                    format(ldrAddr), ci.getTechnicalName()), null);
                }
                nObjsFromClass += ci.getNumberOfObjects();
            }
        }
        if (nObjsFromClass != nObjs + nCls)
        {
            listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                            Messages.SnapshotFactoryImpl_ObjectsFoundButClassesHadObjectsAndClassesInTotal, nObjs, nCls,
                            nObjsFromClass), null);
        }
        listener.subTask(Messages.SnapshotFactoryImpl_ValidatingGCRoots);
        // Check some GC information
        for (IteratorInt it = pidx.gcRoots.keys(); it.hasNext();)
        {
            int idx = it.next();
            if (idx < 0 || idx >= maxIndex)
            {
                listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                Messages.SnapshotFactoryImpl_GCRootIDOutOfRange, idx, maxIndex), null);
            }
            else
            {
                for (ListIterator<XGCRootInfo> it2 = pidx.gcRoots.get(idx).listIterator(); it2.hasNext();)
                {
                    XGCRootInfo ifo = it2.next();
                    int objid = ifo.getObjectId();
                    if (objid != idx)
                    {
                        listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                        Messages.SnapshotFactoryImpl_GCRootIDDoesNotMatchIndex, objid, idx), null);
                    }
                }
            }
        }
        for (int thrd : pidx.thread2objects2roots.getAllKeys())
        {
            if (thrd < 0 || thrd >= maxIndex)
            {
                listener.sendUserMessage(Severity.ERROR,
                                MessageUtil.format(Messages.SnapshotFactoryImpl_GCThreadIDOutOfRange, thrd, maxIndex), null);
            }
            else
            {
                HashMapIntObject<List<XGCRootInfo>> l = pidx.thread2objects2roots.get(thrd);
                for (int idx : l.getAllKeys())
                {
                    if (idx < 0 || idx >= maxIndex)
                    {
                        listener.sendUserMessage(Severity.ERROR, MessageUtil
                                        .format(Messages.SnapshotFactoryImpl_GCThreadRootIDOutOfRange, thrd, idx,
                                                        maxIndex), null);
                    }
                    else
                    {
                        for (XGCRootInfo ifo : l.get(idx))
                        {
                            int objid = ifo.getObjectId();
                            if (objid != idx)
                            {
                                listener.sendUserMessage(Severity.ERROR, MessageUtil.format(
                                                Messages.SnapshotFactoryImpl_GCThreadRootIDDoesNotMatchIndex,
                                                thrd, objid, idx), null);
                            }
                        }
                    }
                }
            }
        }
        listener.done();
    }

    /**
     * Convert an address to a 0x hex number
     * 
     * @param address
     * @return A string representing the address
     */
    private static String format(long address)
    {
        return "0x" + Long.toHexString(address); //$NON-NLS-1$
    }

    /**
     * Describe the class of the object at the given index
     * 
     * @param newObjId
     * @return a class description
     */
    private String objDesc(PreliminaryIndexImpl pidx, int newObjId)
    {
        String clsInfo;
        if (newObjId >= 0)
        {
            ClassImpl classInfo = pidx.classesById.get(newObjId);
            if (classInfo != null)
            {
                clsInfo = MessageUtil.format(Messages.SnapshotFactoryImpl_ObjDescClass, classInfo.getName());
            }
            else
            {
                int clsId = pidx.object2classId.get(newObjId);
                if (clsId >= 0 && clsId < pidx.identifiers.size())
                {
                    long clsAddr = pidx.identifiers.get(clsId);
                    classInfo = pidx.classesById.get(clsId);
                    // If objectToClass has not yet been filled in for objects
                    // then this could be null
                    if (classInfo != null)
                    {
                        clsInfo = MessageUtil.format(Messages.SnapshotFactoryImpl_ObjDescObjType, classInfo.getName(),
                                        format(clsAddr));
                    }
                    else
                    {
                        clsInfo = MessageUtil
                                        .format(Messages.SnapshotFactoryImpl_ObjDescObjTypeAddress, format(clsAddr));
                    }
                }
                else
                {
                    clsInfo = ""; //$NON-NLS-1$
                }
            }
        }
        else
        {
            clsInfo = ""; //$NON-NLS-1$
        }
        return clsInfo;
    }

    private void deleteIndexFiles(File file)
    {
        File directory = file.getParentFile();
        if (directory == null)
            directory = new File("."); //$NON-NLS-1$

        String filename = file.getName();

        int p = filename.lastIndexOf('.');
        final String fragment = p >= 0 ? filename.substring(0, p) : filename;
        final Pattern indexPattern = Pattern.compile("\\.(.*\\.)?index$");//$NON-NLS-1$
        final Pattern logPattern = Pattern.compile("\\.inbound\\.index.*\\.log$");//$NON-NLS-1$

        File[] files = directory.listFiles(new FileFilter()
        {
            public boolean accept(File f)
            {
                if (f.isDirectory())
                    return false;

                String name = f.getName();
                return name.startsWith(fragment)
                                && (indexPattern.matcher(name).matches() || logPattern.matcher(name).matches());
            }
        });

        if (files != null)
        {
            for (File f : files)
                f.delete();
        }
    }
}

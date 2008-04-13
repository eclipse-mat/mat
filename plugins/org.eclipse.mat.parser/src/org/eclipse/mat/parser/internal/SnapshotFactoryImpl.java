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
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.mat.impl.snapshot.ISnapshotFactory;
import org.eclipse.mat.parser.IIndexBuilder;
import org.eclipse.mat.parser.ParserPlugin;
import org.eclipse.mat.parser.internal.oql.OQLQueryImpl;
import org.eclipse.mat.parser.internal.util.ParserRegistry;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;


public class SnapshotFactoryImpl implements ISnapshotFactory
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

    public ISnapshot openSnapshot(File file, IProgressListener listener) throws SnapshotException
    {
        try
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

            String prefix = name.substring(0, name.lastIndexOf('.') + 1);

            try
            {
                File indexFile = new File(prefix + "index");
                if (indexFile.exists())
                {
                    // check if hprof file is newer than index file
                    if (file.lastModified() < indexFile.lastModified())
                    {
                        answer = SnapshotImpl.readFromFile(file, prefix, listener);
                    }
                }
            }
            catch (IOException ignore_and_reparse)
            {
                String message = MessageFormat.format("Reparsing heap dump file due to {0}",
                                new Object[] { ignore_and_reparse.getMessage() });
                listener.sendUserMessage(Severity.WARNING, message, ignore_and_reparse);
            }

            if (answer == null)
            {
                deleteIndexFiles(file);
                answer = parse(file, prefix, listener);
            }

            entry = new SnapshotEntry(1, answer);

            snapshotCache.put(file, entry);

            return answer;
        }
        catch (IOException e)
        {
            throw new SnapshotException(e);
        }
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

    // //////////////////////////////////////////////////////////////
    // Internal implementations
    // //////////////////////////////////////////////////////////////

    private final ISnapshot parse(File file, String prefix, IProgressListener listener) throws IOException,
                    SnapshotException
    {
        ParserRegistry.Parser parser = ParserPlugin.getDefault().getParserRegistry().matchParser(file.getName());
        if (parser == null)
            throw new SnapshotException(MessageFormat.format("No parser registered for file ''{0}''", file.getName()));

        IIndexBuilder indexBuilder = parser.create(IIndexBuilder.class, ParserRegistry.INDEX_BUILDER);

        try
        {
            indexBuilder.init(file, prefix);

            XSnapshotInfo snapshotInfo = new XSnapshotInfo();
            snapshotInfo.setPath(file.getAbsolutePath());
            snapshotInfo.setPrefix(prefix);
            PrelimanryIndexImpl idx = new PrelimanryIndexImpl(snapshotInfo);

            indexBuilder.fill(idx, listener);

            SnapshotImplBuilder builder = new SnapshotImplBuilder(idx.getSnapshotInfo());

            int[] purgedMapping = GarbageCleaner.clean(idx, builder, listener);

            indexBuilder.clean(purgedMapping, listener);

            SnapshotImpl snapshot = builder.create(parser, listener);

            snapshot.calculateDominatorTree(listener);

            return snapshot;
        }
        catch (Exception e)
        {
            indexBuilder.cancel();

            throw SnapshotException.rethrow(e);
        }
    }

    private void deleteIndexFiles(File file)
    {
        File directory = file.getParentFile();
        if (directory == null)
        {
            directory = new File(".");
        }
        String filename = file.getName();

        final Pattern indexPattern = Pattern.compile(filename.substring(0, filename.lastIndexOf('.')) + ".*index");
        final Pattern logPattern = Pattern.compile(filename.substring(0, filename.lastIndexOf('.')) + ".*log");

        String[] names = directory.list(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return indexPattern.matcher(name).matches() || logPattern.matcher(name).matches();
            }
        });

        if (names != null)
        {
            for (String name : names)
            {
                new File(directory, name).delete();
            }
        }
    }
}

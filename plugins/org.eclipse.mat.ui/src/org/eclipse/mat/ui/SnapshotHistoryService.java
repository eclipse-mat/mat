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
package org.eclipse.mat.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.ui.PlatformUI;


public class SnapshotHistoryService
{
    public final static String FILE_NAME = MemoryAnalyserPlugin.getDefault().getStateLocation().toOSString()
                    + File.separator + "snapshotHistory.ser"; //$NON-NLS-1$

    public interface IChangeListener
    {
        void onSnapshotsChanged(List<Entry> visitedSnapshots);
    }

    public static class Entry implements Serializable
    {
        private static final long serialVersionUID = 1L;

        String filePath;
        Long fileLength;
        Serializable info;

        private Entry(String filePath)
        {
            this.filePath = filePath;
        }

        public Long getFileLength()
        {
            return fileLength;
        }

        public String getFilePath()
        {
            return filePath;
        }

        public Serializable getInfo()
        {
            return info;
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof Entry && ((Entry) obj).filePath.equals(filePath);
        }

        @Override
        public int hashCode()
        {
            return filePath.hashCode();
        }

    }

    private static final int NUMBER = 100;
    private static SnapshotHistoryService instance = null;

    LinkedList<Entry> list;
    List<IChangeListener> listeners = new ArrayList<IChangeListener>();

    public static SnapshotHistoryService getInstance()
    {
        if (instance == null)
            instance = new SnapshotHistoryService();

        return instance;
    }

    SnapshotHistoryService()
    {
        this.listeners = new ArrayList<IChangeListener>();
        initializeDocument();
    }

    /**
     * This is a convenience method which extracts the snapshot info object,
     * creates a copy (needed because SnapshotInfo is actually XSnapshotInfo and
     * resides in the private packages of another plugin which causes problems
     * during deserialization) and stores it in the history.
     */
    public void addVisitedPath(IPath path, ISnapshot snapshot)
    {
        SnapshotInfo info = snapshot.getSnapshotInfo();
        Serializable addtlInfo = new SnapshotInfo(info.getPath(), //
                        info.getPrefix(), //
                        info.getJvmInfo(), //
                        info.getIdentifierSize(), //
                        info.getCreationDate(), //
                        info.getNumberOfObjects(), //
                        info.getNumberOfGCRoots(), //
                        info.getNumberOfClasses(), //
                        info.getNumberOfClassLoaders(), //
                        info.getUsedHeapSize());

        addVisitedPath(path, addtlInfo);
    }

    public void addVisitedPath(IPath path, Serializable info)
    {
        Entry e = new Entry(path.toOSString());

        if (list.contains(e))
            list.remove(e);

        e.fileLength = new File(e.filePath).length();
        e.info = info;

        list.addFirst(e);

        if (list.size() > NUMBER)
            list.removeLast();

        saveDocument();

        informListeners();
    }

    public void addVisitedPath(IPath path)
    {
        this.addVisitedPath(path, (Serializable) null);
    }

    public void removePath(IPath path)
    {
        Entry e = new Entry(path.toOSString());

        if (list.remove(e))
        {
            saveDocument();
            informListeners();
        }
    }

    private void informListeners()
    {
        List<Entry> entries = getVisitedEntries();

        for (IChangeListener listener : this.listeners)
            listener.onSnapshotsChanged(entries);
    }

    public List<Entry> getVisitedEntries()
    {
        return Collections.unmodifiableList(list);
    }

    public void addChangeListener(IChangeListener listener)
    {
        this.listeners.add(listener);
    }

    public void removeChangeListener(IChangeListener listener)
    {
        this.listeners.remove(listener);
    }

    @SuppressWarnings("unchecked")
    private synchronized void initializeDocument()
    {
        File file = new File(FILE_NAME);
        if (file.exists())
        {
            try
            {
                final StringBuilder buf = new StringBuilder();

                ObjectInputStream oin = new ObjectInputStream(new FileInputStream(file));
                list = (LinkedList<Entry>) oin.readObject();
                oin.close();

                for (Iterator<Entry> iter = list.iterator(); iter.hasNext();)
                {
                    Entry e = iter.next();
                    if (!new File(e.filePath).exists())
                    {
                        iter.remove();
                        buf.append(e.filePath).append('\n');
                    }
                }

                if (buf.length() > 0)
                {
                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                    {

                        public void run()
                        {
                            MessageDialog.openWarning(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                                            "Some Snapshot from the history list do not exist anymore",
                                            "The following snapshots do not exist anymore. They will be deleted from the history list.\n\nAffected Snapshots:\n\n"
                                                            + buf.toString());
                        }

                    });
                }

            }
            catch (Exception ignore)
            {
                MemoryAnalyserPlugin.log(ignore);
            }
        }

        if (list == null)
            list = new LinkedList<Entry>();
    }

    private synchronized void saveDocument()
    {
        try
        {
            if (!list.isEmpty())
            {
                ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream(FILE_NAME));
                oout.writeObject(list);
                oout.close();
            }
            else
            {
                File file = new File(FILE_NAME);
                file.delete();
            }
        }
        catch (IOException ignore)
        {
            MemoryAnalyserPlugin.log(ignore);
        }
    }

}

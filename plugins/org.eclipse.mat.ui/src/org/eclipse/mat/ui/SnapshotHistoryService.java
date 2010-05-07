/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;

public class SnapshotHistoryService
{
    private final static String FILE_NAME = MemoryAnalyserPlugin.getDefault().getStateLocation().toOSString()
                    + File.separator + "snapshotHistory.ser"; //$NON-NLS-1$

    public interface IChangeListener
    {
        void onFileHistoryChange(List<Entry> visited);
    }

    public static class Entry implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private String editorId;
        private String filePath;
        private Long fileLength;
        private Serializable info;

        private Entry(String editorId, String filePath)
        {
            this.editorId = editorId;
            this.filePath = filePath;
        }

        public Long getFileLength()
        {
            return fileLength;
        }

        public String getEditorId()
        {
            return editorId;
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
    private static SnapshotHistoryService INSTANCE = new SnapshotHistoryService();

    private LinkedList<Entry> list;
    private List<IChangeListener> listeners = new ArrayList<IChangeListener>();

    public static SnapshotHistoryService getInstance()
    {
        return INSTANCE;
    }

    private SnapshotHistoryService()
    {
        this.listeners = new ArrayList<IChangeListener>();
        initializeDocument();
    }

    public synchronized void addVisitedPath(String editorId, String path, Serializable info)
    {
        Entry e = new Entry(editorId, path);
        e.fileLength = new File(e.filePath).length();
        e.info = info;

        List<Entry> copy = null;

        synchronized (list)
        {
            if (list.contains(e))
                list.remove(e);

            list.addFirst(e);

            if (list.size() > NUMBER)
                list.removeLast();

            copy = new ArrayList<Entry>(list);
        }

        saveDocument(copy);
        informListeners(copy);
    }

    public void addVisitedPath(String editorId, String path)
    {
        this.addVisitedPath(editorId, path, (Serializable) null);
    }

    public void removePath(IPath path)
    {
        String filename = path.toOSString();

        List<Entry> copy = null;

        synchronized (list)
        {
            for (Iterator<Entry> iter = list.iterator(); iter.hasNext();)
            {
                Entry entry = iter.next();
                if (entry.getFilePath().equals(filename))
                {
                    iter.remove();
                    copy = new ArrayList<Entry>(list);
                    break;
                }
            }
        }

        if (copy != null)
        {
            saveDocument(copy);
            informListeners(copy);
        }
    }

    private void informListeners(List<Entry> copy)
    {
        for (IChangeListener listener : new ArrayList<IChangeListener>(listeners))
            listener.onFileHistoryChange(copy);
    }

    public List<Entry> getVisitedEntries()
    {
        synchronized (list)
        {
            return new ArrayList<Entry>(list);
        }
    }

    public void addChangeListener(IChangeListener listener)
    {
        this.listeners.add(listener);
    }

    public void removeChangeListener(IChangeListener listener)
    {
        this.listeners.remove(listener);
    }

    /**
     * only called from constructor
     */
    private void initializeDocument()
    {
        File file = new File(FILE_NAME);
        if (file.exists())
        {
            try
            {
                final StringBuilder buf = new StringBuilder();

                list = new LinkedList<Entry>();

                ObjectInputStream oin = new ObjectInputStream(new FileInputStream(file));
                int size = oin.readInt();
                for (int ii = 0; ii < size; ii++)
                    list.add((Entry) oin.readObject());
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
                                            Messages.SnapshotHistoryService_SnapshotsDoNotExist,
                                            Messages.SnapshotHistoryService_NonExistingSnapshotsWillBeDeleted
                                                            + buf.toString());
                        }

                    });
                }

            }
            catch (IOException ignore)
            {
                MemoryAnalyserPlugin.log(ignore);
                new File(FILE_NAME).delete();
            }
            catch (Exception ignore)
            {
                MemoryAnalyserPlugin.log(ignore);
            }
        }

        if (list == null)
            list = new LinkedList<Entry>();
    }

    private static void saveDocument(List<Entry> copy)
    {
        try
        {
            if (!copy.isEmpty())
            {
                ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream(FILE_NAME));
                oout.writeInt(copy.size());
                for (Entry entry : copy)
                    oout.writeObject(entry);
                oout.flush();
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

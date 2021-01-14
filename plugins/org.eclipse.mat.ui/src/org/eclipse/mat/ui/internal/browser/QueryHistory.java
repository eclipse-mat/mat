/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.browser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.mat.ui.MemoryAnalyserPlugin;

public class QueryHistory
{
    private final static int HISTORY_LIMIT = 50;

    private final static String FILE_NAME = MemoryAnalyserPlugin.getDefault().getStateLocation().toOSString()
                    + File.separator + "commandHistory.ser"; //$NON-NLS-1$

    private static LinkedList<String> history = null;

    public synchronized static void addQuery(String queryString)
    {
        if (history == null)
            loadHistory();

        // it is very likely the user picked a very recent command
        for (Iterator<String> iter = history.iterator(); iter.hasNext();)
        {
            String item = iter.next();

            if (queryString.equals(item))
            {
                iter.remove();
                break;
            }
        }

        history.addFirst(queryString);

        while (history.size() > HISTORY_LIMIT)
            history.removeLast();

        saveHistory();
    }

    public synchronized static List<String> getHistoryEntries()
    {
        if (history == null)
            loadHistory();
        return new ArrayList<String>(history);
    }

    @SuppressWarnings("unchecked")
    private synchronized static void loadHistory()
    {
        File file = new File(FILE_NAME);
        if (file.exists())
        {
            try
            {
                /**
                 * org.eclipse.mat.ui.SnapshotHistoryService$Entry
                 * java.lang.Long
                 * java.lang.Number
                 * org.eclipse.mat.snapshot.SnapshotInfo
                 * java.util.Date
                 * java.util.HashMap
                 * java.lang.Boolean
                 */
                ObjectInputStream oin = new ObjectInputStream(new FileInputStream(file)) {
                    @Override
                    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                        // similar to system property jdk.serialFilter
                        String match="java.lang.*;java.util.*;org.eclipse.mat.snapshot.*;org.eclipse.mat.ui.SnapshotHistoryService$Entry;!*"; //$NON-NLS-1$
                        String nm = desc.getName();
                        if (!nm.startsWith("[")) //$NON-NLS-1$
                        {
                            for (String pt : match.split(";")) //$NON-NLS-1$
                            {
                                boolean not = pt.startsWith("!"); //$NON-NLS-1$
                                if (not)
                                    pt = pt.substring(1);
                                boolean m;
                                if (pt.endsWith(".**")) //$NON-NLS-1$
                                    m = nm.startsWith(pt.substring(0, pt.length() - 2));
                                else if (pt.endsWith(".*")) //$NON-NLS-1$
                                    m = nm.startsWith(pt.substring(0, pt.length() - 1))
                                    && !nm.substring(pt.length() - 1).contains("."); //$NON-NLS-1$
                                else if (pt.endsWith("*")) //$NON-NLS-1$
                                    m = nm.startsWith(pt.substring(0, pt.length() - 1));
                                else
                                    m = nm.equals(pt);
                                if (not && m)
                                    throw new InvalidClassException(nm, match);
                                if (m)
                                    break;
                            }
                        }
                        return super.resolveClass(desc);
                    }
                };
                try
                {
                    history = (LinkedList<String>) oin.readObject();
                }
                finally
                {
                    oin.close();
                }
            }
            catch (Exception ignore)
            {
                MemoryAnalyserPlugin.log(ignore);
            }
        }

        if (history == null)
            history = new LinkedList<String>();

    }

    private static void saveHistory()
    {
        try
        {
            if (!history.isEmpty())
            {
                final FileOutputStream out = new FileOutputStream(FILE_NAME);
                try
                {
                    ObjectOutputStream oout = new ObjectOutputStream(out);
                    try
                    {
                        oout.writeObject(history);
                    }
                    finally
                    {
                        oout.close();
                    }
                }
                finally
                {
                    out.close();
                }
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

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
package org.eclipse.mat.ui.internal.browser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    private static void loadHistory()
    {
        File file = new File(FILE_NAME);
        if (file.exists())
        {
            try
            {
                ObjectInputStream oin = new ObjectInputStream(new FileInputStream(file));
                history = (LinkedList<String>) oin.readObject();
                oin.close();
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
                ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream(FILE_NAME));
                oout.writeObject(history);
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

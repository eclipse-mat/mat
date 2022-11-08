/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - FindBugs fix
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.util.MessageUtil;

/* package */class ThreadStackHelper
{
    private static final Logger logger = Logger.getLogger(ThreadStackHelper.class.getName());

    /* package */static HashMapIntObject<IThreadStack> loadThreadsData(ISnapshot snapshot) throws SnapshotException
    {
        String fileName = snapshot.getSnapshotInfo().getPrefix() + "threads"; //$NON-NLS-1$
        File f = new File(fileName);
        if (!f.exists())
            return null;

        HashMapIntObject<IThreadStack> threadId2stack = new HashMapIntObject<IThreadStack>();

        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));) //$NON-NLS-1$
        {
            String line = in.readLine();

            while (line != null)
            {
                line = line.trim();
                if (line.startsWith("Thread")) //$NON-NLS-1$
                {
                    long threadAddress = readThreadAddres(line);
                    List<String> lines = new ArrayList<String>();
                    HashMapIntObject<ArrayInt> line2locals = new HashMapIntObject<ArrayInt>();

                    line = in.readLine();
                    while (line != null && !line.equals("")) //$NON-NLS-1$
                    {
                        lines.add(line.trim());
                        line = in.readLine();
                    }

                    line = in.readLine();
                    if (line != null && line.trim().startsWith("locals")) //$NON-NLS-1$
                    {
                        while ((line = in.readLine()) != null && !line.equals("")) //$NON-NLS-1$
                        {
                            int lineNr = readLineNumber(line);
                            if (lineNr >= 0)
                            {
                                int objectId;
                                try
                                {
                                    objectId = readLocalId(line, snapshot);
                                }
                                catch (SnapshotException e)
                                {
                                    logger.log(Level.WARNING, MessageUtil.format(Messages.ThreadStackHelper_InvalidThreadLocal, line.trim(),
                                                    "0x" + Long.toHexString(threadAddress), e.getLocalizedMessage())); //$NON-NLS-1$
                                    continue;
                                }
                                ArrayInt arr = line2locals.get(lineNr);
                                if (arr == null)
                                {
                                    arr = new ArrayInt();
                                    line2locals.put(lineNr, arr);
                                }
                                arr.add(objectId);
                            }
                        }
                    }

                    if (threadAddress != -1)
                    {
                        try
                        {
                            int threadId = snapshot.mapAddressToId(threadAddress);
                            IThreadStack stack = new ThreadStackImpl(threadId, buildFrames(lines, line2locals));
                            threadId2stack.put(threadId, stack);
                        }
                        catch (SnapshotException se)
                        {
                            // See
                            // https://bugs.eclipse.org/bugs/show_bug.cgi?id=520908
                            logger.log(Level.WARNING, MessageUtil.format(Messages.ThreadStackHelper_InvalidThread,
                                            "0x" + Long.toHexString(threadAddress), se.getLocalizedMessage())); //$NON-NLS-1$
                        }
                    }
                }

                if (line != null)
                    line = in.readLine();
                else
                    break;
            }
        }
        catch (IOException e)
        {
            throw new SnapshotException(e);
        }

        return threadId2stack;

    }

    private static long readThreadAddres(String line)
    {
        int start = line.indexOf("0x"); //$NON-NLS-1$
        if (start < 0)
            return -1;
        return (new BigInteger(line.substring(start + 2), 16)).longValue();
    }

    private static int readLocalId(String line, ISnapshot snapshot) throws SnapshotException
    {
        int start = line.indexOf("0x"); //$NON-NLS-1$
        int end = line.indexOf(',', start);
        long address = (new BigInteger(line.substring(start + 2, end), 16)).longValue();
        return snapshot.mapAddressToId(address);
    }

    private static int readLineNumber(String line)
    {
        int start = line.indexOf("line="); //$NON-NLS-1$
        return Integer.parseInt(line.substring(start + 5));
    }

    private static StackFrameImpl[] buildFrames(List<String> lines, HashMapIntObject<ArrayInt> line2locals)
    {
        int sz = lines.size();

        StackFrameImpl[] frames = new StackFrameImpl[sz];
        for (int i = 0; i < sz; i++)
        {
            int[] localsIds = null;
            ArrayInt locals = line2locals.get(i);
            if (locals != null && locals.size() > 0)
            {
                localsIds = locals.toArray();
            }
            frames[i] = new StackFrameImpl(lines.get(i), localsIds);
        }

        return frames;

    }

}

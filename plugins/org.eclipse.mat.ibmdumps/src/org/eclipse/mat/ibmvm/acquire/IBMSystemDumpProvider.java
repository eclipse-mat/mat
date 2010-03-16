/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.util.IProgressListener;

@Name("IBM System Dump")
public class IBMSystemDumpProvider extends IBMDumpProvider
{
    public IBMSystemDumpProvider()
    {}

    @Override
    protected String agentCommand()
    {
        return "system"; //$NON-NLS-1$
    }

    @Override
    String dumpName()
    {
        return "core.YYmmdd.HHMMSS.%pid%.seq.dmp"; //$NON-NLS-1$
    }

    int files()
    {
        return 1;
    }

    @Override
    long averageFileSize(Collection<File> files)
    {
        long l = 0;
        int i = 0;
        for (File f : files)
        {
            if (f.isFile() && f.getName().endsWith(".dmp")) { //$NON-NLS-1$
                l += f.length();
                ++i;
            }
        }
        if (i > 0)
            return l / i;
        else
            // guess 100MB
            return 100000000L;
    }

    @Override
    File jextract(File preferredDump, File dump, File udir, File home, IProgressListener listener) throws IOException,
                    InterruptedException, SnapshotException
    {
        File result;
        listener.beginTask(Messages.getString("IBMSystemDumpProvider.FormattingDump"), IProgressListener.UNKNOWN_TOTAL_WORK); //$NON-NLS-1$

        ProcessBuilder pb = new ProcessBuilder();
        File jextract;
        if (home != null)
        {
            File homebin = new File(home, "bin"); //$NON-NLS-1$
            jextract = new File(homebin, "jextract"); //$NON-NLS-1$
        }
        else
        {
            jextract = new File("jextract"); //$NON-NLS-1$
        }
        if (preferredDump.getName().endsWith(".zip")) { //$NON-NLS-1$
            pb.command(jextract.getAbsolutePath(), dump.getAbsolutePath(), preferredDump.getAbsolutePath());
            result = preferredDump;
        }
        else
        {
            pb.command(jextract.getAbsolutePath(), dump.getAbsolutePath(), "-nozip"); //$NON-NLS-1$
            result = dump;
        }
        pb.redirectErrorStream(true);
        pb.directory(udir);
        StringBuilder errorBuf = new StringBuilder();
        ;
        int exitCode = 0;
        Process p = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        try
        {
            for (;;)
            {

                while (br.ready())
                {
                    int t = br.read();
                    if (t < 0)
                        break;
                    if (t == '.')
                        listener.worked(1);
                    errorBuf.append((char) t);
                }
                listener.subTask("\n" + errorBuf.toString()); //$NON-NLS-1$

                if (listener.isCanceled())
                {
                    p.destroy();
                    return null;
                }
                try
                {
                    p.exitValue();
                    break;
                }
                catch (IllegalThreadStateException e)
                {
                    Thread.sleep(SLEEP_TIMEOUT);
                }
            }
        }
        finally
        {
            br.close();
        }

        exitCode = p.waitFor();
        if (exitCode != 0) { throw new SnapshotException(MessageFormat.format(Messages
                        .getString("IBMSystemDumpProvider.ReturnCode"), jextract.getAbsolutePath(), exitCode, errorBuf.toString())); //$NON-NLS-1$
        }

        if (!result.canRead()) { throw new FileNotFoundException(MessageFormat.format(Messages
                        .getString("IBMSystemDumpProvider.ReturnCode"), result.getPath(), errorBuf.toString())); //$NON-NLS-1$
        }

        listener.done();

        return result;

    }
}

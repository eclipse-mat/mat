/*******************************************************************************
 * Copyright (c) 2010, 2021 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Helper dump provider - the IBMDumpProvider delegates to this to do the work for system dumps.
 * @author ajohnson
 */
class IBMSystemDumpProvider extends IBMDumpProvider
{
    IBMSystemDumpProvider()
    {}

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

    /**
     * Run jextract and move the files to the target directory
     * @param preferredDump
     * @param dumps
     * @param udir
     * @param home
     * @param listener
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws SnapshotException
     */
    @Override
    File jextract(File preferredDump, boolean compress, List<File>dumps, File udir, File home, IProgressListener listener) throws IOException,
                    InterruptedException, SnapshotException
    {
        File dump = dumps.get(0);
        File result;
        String encoding = System.getProperty("file.encoding", "UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
        String encodingOpt = "-J-Dfile.encoding="+encoding; //$NON-NLS-1$
        preferredDump = mergeFileNames(preferredDump, dump);
        
        if (compress && !preferredDump.getName().endsWith(".zip")) //$NON-NLS-1$
        {
            preferredDump = new File(preferredDump.getPath()+".zip"); //$NON-NLS-1$;
        }

        int work = 1000;
        listener.beginTask(MessageFormat.format(Messages.getString("IBMSystemDumpProvider.FormattingDump"), //$NON-NLS-1$
                           dump, preferredDump), work); 


        final boolean zip = preferredDump.getName().endsWith(".zip"); //$NON-NLS-1$

        // Only need to run jextract to zip a dump - DTFJ can now read dumps directly
        if (zip)
        {
            long dumpLen = dump.length();
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
            pb.command(jextract.getAbsolutePath(), encodingOpt, dump.getAbsolutePath(), preferredDump.getAbsolutePath());
            result = preferredDump;

            pb.redirectErrorStream(true);
            pb.directory(udir);
            StringBuilder errorBuf = new StringBuilder();

            int exitCode = 0;
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), encoding));
            long outlen = 0;
            // space for DLLs plus dump compression
            long estimatedZipLen = 64*1024*1024 + dumpLen / 5;
            long maxEstimatedZipLen = estimatedZipLen * 2;
            // Fraction of estimate length to then switch to maxEstimatedLength
            int f1 = 3;
            int f2 = 4;
            try
            {
                for (;;)
                {

                    while (br.ready())
                    {
                        int t = br.read();
                        if (t < 0)
                            break;
                        errorBuf.append((char) t);
                    }
                    listener.subTask("\n" + errorBuf.toString()); //$NON-NLS-1$
                    long newlen = result.length();
                    long step;
                    if (newlen < f1 * estimatedZipLen / f2)
                    {
                        step = estimatedZipLen / work;
                    }
                    else
                    {
                        // Slow progress down when the zip file keeps growing
                        // past 3/4 of expected length.
                        step = (maxEstimatedZipLen - f1*estimatedZipLen/f2) * f2 / (f2 - f1) / work;
                    }
                    if (newlen - outlen > step)
                    {
                        listener.worked((int) ((newlen - outlen) / step));
                        outlen += (newlen - outlen) / step * step;
                    }

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

            // Tidy up
            if (!result.getCanonicalFile().equals(dump.getCanonicalPath()))
            {
                dump.delete();
            }
        }
        else
        {
            // Move dump
            if (dump.renameTo(preferredDump))
            {
                // Success
                dump = preferredDump;
            }
            else
            {
                // Failed, use original dump
            }
            result = dump;
        }

        listener.done();

        return result;

    }
}

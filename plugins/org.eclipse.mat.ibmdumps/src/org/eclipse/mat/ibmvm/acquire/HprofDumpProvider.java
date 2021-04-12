/*******************************************************************************
 * Copyright (c) 2018,2021 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation/Andrew Johnson - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Helper dump provider - the IBMDumpProvider delegates to this to do the work for HPROF dumps.
 * @author ajohnson
 */
class HprofDumpProvider extends IBMDumpProvider
{
    HprofDumpProvider()
    {}

    @Override
    String dumpName()
    {
        return "java_pid%pid%.seq.hprof"; //$NON-NLS-1$
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
            if (f.isFile() && f.getName().endsWith(".hprof")) { //$NON-NLS-1$
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
    public File jextract(File preferredDump, boolean compress, List<File>dumps, File udir, File javahome, IProgressListener listener) throws IOException, InterruptedException, SnapshotException
    {
        if (compress)
        {
            if (dumps.size() != 1)
                throw new IllegalArgumentException();
            File dump = dumps.get(0);
            return compressFile(preferredDump, dump, listener);
        }
        else
        {
            return super.jextract(preferredDump, compress, dumps, udir, javahome, listener);
        }
    }

    File compressFile(File preferredDump, File dump, IProgressListener listener) throws IOException
    {
        File dumpout = preferredDump.getCanonicalFile().equals(dump.getCanonicalFile()) ? 
                       File.createTempFile(dump.getName(),  null, dump.getParentFile())
                     : preferredDump;
        int bufsize = 64 * 1024;
        int work = (int)(dumpout.length() / bufsize);
        listener.beginTask(Messages.getString("IBMDumpProvider.CompressingDump"), work); //$NON-NLS-1$
        InputStream is = new BufferedInputStream(new FileInputStream(dump), bufsize);
        try
        {
            OutputStream os = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(dumpout)));
            try
            {
                byte buffer[] = new byte[bufsize];
                for (;;)
                {
                    if (listener.isCanceled())
                        return null;
                    int r;
                    r = is.read(buffer);
                    if (r > 0)
                        os.write(buffer, 0, r);
                    else
                        break;
                    listener.worked(1);
                }
            }
            finally
            {
                os.close();
            }
        }
        finally
        {
            is.close();
        }
        if (dump.delete())
        {
            if (!dumpout.getCanonicalFile().equals(preferredDump.getCanonicalFile()) && !dumpout.renameTo(preferredDump))
            {
                throw new IOException(preferredDump.getPath());
            }
        }
        else
        {
            if (!dumpout.delete())
            {
                throw new IOException(dumpout.getPath());
            }
            // Return uncompressed
            preferredDump = dump;
        }
        listener.done();
        return preferredDump;
    }
}

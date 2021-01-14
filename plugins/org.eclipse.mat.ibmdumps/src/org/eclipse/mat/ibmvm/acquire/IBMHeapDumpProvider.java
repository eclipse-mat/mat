/*******************************************************************************
 * Copyright (c) 2010,2019 IBM Corporation
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Helper dump provider - the IBMDumpProvider delegates to this to do the work for heap dumps.
 * @author ajohnson
 */
class IBMHeapDumpProvider extends IBMDumpProvider {
    IBMHeapDumpProvider()
    {
    }
    
    @Override
    String dumpName() {
        return "heapdump.YYmmdd.HHMMSS.%pid%.seq.phd"; //$NON-NLS-1$
    }
    
    @Override
    int files() {
    	return 2;
    }
    
    @Override
    long averageFileSize(Collection<File> files) {
        long l1 = 0;
        int i1 = 0;
        long l2 = 0;
        int i2 = 0;
        for (File f : files)
        {
            if (f.isFile()) {
                String s = f.getName();
                if (s.endsWith(".phd")) { //$NON-NLS-1$
                    l1 += f.length();
                    ++i1;
                } else if (s.endsWith(".txt")) { //$NON-NLS-1$
                    l2 += f.length();
                    ++i2;
                }
            }
        }
        if (i1 > 0 && i2 > 0)
            // both phd and txt
            return (l1 / i1 + l2 / i2) / 2;
        else if (i1 > 0)
            // just phd
            return l1 / i1;
        else if (i2 > 0)
            // just txt
            return l2 / i2;
        else 
            // guess 10MB
            return 10000000L;
    }

    /**
     * The target JVM will have generated a dump of the form
     * heapdump.yyyyMMdd.HHmmss.pid.seq.phd
     * javacore.yyyyMMdd.HHmmss.pid.seq.txt
     * or
     * heapdump&lt;pid&gt;.&lt;timestamp&gt;.phd
     * javacore&lt;pid&gt;.&lt;timestamp&gt;.txt
     * 
     * Options:
     * 1.leave dumps where are
     * 2.move dumps and rename them based on the target
     *    targetfile.xyz
     *    this doesn't work when the user specifies the wrong name
     * 3.move dumps, rename metafiles to match dump file
     * 
     * Only move dumps with heapdump or javacore in the name
     * If the preferred name ends with .gz then compress the dump file.
     * @param preferredDump
     * @param dumps
     * @param udir
     * @param javahome
     * @param listener
     * @return the resultant file
     * @throws IOException
     * @throws InterruptedException
     * @throws SnapshotException
     */
    @Override
    public File jextract(File preferredDump, boolean compress, List<File>dumps, File udir, File javahome, IProgressListener listener) throws IOException, InterruptedException, SnapshotException
    {

        File result = mergeFileNames(preferredDump, dumps.get(0));

        
        if (compress && !result.getName().endsWith(".gz")) //$NON-NLS-1$
        {
            result = new File(result.getPath()+".gz"); //$NON-NLS-1$
        }
            
        
        // See if dump names look as expected
        for (File dump : dumps)
        {
            String name = dump.getName();
            if (!name.contains("heapdump") && //$NON-NLS-1$
                !name.contains("javacore")) //$NON-NLS-1$
            {
                // Play safe and don't move the original files
                return super.jextract(preferredDump, compress, dumps, udir, javahome, listener);
            }
        }
        // User wants to zip up the dump?
        final boolean zip = result.getName().endsWith(".gz"); //$NON-NLS-1$
        if (zip)
        {
            listener.subTask(Messages.getString("IBMDumpProvider.CompressingDump")); //$NON-NLS-1$
            int bufsize = 64 * 1024;
            InputStream is = new BufferedInputStream(new FileInputStream(dumps.get(0)), bufsize);
            try
            {
                OutputStream os = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(result)));
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
                    }
                }
                finally
                {
                    os.close();
                }
            }
            catch (IOException e)
            {
                return super.jextract(preferredDump, compress, dumps, udir, javahome, listener);
            }
            finally
            {
                is.close();
            }
        }
        int renamed = 0;
        for (int i = zip ? 1 : 0; i < dumps.size(); ++i)
        {
            File dump = dumps.get(i);
            String name;
            File dest;
            if (i == 0)
            {
                dest = result;
            }
            else
            {
                // Always rename other files using result name + source extension
                String name1 = result.getName();
                String name2 = dumps.get(i).getName();
                int e1 = name1.lastIndexOf('.');
                int e2 = name2.lastIndexOf('.');
                if (e1 >= 0 && e2 >= 0)
                    name = name1.substring(0, e1) + name2.substring(e2);
                else
                    name = name2;
                dest = new File(result.getParentFile(), name);
            }
            boolean rn = dump.renameTo(dest);
            if (rn)
            {
                ++renamed;
            }
            else if (renamed == 0)
            {
                // Failed to rename anything, so give up now
                return super.jextract(preferredDump, compress, dumps, udir, javahome, listener);
            }
        }
        if (zip)
        {
            // No need to keep the uncompressed dump
            dumps.get(0).delete();
        }
        return result;
    }
}

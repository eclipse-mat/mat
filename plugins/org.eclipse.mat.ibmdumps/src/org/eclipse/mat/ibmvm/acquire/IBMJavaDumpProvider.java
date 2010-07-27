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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Helper dump provider - the IBMDumpProvider delegates to this to do the work for java core dumps.
 * @author ajohnson
 *
 */
public class IBMJavaDumpProvider extends IBMHeapDumpProvider
{
    @Override
    public int files()
    {
        return 1;
    }
    
    @Override
    long averageFileSize(Collection<File> files) {
        long l1 = 0;
        int i1 = 0;
        for (File f : files)
        {
            if (f.isFile()) {
                String s = f.getName();
                if (s.endsWith(".txt")) { //$NON-NLS-1$
                    l1 += f.length();
                    ++i1;
                }
            }
        }
        if (i1 > 0)
            // just javacore
            return l1 / i1;
        else 
            // guess 1MB
            return 1000000L;
    }
    
    /*
     * Call the superclass java/heapdump jextract method.
     * Don't compress javacore files as the reader cannot open them.
     * @param preferredDump
     * @param dumps
     * @param udir
     * @param javahome
     * @param listener
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws SnapshotException
     */
    @Override
    public File jextract(File preferredDump, boolean compress, List<File>dumps, File udir, File javahome, IProgressListener listener) throws IOException, InterruptedException, SnapshotException
    {
        return super.jextract(preferredDump, false, dumps, udir, javahome, listener);
    }
}

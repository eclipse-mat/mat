/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation/Andrew Johnson - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

import java.io.File;
import java.util.Collection;

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

}

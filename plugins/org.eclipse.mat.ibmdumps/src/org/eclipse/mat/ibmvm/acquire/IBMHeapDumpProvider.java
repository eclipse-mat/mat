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
import java.util.Collection;

import org.eclipse.mat.query.annotations.Name;

@Name("IBM Heap Dump")
public class IBMHeapDumpProvider extends IBMDumpProvider {
    public IBMHeapDumpProvider()
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
}
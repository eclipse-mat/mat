/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - Generate a collections dump from the current MAT process
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.HeapDumpProviderRegistry;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.tests.CreateCollectionDump;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class ExtractCollectionEntriesTest3 extends ExtractCollectionEntriesTest2
{
    public ExtractCollectionEntriesTest3(String file) {
         super(file);
    }
    
    @Parameters
    public static Collection<Object[]> data3() throws SnapshotException, IOException
    {
        List<Object[]> parms = new ArrayList<Object[]>();
        
        Collection<HeapDumpProviderDescriptor> descs = HeapDumpProviderRegistry.instance().getHeapDumpProviders();
        int count = 0;
        int found = 0;
        File tmpdir = TestSnapshots.createGeneratedName("collections", null);
        for (HeapDumpProviderDescriptor hd : descs)
        {
            IHeapDumpProvider hdp = hd.getHeapDumpProvider();
            System.out.println("Heap dump provider "+hdp);
            assertThat("Heap Dump Provider", hdp, notNullValue());
            IProgressListener l = new VoidProgressListener();
            List<? extends VmInfo> ls;
            try
            {
                ls = hdp.getAvailableVMs(l);
            }
            catch (SnapshotException e)
            {
                continue;
            }
            assertThat("Heap dump provider " + hdp, ls, notNullValue());
            if (ls == null)
                continue;
            for (VmInfo vm : ls)
            {
                ++count;
                String desc = vm.getDescription();
                System.out.println("Desc "+desc);
                assertThat("VM description", desc, notNullValue());
                if (desc.contains("org.eclipse.mat.tests"))
                {
                    ++found;
                    System.out.println("Desc " + desc);
                    File f = new File(vm.getProposedFileName());
                    System.out.println("Proposed name " + f);
                    String fname = f.getName();
                    int ldot = fname.lastIndexOf('.');
                    // Give the dumps different names so they are not overwritten or fail with the same name
                    String fname2 = "acquire_dump_" + found + fname.substring(ldot);
                    File tmpdump = new File(tmpdir, fname2);
                    System.out.println("Dump " + tmpdump);
                    CreateCollectionDump cdp = new CreateCollectionDump();
                    File dmp = hdp.acquireDump(vm, tmpdump, l);
                    parms.add(new Object[] { dmp.getAbsolutePath() });
                    // To ensure it isn't garbage collected early
                    System.out.println(cdp);
                }
            }
        }
        return parms;
    }

}

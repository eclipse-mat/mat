/*******************************************************************************
 * Copyright (c) 2015, 2020 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson - Generate a collections dump from the current MAT process
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assume.assumeThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.HeapDumpProviderRegistry;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.tests.CreateCollectionDump;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(value = Parameterized.class)
public class ExtractCollectionEntriesTest3 extends ExtractCollectionEntriesTest2
{
    int type;
    String classname;
    public ExtractCollectionEntriesTest3(String file, String dumpname, int type, String classname ) {
         super(file);
         this.type = type;
         this.classname = classname;
    }

    @Parameters(name = "{1} {2} {3}")
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
                    File dmp;
                    try
                    {
                        dmp = hdp.acquireDump(vm, tmpdump, l);
                    }
                    catch (SnapshotException e)
                    {
                        if (e.getMessage().contains("Unsuitable target"))
                        {
                            // Java 9 cannot attach to itself
                            System.out.println("Ignoring dump as: "+e.getMessage());
                            continue;
                        }
                        else
                        {
                            throw e;
                        }
                    }
                    // List collections
                    String dmppath = dmp.getAbsolutePath();
                    System.out.println("Dump "+dmppath+" "+dmp.length());
                    String dmpname = hdp.getClass().getName()+" "+dmp.getName();
                    for (Collection<?> c : cdp.getListCollectionTestData())
                    {
                        Object[] objects = new Object[] { dmppath, dmpname, 1, c.getClass().getName() };
                        for (Object o[] : parms)
                        {
                            if (Arrays.equals(objects,  o))
                            {
                                objects = null;
                                break;
                            }
                        }
                        if (objects != null)
                            parms.add(objects);
                    }
                    // Non-List collections
                    for (Collection<?> c : cdp.getNonListCollectionTestData())
                    {
                        Object[] objects = new Object[] { dmppath, dmpname, 2, c.getClass().getName() };
                        for (Object o[] : parms)
                        {
                            if (Arrays.equals(objects,  o))
                            {
                                objects = null;
                                break;
                            }
                        }
                        if (objects != null)
                            parms.add(objects);
                    }
                    // Empty List collections
                    for (Collection<?> c : cdp.getEmptyListCollectionTestData())
                    {
                        Object[] objects = new Object[] { dmppath, dmpname, 3, c.getClass().getName() };
                        for (Object o[] : parms)
                        {
                            if (Arrays.equals(objects,  o))
                            {
                                objects = null;
                                break;
                            }
                        }
                        if (objects != null)
                            parms.add(objects);
                    }
                    // Empty Non-List collections
                    for (Collection<?> c : cdp.getEmptyNonListCollectionTestData())
                    {
                        Object[] objects = new Object[] { dmppath, dmpname, 4, c.getClass().getName() };
                        for (Object o[] : parms)
                        {
                            if (Arrays.equals(objects,  o))
                            {
                                objects = null;
                                break;
                            }
                        }
                        if (objects != null)
                            parms.add(objects);
                    }
                    // Maps
                    for (Map<?,?> m : cdp.getMapTestData())
                    {
                        Object[] objects = new Object[] { dmppath, dmpname, 5, m.getClass().getName() };
                        for (Object o[] : parms)
                        {
                            if (Arrays.equals(objects,  o))
                            {
                                objects = null;
                                break;
                            }
                        }
                        if (objects != null)
                            parms.add(objects);
                    }
                    // Empty Maps
                    for (Map<?,?> m : cdp.getEmptyMapTestData())
                    {
                        Object[] objects = new Object[] { dmppath, dmpname, 6, m.getClass().getName() };
                        for (Object o[] : parms)
                        {
                            if (Arrays.equals(objects,  o))
                            {
                                objects = null;
                                break;
                            }
                        }
                        if (objects != null)
                            parms.add(objects);
                    }
                    Object[] objects = new Object[] { dmppath, dmpname, 7, null};
                    parms.add(objects);
                    // To ensure it isn't garbage collected early
                    System.out.println(cdp);
                }
            }
        }
        assertThat("Available VMs", count, greaterThan(0));
        assertThat("Available dumps from VMs", parms.size(), greaterThan(0));
        return parms;
    }

    //@Ignore("OOM error in new CI build")
    @Test
    public void testCollections() throws SnapshotException
    {
        //assumeThat("OOM error in new CI build", classname, not(equalTo("java.util.ArrayList")));
        switch (type)
        {
            case 1:
                assumeThat(type, equalTo(1));
                testCollections1(TestSnapshots.getSnapshot(snapfile, false), classname);
                break;
            case 2:
                assumeThat(type, equalTo(2));
                testCollections2(TestSnapshots.getSnapshot(snapfile,false), classname);
                break;
            case 3:
                assumeThat(type, equalTo(3));
                testCollections3(TestSnapshots.getSnapshot(snapfile,false), classname);
                break;
            case 4:
                assumeThat(type, equalTo(4));
                testCollections4(TestSnapshots.getSnapshot(snapfile,false), classname);
                break;
            case 5:
                assumeThat(type, equalTo(5));
                testCollections5(TestSnapshots.getSnapshot(snapfile,false), classname);
                break;
            case 6:
                assumeThat(type, equalTo(6));
                testCollections6(TestSnapshots.getSnapshot(snapfile,false), classname);
                break;
            case 7:
                assumeThat(type, equalTo(7));
                if (TestSnapshots.freeSnapshot(snapfile))
                {
                    System.out.println("Unable to dispose of snapshot " + snapfile);
                }
                break;
        }
    }

}

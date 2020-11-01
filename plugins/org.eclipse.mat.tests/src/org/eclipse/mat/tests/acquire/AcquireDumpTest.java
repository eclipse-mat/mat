/*******************************************************************************
 * Copyright (c) 2015, 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.acquire;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.HeapDumpProviderRegistry;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test the triggering and collection of heap dumps from other processes.
 */
@RunWith(value = Parameterized.class)
public class AcquireDumpTest
{
    @Rule
    public ErrorCollector collector = new ErrorCollector();

    HeapDumpProviderDescriptor hdpd;
    int num;
    int nall;
    static int count;
    static int found;
    public AcquireDumpTest(HeapDumpProviderDescriptor hdpd, String name, int id, int n)
    {
        this.hdpd = hdpd;
        num = id;
        nall = n;
    }
    
    @Parameters(name = "{1}")
    public static Collection<Object[]> data()
    {
        List<Object[]> parms = new ArrayList<Object[]>();
        Collection<HeapDumpProviderDescriptor> descs = HeapDumpProviderRegistry.instance().getHeapDumpProviders();
        int id = 0;
        for (HeapDumpProviderDescriptor hdpd : descs)
        {
            ++id;
            parms.add(new Object[] {hdpd, hdpd.getName(), id, descs.size()});
        }
        return parms;
    }

    /**
     * Check there are JMap and IBM dump providers
     */
    @Test
    public void test()
    {
        Collection<HeapDumpProviderDescriptor> descs = HeapDumpProviderRegistry.instance().getHeapDumpProviders();
        collector.checkThat("Should be HPROF and IBM Dumps descriptors", descs.size(), greaterThanOrEqualTo(2));
    }

    /**
     * Check the providers have sensible properties
     */
    @Test
    public void test2()
    {
        HeapDumpProviderDescriptor hd = hdpd;
        collector.checkThat("Should be some help", hd.getHelp().length(), greaterThan(20));
        collector.checkThat("Should be a name", hd.getName().length(), greaterThan(3));
        collector.checkThat("Locale", hd.getHelpLocale(), notNullValue());
        // collector.checkThat("Icon", hd.getIcon(), notNullValue());
        collector.checkThat("Should be an ID", hd.getIdentifier().length(), greaterThan(3));
        IHeapDumpProvider hdp = hd.getHeapDumpProvider();
        collector.checkThat("Heap Dump Provider", hdp, notNullValue());
        collector.checkThat("Heap Dump Provider toString", hdp.toString(), notNullValue());
    }

    /**
     * Actually generate a dump and parse it
     * 
     * @throws SnapshotException
     * @throws IOException
     */
    public void test3(boolean compress) throws SnapshotException, IOException
    {
        Collection<HeapDumpProviderDescriptor> descs = HeapDumpProviderRegistry.instance().getHeapDumpProviders();
        File tmpdir = TestSnapshots.createGeneratedName("acquire", null);
        HeapDumpProviderDescriptor hd = hdpd;
        do {
            // Currently compressed HPROF is not too slow, but IBM is
            if (compress)
                assumeThat(hd.getName(), containsString("HPROF"));
            IHeapDumpProvider hdp = hd.getHeapDumpProvider();
            collector.checkThat("Heap Dump Provider", hdp, notNullValue());
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
            collector.checkThat("Heap dump provider "+hdp, ls, notNullValue());
            if (ls == null)
                continue;
            for (VmInfo vm : ls)
            {
                ++count;
                String desc = vm.getDescription();
                collector.checkThat("VM description", desc, notNullValue());
                if (desc.contains("org.eclipse.mat.tests"))
                {
                    System.out.println("Desc " + desc);
                    // If we can, try compressing the dump to test more code paths
                    try
                    {
                        vm.getClass().getField("compress").set(vm, compress);
                    }
                    catch (NoSuchFieldException e1)
                    {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    catch (SecurityException e1)
                    {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    catch (IllegalArgumentException e)
                    {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    catch (IllegalAccessException e)
                    {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    File f = new File(vm.getProposedFileName());
                    System.out.println("Proposed name "+f+" "+hdp);
                    String fname = f.getName();
                    int ldot = fname.endsWith(".gz") ? 
                                    fname.lastIndexOf('.', fname.length() - 4)
                                  : fname.lastIndexOf('.');
                    String fname2 = "acquire_dump_" + (found+1) + fname.substring(ldot);
                    File tmpdump = new File(tmpdir, fname2);
                    System.out.println("Dump " + tmpdump);
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
                    collector.checkThat("Dump file", dmp, notNullValue());
                    try
                    {
                        ISnapshot answer = SnapshotFactory.openSnapshot(dmp, Collections.<String, String> emptyMap(), l);
                        try
                        {
                            collector.checkThat("Snapshot", answer, notNullValue());
                            found++;
                            // Currently zipped hprof is very slow (>1 hour)
                            if (!compress)
                            {
                                checkEclipseBundleQuery(answer);
                            }
                        }
                        finally
                        {
                            SnapshotFactory.dispose(answer);
                        }
                    }
                    finally
                    {
                        boolean del = dmp.delete();
                        if (!del) {
                            System.out.println("Unable to delete "+dmp);
                        }
                    }
                }
            }

        } while (false);
        // See if any of the tests with any provider actually loads a dump
        if (num == nall)
        {
            collector.checkThat("Available VMs", count, greaterThan(0));
            collector.checkThat("Available dumps from VMs", found, greaterThan(0));
        }
    }

    /**
     * Actually generate a dump and parse it
     * 
     * @throws SnapshotException
     * @throws IOException
     */
    @Ignore("OOM error with new CI build")
    @Test
    public void testAcquireDumpUncompressed() throws SnapshotException, IOException
    {
        test3(false);
    }

    /**
     * Actually generate a dump and parse it
     * 
     * @throws SnapshotException
     * @throws IOException
     */
    @Ignore("OOM error with new CI build")
    @Test
    public void testAcquireDumpCompressed() throws SnapshotException, IOException
    {
        test3(true);
    }

    /**
     * This query requires an heap dump from a running Eclipse system to 
     * test the Eclipse bundle query. Other test dumps from non-Eclipse programs won't work.
     * @param snapshot
     * @throws SnapshotException
     */
    void checkEclipseBundleQuery(ISnapshot snapshot) throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("bundle_registry -groupby NONE", snapshot);
        assertNotNull(query);
        IResult result = query.execute(new VoidProgressListener());
        assertNotNull(result);
        IResultTree tree = (IResultTree) result;
        int found = 0;
        int f2 = 0;
        for (Object o : tree.getElements())
        {
            IContextObject ctx = tree.getContext(o);
            Object o2 = tree.getColumnValue(o, 0);
            if (o2.toString().startsWith("org.eclipse.mat.tests "))
            {
                found++;
            }
            if (o2.toString().startsWith("org.eclipse.mat.api "))
            {
                List<?> l3 = tree.getChildren(o);
                for (Object o3 : l3)
                {
                    Object o4 = tree.getColumnValue(o3, 0);
                    if (o4.toString().startsWith("Dependencies"))
                    {
                        f2 |= 1;
                        checkSubtree(tree, o3, 3, "org.eclipse.mat.report ", "Expected dependencies of org.eclipse.mat.api to include");
                    }
                    if (o4.toString().startsWith("Dependents"))
                    {
                        f2 |= 2;
                        checkSubtree(tree, o3, 4, "org.eclipse.mat.parser ", "Expected dependendents of org.eclipse.mat.api to include");
                    }
                    if (o4.toString().startsWith("Extension Points"))
                    {
                        f2 |= 4;
                        checkSubtree(tree, o3, 7, "org.eclipse.mat.api.factory", "Expected extension points of org.eclipse.mat.api to include");
                    }
                    if (o4.toString().startsWith("Extensions"))
                    {
                        f2 |= 8;
                        checkSubtree(tree, o3, 7, "org.eclipse.mat.api.nameResolver", "Expected extensions of org.eclipse.mat.api to include");
                    }
                    if (o4.toString().startsWith("Used Services"))
                    {
                        f2 |= 16;
                        checkSubtree(tree, o3, 1, "org.eclipse.osgi.service.debug.DebugOptions", "Expected used services of org.eclipse.mat.api to include");
                    }
                }
            }
        }
        assertEquals("Expected to find a org.eclipse.mat.tests plugin", found, 1);
        assertEquals("Expected Dependencies,Dependents, Extension Points, Extensions, Used Services from org.eclipse.mat.api", f2, 31);
    }

    private void checkSubtree(IResultTree tree, Object o3, int minElements, String toFind, String errMsg)
    {
        List<?> l5 = tree.getChildren(o3);
        assertThat(errMsg + " " + l5.toString(), l5.size(), greaterThanOrEqualTo(minElements));
        boolean foundItem = false;
        for (Object o6 : l5)
        {
            Object o7 = tree.getColumnValue(o6, 0);
            if (o7.toString().startsWith(toFind))
            {
                foundItem = true;
            }
            //System.out.println("Found "+o7+" "+errMsg);
        }
        assertTrue(errMsg+" "+toFind, foundItem);
    }
}

/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import org.eclipse.mat.parser.index.IIndexReader.IOne2ManyIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2ManyObjectsIndex;
import org.eclipse.mat.parser.index.IndexReader;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IndexWriter.KeyWriter;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(value = Parameterized.class)
public class TestIndex
{
    // Number of arrays
    int M = 6000;
    // Size of array
    int N = 600000;
    // Variation in size
    int P = 6;
    // Increase this for the huge tests
    long MAXELEMENTS = 30000000L;
    // Increase this for the huge tests
    long MAXELEMENTS2 = 30000000L;
    static final boolean verbose = false;

    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
                        { 0, 0, 0 },
                        { 1, 0, 0 },
                        { 6, 6, 0 },
                        // Test some boundary conditions for pages
                        { 1, IndexWriter.PAGE_SIZE_INT - 1, 0 }, 
                        { 1, IndexWriter.PAGE_SIZE_INT, 0 },
                        { 1, IndexWriter.PAGE_SIZE_INT + 1, 0 },
                        { 1, IndexWriter.PAGE_SIZE_INT * 2 - 1, 0 },
                        { 1, IndexWriter.PAGE_SIZE_INT * 2, 0 },
                        { 1, IndexWriter.PAGE_SIZE_INT * 2 + 1, 0 },
                        { IndexWriter.PAGE_SIZE_INT - 1, 1, 0 },
                        { IndexWriter.PAGE_SIZE_INT, 1, 0 },
                        { IndexWriter.PAGE_SIZE_INT + 1, 1, 0 },
                        { IndexWriter.PAGE_SIZE_INT * 2 - 1, 1, 0 },
                        { IndexWriter.PAGE_SIZE_INT * 2, 1, 0 },
                        { IndexWriter.PAGE_SIZE_INT * 2 + 1, 1, 0 },
                        // medium
                        { 100, 1000, 11 },
                        // <2G refs
                        {3000, 600000, 11},
                        // >2G <4G refs
                        {6000, 600000, 11},
                        // >4G refs
                        {9000, 600000, 11},
        });
    }

    public TestIndex(int m, int n, int p)
    {
        this.M = m;
        this.N = n;
        this.P = p;
    }

    @Test
    public void test1ToN() throws IOException
    {
        assumeTrue((long) M * N < MAXELEMENTS2);
        int ii[][] = new int[P + 1][];
        for (int p = 0; p < P + 1; p++)
        {
            int nn = N + p;
            ii[p] = new int[nn];
            for (int i = 0; i < nn; ++i)
            {
                ii[p][i] = i;
            }
        }
        File indexFile = File.createTempFile("1toN", ".index");
        try
        {
            IndexWriter.IntArray1NWriter f = new IndexWriter.IntArray1NWriter(M, indexFile);
            for (int j = 0; j < M; ++j)
            {
                // Vary the length a little
                int p = j % (P + 1);
                if (verbose)
                    System.out.println("Writing " + j + "/" + M);
                f.log(j, ii[p]);
            }
            IOne2ManyIndex i2 = f.flush();
            try
            {
                for (int j = 0; j < M; ++j)
                {
                    if (verbose)
                        System.out.println("Reading " + j + "/" + M);
                    int i3[] = i2.get(j);
                    int p = j % (P + 1);
                    // Junit array comparison is too slow
                    if (!Arrays.equals(ii[p], i3))
                        Assert.assertArrayEquals(ii[p], i3);
                }
            }
            finally
            {
                i2.close();
            }
        }
        finally
        {
            indexFile.delete();
        }
    }
    
    @Test
    public void testInbound() throws IOException
    {
        assumeTrue((long) M * N < MAXELEMENTS);
        int ii[][] = new int[P + 1][];
        for (int p = 0; p < P + 1; p++)
        {
            int nn = N + p;
            ii[p] = new int[nn];
            for (int i = 0; i < nn; ++i)
            {
                ii[p][i] = i;
            }
        }
        int mx = Math.max(M, N+P);
        File indexFile = File.createTempFile("Inbound", ".index");
        try
        {
            IndexWriter.InboundWriter f = new IndexWriter.InboundWriter(mx, indexFile);
            for (int j = 0; j < M; ++j)
            {
                // Vary the length a little
                int p = j % (P + 1);
                if (verbose)
                    System.out.println("Writing " + j + "/" + M);
                for (int k = 0; k < ii[p].length; ++k)
                {
                    f.log(j, ii[p][k], k == 0);
                }
            }
            KeyWriter kw = new KeyWriter()
            {

                public void storeKey(int index, Serializable key)
                {
                    // TODO Auto-generated method stub

                }

            };
            IOne2ManyObjectsIndex z = f.flush(new VoidProgressListener(), kw);
            for (int j = 0; j < M; ++j)
            {
                if (verbose)
                    System.out.println("Reading " + j + "/" + M);
                int p = j % (P + 1);
                int i2[] = z.get(j);
                for (int i : i2)
                {
                    assertTrue(i >= 0);
                }
                if (!Arrays.equals(ii[p], i2)) {
                    assertEquals(ii[p], i2);
                }
            }
            z.close();
        }
        finally
        {
            indexFile.delete();
        }
    }
    
    @Test
    public void testLong() throws IOException
    {
        assumeTrue((long) M * N < MAXELEMENTS);
        Random r = new Random();
        long ii[][] = new long[P + 1][];
        for (int p = 0; p < P + 1; p++)
        {
            int nn = N + p;
            ii[p] = new long[nn];
            for (int i = 0; i < nn; ++i)
            {
                ii[p][i] = r.nextLong();
            }
        }
        File indexFile = File.createTempFile("LongOutbound", ".index");
        try
        {
            IndexWriter.LongArray1NWriter f = new IndexWriter.LongArray1NWriter(M, indexFile);
            for (int j = 0; j < M; ++j)
            {
                // Vary the length a little
                int p = j % (P + 1);
                if (verbose)
                    System.out.println("Writing " + j + "/" + M);
                f.log(j, ii[p]);
            }
            f.flush();
            
            IndexReader.LongIndex1NReader i2 = new IndexReader.LongIndex1NReader(indexFile);
            try
            {
                for (int j = 0; j < M; ++j)
                {
                    if (verbose)
                        System.out.println("Reading " + j + "/" + M);
                    long i3[] = i2.get(j);
                    int p = j % (P + 1);
                    // Junit array comparison is too slow
                    if (!Arrays.equals(ii[p], i3))
                        Assert.assertArrayEquals(ii[p], i3);
                }
            }
            finally
            {
                i2.close();
            }
        }
        finally
        {
            indexFile.delete();
        }
    }

    @Test
    public void test1ToNSorted() throws IOException
    {
        assumeTrue((long) M * N < MAXELEMENTS2);
        int ii[][] = new int[P + 1][];
        for (int p = 0; p < P + 1; p++)
        {
            int nn = N + p;
            ii[p] = new int[nn];
            for (int i = 0; i < nn; ++i)
            {
                ii[p][i] = i;
            }
        }
        File indexFile = File.createTempFile("1toN", ".index");
        try
        {
            IndexWriter.IntArray1NSortedWriter f = new IndexWriter.IntArray1NSortedWriter(M, indexFile);
            for (int j = 0; j < M; ++j)
            {
                // Vary the length a little
                int p = j % (P + 1);
                if (verbose)
                    System.out.println("Writing " + j + "/" + M);
                f.log(j, ii[p]);
            }
            IOne2ManyIndex i2 = f.flush();
            try
            {
                for (int j = 0; j < M; ++j)
                {
                    if (verbose)
                        System.out.println("Reading " + j + "/" + M);
                    int i3[] = i2.get(j);
                    int p = j % (P + 1);
                    // Junit array comparison is too slow
                    if (!Arrays.equals(ii[p], i3))
                        Assert.assertArrayEquals(ii[p], i3);
                }
            }
            finally
            {
                i2.close();
            }
        }
        finally
        {
            indexFile.delete();
        }
    }

    @Test
    public void test1ToNSortedReader() throws IOException
    {
        assumeTrue((long) M * N < MAXELEMENTS2);
        int ii[][] = new int[P + 1][];
        for (int p = 0; p < P + 1; p++)
        {
            int nn = N + p;
            ii[p] = new int[nn];
            for (int i = 0; i < nn; ++i)
            {
                ii[p][i] = i;
            }
        }
        File indexFile = File.createTempFile("1toN", ".index");
        try
        {
            IndexWriter.IntArray1NSortedWriter f = new IndexWriter.IntArray1NSortedWriter(M, indexFile);
            for (int j = 0; j < M; ++j)
            {
                // Vary the length a little
                int p = j % (P + 1);
                if (verbose)
                    System.out.println("Writing " + j + "/" + M);
                f.log(j, ii[p]);
            }
            IOne2ManyIndex i2 = f.flush();
            i2 = new IndexReader.IntIndex1NSortedReader(indexFile);
            try
            {
                for (int j = 0; j < M; ++j)
                {
                    if (verbose)
                        System.out.println("Reading " + j + "/" + M);
                    int i3[] = i2.get(j);
                    int p = j % (P + 1);
                    // Junit array comparison is too slow
                    if (!Arrays.equals(ii[p], i3))
                        Assert.assertArrayEquals(ii[p], i3);
                }
            }
            finally
            {
                i2.close();
            }
        }
        finally
        {
            indexFile.delete();
        }
    }
}

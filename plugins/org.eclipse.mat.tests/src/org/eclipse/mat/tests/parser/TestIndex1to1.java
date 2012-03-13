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
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.index.IndexReader;
import org.eclipse.mat.parser.index.IndexWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(value = Parameterized.class)
public class TestIndex1to1
{
    // Size of array
    long N = 600000;
    // Increase this for the huge tests
    long MAXELEMENTS = 30000000L;
    long MAXELEMENTS2 = 30000000L;
    static final boolean verbose = false;

    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
                        { 0 },
                        { 6 },
                        // Test some boundary conditions for pages
                        { IndexWriter.PAGE_SIZE_INT - 1 }, 
                        { IndexWriter.PAGE_SIZE_INT },
                        { IndexWriter.PAGE_SIZE_INT + 1 },
                        { IndexWriter.PAGE_SIZE_INT * 2 - 1 },
                        { IndexWriter.PAGE_SIZE_INT * 2 },
                        { IndexWriter.PAGE_SIZE_INT * 2 + 1 },
                        // medium
                        { 1000 },
                        // <2G refs
                        {3000 * 600000},
        });
    }

    public TestIndex1to1(long n)
    {
        this.N = n;
    }

    @Test
    public void intIndex1() throws IOException
    {
        assumeTrue(N < MAXELEMENTS);
        long n = N;
        int n2 = (int) Math.min(n, Integer.MAX_VALUE);
        IndexWriter.IntIndexCollector ic = new IndexWriter.IntIndexCollector(n2, 31);
        for (int i = 0; i < n2; ++i)
        {
            ic.set(i, i);
        }

        for (int i = 0; i < n2; ++i)
        {
            int jj = ic.get(i);
            if (i != jj)
                assertEquals(i, jj);
        }
        ic.close();
    }
    
    @Test
    public void intIndex2() throws IOException
    {
        assumeTrue(N < MAXELEMENTS2);
        File indexFile = File.createTempFile("int1", ".index");
        long n = N;
        int n2 = (int) Math.min(n, Integer.MAX_VALUE);
        IndexWriter.IntIndexCollector ic = new IndexWriter.IntIndexCollector(n2, 31);
        for (int i = 0; i < n2; ++i)
        {
            ic.set(i, i);
        }

        try
        {
            IIndexReader.IOne2OneIndex i2 = ic.writeTo(indexFile);
            for (int i = 0; i < n2; ++i)
            {
                int jj = i2.get(i);
                if (i != jj)
                    assertEquals(i, jj);
            }
            if (n < Integer.MAX_VALUE)
                assertEquals(n, i2.size());
            i2.close();
        }
        finally
        {
            indexFile.delete();
        }
    }

    @Test
    public void intIndex3() throws IOException
    {
        assumeTrue(N < MAXELEMENTS);
        File indexFile = File.createTempFile("int1", ".index");
        final long n = N;
        final int n2 = (int) Math.min(n, Integer.MAX_VALUE);
        IndexWriter.IntIndexStreamer ic = new IndexWriter.IntIndexStreamer();
        
        try
        {
            IIndexReader.IOne2OneIndex i2 = ic.writeTo(indexFile, new IteratorInt() {
                long i;
                public boolean hasNext()
                {
                    return i < n;
                }

                public int next()
                {
                    return (int)i++;
                }

            });
            for (int i = 0; i < n2; ++i)
            {
                int in = i2.get(i);
                if (i != in)
                    assertEquals(i, in);
            }
            IndexReader.IntIndexReader ir = new IndexReader.IntIndexReader(indexFile);
            long i = 0;

            for (IteratorInt it = ir.iterator(); it.hasNext(); ++i) {
                int in = it.next();
                if (i != in)
                    assertEquals(i, in);
            }
            assertEquals(n, i);
            if (n < Integer.MAX_VALUE)
                assertEquals(n, i2.size());
            ir.close();
        }
        finally
        {
            indexFile.delete();
        }
    }

    @Test
    public void intIndex4() throws IOException
    {
        assumeTrue(N < MAXELEMENTS2);
        long n = N;
        int n2 = (int) Math.min(n, Integer.MAX_VALUE);
        IndexWriter.SizeIndexCollectorUncompressed ic = new IndexWriter.SizeIndexCollectorUncompressed(n2);
        for (int i = 0; i < n2; ++i)
        {
            long v = i;
            ic.set(i, v);
        }

        for (int i = 0; i < n2; ++i)
        {
            int jj = ic.get(i);
            if (i != jj)
                assertEquals(i, jj);
        }
    }

    @Test
    public void intIndex5() throws IOException
    {
        assumeTrue(N < MAXELEMENTS2);
        File indexFile = File.createTempFile("int1", ".index");
        long n = N;
        int n2 = (int) Math.min(n, Integer.MAX_VALUE);
        IndexWriter.SizeIndexCollectorUncompressed ic = new IndexWriter.SizeIndexCollectorUncompressed(n2);
        for (int i = 0; i < n2; ++i)
        {
            ic.set(i, i);
        }

        try
        {
            IIndexReader.IOne2SizeIndex i2 = ic.writeTo(indexFile);
            for (int i = 0; i < n2; ++i)
            {
                int jj = i2.get(i);
                if (i != jj)
                    assertEquals(i, jj);
            }
            if (n < Integer.MAX_VALUE)
                assertEquals(n, i2.size());
            i2.close();
        }
        finally
        {
            indexFile.delete();
        }
    }

    @Test
    public void intIndex6() throws IOException
    {
        assumeTrue(N < MAXELEMENTS2);
        File indexFile = File.createTempFile("int1", ".index");
        long n = N;
        int n2 = (int) Math.min(n, Integer.MAX_VALUE);
        IndexWriter.SizeIndexCollectorUncompressed ic = new IndexWriter.SizeIndexCollectorUncompressed(n2);
        for (int i = 0; i < n2; ++i)
        {
            ic.set(i, i);
        }

        try
        {
            ic.writeTo(indexFile);
            IIndexReader.IOne2SizeIndex i2 = new IndexReader.SizeIndexReader(indexFile);
            for (int i = 0; i < n2; ++i)
            {
                int jj = i2.get(i);
                if (i != jj)
                    assertEquals(i, jj);
            }
            if (n < Integer.MAX_VALUE)
                assertEquals(n, i2.size());
            i2.close();
        }
        finally
        {
            indexFile.delete();
        }
    }
}
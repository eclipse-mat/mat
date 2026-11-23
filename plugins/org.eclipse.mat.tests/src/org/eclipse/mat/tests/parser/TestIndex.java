/*******************************************************************************
 * Copyright (c) 2012,2026 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *    Jason Koch (Netflix, Inc) - fixes
 *******************************************************************************/
package org.eclipse.mat.tests.parser;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.mat.parser.index.IIndexReader.IOne2ManyIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2ManyObjectsIndex;
import org.eclipse.mat.parser.index.IndexReader;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IndexWriter.KeyWriter;
import org.eclipse.mat.parser.io.BitInputStream;
import org.eclipse.mat.parser.io.BitOutputStream;
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

    @Parameters(name = "Arrays {0} size {1} variation {2}")
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
            assertTrue(indexFile.delete());
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
            try
            {
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
                        Assert.assertArrayEquals(ii[p], i2);
                    }
                }
            }
            finally
            {
                z.close();
            }
        }
        finally
        {
            assertTrue(indexFile.delete());
        }
    }
    
    /**
     * Reference bit encoder, independent of the stream implementation:
     * writes each value MSB first, one bit at a time, and zero pads the
     * final byte.
     */
    private static byte[] bitStreamOracle(int[] values, int[] widths)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int current = 0;
        int fill = 0;
        for (int i = 0; i < values.length; i++)
        {
            for (int bit = widths[i] - 1; bit >= 0; bit--)
            {
                current = (current << 1) | ((values[i] >>> bit) & 1);
                if (++fill == 8)
                {
                    bytes.write(current);
                    current = 0;
                    fill = 0;
                }
            }
        }
        if (fill != 0)
            bytes.write(current << (8 - fill));
        return bytes.toByteArray();
    }

    @Test
    public void testBitStreamEncoding() throws IOException
    {
        assumeTrue(M == 6 && N == 6 && P == 0);
        Random random = new Random(6);
        int[] values = new int[50000];
        int[] widths = new int[values.length];
        int count = 0;
        int bitOffset = 0;
        for (int width = 0; width <= 32; width++)
        {
            for (int offset = 0; offset < 8; offset++)
            {
                // Pad to a byte boundary, then start at each bit offset within a byte.
                widths[count++] = (8 - bitOffset) & 7;
                widths[count++] = offset;
                for (int value : new int[] { 0, -1, Integer.MIN_VALUE, random.nextInt() })
                {
                    values[count] = value;
                    widths[count++] = width;
                }
                bitOffset = (offset + 4 * width) & 7;
            }
        }
        for (; count < values.length; count++)
        {
            values[count] = random.nextInt();
            widths[count] = random.nextInt(33);
        }
        byte[] expected = bitStreamOracle(values, widths);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BitOutputStream out = new BitOutputStream(bytes))
        {
            for (int i = 0; i < values.length; i++)
            {
                int written = widths[i] == 1 ? out.writeBit(values[i]) : out.writeInt(values[i], widths[i]);
                Assert.assertEquals(widths[i], written);
            }
        }
        Assert.assertArrayEquals(expected, bytes.toByteArray());
        for (int tail = 0; tail < 8; tail++)
        {
            ByteArrayOutputStream padded = new ByteArrayOutputStream();
            try (BitOutputStream out = new BitOutputStream(padded))
            {
                out.writeInt(-1, tail);
            }
            byte[] expectedPadding = tail == 0 ? new byte[0] : new byte[] { (byte) (0xff << (8 - tail)) };
            Assert.assertArrayEquals(expectedPadding, padded.toByteArray());
        }
        // Short reads also cover buffer boundaries; 32-bit fields must keep the sign bit.
        for (final int chunk : new int[] { BitInputStream.DEFAULT_BUFFER_SIZE, 3 })
        {
            InputStream shortReads = new ByteArrayInputStream(expected)
            {
                @Override
                public synchronized int read(byte[] b, int off, int len)
                {
                    return super.read(b, off, Math.min(len, chunk));
                }
            };
            try (BitInputStream in = new BitInputStream(shortReads))
            {
                long bits = 0;
                for (int i = 0; i < values.length; i++)
                {
                    int actual = widths[i] == 1 ? in.readBit() : in.readInt(widths[i]);
                    Assert.assertEquals((int) (values[i] & ((1L << widths[i]) - 1)), actual);
                    bits += widths[i];
                }
                Assert.assertEquals(0, in.readInt((int) ((8 - (bits & 7)) & 7)));
                try
                {
                    in.readBit();
                    Assert.fail("Expected EOFException at end of stream");
                }
                catch (EOFException expectedEOF)
                {}
                Assert.assertEquals(0, in.readInt(0));
            }
        }
    }

    @Test
    public void testInboundLogEncoding() throws IOException
    {
        assumeTrue(M == 6 && N == 6 && P == 0);
        Random random = new Random(61);
        for (int width = 1; width <= 31; width++)
        {
            int size = 1 << (width - 1);
            File indexFile = File.createTempFile("InboundBits", ".index");
            File logFile = new File(indexFile.getAbsolutePath() + "0.log"); //$NON-NLS-1$
            IndexWriter.InboundWriter writer = new IndexWriter.InboundWriter(size, indexFile);
            try
            {
                int[] values = new int[5000 * 3];
                int[] widths = new int[values.length];
                for (int i = 0; i < values.length; i += 3)
                {
                    values[i] = i & 1;
                    values[i + 2] = random.nextInt(size);
                    widths[i] = 1;
                    widths[i + 1] = widths[i + 2] = width;
                    writer.log(0, values[i + 2], values[i] != 0);
                }
                writer.close();
                Assert.assertArrayEquals(bitStreamOracle(values, widths), Files.readAllBytes(logFile.toPath()));
            }
            finally
            {
                writer.close();
                assertTrue(logFile.delete());
                assertTrue(indexFile.delete());
            }
        }
    }

    @Test
    public void testBitStreamFlushAndFailures() throws IOException
    {
        assumeTrue(M == 6 && N == 6 && P == 0);
        final boolean[] closed = new boolean[1];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        {
            @Override
            public void close()
            {
                closed[0] = true;
            }
        };
        try (BitOutputStream out = new BitOutputStream(bytes))
        {
            out.flush();
            out.writeBit(1);
            out.flush();
            out.flush();
            out.writeInt(0xabc, 12);
            out.flush();
            out.writeInt(-1, 0);
            out.writeBit(0);
        }
        assertTrue(closed[0]);
        Assert.assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0xab, (byte) 0xc0, 0 }, bytes.toByteArray());
        InputStream shortReads = new ByteArrayInputStream(new byte[] { (byte) 0xa0, 0, 0, (byte) 0xd0 })
        {
            @Override
            public synchronized int read(byte[] b, int off, int len)
            {
                return super.read(b, off, Math.min(len, 3));
            }
        };
        try (BitInputStream in = new BitInputStream(shortReads))
        {
            Assert.assertEquals(10, in.readInt(4));
            // flush discards the whole read-ahead buffer and pending bits, it is not just byte alignment.
            in.flush();
            Assert.assertEquals(0xd0, in.readInt(8));
        }
        for (int length = 0; length < 4; length++)
        {
            try (BitInputStream in = new BitInputStream(new ByteArrayInputStream(new byte[length])))
            {
                try
                {
                    in.readInt(32);
                    Assert.fail("Expected EOFException for a truncated field");
                }
                catch (EOFException expectedEOF)
                {}
            }
        }
        final IOException failure = new IOException("bit stream I/O failure test");
        BitInputStream brokenIn = new BitInputStream(new InputStream()
        {
            @Override
            public int read() throws IOException
            {
                throw failure;
            }
        });
        try (BitInputStream in = brokenIn)
        {
            Assert.assertEquals(0, in.readInt(0));
            try
            {
                in.readBit();
                Assert.fail("Read exception should not be swallowed");
            }
            catch (IOException actual)
            {
                Assert.assertSame(failure, actual);
            }
        }
        BitInputStream brokenClose = new BitInputStream(new ByteArrayInputStream(new byte[0])
        {
            @Override
            public void close() throws IOException
            {
                throw failure;
            }
        });
        try
        {
            brokenClose.close();
            Assert.fail("Input stream close exception should not be swallowed");
        }
        catch (IOException actual)
        {
            Assert.assertSame(failure, actual);
        }
        for (int operation = 0; operation < 3; operation++)
        {
            final int failingOperation = operation;
            BitOutputStream out = new BitOutputStream(new OutputStream()
            {
                @Override
                public void write(int b) throws IOException
                {
                    if (failingOperation == 0)
                        throw failure;
                }
                @Override
                public void flush() throws IOException
                {
                    if (failingOperation == 1)
                        throw failure;
                }
                @Override
                public void close() throws IOException
                {
                    if (failingOperation == 2)
                        throw failure;
                }
            });
            out.writeBit(1);
            try
            {
                out.close();
                Assert.fail("Write, flush or close exception should not be swallowed");
            }
            catch (IOException actual)
            {
                Assert.assertSame(failure, actual);
            }
        }
    }

    @Test
    public void testLong() throws IOException
    {
        assumeTrue((long) M * N < MAXELEMENTS);
        Random r = ThreadLocalRandom.current();
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
            assertTrue(indexFile.delete());
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
            assertTrue(indexFile.delete());
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
            i2.close();
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
            assertTrue(indexFile.delete());
        }
    }
}

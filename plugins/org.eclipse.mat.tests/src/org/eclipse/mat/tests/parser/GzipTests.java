/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.parser;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.eclipse.mat.hprof.ChunkedGZIPRandomAccessFile;
import org.eclipse.mat.hprof.GZIPInputStream2;
import org.eclipse.mat.hprof.SeekableStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

//import java.util.zip.InflaterInputStream;
//import io.nayuki.deflate.orig.InflaterInputStream;
//import io.nayuki.deflate.latest.InflaterInputStream;
import io.nayuki.deflate.InflaterInputStream;

/**
 * Tests the decompression code used by HPROF.
 *
 */
@RunWith(value = Parameterized.class)
public class GzipTests
{
    @Parameters(name = "Compression {0}")
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
            {Deflater.DEFAULT_COMPRESSION},
            {Deflater.BEST_COMPRESSION},
            {Deflater.BEST_SPEED},
            {5}
        });
    }
    int comp;
    private static boolean verbose = true;
    public GzipTests(int comp)
    {
        this.comp = comp;
    }

    static byte[] randomText(int size)
    {
        Random rn = new Random(1);
        // generate some word like data
        byte b[] = new byte[size];

        int inputLen = b.length;
        String letters = "eeeeeeeeeeeetttttttttaaaaaaaaiiiiiiiinnnnnnnnoooooooossssssss" + //$NON-NLS-1$
        "hhhhhhrrrrrrddddlllluuucccmmmfffwwyyggppbbvvkqjxz            "; //$NON-NLS-1$
        for (int i = 0; i < inputLen; ++i)
        {
            char c = letters.charAt(rn.nextInt(letters.length()));
            if (rn.nextDouble() < 0.1 && i > 1 && b[i - 1] == ' ' && Character.isAlphabetic(b[i - 2]))
            {
                c = Character.toUpperCase(c);
                b[i - 2] = '.';
            }
            b[i] = (byte)c;
        }
        if (verbose) System.out.println(new String(b, 0, 3000));
        return b;
    }

    static byte[] extendData(byte initial[], int count)
    {
        // Now alternate it with some randomish data
        int inputLen = initial.length;
        int rep = count * 2;
        byte b2[] = new byte[inputLen * rep];
        for (int i = 0; i < rep;)
        {
            System.arraycopy(initial, 0, b2, i * inputLen, inputLen);
            ++i;
            for (int j = 0; j < inputLen; ++j)
            {
                b2[i * inputLen + j] = (byte)((j * 0x91234567L) >> 24);
            }
            ++i;
        }
        return b2;
    }

    int compress(byte in[], byte out[])
    {
        // Now compress it
        Deflater def = new Deflater(comp, true);
        //def.setStrategy(Deflater.FILTERED);
        //def.setStrategy(Deflater.HUFFMAN_ONLY);
        def.setStrategy(Deflater.DEFAULT_STRATEGY);
        def.setInput(in);
        def.finish();
        int bol = def.deflate(out);
        def.end();
        if (verbose) System.out.println("Original "+in.length);
        if (verbose) System.out.println("Compressed to "+bol);
        return bol;
    }

    byte[] gzip1(byte b[]) throws IOException
    {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            try (GZIPOutputStream gos = new GZIPOutputStream(out)
            {
                {
                    def = new Deflater(comp, true);
                }
            })
            {
                gos.write(b);
                gos.close();
            }
            return out.toByteArray();
        }
    }

    byte[] chunkedGzip1(byte b[]) throws IOException
    {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            try (ChunkedGZIPRandomAccessFile.ChunkedGZIPOutputStream gos = new ChunkedGZIPRandomAccessFile.ChunkedGZIPOutputStream(out))
            {
                gos.write(b);
                gos.close();
            }
            return out.toByteArray();
        }
    }

    @Test
    public void test1() throws IOException
    {
        byte b[] = randomText(216962);
        int inputLen = b.length;

        b = extendData(b, 13);
        inputLen = b.length;

        byte bo[] = new byte[inputLen];
        int bol = compress(b, bo);

        // Simple test - read the entire data
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bo, 0, bol))
        {
            try (InflaterInputStream inf = new InflaterInputStream(bis, false)) {
                byte bo2[] = inf.readAllBytes();
                assertThat(bo2, equalTo(b));
            }
        }

        /*
         * Complex test
         */
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bo, 0, bol))
        {
            try (InflaterInputStream inf = new InflaterInputStream(bis, false)) {
                Random r = new Random(1);
                byte bo2[] = new byte[inputLen];
                int MAXMARK = 32211;
                int mark = -1;
                // Original code
                boolean isMarkSupported = inf.markSupported();
                for (int i = 0; i < inputLen; )
                {
                    if (isMarkSupported && r.nextDouble() < 0.05)
                    {
                        // Test mark
                        mark = i;
                        inf.mark(MAXMARK);
                        if (verbose) System.out.println("mark() at "+i);
                    }
                    else if (r.nextDouble() < 0.10 && mark >= 0 && i - mark < MAXMARK)
                    {
                        // Test reset
                        inf.reset();
                        // scrub the previous data
                        Arrays.fill(bo2, mark, i, (byte)0);
                        i = mark;
                        if (verbose) System.out.println("reset() to "+i);
                    }
                    if (r.nextDouble() < 0.01)
                    {
                        // Test read of a byte
                        int rd = inf.read();
                        assertThat(rd, greaterThanOrEqualTo(0));
                        bo2[i] = (byte)rd;
                        ++i;
                        if (verbose) System.out.println("read() to "+i);
                    }
                    else
                    {
                        int rr = r.nextInt(40000);
                        int off = Math.min(inputLen - i, rr);
                        if (r.nextDouble() < 0.02)
                        {
                            // Test available
                            int avail = inf.available();
                            off = Math.min(avail, off);
                        }
                        if (verbose) System.out.println("Read at "+i+" for "+off);
                        if (r.nextDouble() < 0.08 && mark >= 0 && i + off - mark < MAXMARK)
                        {
                            // Test skip
                            long rd = inf.skip(off);
                            assertThat(rd, greaterThanOrEqualTo(0L));
                            i += (int)rd;
                            if (verbose) System.out.println("skip() to "+i);
                            // and reset back
                            inf.reset();
                            // scrub the previous data
                            Arrays.fill(bo2, mark, i, (byte)0);
                            i = mark;
                            if (verbose) System.out.println("reset() to "+i);
                        }
                        else
                        {
                            // Test started read
                            int rd = inf.read(bo2, i, off);
                            assertThat(rd, anyOf(greaterThan(0), equalTo(off)));
                            i += rd;
                            if (verbose) System.out.println("read() from "+(i-rd)+" to "+i);
                        }
                    }
                }
                for (int i = 0; i < b.length; ++i)
                {
                    assertThat("Offset "+i, bo2[i], equalTo(b[i]));
                }
            }
        }
    }

    @Test
    public void testSeekableInflater() throws IOException
    {
        byte b[] = randomText(216962);
        int inputLen = b.length;

        b = extendData(b, 13);
        inputLen = b.length;

        byte bo[] = new byte[inputLen];
        int bol = compress(b, bo);

        SeekableStream ss = new SeekableStream(new Supplier<InputStream>() {
                public InputStream get()
                {
                    return new InflaterInputStream(new ByteArrayInputStream(bo, 0, bol), false);
                }
        }, 10);
        checkSeekableStream(b, ss);
    }

    private void checkSeekableStream(byte[] b, SeekableStream ss) throws IOException
    {
        int inputLen = b.length;
        Random r = new Random(1);
        byte bo2[] = new byte[inputLen];
        for (int i = 0; i < 400; ++i) {
            int pos = r.nextInt(inputLen);
            int l = r.nextInt(inputLen - pos);
            ss.seek(pos);
            int rd = ss.read(bo2, pos, l);
            assertThat(rd, greaterThanOrEqualTo(0));
            for (int j = pos; j < pos + rd; ++j)
            {
                assertThat("Offset "+i, bo2[j], equalTo(b[j]));
            }
        }
    }

    @Test
    public void testSeekableGZip() throws IOException
    {
        byte b[] = randomText(216962);

        b = extendData(b, 13);

        byte bo[] = comp == 5 ? chunkedGzip1(b) : gzip1(b);

        SeekableStream ss = new SeekableStream(new Supplier<InputStream>() {
                public InputStream get()
                {
                    try {
                        InputStream is =  new GZIPInputStream(new ByteArrayInputStream(bo));
                        if (verbose) System.out.println("Constructed stream " + is);
                        return is;
                    }
                    catch (IOException e)
                    {
                        throw new UncheckedIOException(e);
                    }
                }
        }, 10);
        checkSeekableStream(b, ss);
    }

    static class SeekableByteArrayInputStream extends ByteArrayInputStream
    {
        public SeekableByteArrayInputStream(byte[] buf)
        {
            super(buf);
        }
        public long position()
        {
            return pos;
        }
        public void seek(long newpos) throws EOFException
        {
            if (pos > Integer.MAX_VALUE)
                throw new EOFException(String.valueOf(newpos));
            pos = (int)newpos;
        }
    }

    @Test
    public void testSeekableGZip2() throws IOException
    {
        byte b[] = randomText(216962);
        int inputLen = b.length;

        b = extendData(b, 13);
        inputLen = b.length;

        byte bo[] = comp == 5 ? chunkedGzip1(b) : gzip1(b);

        SeekableStream.RandomAccessInputStream ras = new SeekableStream.RandomAccessInputStream(new SeekableByteArrayInputStream(bo)) {

            @Override
            protected long position() throws IOException
            {
                return ((SeekableByteArrayInputStream)in).position();
            }

            @Override
            protected void seek(long newpos) throws IOException
            {
                ((SeekableByteArrayInputStream)in).seek(newpos);
            }
        };
        SeekableStream ss = new SeekableStream(new Supplier<InputStream>() {
                public InputStream get()
                {
                    try {
                        InputStream is =  new GZIPInputStream2(ras);
                        if (verbose) System.out.println("Constructed stream " + is);
                        return is;
                    }
                    catch (IOException e)
                    {
                        throw new UncheckedIOException(e);
                    }
                }
        }, ras, 10, inputLen);
        checkSeekableStream(b, ss);
    }
}

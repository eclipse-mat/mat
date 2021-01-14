/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - enhancements for huge dumps
 *******************************************************************************/
package org.eclipse.mat.parser.index;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.lang.ref.SoftReference;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayIntCompressed;
import org.eclipse.mat.collect.ArrayLongCompressed;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.parser.index.IndexWriter.ArrayIntLongCompressed;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.io.SimpleBufferedRandomAccessInputStream;

/**
 * Implementations to read index files.
 */
public abstract class IndexReader
{
    public static final boolean DEBUG = false;

    /**
     * An int to int index reader.
     * 
     * Disk file structure:
     * <pre>
     * Page 0: ArrayIntCompressed
     * Page 1: ArrayIntCompressed
     * ...
     * Page n: ArrayIntCompressed
     * page 0 start in file (8)
     * page 1 start in file (8)
     * ...
     * page n start in file (8)
     * page n+1 start in file (8) (i.e. location of 'page 0 start in file' field)
     * page size (4)
     * total size (4)
     * </pre>
     * 
     * Experimental for version 1.2: 
     * The disk format has been enhanced to allow more than
     * 2^31 entries by using the page n+1 start pointer to find the start
     * of the page offsets, and so the number of pages, and the size field
     * is then negative and used to measure the number of entries on the
     * last page (from 1 to page size).
     * This is experimental and index files with 2^31 entries or more
     * are not compatible with 1.1 or earlier and might not be compatible
     * with 1.3 or later.
     */
    public static class IntIndexReader extends IndexWriter.IntIndex<SoftReference<ArrayIntCompressed>> implements
                    IIndexReader.IOne2OneIndex
    {
        public Object LOCK = new Object();

        File indexFile;
        public SimpleBufferedRandomAccessInputStream in;
        long[] pageStart;

        IntIndexReader(File indexFile, IndexWriter.Pages<SoftReference<ArrayIntCompressed>> pages, long size,
                        int pageSize, long[] pageStart)
        {
            this.size = size;
            this.pageSize = pageSize;
            this.pages = pages;

            this.indexFile = indexFile;
            this.pageStart = pageStart;

            if (indexFile != null)
                open();
        }

        public IntIndexReader(File indexFile, IndexWriter.Pages<SoftReference<ArrayIntCompressed>> pages, int size,
                        int pageSize, long[] pageStart)
        {
            this(indexFile, pages, (long)size, pageSize, pageStart);
        }

        public IntIndexReader(File indexFile) throws IOException
        {
            this(new SimpleBufferedRandomAccessInputStream(new RandomAccessFile(indexFile, "r")), 0, indexFile.length());//$NON-NLS-1$
            this.indexFile = indexFile;
        }

        public IntIndexReader(SimpleBufferedRandomAccessInputStream in, long start, long length) throws IOException
        {
            this.in = in;
            this.in.seek(start + length - 16);

            long lastOffset = this.in.readLong();
            int pageSize = this.in.readInt();
            int size = this.in.readInt();

            int pages;
            if (size >= 0)
            {
                init(size, pageSize);

                pages = (size / pageSize) + (size % pageSize > 0 ? 2 : 1);
            }
            else
            {
                // large dump format, find number of pages using offsets
                pages = (int)((start + length - 8 - lastOffset) / 8);
                // then find the total size from pages and entries in last page
                long sizeL = (pages - 2L) * pageSize - size;
                init(sizeL, pageSize);
            }

            pageStart = new long[pages];

            this.in.seek(start + length - 8 - (pageStart.length * 8));
            this.in.readLongArray(pageStart);
        }

        private synchronized void open()
        {
            try
            {
                if (in != null)
                    return;

                if (indexFile == null)
                    throw new IOException(Messages.IndexReader_Error_IndexIsEmbedded);

                in = new SimpleBufferedRandomAccessInputStream(new RandomAccessFile(this.indexFile, "r"));//$NON-NLS-1$
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        public synchronized void close()
        {
            unload();

            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException ignore)
                {
                    // $JL-EXC$
                }
                finally
                {
                    in = null;
                }
            }
        }

        @Override
        protected ArrayIntCompressed getPage(int page)
        {
            SoftReference<ArrayIntCompressed> ref = pages.get(page);
            ArrayIntCompressed array = ref == null ? null : ref.get();
            if (array == null)
            {
                synchronized (LOCK)
                {
                    ref = pages.get(page);
                    array = ref == null ? null : ref.get();

                    if (array == null)
                    {
                        try
                        {
                            byte[] buffer = null;

                            this.in.seek(pageStart[page]);

                            buffer = new byte[(int) (pageStart[page + 1] - pageStart[page])];
                            if (this.in.read(buffer) != buffer.length)
                                throw new IOException();

                            array = new ArrayIntCompressed(buffer);

                            synchronized (pages)
                            {
                                pages.put(page, new SoftReference<ArrayIntCompressed>(array));
                            }
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
            return array;
        }

        public void delete()
        {
            close();

            if (indexFile != null)
                indexFile.delete();
        }

    }

    /**
     * Internal class used to index using large offsets
     * into the body of stream of entries of a 1 to N index.
     * This looks like an IntIndexReader but the pages can hold
     * long values.
     */
    static class PositionIndexReader extends IntIndexReader
    {
        public PositionIndexReader(File indexFile) throws IOException
        {
            super(indexFile);
        }
        public PositionIndexReader(SimpleBufferedRandomAccessInputStream in, long start, long length) throws IOException
        {
            super(in, start, length);
        }
        public PositionIndexReader(File indexFile, IndexWriter.Pages<SoftReference<ArrayIntCompressed>> pages, int size,
                        int pageSize, long[] pageStart)
        {
            super(indexFile, pages, (long)size, pageSize, pageStart);
        }

        @Override
        long getPos(int index) {
            int page = page(index);
            int offset = offset(index);
            ArrayIntLongCompressed a = getPage(page);
            return a.getPos(offset);
        }

        @Override
        protected ArrayIntLongCompressed getPage(int page)
        {
            SoftReference<ArrayIntCompressed> ref = pages.get(page);
            ArrayIntCompressed array = ref == null ? null : ref.get();
            if (array instanceof ArrayIntLongCompressed)
            {
                return (ArrayIntLongCompressed)array;
            }
            else 
            {
                synchronized (LOCK)
                {
                    if (array == null)
                        array = super.getPage(page);
                    ArrayIntLongCompressed ret = new ArrayIntLongCompressed(array);
                    synchronized (pages)
                    {
                        pages.put(page, new SoftReference<ArrayIntCompressed>(ret));
                    }
                    return ret;
                }
            }
        }
    }

    /**
     * Creates a index reader for array sizes, presuming the sizes are stored as ints
     * and get expanded in the reverse of the compression.
     * @since 1.0
     */
    public static class SizeIndexReader implements IIndexReader.IOne2SizeIndex
    {
        private IIndexReader.IOne2OneIndex idx;

        /**
         * Constructor used when reopening a dump
         * @param indexFile
         * @throws IOException
         */
        public SizeIndexReader(File indexFile) throws IOException
        {
            this(new IntIndexReader(indexFile));
        }
        
        /**
         * Construct a size index reader based on a int index holding the compressed data
         * @param idx
         */
        public SizeIndexReader(IIndexReader.IOne2OneIndex idx)
        {
            this.idx = idx;
        }
        
        /**
         * Expand the compressed size.
         */
        public long getSize(int index)
        {
            return IndexWriter.SizeIndexCollectorUncompressed.expand(get(index));
        }

        /**
         * Get the (compressed) size.
         * Delegate to the int index.
         */
        public int get(int index)
        {
            return idx.get(index);
        }

        /**
         * Delegate to the int index.
         */
        public int[] getAll(int[] index)
        {
            return idx.getAll(index);
        }

        /**
         * Delegate to the int index.
         */
        public int[] getNext(int index, int length)
        {
            return idx.getNext(index, length);
        }

        /**
         * Delegate to the int index.
         */
        public void close() throws IOException
        {
            idx.close();
        }

        /**
         * Delegate to the int index.
         */
        public void delete()
        {
            idx.delete();
        }

        /**
         * Delegate to the int index.
         */
        public int size()
        {
            return idx.size();
        }

        /**
         * Delegate to the int index.
         */
        public void unload() throws IOException
        {
            idx.unload();
        }
    }

    /* package */static class IntIndex1NReader implements IIndexReader.IOne2ManyIndex
    {
        File indexFile;
        SimpleBufferedRandomAccessInputStream in;
        IntIndexReader header;
        IntIndexReader body;

        public IntIndex1NReader(File indexFile) throws IOException
        {
            try
            {
                this.indexFile = indexFile;

                open();

                long indexLength = indexFile.length();
                in.seek(indexLength - 8);
                long divider = in.readLong();

                this.header = new PositionIndexReader(in, divider, indexLength - divider - 8);
                this.body = new IntIndexReader(in, 0, divider);

                this.body.LOCK = this.header.LOCK;

            }
            catch (RuntimeException e)
            {
                close();
                throw e;
            }
        }

        public IntIndex1NReader(File indexFile, IIndexReader.IOne2OneIndex header, IIndexReader.IOne2OneIndex body)
        {
            this.indexFile = indexFile;
            this.header = ((IntIndexReader) header);
            this.body = ((IntIndexReader) body);

            this.body.LOCK = this.header.LOCK;

            open();
        }

        public int[] get(int index)
        {
            long p = header.getPos(index);

            int length = body.get(p);

            return body.getNext(p + 1, length);
        }

        protected synchronized void open()
        {
            try
            {
                if (in == null)
                {

                    in = new SimpleBufferedRandomAccessInputStream(new RandomAccessFile(this.indexFile, "r"));//$NON-NLS-1$

                    if (this.header != null)
                        this.header.in = in;

                    if (this.body != null)
                        this.body.in = in;
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        public synchronized void close()
        {
            header.unload();
            body.unload();

            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException ignore)
                {
                    // $JL-EXC$
                }
                finally
                {
                    in = null;
                    if (this.header != null)
                        this.header.in = null;
                    if (this.body != null)
                        this.body.in = null;
                }
            }
        }

        public void unload() throws IOException
        {
            header.unload();
            body.unload();
        }

        public int size()
        {
            return header.size();
        }

        public void delete()
        {
            close();

            if (indexFile != null)
                indexFile.delete();
        }
    }

    public static class IntIndex1NSortedReader extends IntIndex1NReader
    {
        public IntIndex1NSortedReader(File indexFile) throws IOException
        {
            super(indexFile);
        }

        /**
         * @throws IOException
         */
        public IntIndex1NSortedReader(File indexFile, IOne2OneIndex header, IOne2OneIndex body) throws IOException
        {
            super(indexFile, header, body);
        }

        /**
         * The header holds positions encoded as p+1 into the body
         * There is no length field - the length is up to the next one,
         * which is greater than the first.
         * 0 means no data
         * E.g.
         * 10 6 1 0 14
         * Reading item 0 gets from [10,14)
         * Reading item 1 gets from [6,14)
         * Reading item 2 gets from [1,14)
         * Reading item 3 gets an empty array
         */
        public int[] get(int index)
        {
            long p0;
            long p1;

            if (index + 1 < header.size())
            {
                p0 = header.getPos(index++);
                p1 = header.getPos(index);
                if (p0 == 0)
                    return new int[0];

                for (index++; p1 < p0 && index < header.size(); index++)
                    p1 = header.getPos(index);

                if (p1 < p0)
                    p1 = body.size + 1;
            }
            else
            {
                p0 = header.getPos(index);
                if (p0 == 0)
                    return new int[0];
                p1 = body.size + 1;
            }

            return body.getNext(p0 - 1, (int)(p1 - p0));
        }

    }

    static class InboundReader extends IntIndex1NSortedReader implements IIndexReader.IOne2ManyObjectsIndex
    {
        public InboundReader(File indexFile) throws IOException
        {
            super(indexFile);
        }

        public InboundReader(File indexFile, IOne2OneIndex header, IOne2OneIndex body) throws IOException
        {
            super(indexFile, header, body);
        }

        public int[] getObjectsOf(Serializable key) throws SnapshotException, IOException
        {
            if (key == null)
                return new int[0];

            if (key instanceof long[])
            {
                long[] pos = (long[]) key;

                synchronized (this)
                {
                    return body.getNext(pos[0], (int)pos[1]);
                }
            }
            else
            {
                int[] pos = (int[]) key;

                synchronized (this)
                {
                    // Treat pos[0] as unsigned
                    if (pos[0] >= 0)
                        return body.getNext(pos[0], pos[1]);
                    else
                        return body.getNext(pos[0] & 0xffffffffL, pos[1]);
                }
            }
        }

    }

    /**
     * Creates a int to long index reader
     * 
     * Disk file structure:
     * <pre>
     * Page 0: ArrayLongCompressed
     * Page 1: ArrayLongCompressed
     * ...
     * Page n: ArrayLongCompressed
     * page 0 start in file (8)
     * page 1 start in file (8)
     * ...
     * page n start in file (8)
     * page n+1 start in file (8) (i.e. location of 'page 0 start in file' field)
     * page size (4)
     * total size (4)
     * </pre>
     */
    public static class LongIndexReader extends IndexWriter.LongIndex implements IIndexReader.IOne2LongIndex
    {
        Object LOCK = new Object();

        File indexFile;
        SimpleBufferedRandomAccessInputStream in;
        long[] pageStart;

        public LongIndexReader(File indexFile, HashMapIntObject<Object> pages, int size, int pageSize, long[] pageStart)
                        throws IOException
        {
            this.size = size;
            this.pageSize = pageSize;
            this.pages = pages;

            this.indexFile = indexFile;
            this.pageStart = pageStart;

            open();
        }

        public LongIndexReader(File indexFile) throws IOException
        {
            this(new SimpleBufferedRandomAccessInputStream(new RandomAccessFile(indexFile, "r")), 0, indexFile.length());//$NON-NLS-1$
            this.indexFile = indexFile;

            open();
        }

        protected LongIndexReader(SimpleBufferedRandomAccessInputStream in, long start, long length) throws IOException
        {
            this.in = in;
            this.in.seek(start + length - 8);

            int pageSize = this.in.readInt();
            int size = this.in.readInt();

            init(size, pageSize);

            int pages = (size / pageSize) + (size % pageSize > 0 ? 2 : 1);

            pageStart = new long[pages];

            this.in.seek(start + length - 8 - (pageStart.length * 8));
            this.in.readLongArray(pageStart);
        }

        private synchronized void open() throws IOException
        {
            if (in != null)
                return;

            if (indexFile == null)
                throw new IOException(Messages.IndexReader_Error_IndexIsEmbedded);

            in = new SimpleBufferedRandomAccessInputStream(new RandomAccessFile(this.indexFile, "r"));//$NON-NLS-1$
        }

        public synchronized void close()
        {
            unload();

            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException ignore)
                {
                    // $JL-EXC$
                }
                finally
                {
                    in = null;
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected ArrayLongCompressed getPage(int page)
        {
            SoftReference<ArrayLongCompressed> ref = (SoftReference<ArrayLongCompressed>) pages.get(page);
            ArrayLongCompressed array = ref == null ? null : ref.get();
            if (array == null)
            {
                synchronized (LOCK)
                {
                    ref = (SoftReference<ArrayLongCompressed>) pages.get(page);
                    array = ref == null ? null : ref.get();

                    if (array == null)
                    {
                        try
                        {
                            byte[] buffer = null;

                            this.in.seek(pageStart[page]);

                            buffer = new byte[(int) (pageStart[page + 1] - pageStart[page])];
                            if (this.in.read(buffer) != buffer.length)
                                throw new IOException();

                            array = new ArrayLongCompressed(buffer);

                            synchronized (pages)
                            {
                                pages.put(page, new SoftReference<ArrayLongCompressed>(array));
                            }
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
            return array;
        }

        public void delete()
        {
            close();

            if (indexFile != null)
                indexFile.delete();
        }

    }

    public static class LongIndex1NReader implements IIndexReader
    {
        File indexFile;
        SimpleBufferedRandomAccessInputStream in;
        IntIndexReader header;
        LongIndexReader body;

        public LongIndex1NReader(File indexFile) throws IOException
        {
            this.indexFile = indexFile;

            open();

            long indexLength = indexFile.length();
            in.seek(indexLength - 8);
            long divider = in.readLong();

            this.header = new IntIndexReader(in, divider, indexLength - divider - 8);
            this.body = new LongIndexReader(in, 0, divider);

            this.body.LOCK = this.header.LOCK;
        }

        public long[] get(int index)
        {
            int p = header.get(index);

            if (p == 0)
                return new long[0];

            int length = (int) body.get(p - 1);

            return body.getNext(p, length);
        }

        protected synchronized void open()
        {
            try
            {
                if (in == null)
                {

                    in = new SimpleBufferedRandomAccessInputStream(new RandomAccessFile(this.indexFile, "r"));//$NON-NLS-1$

                    if (this.header != null)
                        this.header.in = in;

                    if (this.body != null)
                        this.body.in = in;
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        public synchronized void close()
        {
            unload();

            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException ignore)
                {
                    // $JL-EXC$
                }
                finally
                {
                    in = this.header.in = this.body.in = null;
                }
            }
        }

        public void unload()
        {
            header.unload();
            body.unload();
        }

        public int size()
        {
            return header.size();
        }

        public void delete()
        {
            close();

            if (indexFile != null)
                indexFile.delete();
        }
    }
}

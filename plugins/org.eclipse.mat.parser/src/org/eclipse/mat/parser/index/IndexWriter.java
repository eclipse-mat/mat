/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - enhancements for huge dumps
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.parser.index;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayIntCompressed;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.ArrayLongCompressed;
import org.eclipse.mat.collect.ArrayUtils;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapIntObject.Entry;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2OneIndex;
import org.eclipse.mat.parser.index.IndexReader.SizeIndexReader;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.io.BitInputStream;
import org.eclipse.mat.parser.io.BitOutputStream;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public abstract class IndexWriter
{
    public static final int PAGE_SIZE_INT = 1000000;
    public static final int PAGE_SIZE_LONG = 500000;
    // Set this to true to test more code paths with smaller indices
    private static final boolean TEST = false;
    // How much to resize break points for large formats to make testing easier
    private static final int TESTSCALE = TEST ? 18 : 0;
    // Switch point from plain size to encoded size for 1 to 1 index
    private static final int FORMAT1_MAX_SIZE = Integer.MAX_VALUE >>> TESTSCALE;
    // Switch point for using a long valued header index
    private static final long MAX_OLD_HEADER_VALUE = 0xffffffffL >>> TESTSCALE;
    // Switch point for inbound key to using longs
    private static final long INBOUND_MAX_KEY1 = Integer.MAX_VALUE >>> TESTSCALE;

    public interface KeyWriter
    {
        public void storeKey(int index, Serializable key);
    }

    // //////////////////////////////////////////////////////////////
    // integer based indices
    // //////////////////////////////////////////////////////////////

    public static class Identifier implements IIndexReader.IOne2LongIndex
    {
        long[] identifiers;
        int size;

        public void add(long id)
        {
            if (identifiers == null)
            {
                identifiers = new long[10000];
                size = 0;
            }

            if (size + 1 > identifiers.length)
            {
                int minCapacity = size + 1;
                int newCapacity = newCapacity(identifiers.length, minCapacity);
                if (newCapacity < minCapacity)
                {
                    // Avoid strange exceptions later
                    throw new OutOfMemoryError(MessageUtil.format(Messages.IndexWriter_Error_ArrayLength, minCapacity, newCapacity)); 
                }
                identifiers = copyOf(identifiers, newCapacity);
            }

            identifiers[size++] = id;
        }

        public void sort()
        {
            Arrays.parallelSort(identifiers, 0, size);
        }

        public int size()
        {
            return size;
        }

        public long get(int index)
        {
            if (index < 0 || index >= size)
                throw new IndexOutOfBoundsException("Index: "+index+", Size: "+size); //$NON-NLS-1$//$NON-NLS-2$

            return identifiers[index];
        }

        public int reverse(long val)
        {
            int a, c;
            for (a = 0, c = size; a < c;)
            {
                // Avoid overflow problems by using unsigned divide by 2
                int b = (a + c) >>> 1;
                long probeVal = get(b);
                if (val < probeVal)
                {
                    c = b;
                }
                else if (probeVal < val)
                {
                    a = b + 1;
                }
                else
                {
                    return b;
                }
            }
            // Negative index indicates not found (and where to insert)
            return -1 - a;
        }

        public IteratorLong iterator()
        {
            return new IteratorLong()
            {

                int index = 0;

                public boolean hasNext()
                {
                    return index < size;
                }

                public long next()
                {
                    return identifiers[index++];
                }

            };
        }

        public long[] getNext(int index, int length)
        {
            long answer[] = new long[length];
            for (int ii = 0; ii < length; ii++)
                answer[ii] = identifiers[index + ii];
            return answer;
        }

        public void close() throws IOException
        {}

        public void delete()
        {
            identifiers = null;
        }

        public void unload() throws IOException
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class IntIndexCollectorUncompressed
    {
        int[] dataElements;

        public IntIndexCollectorUncompressed(int size)
        {
            dataElements = new int[size];
        }

        public void set(int index, int value)
        {
            dataElements[index] = value;
        }

        public int get(int index)
        {
            return dataElements[index];
        }

        public IIndexReader.IOne2OneIndex writeTo(File indexFile) throws IOException
        {
            return new IntIndexStreamer().writeTo(indexFile, dataElements);
        }
    }

    /**
     * Store sizes of objects by
     * compressing the size to a 32-bit int.
     * @since 1.0
     */
    public static class SizeIndexCollectorUncompressed extends IntIndexCollectorUncompressed
    {

        public SizeIndexCollectorUncompressed(int size)
        {
            super(size);
        }

        /**
         * Cope with objects bigger than Integer.MAX_VALUE.
         * E.g. double[Integer.MAX_VALUE] 
         * The original problem was that the array to size mapping had an integer as the size (IntIndexCollectorUncompressed). 
         * This array would be approximately 0x18 + 0x8 * 0x7fffffff = 0x400000010 bytes, too big for an int.
         * Expanding the array size array to longs could be overkill.
         * Instead we do some simple compression - values 0 - 0x7fffffff convert as now,
         * int values 0x80000000 to 0xffffffff convert to <code>(n &amp; 0x7fffffffL)*8 + 0x80000000L</code>.
         * @param y the long value in the range -1 to 0x7fffffff, 0x80000000L to 0x400000000L
         * @return the compressed value as an int
         */
        public static int compress(long y)
        {
            int ret;
            if (y < 0)
                ret = -1;
            else if (y <= Integer.MAX_VALUE)
                ret = (int) y;
            else if (y <= 0x400000000L)
            {
                ret = (int) (y / 8) + 0x70000000;
            }
            else
                ret = 0xf0000000;
            return ret;
        }

        /**
         * Expand the result of the compression
         * @param x the compressed value
         * @return the expanded value as a long in the range -1 to 0x7fffffff, 0x80000000L to 0x400000000L
         */
        public static long expand(int x) {
            long ret;
            if (x >= -1)
                ret = x;
            else if (x < 0xf0000000)
            {
                ret = (x & 0x7fffffffL) * 8 + 0x80000000L;
            }
            else
                ret = 0x400000000L;
            return ret;
        }

        public void set(int index, long value)
        {
            set(index, compress(value));
        }

        public long getSize(int index)
        {
            int v = get(index);
            return expand(v);
        }

        public IIndexReader.IOne2SizeIndex writeTo(File indexFile) throws IOException
        {
            return new SizeIndexReader(new IntIndexStreamer().writeTo(indexFile, dataElements));
        }
    }

    static class Pages<V>
    {
        int size;
        Object[] elements;

        public Pages(int initialSize)
        {
            elements = new Object[initialSize];
            size = 0;
        }

        private void ensureCapacity(int minCapacity)
        {
            int oldCapacity = elements.length;
            if (minCapacity > oldCapacity)
            {
                int newCapacity = newCapacity(oldCapacity, minCapacity);
                if (newCapacity < minCapacity)
                {
                    // Avoid strange exceptions later
                    throw new OutOfMemoryError(MessageUtil.format(Messages.IndexWriter_Error_ObjectArrayLength, minCapacity, newCapacity));
                }
                Object[] copy = new Object[newCapacity];
                System.arraycopy(elements, 0, copy, 0, Math.min(elements.length, newCapacity));
                elements = copy;
            }
        }

        @SuppressWarnings("unchecked")
        public V get(int key)
        {
            return (key >= elements.length) ? null : (V) elements[key];
        }

        public void put(int key, V value)
        {
            ensureCapacity(key + 1);
            elements[key] = value;
            size = Math.max(size, key + 1);
        }

        public int size()
        {
            return size;
        }
    }

    abstract static class IntIndex<V>
    {
        int pageSize;
        long size;
        Pages<V> pages;

        protected IntIndex()
        {}

        protected IntIndex(int size)
        {
            init(size, PAGE_SIZE_INT);
        }

        protected void init(int size, int pageSize)
        {
            this.size = size;
            this.pageSize = pageSize;
            this.pages = new Pages<V>(size / pageSize + 1);
        }

        void init(long size, int pageSize)
        {
            this.size = size;
            this.pageSize = pageSize;
            this.pages = new Pages<V>((int)(size / pageSize) + 1);
        }

        public int get(int index)
        {
            // Should we allow index to be unsigned?
            return get0(index);
        }

        int get(long index)
        {
            if (index == (int)index) 
            {
                // in case get(int) is overridden 
                return get((int)index);
            }
            else
            {
                return get0(index);
            }
        }

        private int get0(long index)
        {
            int page = page(index);
            int pageIndex = offset(index);
            ArrayIntCompressed array = getPage(page);
            return array.get(pageIndex);
        }

        long getPos(int index)
        {
            // Should we allow index to be unsigned?
            return get(index) & 0xffffffffL;
        }

        private int page(int index)
        {
            return index / pageSize;
        }

        private int offset(int index)
        {
            return index % pageSize;
        }

        int page(long index)
        {
            return (int)(index / pageSize);
        }

        int offset(long index)
        {
            return (int)(index % pageSize);
        }

        public int[] getNext(int index, int length)
        {
            // Should we allow index to be unsigned?
            return getNext0(index, length);
        }

        int[] getNext(long index, int length)
        {
            if (index == (int)index)
            {
                // in case getNext(int, int) is overridden 
                return getNext((int)index, length);
            }
            else
            {
                return getNext0(index, length);
            }
        }

        private int[] getNext0(long index, int length)
            {
            int answer[] = new int[length];
            if (length == 0)
                return answer;
            int page = page(index);
            int pageIndex = offset(index);

            ArrayIntCompressed array = getPage(page);
            for (int ii = 0; ii < length; ii++)
            {
                answer[ii] = array.get(pageIndex++);
                if (pageIndex >= pageSize && ii + 1 < length)
                {
                    array = getPage(++page);
                    pageIndex = 0;
                }
            }

            return answer;
        }

        public int[] getAll(int index[])
        {
            int[] answer = new int[index.length];

            int page = -1;
            ArrayIntCompressed array = null;

            for (int ii = 0; ii < answer.length; ii++)
            {
                int p = page(index[ii]);
                if (p != page)
                    array = getPage(page = p);

                answer[ii] = array.get(offset(index[ii]));
            }

            return answer;
        }

        public void set(int index, int value)
        {
            ArrayIntCompressed array = getPage(page(index));
            array.set(offset(index), value);
        }

        protected abstract ArrayIntCompressed getPage(int page);

        public synchronized void unload()
        {
            this.pages = new Pages<V>(page(size) + 1);
        }

        public int size()
        {
            if (size > Integer.MAX_VALUE)
                throw new IllegalStateException();
            return (int)Math.min(Integer.MAX_VALUE, size);
        }

        public IteratorInt iterator()
        {
            return new IntIndexIterator(this);
        }
    }

    static class IntIndexIterator implements IteratorInt
    {
        IntIndex<?> intArray;
        long nextIndex = 0;

        public IntIndexIterator(IntIndex<?> intArray)
        {
            this.intArray = intArray;
        }

        public int next()
        {
            return intArray.get(nextIndex++);
        }

        public boolean hasNext()
        {
            return nextIndex < intArray.size;
        }
    }


    public static class IntIndexCollector extends IntIndex<ArrayIntCompressed> implements IOne2OneIndex
    {
        final int mostSignificantBit;
        final int size;
        final int pageSize = IndexWriter.PAGE_SIZE_INT;
        final ConcurrentHashMap<Integer, ArrayIntCompressed> pages = new ConcurrentHashMap<Integer, ArrayIntCompressed>();

        public IntIndexCollector(int size, int mostSignificantBit)
        {
            this.size = size;
            this.mostSignificantBit = mostSignificantBit;
        }

        protected ArrayIntCompressed getPage(int page)
        {
            ArrayIntCompressed existing = pages.get(page);
            if (existing != null) return existing;

            int ps = page < (size / pageSize) ? pageSize : size % pageSize;
            ArrayIntCompressed newArray = new ArrayIntCompressed(ps, 31 - mostSignificantBit, 0);
            existing = pages.putIfAbsent(page, newArray);
            return (existing != null) ? existing : newArray;
        }

        public IIndexReader.IOne2OneIndex writeTo(File indexFile) throws IOException
        {
            // needed to re-compress
            return new IntIndexStreamer().writeTo(indexFile, new IteratorInt()
            {
                int index = 0;
                public boolean hasNext()
                {
                    return (index < size);
                }

                public int next()
                {
                    return get(index++);
                }
            });
        }

        public IIndexReader.IOne2OneIndex writeTo(DataOutputStream out, long position) throws IOException
        {
            // needed to re-compress
            return new IntIndexStreamer().writeTo(out, position, new IteratorInt()
            {
                int index = 0;
                public boolean hasNext()
                {
                    return (index < size);
                }

                public int next()
                {
                    return get(index++);
                }
            });
        }

        public void set(int index, int value)
        {
            ArrayIntCompressed array = getPage(index / pageSize);
            // TODO unlock this by having ArrayIntCompressed use atomics
            // uses bit operations internally, so we should sync against the page
            synchronized(array)
            {
                array.set(index % pageSize, value);
            }
        }

        public int get(int index)
        {
            ArrayIntCompressed array = getPage(index / pageSize);

            // TODO unlock this by having ArrayIntCompressed use atomics
            // we currently lock a whole page, when we only need a single element
            synchronized(array) {
                return array.get(index % pageSize);
            }
        }

        public void close() throws IOException
        { }

        public void delete()
        {
            pages.clear();
        }

        public int size()
        {
            return size;
        }

        public void unload()
        {
            throw new UnsupportedOperationException(Messages.IndexWriter_NotImplemented);
        }

        public int[] getAll(int[] index)
        {
            throw new UnsupportedOperationException(Messages.IndexWriter_NotImplemented);
        }

        public int[] getNext(int index, int length)
        {
            throw new UnsupportedOperationException(Messages.IndexWriter_NotImplemented);
        }
    }

    public static class IntIndexStreamer extends IntIndex<SoftReference<ArrayIntCompressed>>
    {
        DataOutputStream out;
        ArrayLong pageStart;
        int[] page;
        int left;

        // tracks number of pages added, may not be available if they are still being compressed
        int pagesAdded;

        // if the writer task has an exception during async we want to throw it on the next write
        Exception storedException = null;
        // if the writer task has an OutOfMemoryError during async we want to throw it on the next write
        OutOfMemoryError storedError = null;

        // TODO bound these queues, and add more compressor threads if needed
        // for the moment it is ok, as the compress and write stage is fast
        // could do more threads but no point
        final ExecutorService compressor = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "IntIndexStreamer-Compressor")); //$NON-NLS-1$
        // this must be single threaded to ensure page update logic stays correct
        final ExecutorService writer = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "IntIndexStreamer-Writer")); //$NON-NLS-1$

        public IIndexReader.IOne2OneIndex writeTo(File indexFile, IteratorInt iterator) throws IOException
        {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

            openStream(out, 0);
            addAll(iterator);
            closeStream();

            out.close();

            return getReader(indexFile);
        }

        public IIndexReader.IOne2OneIndex writeTo(File indexFile, int[] array) throws IOException
        {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

            openStream(out, 0);
            addAll(array);
            closeStream();

            out.close();

            return getReader(indexFile);
        }

        public IIndexReader.IOne2OneIndex writeTo(DataOutputStream out, long position, IteratorInt iterator)
                        throws IOException
        {
            openStream(out, position);
            addAll(iterator);
            closeStream();

            return getReader(null);
        }

        public IIndexReader.IOne2OneIndex writeTo(DataOutputStream out, long position, int[] array) throws IOException
        {
            openStream(out, position);
            addAll(array);
            closeStream();

            return getReader(null);
        }

        void openStream(DataOutputStream out, long position)
        {
            this.out = out;

            init(0, PAGE_SIZE_INT);

            this.page = new int[pageSize];
            this.pageStart = new ArrayLong();
            this.pageStart.add(position);
            this.left = page.length;
        }

        /**
         * @return total bytes written to index file
         */
        long closeStream() throws IOException
        {
            try
            {
                if (left < page.length)
                    addPage();
                else
                    new WriterTask(null); // Dummy to test for pending exception
            } 
            catch (IOException e)
            {
                // Early termination with an exception
                try
                {
                    compressor.shutdownNow();
                    compressor.awaitTermination(100, TimeUnit.SECONDS);
                    writer.shutdownNow();
                    writer.awaitTermination(100, TimeUnit.SECONDS);
                    throw e;
                }
                catch (InterruptedException ie)
                {
                    throw new IOException(ie);
                }
            }

            try
            {
                compressor.shutdown();
                compressor.awaitTermination(100, TimeUnit.SECONDS);
                writer.shutdown();
                writer.awaitTermination(100, TimeUnit.SECONDS);
            }
            catch (InterruptedException ie)
            {
                throw new IOException(ie);
            }

            // write header information
            for (int jj = 0; jj < pageStart.size(); jj++)
                out.writeLong(pageStart.get(jj));

            out.writeInt(pageSize);
            // Encoded size is the negative number of entries in the last page
            int s = size <= FORMAT1_MAX_SIZE ? (int)size : -(int)((size + pageSize - 1) % pageSize + 1);
            out.writeInt(s);

            this.page = null;

            this.out = null;

            return this.pageStart.lastElement() + (8 * pageStart.size()) + 8 - this.pageStart.firstElement();
        }

        IndexReader.IntIndexReader getReader(File indexFile)
        {
            return new IndexReader.IntIndexReader(indexFile, pages, size, pageSize, pageStart.toArray());
        }

        void addAll(IteratorInt iterator) throws IOException
        {
            while (iterator.hasNext())
                add(iterator.next());
        }

        void add(int value) throws IOException
        {
            if (left == 0)
                addPage();

            page[page.length - left--] = value;
            size++;

        }

        void addAll(int[] values) throws IOException
        {
            addAll(values, 0, values.length);
        }

        void addAll(int[] values, int offset, int length) throws IOException
        {
            while (length > 0)
            {
                if (left == 0)
                    addPage();

                int chunk = Math.min(left, length);

                System.arraycopy(values, offset, page, page.length - left, chunk);
                left -= chunk;
                size += chunk;

                length -= chunk;
                offset += chunk;
            }
        }

        long addAllWithLengthFirst(int[] values, int offset, int length) throws IOException
        {
            long startPos = size;
            add(length);
            addAll(values, offset, length);
            return startPos;
        }

        private void addPage() throws IOException
        {
            Future<byte[]> compressionResult = compressor.submit(new CompressionTask(page, left, pagesAdded));
            // compression may finish in any order, but writes have to be correct, so order them
            writer.execute(new WriterTask(compressionResult));

            pagesAdded += 1;
            page = new int[page.length];
            left = page.length;
        }

        @Override
        protected ArrayIntCompressed getPage(int page)
        {
            throw new UnsupportedOperationException();
        }

        /*
         * Compresses a page of data to a byte array
         */
        private class CompressionTask implements Callable<byte[]>
        {
            final int[] page;
            final int left;
            final int pageNumber;
            public CompressionTask(final int[] page, final int left, final int pageNumber)
            {
                this.page = page;
                this.left = left;
                this.pageNumber = pageNumber;
            }

            public byte[] call()
            {
                ArrayIntCompressed array = new ArrayIntCompressed(page, 0, page.length - left);
                pages.put(pageNumber, new SoftReference<ArrayIntCompressed>(array));
                return array.toByteArray();
            }
        }

        /*
         * Writes data to output
         * Assumes there is only a single WriterTask that can ever be running
         */
        private class WriterTask implements Runnable {
            final Future<byte[]> byteData;
            public WriterTask(final Future<byte[]> byteData) throws IOException
            {
                this.byteData = byteData;
                if (storedException != null)
                {
                    throw new IOException(Messages.IndexWriter_Stored_Exception, storedException);
                }
                if (storedError != null)
                {
                    throw new IOException(Messages.IndexWriter_StoredError, storedError);
                }
            }

            public void run() {
                byte[] buffer;
                try
                {
                    buffer = byteData.get();
                    out.write(buffer);
                    int written = buffer.length;

                    // update the shared page list
                    pageStart.add(pageStart.lastElement() + written);
                }
                catch (InterruptedException | ExecutionException | IOException e)
                {
                    storedException = e;
                }
                catch (OutOfMemoryError e)
                {
                    storedError = e;
                }
            }
        }
    }

    public static class IntArray1NWriter 
    {
        // length of set() queue to buffer before writing to output as a batch
        private static final int TASK_BUFFER_SIZE = 1024;
        /** Number of ints to write out in the buffer */
        private static final int TASK_BUFFER_MAX_OBJECTS = 10000;
        /** Size of all arrays in the buffer */
        private static final int TASK_BUFFER_MAX_MEMORY = 20000;

        int[] header;
        // Used to expand the range of values stored in the header up to 2^40
        byte[] header2;
        File indexFile;

        DataOutputStream out;
        IntIndexStreamer body;

        CopyOnWriteArrayList<ArrayListSetTask> allTasks = new CopyOnWriteArrayList<ArrayListSetTask>();
        ThreadLocal<ArrayListSetTask> threadTaskQueue = ThreadLocal.withInitial(new Supplier<ArrayListSetTask>()
        {
            public ArrayListSetTask get()
            {
                ArrayListSetTask newList = new ArrayListSetTask(TASK_BUFFER_SIZE);
                allTasks.add(newList);
                return newList;
            }
        });

        public IntArray1NWriter(int size, File indexFile) throws IOException
        {
            this.header = new int[size];
            this.header2 = new byte[size];
            this.indexFile = indexFile;

            this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
            this.body = new IntIndexStreamer();
            this.body.openStream(this.out, 0);
        }

        public void log(Identifier identifier, int index, ArrayLong references) throws IOException
        {
            log((IIndexReader.IOne2LongIndex)identifier, index, references);
        }

        /**
         * @since 1.2
         */
        public void log(IIndexReader.IOne2LongIndex identifier, int index, ArrayLong references) throws IOException
        {
            // remove duplicates and convert to identifiers
            // keep pseudo reference as first one

            long pseudo = references.firstElement();

            references.sort();

            int[] objectIds = new int[references.size()];
            int length = 1;

            long current = 0, last = references.firstElement() - 1;
            for (int ii = 0; ii < objectIds.length; ii++)
            {
                current = references.get(ii);
                if (last != current)
                {
                    int objectId = identifier.reverse(current);

                    if (objectId >= 0)
                    {
                        int jj = (current == pseudo) ? 0 : length++;
                        objectIds[jj] = objectId;
                    }

                }

                last = current;
            }

            this.set(index, objectIds, 0, length);
        }

        /**
         * must not contain duplicates!
         */
        public void log(int index, ArrayInt references) throws IOException
        {
            this.set(index, references.toArray(), 0, references.size());
        }

        public void log(int index, int[] values) throws IOException
        {
            this.set(index, values, 0, values.length);
        }

        void setHeader(int index, long val)
        {
            header[index] = (int)val;
            header2[index] = (byte)(val >> 32);
        }

        private long getHeader(int index)
        {
            return ((header2[index] & 0xffL) << 32) | (header[index] & 0xffffffffL);
        }

        protected void set(int index, int[] values, int offset, int length) throws IOException
        {
            ArrayListSetTask tasks = threadTaskQueue.get();
            tasks.add(new SetTask(index, values, offset, length));

            long memsize = tasks.memcount;
            long objcount = tasks.objcount;
            if (tasks.size() >= TASK_BUFFER_SIZE || memsize > TASK_BUFFER_MAX_OBJECTS || objcount > TASK_BUFFER_MAX_MEMORY)
            {
                publishTasks(tasks);
                tasks.clear();
            }
        }

        void publishTasks(final ArrayListSetTask tasks) throws IOException
        {
            synchronized(body)
            {
                for(SetTask t : tasks)
                {
                    long bodyPos = body.addAllWithLengthFirst(t.values, t.offset, t.length);
                    setHeader(t.index, bodyPos);
                }
            }
        }

        public IIndexReader.IOne2ManyIndex flush() throws IOException
        {
            synchronized(body)
            {
                for(ArrayListSetTask list : allTasks)
                {
                    publishTasks(list);
                    list.clear();
                }
            }

            long divider = body.closeStream();

            IIndexReader.IOne2OneIndex headerIndex = null;
            boolean usesHeader2 = false;
            for(int i = 0; i < header2.length; i++)
            {
                if (header2[i] != 0)
                {
                    usesHeader2 = true;
                    break;
                }
            }
            if (usesHeader2)
            {
                headerIndex = new PosIndexStreamer().writeTo2(out, divider, new IteratorLong()
                {
                    int i;

                    public boolean hasNext()
                    {
                        return i < header.length;
                    }

                    public long next()
                    {
                        long ret = getHeader(i++);
                        return ret;
                    }
                });

            }
            else
            {
                headerIndex = new IntIndexStreamer().writeTo(out, divider, header);
            }

            out.writeLong(divider);

            out.close();
            out = null;

            return createReader(headerIndex, body.getReader(null));
        }

        /**
         * @throws IOException
         */
        protected IIndexReader.IOne2ManyIndex createReader(IIndexReader.IOne2OneIndex headerIndex,
                        IIndexReader.IOne2OneIndex bodyIndex) throws IOException
        {
            return new IndexReader.IntIndex1NReader(this.indexFile, headerIndex, bodyIndex);
        }

        public void cancel()
        {
            try
            {
                if (out != null)
                {
                    out.close();
                    if (body != null)
                        body.closeStream();
                    body = null;
                    out = null;
                }
            }
            catch (IOException ignore)
            {}
            finally
            {
                if (indexFile.exists())
                    indexFile.delete();
            }
        }

        public File getIndexFile()
        {
            return indexFile;
        }

        private static class SetTask {
            final int index;
            final int[] values;
            final int offset;
            final int length;
            public SetTask(final int index, final int[] values, final int offset, final int length) {
                this.index = index;
                this.values = values;
                this.offset = offset;
                this.length = length;
            }
        }

        private static class ArrayListSetTask extends ArrayList<SetTask> {
            /**
             * 
             */
            private static final long serialVersionUID = 1L;
            /** Number of values to write */
            long objcount;
            /** total size of all arrays */
            long memcount;
            public ArrayListSetTask(int n)
            {
                super(n);
            }
            public boolean add(SetTask t)
            {
                objcount += t.length;
                memcount += t.values.length;
                return super.add(t);
            }
            public void clear()
            {
                objcount = 0;
                memcount = 0;
                super.clear();
            }
        }
    }

    public static class IntArray1NSortedWriter extends IntArray1NWriter
    {
        public IntArray1NSortedWriter(int size, File indexFile) throws IOException
        {
            super(size, indexFile);
        }

        protected void set(int index, int[] values, int offset, int length) throws IOException
        {
            long bodyPos = body.size + 1;
            setHeader(index, bodyPos);

            body.addAll(values, offset, length);
        }

        protected IIndexReader.IOne2ManyIndex createReader(IIndexReader.IOne2OneIndex headerIndex,
                        IIndexReader.IOne2OneIndex bodyIndex) throws IOException
        {
            return new IndexReader.IntIndex1NSortedReader(this.indexFile, headerIndex, bodyIndex);
        }

    }

    public static class InboundWriter
    {
        int size;
        File indexFile;
        int[] header;
        // Used to expand the range of values stored in the header up to 2^40
        byte[] header2;

        int bitLength;
        int pageSize;
        BitOutputStream[] segments;
        long[] segmentSizes;

        /**
         * @throws IOException
         */
        public InboundWriter(int size, File indexFile) throws IOException
        {
            this.size = size;
            this.indexFile = indexFile;

            int requiredSegments = (size / 500000) + 1;

            int segments = 1;
            while (segments < requiredSegments)
                segments <<= 1;

            this.bitLength = mostSignificantBit(size) + 1;
            this.pageSize = (size / segments) + 1;
            this.segments = new BitOutputStream[segments];
            this.segmentSizes = new long[segments];
        }

        public void log(int objectIndex, int refIndex, boolean isPseudo) throws IOException
        {
            int segment = objectIndex / pageSize;
            if (segments[segment] == null)
            {
                File segmentFile = new File(this.indexFile.getAbsolutePath() + segment + ".log");//$NON-NLS-1$
                segments[segment] = new BitOutputStream(new FileOutputStream(segmentFile));
            }

            segments[segment].writeBit(isPseudo ? 1 : 0);
            segments[segment].writeInt(objectIndex, bitLength);
            segments[segment].writeInt(refIndex, bitLength);

            segmentSizes[segment]++;
        }

        void setHeader(int index, long val)
        {
            header[index] = (int)val;
            byte hi = (byte)(val >> 32);
            if ((hi != 0 || val > MAX_OLD_HEADER_VALUE) && header2 == null)
                header2 = new byte[header.length];
            // Once we have started, always overwrite
            if (header2 != null)
                header2[index] = hi;
        }

        private long getHeader(int index)
        {
            return (header2 != null ? (header2[index] & 0xffL) << 32 : 0) | (header[index] & 0xffffffffL);
        }

        public IIndexReader.IOne2ManyObjectsIndex flush(IProgressListener monitor, KeyWriter keyWriter)
                        throws IOException
        {
            close();

            header = new int[size];

            DataOutputStream index = new DataOutputStream(new BufferedOutputStream(
                            new FileOutputStream(this.indexFile), 1024 * 256));

            BitInputStream segmentIn = null;

            IntIndexStreamer body = new IntIndexStreamer();
            body.openStream(index, 0);
            boolean bodyopen = true;
            try
            {

                for (int segment = 0; segment < segments.length; segment++)
                {
                    if (monitor.isCanceled())
                        throw new IProgressListener.OperationCanceledException();

                    File segmentFile = new File(this.indexFile.getAbsolutePath() + segment + ".log");//$NON-NLS-1$
                    int startIndex = segment * pageSize;
                    processGiantSegmentFile(monitor, keyWriter, body, segmentFile, segmentSizes[segment], segment, startIndex);
                }

                // write header
                long divider = body.closeStream();
                bodyopen = false;
                IIndexReader.IOne2OneIndex headerIndex = null;
                if (header2 != null)
                {
                    headerIndex = new PosIndexStreamer().writeTo2(index, divider, new IteratorLong()
                    {
                        int i;

                        public boolean hasNext()
                        {
                            return i < header.length;
                        }

                        public long next()
                        {
                            long ret = getHeader(i++);
                            return ret;
                        }
                    });

                }
                else
                {
                    headerIndex = new IntIndexStreamer().writeTo(index, divider, header);
                }

                index.writeLong(divider);

                index.flush();
                index.close();

                index = null;

                // return index reader
                return new IndexReader.InboundReader(this.indexFile, headerIndex, body.getReader(null));
            }
            finally
            {
                header = null;
                header2 = null;
                try
                {
                    if (index != null)
                        index.close();
                    if (bodyopen)
                        body.closeStream();
                }
                catch (IOException ignore)
                {}

                try
                {
                    if (segmentIn != null)
                        segmentIn.close();
                }
                catch (IOException ignore)
                {}

                if (monitor.isCanceled())
                    cancel();
            }
        }

        private void processGiantSegmentFile(IProgressListener monitor, KeyWriter keyWriter,
                        IntIndexStreamer body, File segmentFile, long segmentSize, int segment, int startIndex) throws IOException
                        {
            final int SUBSIZE = 500000 * 16;
            if (!segmentFile.exists())
                return;
            if (segmentSize < SUBSIZE)
            {
                processSegmentFile(monitor, keyWriter, body, segmentFile, (int)segmentSize, segment);
                return;
            }

            // read payload and get counts of refs per object
            BitInputStream segmentIn = new BitInputStream(new FileInputStream(segmentFile));
            int counts[];
            try
            {
                counts = new int[pageSize];
                for (long ii = 0; ii < segmentSize; ii++)
                {

                    segmentIn.readBit();

                    int objIndex = segmentIn.readInt(bitLength);
                    segmentIn.readInt(bitLength);

                    counts[objIndex - startIndex]++;
                }
            }
            finally
            {
                segmentIn.close();
            }

            if (monitor.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            // Work out where to split the segment
            int subsegment[] = new int[pageSize];
            int subsegSize = SUBSIZE;
            int subsegs = -1;
            for (int jj = 0; jj < counts.length; ++jj)
            {
                if (counts[jj] > 0 && (long)subsegSize + counts[jj] > SUBSIZE)
                {
                    subsegSize = 0;
                    subsegs++;
                }
                subsegSize += counts[jj];
                subsegment[jj] = subsegs;
            }
            ++subsegs;

            if (subsegs <= 1)
            {
                // Only one subsegment, so use the original segment
                processSegmentFile(monitor, keyWriter, body, segmentFile, (int)segmentSize, segment);
                return;
            }

            // Create the subsegments
            BitOutputStream[] subsegments = new BitOutputStream[subsegs];
            int[] subsegmentSizes = new int[subsegs];
            try
            {
                try
                {
                    for (int ss = 0; ss < subsegs; ++ss)
                    {
                        File subsegmentFile = new File(this.indexFile.getAbsolutePath() + segment +"." + ss + ".log");//$NON-NLS-1$ //$NON-NLS-2$
                        subsegments[ss] = new BitOutputStream(new FileOutputStream(subsegmentFile));
                    }

                    // Partition payload
                    segmentIn = new BitInputStream(new FileInputStream(segmentFile));
                    try
                    {
                        for (long ii = 0; ii < segmentSize; ii++)
                        {
                            if (ii % 1000 == 0 && monitor.isCanceled())
                                throw new IProgressListener.OperationCanceledException();

                            boolean isPseudo = segmentIn.readBit() == 1;

                            int objectIndex = segmentIn.readInt(bitLength);
                            int refIndex = segmentIn.readInt(bitLength);

                            int subseg = subsegment[objectIndex - startIndex];

                            subsegments[subseg].writeBit(isPseudo ? 1 : 0);
                            subsegments[subseg].writeInt(objectIndex, bitLength);
                            subsegments[subseg].writeInt(refIndex, bitLength);

                            subsegmentSizes[subseg]++;

                        }
                    }
                    finally
                    {
                        segmentIn.close();
                    }
                }
                finally 
                {
                    // Close the subfiles
                    for (int ss = 0; ss < subsegs; ++ss)
                    {
                        if (subsegments[ss] != null)
                            subsegments[ss].close();
                    }
                }

                // delete segment log
                segmentFile.delete();
                segmentFile = null;

                // Process the subsegments
                for (int ss = 0; ss < subsegs; ++ss)
                {
                    File subsegmentFile = new File(this.indexFile.getAbsolutePath() + segment +"." + ss + ".log");//$NON-NLS-1$ //$NON-NLS-2$
                    processSegmentFile(monitor, keyWriter, body, subsegmentFile, subsegmentSizes[ss], segment);
                }
            }
            finally
            {
                // Tidy up in case of cancel
                // Normal operation will have deleted these files
                for (int ss = 0; ss < subsegs; ++ss)
                {
                    File subsegmentFile = new File(this.indexFile.getAbsolutePath() + segment +"." + ss + ".log");//$NON-NLS-1$ //$NON-NLS-2$
                    if (subsegmentFile.exists())
                        subsegmentFile.delete();
                }
            }
        }

        private void processSegmentFile(IProgressListener monitor, KeyWriter keyWriter, IntIndexStreamer body, File segmentFile, int segmentSize, int segment) throws IOException
        {
            if (!segmentFile.exists())
                return;

            // read & sort payload
            BitInputStream segmentIn = new BitInputStream(new FileInputStream(segmentFile));

            int objIndex[];
            int refIndex[];
            try
            {
                objIndex= new int[segmentSize];
                refIndex= new int[segmentSize];

                for (int ii = 0; ii < segmentSize; ii++)
                {

                    boolean isPseudo = segmentIn.readBit() == 1;

                    objIndex[ii] = segmentIn.readInt(bitLength);
                    refIndex[ii] = segmentIn.readInt(bitLength);

                    if (isPseudo)
                        refIndex[ii] = -1 - refIndex[ii]; // 0 is a valid!
                }
            }
            finally
            {
                segmentIn.close();
                segmentIn = null;
            }

            if (monitor.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            // delete segment log
            segmentFile.delete();
            segmentFile = null;

            processSegment(monitor, keyWriter, body, objIndex, refIndex);
        }

        private void processSegment(IProgressListener monitor, KeyWriter keyWriter,
                        IntIndexStreamer body, int[] objIndex, int[] refIndex) throws IOException
        {
            // sort (only by objIndex though)
            ArrayUtils.sort(objIndex, refIndex);

            // write index body
            int start = 0;
            int previous = -1;

            for (int ii = 0; ii <= objIndex.length; ii++)
            {
                if (ii == 0)
                {
                    start = ii;
                    previous = objIndex[ii];
                }
                else if (ii == objIndex.length || previous != objIndex[ii])
                {
                    if (monitor.isCanceled())
                        throw new IProgressListener.OperationCanceledException();

                    setHeader(previous, body.size + 1);

                    processObject(keyWriter, body, previous, refIndex, start, ii);

                    if (ii < objIndex.length)
                    {
                        previous = objIndex[ii];
                        start = ii;
                    }
                }
            }
        }

        private void processObject(KeyWriter keyWriter, IntIndexStreamer body, int objectId,
                        int[] refIndex, int fromIndex, int toIndex) throws IOException
        {
            Arrays.sort(refIndex, fromIndex, toIndex);

            int endPseudo = fromIndex;

            if ((toIndex - fromIndex) > 100000)
            {
                BitField duplicates = new BitField(size);

                int jj = fromIndex;

                for (; jj < toIndex; jj++) // pseudo references
                {
                    if (refIndex[jj] >= 0)
                        break;

                    endPseudo++;
                    refIndex[jj] = -refIndex[jj] - 1;

                    if (!duplicates.get(refIndex[jj]))
                    {
                        body.add(refIndex[jj]);
                        duplicates.set(refIndex[jj]);
                    }
                }

                for (; jj < toIndex; jj++) // other references
                {
                    if ((jj == fromIndex || refIndex[jj - 1] != refIndex[jj]) && !duplicates.get(refIndex[jj]))
                    {
                        body.add(refIndex[jj]);
                    }
                }
            }
            else
            {
                SetInt duplicates = new SetInt(toIndex - fromIndex);

                int jj = fromIndex;

                for (; jj < toIndex; jj++) // pseudo references
                {
                    if (refIndex[jj] >= 0)
                        break;

                    endPseudo++;
                    refIndex[jj] = -refIndex[jj] - 1;

                    if (duplicates.add(refIndex[jj]))
                        body.add(refIndex[jj]);
                }

                for (; jj < toIndex; jj++) // other references
                {
                    if ((jj == fromIndex || refIndex[jj - 1] != refIndex[jj]) && !duplicates.contains(refIndex[jj]))
                    {
                        body.add(refIndex[jj]);
                    }
                }
            }

            if (endPseudo > fromIndex)
            { 
                long h = getHeader(objectId);
                if (h > INBOUND_MAX_KEY1)
                {
                    keyWriter.storeKey(objectId, new long[] { h - 1, endPseudo - fromIndex });
                }
                else
                {
                    keyWriter.storeKey(objectId, new int[] { header[objectId] - 1, endPseudo - fromIndex });
                }
            }
        }

        public synchronized void cancel()
        {
            try
            {
                close();

                if (segments != null)
                {
                    for (int ii = 0; ii < segments.length; ii++)
                    {
                        new File(this.indexFile.getAbsolutePath() + ii + ".log").delete();//$NON-NLS-1$
                    }
                }
            }
            catch (IOException ignore)
            {}
            finally
            {
                indexFile.delete();
            }
        }

        public synchronized void close() throws IOException
        {
            if (segments != null)
            {
                for (int ii = 0; ii < segments.length; ii++)
                {
                    if (segments[ii] != null)
                    {
                        segments[ii].flush();
                        segments[ii].close();
                        segments[ii] = null;
                    }
                }
            }
        }

        public File getIndexFile()
        {
            return indexFile;
        }

    }

    public static class IntArray1NUncompressedCollector
    {
        int[][] elements;
        File indexFile;

        /**
         * @throws IOException
         */
        public IntArray1NUncompressedCollector(int size, File indexFile) throws IOException
        {
            this.elements = new int[size][];
            this.indexFile = indexFile;
        }

        public void log(int classId, int methodId)
        {
            if (elements[classId] == null)
            {
                elements[classId] = new int[] { methodId };
            }
            else
            {
                int[] newChildren = new int[elements[classId].length + 1];
                System.arraycopy(elements[classId], 0, newChildren, 0, elements[classId].length);
                newChildren[elements[classId].length] = methodId;
                elements[classId] = newChildren;
            }
        }

        public File getIndexFile()
        {
            return indexFile;
        }

        public IIndexReader.IOne2ManyIndex flush() throws IOException
        {
            IntArray1NSortedWriter writer = new IntArray1NSortedWriter(elements.length, indexFile);
            for (int ii = 0; ii < elements.length; ii++)
            {
                if (elements[ii] != null)
                    writer.log(ii, elements[ii]);
            }
            return writer.flush();
        }

    }

    // //////////////////////////////////////////////////////////////
    // long based indices
    // //////////////////////////////////////////////////////////////

    public static class LongIndexCollectorUncompressed
    {
        long[] dataElements;

        public LongIndexCollectorUncompressed(int size)
        {
            dataElements = new long[size];
        }

        public void set(int index, long value)
        {
            dataElements[index] = value;
        }

        public long get(int index)
        {
            return dataElements[index];
        }

        public IIndexReader.IOne2LongIndex writeTo(File indexFile) throws IOException
        {
            return new LongIndexStreamer().writeTo(indexFile, dataElements);
        }
    }

    abstract static class LongIndex
    {
        private static final int DEPTH = 10;

        int pageSize;
        int size;
        // pages are either IntArrayCompressed or
        // SoftReference<IntArrayCompressed>
        HashMapIntObject<Object> pages;
        HashMapIntLong binarySearchCache = new HashMapIntLong(1 << DEPTH);

        protected LongIndex()
        {}

        protected LongIndex(int size)
        {
            init(size, PAGE_SIZE_LONG);
        }

        protected void init(int size, int pageSize)
        {
            this.size = size;
            this.pageSize = pageSize;
            this.pages = new HashMapIntObject<Object>(size / pageSize + 1);
        }

        public long get(int index)
        {
            ArrayLongCompressed array = getPage(index / pageSize);
            return array.get(index % pageSize);
        }

        public long[] getNext(int index, int length)
        {
            long answer[] = new long[length];
            int page = index / pageSize;
            int pageIndex = index % pageSize;

            ArrayLongCompressed array = getPage(page);
            for (int ii = 0; ii < length; ii++)
            {
                answer[ii] = array.get(pageIndex++);
                if (pageIndex >= pageSize && ii + 1 < length)
                {
                    array = getPage(++page);
                    pageIndex = 0;
                }
            }

            return answer;
        }

        public int reverse(long value)
        {
            int low = 0;
            int high = size - 1;

            int depth = 0;
            int page = -1;
            ArrayLongCompressed array = null;

            while (low <= high)
            {
                // Avoid overflow problems by using unsigned divide by 2
                int mid = (low + high) >>> 1;

                long midVal;

                if (depth++ < DEPTH)
                {
                    try
                    {
                        midVal = binarySearchCache.get(mid);
                    }
                    catch (NoSuchElementException e)
                    {
                        int p = mid / pageSize;
                        if (p != page)
                            array = getPage(page = p);

                        midVal = array.get(mid % pageSize);

                        binarySearchCache.put(mid, midVal);
                    }
                }
                else
                {
                    int p = mid / pageSize;
                    if (p != page)
                        array = getPage(page = p);

                    midVal = array.get(mid % pageSize);
                }

                if (midVal < value)
                    low = mid + 1;
                else if (midVal > value)
                    high = mid - 1;
                else
                    return mid; // key found
            }
            return -(low + 1); // key not found.
        }

        public void set(int index, long value)
        {
            ArrayLongCompressed array = getPage(index / pageSize);
            array.set(index % pageSize, value);
        }

        protected abstract ArrayLongCompressed getPage(int page);

        public synchronized void unload()
        {
            pages = new HashMapIntObject<Object>(size / pageSize + 1);
            binarySearchCache = new HashMapIntLong(1 << DEPTH);
        }

        public int size()
        {
            return size;
        }

        public IteratorLong iterator()
        {
            return new LongIndexIterator(this);
        }
    }

    static class LongIndexIterator implements IteratorLong
    {
        LongIndex longArray;
        int nextIndex = 0;

        public LongIndexIterator(LongIndex longArray)
        {
            this.longArray = longArray;
        }

        public long next()
        {
            return longArray.get(nextIndex++);
        }

        public boolean hasNext()
        {
            return nextIndex < longArray.size();
        }
    }

    public static class LongIndexCollector extends LongIndex
    {
        final int mostSignificantBit;
        final int size;
        final int pageSize = IndexWriter.PAGE_SIZE_LONG;
        final ConcurrentHashMap<Integer, ArrayLongCompressed> pages = new ConcurrentHashMap<Integer, ArrayLongCompressed>();

        public LongIndexCollector(int size, int mostSignificantBit)
        {
            this.size = size;
            this.mostSignificantBit = mostSignificantBit;
        }

        protected ArrayLongCompressed getPage(int page)
        {
            ArrayLongCompressed existing = pages.get(page);
            if (existing != null) return existing;

            int ps = page < (size / pageSize) ? pageSize : size % pageSize;
            ArrayLongCompressed newArray = new ArrayLongCompressed(ps, 63 - mostSignificantBit, 0);
            existing = pages.putIfAbsent(page, newArray);
            return (existing != null) ? existing : newArray;
        }

        public IIndexReader.IOne2LongIndex writeTo(File indexFile) throws IOException
        {
            HashMapIntObject<Object> output = new HashMapIntObject<Object>(pages.size());
            for(Map.Entry<Integer, ArrayLongCompressed> entry : pages.entrySet()) {
                output.put(entry.getKey(), entry.getValue());
            }
            // needed to re-compress
            return new LongIndexStreamer().writeTo(indexFile, this.size, output, this.pageSize);
        }

        public void set(int index, long value)
        {
            ArrayLongCompressed array = getPage(index / pageSize);
            // uses bit operations internally, so we should sync against the page
            // TODO unlock this by having ArrayLongCompressed use atomics
            synchronized(array)
            {
                array.set(index % pageSize, value);
            }
        }
    }

    public static class LongIndexStreamer extends LongIndex
    {
        DataOutputStream out;
        ArrayLong pageStart;
        long[] page;
        int left;

        // tracks number of pages added, may not be available if they are still being compressed
        int pagesAdded;

        // if the writer task has an exception during async we want to throw it on the next write
        Exception storedException = null;
        // if the writer task has an OutOfMemoryError during async we want to throw it on the next write
        OutOfMemoryError storedError = null;

        // TODO bound these queues, and add more compressor threads if needed
        // for the moment it is ok, as the compress and write stage is fast
        // could do more threads but no point
        final ExecutorService compressor = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "LongIndexStreamer-Compressor")); //$NON-NLS-1$
        // this must be single threaded to ensure page update logic stays correct
        final ExecutorService writer = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "LongIndexStreamer-Writer")); //$NON-NLS-1$

        public LongIndexStreamer()
        {}

        public LongIndexStreamer(File indexFile) throws IOException
        {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
            openStream(out, 0);
        }

        public void close() throws IOException
        {
            DataOutputStream out = this.out;
            closeStream();
            out.close();
        }

        public IOne2LongIndex writeTo(File indexFile, int size, HashMapIntObject<Object> pages, int pageSize)
                        throws IOException
        {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

            openStream(out, 0);

            int noOfPages = size / pageSize + (size % pageSize > 0 ? 1 : 0);
            for (int ii = 0; ii < noOfPages; ii++)
            {
                ArrayLongCompressed a = (ArrayLongCompressed) pages.get(ii);
                int len = (ii + 1) < noOfPages ? pageSize : (size % pageSize);

                if (a == null)
                    addAll(new long[len]);
                else
                    for (int jj = 0; jj < len; jj++)
                    {
                        add(a.get(jj));
                    }
            }

            closeStream();
            out.close();

            return getReader(indexFile);
        }

        public IOne2LongIndex writeTo(File indexFile, long[] array) throws IOException
        {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

            openStream(out, 0);
            addAll(array);
            closeStream();

            out.close();

            return getReader(indexFile);
        }

        public IOne2LongIndex writeTo(File indexFile, IteratorLong iterator) throws IOException
        {
            FileOutputStream fos = new FileOutputStream(indexFile);
            try
            {
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos));

                openStream(out, 0);
                addAll(iterator);
                closeStream();

                out.flush();

                return getReader(indexFile);
            }
            finally
            {
                try
                {
                    fos.close();
                }
                catch (IOException ignore)
                {}
            }
        }

        public IOne2LongIndex writeTo(File indexFile, ArrayLong array) throws IOException
        {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));

            openStream(out, 0);
            addAll(array);
            closeStream();

            out.close();

            return getReader(indexFile);
        }

        IIndexReader.IOne2LongIndex writeTo(DataOutputStream out, long position, IteratorLong iterator)
                        throws IOException
        {
            openStream(out, position);
            addAll(iterator);
            closeStream();

            return getReader(null);
        }

        void openStream(DataOutputStream out, long position)
        {
            this.out = out;

            init(0, PAGE_SIZE_LONG);

            this.page = new long[pageSize];
            this.pageStart = new ArrayLong();
            this.pageStart.add(position);
            this.left = page.length;
        }

        /**
         * @return total bytes written to index file
         */
        long closeStream() throws IOException
        {
            try
            {
                if (left < page.length)
                    addPage();
                else
                    new WriterTask(null); // Dummy to test for pending exception
            } 
            catch (IOException e)
            {
                // Early termination with an exception
                try
                {
                    compressor.shutdownNow();
                    compressor.awaitTermination(100, TimeUnit.SECONDS);
                    writer.shutdownNow();
                    writer.awaitTermination(100, TimeUnit.SECONDS);
                    throw e;
                }
                catch (InterruptedException ie)
                {
                    throw new IOException(ie);
                }
            }

            try
            {
                compressor.shutdown();
                compressor.awaitTermination(100, TimeUnit.SECONDS);
                writer.shutdown();
                writer.awaitTermination(100, TimeUnit.SECONDS);
            }
            catch (InterruptedException ie)
            {
                throw new IOException(ie);
            }

            // write header information
            for (int jj = 0; jj < pageStart.size(); jj++)
                out.writeLong(pageStart.get(jj));

            out.writeInt(pageSize);
            out.writeInt(size);

            this.page = null;

            this.out = null;

            return this.pageStart.lastElement() + (8 * pageStart.size()) + 8 - this.pageStart.firstElement();
        }

        IndexReader.LongIndexReader getReader(File indexFile) throws IOException
        {
            return new IndexReader.LongIndexReader(indexFile, pages, size, pageSize, pageStart.toArray());
        }

        public void addAll(IteratorLong iterator) throws IOException
        {
            while (iterator.hasNext())
                add(iterator.next());
        }

        public void addAll(ArrayLong array) throws IOException
        {
            for (IteratorLong e = array.iterator(); e.hasNext();)
                add(e.next());
        }

        public void add(long value) throws IOException
        {
            if (left == 0)
                addPage();

            page[page.length - left--] = value;
            size++;

        }

        public void addAll(long[] values) throws IOException
        {
            addAll(values, 0, values.length);
        }

        public void addAll(long[] values, int offset, int length) throws IOException
        {
            while (length > 0)
            {
                if (left == 0)
                    addPage();

                int chunk = Math.min(left, length);

                System.arraycopy(values, offset, page, page.length - left, chunk);
                left -= chunk;
                size += chunk;

                length -= chunk;
                offset += chunk;
            }
        }

        private void addPage() throws IOException
        {
            Future<byte[]> compressionResult = compressor.submit(new CompressionTask(page, left, pagesAdded));
            // compression may finish in any order, but writes have to be correct, so order them
            writer.execute(new WriterTask(compressionResult));

            pagesAdded += 1;
            page = new long[page.length];
            left = page.length;
        }

        @Override
        protected ArrayLongCompressed getPage(int page)
        {
            throw new UnsupportedOperationException();
        }

        /*
         * Compresses a page of data to a byte array
         */
        private class CompressionTask implements Callable<byte[]>
        {
            final long[] page;
            final int left;
            final int pageNumber;
            public CompressionTask(final long[] page, final int left, final int pageNumber)
            {
                this.page = page;
                this.left = left;
                this.pageNumber = pageNumber;
            }

            public byte[] call()
            {
                ArrayLongCompressed array = new ArrayLongCompressed(page, 0, page.length - left);
                pages.put(pageNumber, new SoftReference<ArrayLongCompressed>(array));
                return array.toByteArray();
            }
        }

        /*
         * Writes data to output
         * Assumes there is only a single WriterTask that can ever be running
         */
        private class WriterTask implements Runnable {
            final Future<byte[]> byteData;
            public WriterTask(final Future<byte[]> byteData) throws IOException
            {
                this.byteData = byteData;
                if (storedException != null)
                {
                    throw new IOException(Messages.IndexWriter_StoredException, storedException);
                }
                if (storedException != null)
                {
                    throw new IOException(Messages.IndexWriter_StoredError, storedError);
                }
            }

            public void run() {
                byte[] buffer;
                try
                {
                    buffer = byteData.get();
                    out.write(buffer);
                    int written = buffer.length;

                    // update the shared page list
                    pageStart.add(pageStart.lastElement() + written);
                }
                catch (InterruptedException | ExecutionException | IOException e)
                {
                    storedException = e;
                }
                catch (OutOfMemoryError e)
                {
                    storedError = e;
                }
            }
        }
    }

    /**
     * A class which looks like an ArrayIntCompressed but can hold longs too.
     */
    static class ArrayIntLongCompressed extends ArrayIntCompressed
    {
        final ArrayLongCompressed base;
        ArrayIntLongCompressed(ArrayLongCompressed b)
        {
            super(b.toByteArray());
            base = b;
        }
        ArrayIntLongCompressed(ArrayIntCompressed b)
        {
            super(b.toByteArray());
            base = new ArrayLongCompressed(b.toByteArray());
        }
        long getPos(int offset)
        {
            return base.get(offset);
        }
    }

    /**
     * Streams positions in another index, positions can be huge.
     */
    static class PosIndexStreamer extends LongIndexStreamer
    {
        public PosIndexStreamer()
        {
            super();
        }
        IIndexReader.IOne2OneIndex writeTo2(DataOutputStream out, long position, IteratorLong iterator)
                        throws IOException
        {
            openStream(out, position);
            addAll(iterator);
            closeStream();
            // Convert the page cache to ArrayInt format
            Pages<SoftReference<ArrayIntCompressed>> pages2 = new Pages<SoftReference<ArrayIntCompressed>>(pages.size());
            for (Iterator<Entry<Object>> it = pages.entries(); it.hasNext(); )
            {
                Entry<Object> e = it.next();
                Object o = e.getValue();
                if (o instanceof SoftReference<?>)
                {
                    SoftReference<?> sr = (SoftReference<?>)o;
                    Object rr = sr.get();
                    if (rr instanceof ArrayLongCompressed)
                    {
                        ArrayLongCompressed f = (ArrayLongCompressed)rr;
                        pages2.put(e.getKey(), new SoftReference<ArrayIntCompressed>(new ArrayIntLongCompressed(f)));
                    }
                }
            }
            return new IndexReader.PositionIndexReader(null, pages2, size, pageSize, pageStart.toArray());
        }
    }

    public static class LongArray1NWriter
    {
        int[] header;
        File indexFile;

        DataOutputStream out;
        LongIndexStreamer body;

        public LongArray1NWriter(int size, File indexFile) throws IOException
        {
            this.header = new int[size];
            this.indexFile = indexFile;

            this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
            this.body = new LongIndexStreamer();
            this.body.openStream(this.out, 0);
        }

        public void log(int index, long[] values) throws IOException
        {
            this.set(index, values, 0, values.length);
        }

        protected void set(int index, long[] values, int offset, int length) throws IOException
        {
            header[index] = body.size() + 1;

            body.add(length);

            body.addAll(values, offset, length);
        }

        public void flush() throws IOException
        {
            long divider = body.closeStream();

            new IntIndexStreamer().writeTo(out, divider, header).close();

            out.writeLong(divider);

            out.close();
            out = null;
        }

        public void cancel()
        {
            try
            {
                if (out != null)
                {
                    out.close();
                    if (body != null)
                        body.closeStream();
                    body = null;
                    out = null;
                }
            }
            catch (IOException ignore)
            {}
            finally
            {
                if (indexFile.exists())
                    indexFile.delete();
            }
        }

        public File getIndexFile()
        {
            return indexFile;
        }
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    public static long[] copyOf(long[] original, int newLength)
    {
        long[] copy = new long[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    private static int newCapacity(int oldCapacity, int minCapacity)
    {
        // Scale by 1.5 without overflow
        int newCapacity = (oldCapacity * 3 >>> 1);
        if (newCapacity < minCapacity)
        {
            newCapacity = (minCapacity * 3 >>> 1);
            if (newCapacity < minCapacity)
            {
                // Avoid VM limits for final size
                newCapacity = Integer.MAX_VALUE - 8;
            }
        }
        return newCapacity;
    }

    public static int mostSignificantBit(int x)
    {
        int length = 0;
        if ((x & 0xffff0000) != 0)
        {
            length += 16;
            x >>= 16;
        }
        if ((x & 0xff00) != 0)
        {
            length += 8;
            x >>= 8;
        }
        if ((x & 0xf0) != 0)
        {
            length += 4;
            x >>= 4;
        }
        if ((x & 0xc) != 0)
        {
            length += 2;
            x >>= 2;
        }
        if ((x & 0x2) != 0)
        {
            length += 1;
            x >>= 1;
        }
        if ((x & 0x1) != 0)
        {
            length += 1;
            // x >>= 1;
        }

        return length - 1;
    }

    public static int mostSignificantBit(long x)
    {
        long lead = x >>> 32;
        return lead == 0x0 ? mostSignificantBit((int) x) : 32 + mostSignificantBit((int) lead);
    }

}

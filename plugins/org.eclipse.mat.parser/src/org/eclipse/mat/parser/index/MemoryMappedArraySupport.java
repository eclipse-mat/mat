package org.eclipse.mat.parser.index;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.function.Function;

public class MemoryMappedArraySupport<T> {
    // this is limited to a 2GB length array, and identifiers are x bytes => x*2GB file at most
    // but, we are constrained to at most 2GB-indexing into ByteBuffer, so need to sub-map
    final static int MB = 1*1024*1024;
    final static long BUFFER_SIZE = 1024L*MB;
    final static long FILE_EXTEND_LENGTH = 64*MB;
    final static long MAX_ELEMENTS = 2L*1024*MB;

    final T[] buffers;

    final RandomAccessFile file;
    final String filename;
    int nextWriteIndex = 0;
    long lengthCache = 0;
    final int maxCapacity;
    final long sizeOfElement;
    final Function<ByteBuffer, T> castFn;

    public MemoryMappedArraySupport(String filename, final int initialCapacity, final int maxCapacity,
            final long sizeOfElement, final Function<ByteBuffer, T> castFn, final Class<T> clazz) {
        if (filename == null) {
            try {
                File file = File.createTempFile("mat_memory_mapped_array", null);
                filename = file.getCanonicalPath();
                // since it's a scratch file, we will clear it on exit
                new File(filename).deleteOnExit();
            } catch (IOException e) {
                throw new RuntimeException("could not create temporary array store", e);
            }
        }
        this.filename = filename;
        try {
            file = new RandomAccessFile(filename, "rw");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.maxCapacity = maxCapacity;
        this.nextWriteIndex = initialCapacity;
        this.castFn = castFn;
        this.sizeOfElement = sizeOfElement;
        this.buffers = (T[]) Array.newInstance(clazz, (int) ((MAX_ELEMENTS * sizeOfElement) / BUFFER_SIZE));

        // fill any buffers
        for(int i = 0; i < bufferNumber(initialCapacity - 1); i++) {
            createBufferIfNull(i);
        }

        if (initialCapacity > 0) {
            ensureExists(initialCapacity - 1);
            // backfill any buffers
            for(int i = 0; i < bufferNumber(initialCapacity - 1); i++) {
                createBufferIfNull(i);
            }
        }
    }

    int offsetIntoBuffer(int index) {
	    return (int) (((long)index) % (BUFFER_SIZE/sizeOfElement));
    }

    int bufferNumber(int index) {
        int result = (int) (((long)index) / (BUFFER_SIZE/sizeOfElement));
        if (result < 0) {
            System.out.println("index = " + index + ", buffer_size = " + BUFFER_SIZE + ", sizeOfElement = " + sizeOfElement);
        }
        return result;
    }

    public int size() {
        return nextWriteIndex;
    }

    public void unload() /*throws IOException*/ {
        for(int i = 0; i < buffers.length; i++) {
            buffers[i] = null;
        }
    }

    public void close() throws IOException {
        unload();
        file.close();
    }

    public void delete() {
        new File(filename).delete();
    }

    long fileLength() {
        return lengthCache;
    }

    void fileSetLength(long length) {
        try {
            file.setLength(length);
            lengthCache = length;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void checkExists(int index) {
        if (((index + 1) * sizeOfElement) > fileLength() || (index < 0)) {
            throw new IndexOutOfBoundsException("index = " + index + ", file length = " + fileLength());
        }
    }

    void createBufferIfNull(int bufferIndex) {
        if (buffers[bufferIndex] == null) {
            try {
                ByteBuffer newBuffer = file.getChannel().map(MapMode.READ_WRITE, bufferIndex*BUFFER_SIZE, BUFFER_SIZE);
                buffers[bufferIndex] = castFn.apply(newBuffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void ensureExists(int index) {
        if (index > maxCapacity || index < 0) {
            throw new IndexOutOfBoundsException("too many elements for array. index = " + index + ", maxCapacity = " + maxCapacity);
        }


        long requiredLength = (index + 1) * sizeOfElement;
        while (requiredLength > fileLength()) {
            fileSetLength(fileLength() + FILE_EXTEND_LENGTH);
        }

        createBufferIfNull(bufferNumber(index));
    }
}

/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Used to wrap an non-seekable stream to make it seekable.
 * Constructed with a supplier of the non-seekable stream (which could be a decompression
 * stream or decryption stream). Multiple instances of this stream are then constructed
 * and each starts at the beginning.
 * Optionally supplied underlying seekable {@link RandomAccessInputStream} stream.
 * This is used if the supplier does
 * not provided a fresh underlying stream each  time. A seek is used to move the underlying
 * stream to the correct position when switching between unseekable streams. In this
 * case the supplier of non-seekable streams should wrap the innermost stream from
 * the underlying seekable stream with a {@link UnclosableInputStream} so that the
 * non-seekable streams can be closed (to release resources) without closing the
 * underlying seekable.
 */
public class SeekableStream extends InputStream implements Closeable, AutoCloseable
{
    /**
     * Used to wrap and indicate a underlying stream which has a position and has seek().
     * {@link FilterInputStream#close()} is called when {@link SeekableStream#close()}
     * is called.
     */
    public abstract static class RandomAccessInputStream extends FilterInputStream
    {
        /**
         * A wrapper stream for a random access stream.
         * @param in
         */
        protected RandomAccessInputStream(InputStream in)
        {
            super(in);
        }

        /**
         * Get the current position in the stream.
         * @return
         * @throws IOException
         */
        abstract long position() throws IOException;

        /**
         * Set the current position.
         * @param newpos
         * @throws IOException
         */
        abstract void seek(long newpos) throws IOException;
    }

    /**
     * Wraps an {@link InputStream} so that
     * close does not propagate to the underlying stream.
     */
    public static class UnclosableInputStream extends FilterInputStream
    {

        public UnclosableInputStream(InputStream in)
        {
            super(in);
        }

        public void close()
        {
            // Do nothing
        }
    }

    /**
     * Internal class to hold position of a decompression stream.
     */
    static class PosStream extends FilterInputStream implements Comparable<PosStream>
    {
        protected PosStream(InputStream in, long seq1)
        {
            super(in);
            seq = seq1;
        }

        /**
         * Dummy for searching in TreeSet
         * @param pos
         * @param seq
         */
        protected PosStream(long pos, long seq1)
        {
            super(null);
            this.pos = pos;
            seq = seq1;
        }

        /** Position in stream */
        private long pos;
        /**
         * Sequence number. Unique,
         * allows more than one PosStream with the same
         * position in the TreeSet.
         */
        long seq;
        /** The non-seekable stream */
        //InputStream is;
        /** Position in the base stream when not in use. */
        long basepos;

        @Override
        public int read() throws IOException
        {
            int r = super.read();
            if (r != -1)
                ++pos;
            return r;
        }

        @Override
        public int read(byte buf[], int off, int len) throws IOException
        {
            int r = super.read(buf, off, len);
            if (r != -1)
                pos += r;
            return r;
        }

        @Override
        public int compareTo(PosStream o)
        {
            if (pos < o.pos)
                return -1;
            else if (pos > o.pos)
                return 1;
            else
                return Long.compare(seq, o.seq);
        }

        public boolean equals(Object o)
        {
            return o instanceof PosStream && compareTo((PosStream)o) == 0;
        }

        public int hashCode()
        {
            return (int)pos ^ (int)seq;
        }

        public String toString()
        {
            return super.toString() + " " + pos + " (" + seq + ")";  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        long position()
        {
            return pos;
        }

        void position(long pos)
        {
            this.pos = pos;
        }
    }

    /** Supplier of the non-seekable streams */
    Supplier<InputStream> genstream;
    /** A sequence number to ensure that the PosStreams are different */
    long nextseq = 0;
    /** Used to see how often to clean up the cache */
    int cleanup;
    /** Keeps the different unseekable streams in order of position */
    TreeSet<PosStream> ts = new TreeSet<PosStream>();
    /** The current stream used for reading */
    PosStream current;
    /** How many streams to keep active */
    int cachesize;
    /** The last position if the current stream has been closed */
    long lastpos;
    /**
     * The underlying random access stream which all of the decompression
     * streams use. When switching streams seek() will be used to switch
     * the position of this stream.
     */
    RandomAccessInputStream underlying;
    /**
     * Alternative underlying channel with position and seek.
     */
    SeekableByteChannel underlyingChannel;

    /**
     * Creates a seekable stream out of a non-seekable stream.
     *
     * @param genstream
     *            Supplier of a new non-seekable stream for the same resource,
     *            {@link #underlying}.
     *            The streams are closed when no longer needed.
     *            Wrap the innermost stream with {@link UnclosableStream}
     *            to avoid closing the underlying stream when one of the
     *            non-seekable streams is closed by this class.
     * @param underlying
     *            The underlying seekable stream.
     *            Avoids having to open a file multiple times if a seek can be used instead.
     * @param cachesize number of seekable streams to use
     * @throws IOException
     */
    public SeekableStream(Supplier<InputStream> genstream, RandomAccessInputStream underlying, int cachesize) throws IOException
    {
        this.genstream = genstream;
        this.cachesize = cachesize;
        this.underlying = underlying;
        cleanup = initcleanup();
        seek(0);
    }

    /**
     * Slowly reduce the clean up rate as the cache gets bigger
     * to stop it taking too long.
     * @return
     */
    private int initcleanup()
    {
        return (int)Math.pow(cachesize + 1, 0.75);
    }

    /**
     * Creates a seekable stream out of a non-seekable stream.
     * @param genstream
     *            Supplier of a new non-seekable stream for the same resource.
     *            The streams are closed when no longer needed.
     *            Wrap the innermost stream with {@link UnclosableStream}
     *            to avoid closing the underlying stream when one of the
     *            non-seekable streams is closed by this class.
     * @param underlying
     *            The underlying seekable stream.
     *            Avoids having to open a file multiple times if a seek can be used instead.
     * @param cachesize number of seekable streams to use
     * @throws IOException
     */
    public SeekableStream(Supplier<InputStream> genstream, SeekableByteChannel underlying, int cachesize) throws IOException
    {
        this.genstream = genstream;
        this.cachesize = cachesize;
        this.underlyingChannel = underlying;
        cleanup = initcleanup();
        seek(0);
    }

    /**
     * Creates a seekable stream out of a non-seekable stream.
     * @param genstream
     *            Supplier of a new non-seekable stream for the same resource.
     *            The streams are closed when no longer needed.
     * @param cachesize number of seekable streams to use
     * @throws IOException
     */
    public SeekableStream(Supplier<InputStream> genstream, int cachesize) throws IOException
    {
        this.genstream = genstream;
        this.cachesize = cachesize;
        cleanup = initcleanup();
        seek(0);
    }

    /**
     * Get the position of the underlying stream.
     * Used when switching between non-seekable streams.
     * @return
     * @throws IOException
     */
    long underlyingPosition() throws IOException
    {
        if (underlying != null)
            return underlying.position();
        else if (underlyingChannel != null)
            return underlyingChannel.position();
        else
            return -1;
    }

    /**
     * Set the position of the underlying stream.
     * Used when switching between non-seekable streams.
     * @param pos
     * @throws IOException
     */
    void underlyingPosition(long pos) throws IOException
    {
        if (underlying != null)
            underlying.seek(pos);
        else if (underlyingChannel != null)
            underlyingChannel.position(pos);
    }

    /**
     * Close the underlying stream.
     * @return true if a stream was closed
     * @throws IOException
     */
    boolean underlyingClose() throws IOException
    {
        if (underlying != null)
        {
            underlying.close();
            return true;
        }
        else if (underlyingChannel != null)
        {
            underlyingChannel.close();
            return true;
        }
        else
            return false;
    }

    /**
     * Close a non-seekable stream
     * @param pos
     * @throws IOException
     */
    void streamClose(PosStream pos) throws IOException
    {
        pos.close();
    }

   boolean underlying()
    {
        if (underlying != null)
        {
            return true;
        }
        else if (underlyingChannel != null)
        {
            return true;
        }
        else
            return false;
    }

    /**
     * Remove an entry in the decompression stream cache.
     * @throws IOException
     */
    void clearEntry() throws IOException
    {
        // clear an entry
        if (ts.isEmpty())
            return;
        if (nextseq % cleanup == 0)
        {
            clearClosest();
            return;
        }
        PosStream last = ts.last();
        if (last != null)
        {
            PosStream first = ts.first();
            PosStream toremove = last;
            PosStream lastbutone = ts.lower(last);
            if (lastbutone != null)
            {
                if (first.position() < last.position() - lastbutone.position())
                    toremove = first;
            }
            ts.remove(toremove);
            streamClose(toremove);
        }
    }

    /**
     * Remove an entry in the decompression stream cache.
     * @throws IOException
     */
    void clearClosest() throws IOException
    {
        if (ts.size() == 0)
            return;
        // Find the smallest gap in positions between two streams
        long pos = 0;
        PosStream best = null;
        long bestgap = Long.MAX_VALUE;
        for (PosStream p : ts)
        {
            if (p.position() - pos < bestgap)
            {
                best = p;
                bestgap = p.position() - pos;
            }
            pos = p.position();
        }
        ts.remove(best);
        streamClose(best);
    }

    /**
     * Move to a position in the seekable stream. Locates the stream closest
     * before the seek point. Skip to the seek point. If no underlying stream is
     * before the seek point, create another and discard an existing stream if
     * required.
     *
     * @param pos
     * @throws IOException
     */
    public void seek(long pos) throws IOException
    {
        if (current != null)
        {
            // Remember the position in the underlying stream
            current.basepos = underlyingPosition();
            // Add the current stream to the list so it can be searched for
            ts.add(current);
        }
        // Create a PosStream so we can search for an existing close one
        PosStream dummy = new PosStream(pos, nextseq);
        PosStream found = ts.floor(dummy);
        if (found != null)
        {
            // remove from tree set as we change the position
            ts.remove(found);
            underlyingPosition(found.basepos);
        }
        else
        {
            // New entry
            if (ts.size() > cachesize)
            {
                clearEntry();
            }
            underlyingPosition(0);
            try
            {
                // Ask the caller to generate a new stream
                found = new PosStream(genstream.get(), nextseq++);
            }
            catch (UncheckedIOException e)
            {
                // Switch back to the current
                if (current != null)
                {
                    ts.remove(current);
                    underlyingPosition(current.basepos);
                }
                throw e.getCause();
            }
        }
        current = found;
        long toSkip = pos - found.position();
        while (toSkip > 0)
        {
            long skipped = skip(pos - found.position());
            if (skipped == 0)
                throw new IOException();
            // Skip should normally call read() which will update pos
            toSkip -= skipped;
        }
    }

    public long position()
    {
        return current != null ? current.pos : lastpos;
    }

    @Override
    public int read() throws IOException
    {
        if (current == null)
            return -1;
        int r = current.read();
        if (r < 0)
        {
            // Indicate at end of stream
            lastpos = current.position();
            // Come to the end of the stream, so remove the underlying stream
            streamClose(current);
            current = null;
        }
        return r;
    }

    //@Override
    public int read(byte buffer[], int offset, int length) throws IOException
    {
        if (current == null)
            return -1;
        int r = current.read(buffer, offset, length);
        if (r < 0)
        {
            // Indicate at end of stream
            lastpos = current.position();
            // Come to the end of the stream, so remove the underlying stream
            streamClose(current);
            current = null;
        }
        return r;
    }

    /**
     * Closes and cleans up the {@link SeekableStream}, including
     * the non-seekable streams.
     * Does not close the underlying stream or channel, this should be
     * done explicitly.
     */
    public void close() throws IOException
    {
        for (Iterator<PosStream>it = ts.iterator(); it.hasNext(); )
        {
            PosStream ps = it.next();
            // We can close the streams now as the
            // underlying stream is about to be closed too.
            streamClose(ps);
            it.remove();
        }
        if (current != null)
        {
            // Indicate at end of stream
            lastpos = current.position();
            streamClose(current);
        }
        current = null;
        // No need to close the underlying streams, that is the job of the caller
    }

    public String toString()
    {
        long pos = current != null ? current.position() : lastpos;
        return this.getClass().getName() + " " + pos + " " + ts; //$NON-NLS-1$ //$NON-NLS-2$
    }

}

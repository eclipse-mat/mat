package org.eclipse.mat.parser.index;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.parser.index.IndexWriter.Identifier;
import org.eclipse.mat.parser.index.IndexWriter.PosIndexStreamer;

public class IntArray1NWriter
{
    int[] header;
    // Used to expand the range of values stored in the header up to 2^40
    byte[] header2;
    File indexFile;

    DataOutputStream out;
    IntIndexStreamer body;

    public IntArray1NWriter(int size, File indexFile) throws IOException
    {
        this.header = new int[size];
        // Lazy allocate header2
        // this.header2 = new byte[size];
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
        byte hi = (byte)(val >> 32);
        if ((hi != 0 || val > IndexWriter.MAX_OLD_HEADER_VALUE) && header2 == null)
            header2 = new byte[header.length];
        // Once we have started, always overwrite
        if (header2 != null)
            header2[index] = hi;
    }

    private long getHeader(int index)
    {
        return (header2 != null ? (header2[index] & 0xffL) << 32 : 0) | (header[index] & 0xffffffffL);
    }

    protected void set(int index, int[] values, int offset, int length) throws IOException
    {
        long bodyPos = body.size;
        setHeader(index, bodyPos);

        body.add(length);

        body.addAll(values, offset, length);
    }

    public IIndexReader.IOne2ManyIndex flush() throws IOException
    {
        long divider = body.closeStream();

        IIndexReader.IOne2OneIndex headerIndex = null;
        if (header2 != null)
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
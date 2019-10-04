/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG, IBM Corporation and others.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.parser.index.IndexWriter.Identifier;
import org.eclipse.mat.parser.index.IndexWriter.PosIndexStreamer;

public class IntArray1NWriter
{
    // length of set() queue to buffer before writing to output as a batch
    private static final int TASK_BUFFER_SIZE = 1024;

    int[] header;
    // Used to expand the range of values stored in the header up to 2^40
    byte[] header2;
    File indexFile;

    DataOutputStream out;
    IntIndexStreamer body;

    CopyOnWriteArrayList<ArrayList<SetTask>> allTasks = new CopyOnWriteArrayList<ArrayList<SetTask>>();
    ThreadLocal<ArrayList<SetTask>> threadTaskQueue = ThreadLocal.withInitial(new Supplier<ArrayList<SetTask>>()
    {
        public ArrayList<SetTask> get()
        {
            ArrayList<SetTask> newList = new ArrayList<SetTask>(TASK_BUFFER_SIZE);
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
        ArrayList<SetTask> tasks = threadTaskQueue.get();
        tasks.add(new SetTask(index, values, offset, length));

        if (tasks.size() >= TASK_BUFFER_SIZE)
        {
            publishTasks(tasks);
            tasks.clear();
        }
    }

    void publishTasks(final ArrayList<SetTask> tasks) throws IOException
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
            for(ArrayList<SetTask> list : allTasks)
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
}

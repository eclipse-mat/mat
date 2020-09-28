/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - multiple heap dumps
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.IHprofParserHandler.HeapObject;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor;

/**
 * Parser used to read the hprof formatted heap dump
 */

public class Pass2Parser extends AbstractParser
{
    private IHprofParserHandler handler;
    private SimpleMonitor.Listener monitor;
    private IPositionInputStream in;
    private boolean parallel;
    private long streamLength;

    public Pass2Parser(IHprofParserHandler handler, SimpleMonitor.Listener monitor,
                    HprofPreferences.HprofStrictness strictnessPreference, long streamLength, boolean parallel)
    {
        super(strictnessPreference);
        this.handler = handler;
        this.monitor = monitor;
        this.streamLength = streamLength;
        this.parallel = parallel;
    }

    public void read(File file, String dumpNrToRead) throws SnapshotException, IOException
    {
        in = new BufferingRafPositionInputStream(file, 0, 8*1024, streamLength);

        int currentDumpNr = 0;

        try
        {
            version = readVersion(in);
            idSize = in.readInt();
            if (idSize != 4 && idSize != 8)
                throw new SnapshotException(Messages.Pass1Parser_Error_SupportedDumps);
            in.skipBytes(8); // creation date

            long fileSize = streamLength;
            long curPos = in.position();

            while (curPos < fileSize)
            {
                if (monitor.isProbablyCanceled())
                    throw new IProgressListener.OperationCanceledException();
                monitor.totalWorkDone(curPos / 1000);

                /*
                 * Use this instead of
                 * record = in.readUnsignedByte();
                 * so that we can detect the end of a zipped stream.
                 */
                int r = in.read();
                if (r == -1)
                    break;
                int record = r & 0xff;

                in.skipBytes(4); // time stamp

                long length = in.readUnsignedInt();
                if (length < 0)
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_IllegalRecordLength,
                                    length, in.position(), record));

                length = updateLengthIfNecessary(fileSize, curPos, record, length, monitor);
                // Do not read beyond the available space
                if (curPos + 9 + length > fileSize)
                {
                    length = fileSize - curPos - 9;
                }

                switch (record)
                {
                    case Constants.Record.HEAP_DUMP:
                    case Constants.Record.HEAP_DUMP_SEGMENT:
                        if (dumpMatches(currentDumpNr, dumpNrToRead))
                            readDumpSegments(length);
                        else
                            in.skipBytes(length);

                        if (record == Constants.Record.HEAP_DUMP)
                            currentDumpNr++;

                        break;
                    case Constants.Record.HEAP_DUMP_END:
                        currentDumpNr++;
                        in.skipBytes(length);
                        break;
                    default:
                        in.skipBytes(length);
                        break;
                }

                curPos = in.position();
            }
        }
        finally
        {
            try
            {
                in.close();
            }
            catch (IOException ignore)
            {}
        }
    }

    private void readDumpSegments(long length) throws SnapshotException, IOException
    {
        Stream<HeapObject> heapObjects = StreamSupport.stream(
                        new HeapObjectParser(length), parallel);
        try
        {
            heapObjects.forEach(t -> {
                try
                {
                    handler.addObject(t);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
        catch (UncheckedIOException e)
        {
            throw e.getCause();
        }
    }

    /**
     * Core stream parsing logic wrapped into a Spliterator
     *
     * Supports easier downstream parallel processing.
     */
    private class HeapObjectParser implements Spliterator<HeapObject>
    {
        static final int BATCH_SIZE = 512;
        static final long MAX_MEM = 1000000;

        final long end;

        // a bit ugly, but an instance variable allows us to capture elements easily
        private HeapObject _nextItemCapture = null;

        public HeapObjectParser(long length)
        {
            this.end = length + in.position();
        }

        public int characteristics()
        {
            return SUBSIZED | ORDERED | DISTINCT | IMMUTABLE | NONNULL;
        }

        public long estimateSize()
        {
            // do not know yet how long the remainder of the stream is
            return Long.MAX_VALUE;
        }

        public Spliterator<HeapObject> trySplit() {
            // read another N items from the queue
            final HeapObject[] nextBatch = new HeapObject[BATCH_SIZE];
            int found = 0;
            long memsize = 0;
            while (tryAdvance(t -> _nextItemCapture = t))
            {
                nextBatch[found] = _nextItemCapture;
                found++;
                if (_nextItemCapture.isObjectArray)
                    memsize += _nextItemCapture.ids.length;
                if (found >= nextBatch.length) break;
                if (memsize >= MAX_MEM) break;
            }
            // tryAdvance indicated end of stream, and no entries found, bail out
            if (found == 0)
            {
                return null;
            }
            // we have a loaded buffer to share
            return Spliterators.spliterator(nextBatch, 0, found, characteristics());
        }

        public boolean tryAdvance(Consumer<? super HeapObject> action)
        {
            try
            {
                long inputPosition = in.position();

                while (inputPosition < end)
                {
                    int segmentType = in.readUnsignedByte();
                    HeapObject heapObject = null;
                    switch (segmentType)
                    {
                        case Constants.DumpSegment.ROOT_UNKNOWN:
                        case Constants.DumpSegment.ROOT_STICKY_CLASS:
                        case Constants.DumpSegment.ROOT_MONITOR_USED:
                            in.skipBytes(idSize);
                            break;
                        case Constants.DumpSegment.ROOT_JNI_GLOBAL:
                            in.skipBytes(idSize * 2);
                            break;
                        case Constants.DumpSegment.ROOT_NATIVE_STACK:
                        case Constants.DumpSegment.ROOT_THREAD_BLOCK:
                            in.skipBytes(idSize + 4);
                            break;
                        case Constants.DumpSegment.ROOT_THREAD_OBJECT:
                        case Constants.DumpSegment.ROOT_JNI_LOCAL:
                        case Constants.DumpSegment.ROOT_JAVA_FRAME:
                            in.skipBytes(idSize + 8);
                            break;
                        case Constants.DumpSegment.CLASS_DUMP:
                            skipClassDump();
                            break;
                        case Constants.DumpSegment.INSTANCE_DUMP:
                            heapObject = readInstanceDump(inputPosition);
                            break;
                        case Constants.DumpSegment.OBJECT_ARRAY_DUMP:
                            heapObject = readObjectArrayDump(inputPosition);
                            break;
                        case Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP:
                            heapObject = readPrimitiveArrayDump(inputPosition);
                            break;
                        default:
                            throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_InvalidHeapDumpFile,
                                            Integer.toHexString(segmentType), Long.toHexString(inputPosition)));
                    }
                    inputPosition = in.position();
                    if (heapObject != null)
                    {
                        action.accept(heapObject);
                        return true;
                    }
                }
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
            return false;
        }
    }

    private void skipClassDump() throws IOException
    {
        in.skipBytes(7 * idSize + 8);

        int constantPoolSize = in.readUnsignedShort();
        for (int ii = 0; ii < constantPoolSize; ii++)
        {
            in.skipBytes(2);
            skipValue(in);
        }

        int numStaticFields = in.readUnsignedShort();
        for (int i = 0; i < numStaticFields; i++)
        {
            in.skipBytes(idSize);
            skipValue(in);
        }

        int numInstanceFields = in.readUnsignedShort();
        in.skipBytes((idSize + 1) * numInstanceFields);
    }

    private HeapObject readInstanceDump(long segmentStartPos) throws IOException
    {
        long id = in.readID(idSize);
        in.skipBytes(4);
        long classID = in.readID(idSize);
        int bytesFollowing = in.readInt();

        byte[] objectData = new byte[bytesFollowing];
        in.readFully(objectData);

        return HeapObject.forInstance(id, classID, objectData, segmentStartPos, idSize);
    }

    private HeapObject readObjectArrayDump(long segmentStartPos) throws IOException
    {
        long id = in.readID(idSize);

        in.skipBytes(4);
        int size = in.readInt();
        long arrayClassObjectID = in.readID(idSize);

        long[] ids = new long[size];
        for(int i = 0; i < size; i++)
        {
            ids[i] = in.readID(idSize);
        }

        return HeapObject.forObjectArray(id,  arrayClassObjectID,  size, ids, segmentStartPos);
    }

    private HeapObject readPrimitiveArrayDump(long segmentStartPos) throws SnapshotException, IOException
    {
        long id = in.readID(idSize);

        in.skipBytes(4);
        int size = in.readInt();
        byte elementType = in.readByte();

        if ((elementType < IPrimitiveArray.Type.BOOLEAN) || (elementType > IPrimitiveArray.Type.LONG))
            throw new SnapshotException(Messages.Pass1Parser_Error_IllegalType);

        int elementSize = IPrimitiveArray.ELEMENT_SIZE[elementType];
        in.skipBytes((long) elementSize * size);

        return HeapObject.forPrimitiveArray(id, elementType, size, segmentStartPos);
    }

}

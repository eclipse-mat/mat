/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import java.util.Arrays;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.SetLong;
import org.eclipse.mat.hprof.IHprofParserHandler.HeapObject;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject.Type;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;
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
    private HashMapLongObject<Long> classSerNum2id = new HashMapLongObject<Long>();
    private HashMapLongObject<String> constantPool = new HashMapLongObject<String>();
    private final String METHODSASCLASSES = HprofPreferences.methodsAsClasses();
    private final boolean READFRAMES = HprofPreferences.FRAMES_ONLY.equals(METHODSASCLASSES)
                    || HprofPreferences.RUNNING_METHODS_AS_CLASSES.equals(METHODSASCLASSES);
    private SetLong frameAddresses = new SetLong();

    public Pass2Parser(IHprofParserHandler handler, SimpleMonitor.Listener monitor,
                    HprofPreferences.HprofStrictness strictnessPreference, long streamLength, boolean parallel)
    {
        super(strictnessPreference);
        this.handler = handler;
        this.monitor = monitor;
        this.streamLength = streamLength;
        this.parallel = parallel;
    }

    public void read(File file, String prefix, String dumpNrToRead) throws SnapshotException, IOException
    {
        in = new BufferingRafPositionInputStream(file, prefix, 0, 8*1024, streamLength);

        int currentDumpNr = 0;

        try
        {
            version = readVersion(in);
            idSize = in.readInt();
            if (idSize != 4 && idSize != 8)
                throw new SnapshotException(Messages.Pass1Parser_Error_SupportedDumps);
            checkSkipBytes(8); // creation date

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

                checkSkipBytes(4); // time stamp

                long length = in.readUnsignedInt();
                if (length < 0)
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_IllegalRecordLength,
                                    length, in.position(), record));

                length = updateLengthIfNecessary(fileSize, curPos, record, length, monitor);
                // Do not read beyond the available space
                if (curPos + 9 + length > fileSize)
                {
                    switch (strictnessPreference)
                    {
                        case STRICTNESS_STOP:
                            break;
                        case STRICTNESS_WARNING:
                        case STRICTNESS_PERMISSIVE:
                            long length1 = fileSize - curPos - 9;
                            monitor.sendUserMessage(Severity.WARNING, MessageUtil.format(
                                            Messages.AbstractParser_GuessedRecordLength,
                                            Integer.toHexString(record),
                                            Long.toHexString(curPos), length, length1), null);
                            length = length1;
                            break;
                    }
                }

                switch (record)
                {
                    case Constants.Record.STRING_IN_UTF8:
                        if (READFRAMES)
                            readString(length);
                        else
                            checkSkipBytes(length);
                        break;
                    case Constants.Record.LOAD_CLASS:
                        if (READFRAMES)
                            readLoadClass(length);
                        else
                            checkSkipBytes(length);
                        break;
                    case Constants.Record.STACK_FRAME:
                        if (READFRAMES)
                            readStackFrame(curPos, length);
                        else
                            checkSkipBytes(length);
                        break;
                    case Constants.Record.HEAP_DUMP:
                    case Constants.Record.HEAP_DUMP_SEGMENT:
                        if (dumpMatches(currentDumpNr, dumpNrToRead))
                            readDumpSegments(length);
                        else
                            checkSkipBytes(length);

                        if (record == Constants.Record.HEAP_DUMP)
                            currentDumpNr++;

                        break;
                    case Constants.Record.HEAP_DUMP_END:
                        currentDumpNr++;
                        checkSkipBytes(length);
                        break;
                    default:
                        checkSkipBytes(length);
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

    /**
     * Note the constant pool strings.
     * Used for methods as classes.
     */
    private void readString(long length) throws IOException
    {
        long id = in.readID(idSize);
        byte[] chars = new byte[(int) (length - idSize)];
        in.readFully(chars);
        constantPool.put(id, new String(chars, "UTF-8")); //$NON-NLS-1$
    }

    /*
     * Note the classes.
     * Used for methods as classes.
     */
    private void readLoadClass(long length) throws IOException
    {
        long classSerNum = in.readUnsignedInt(); // used in stacks frames
        long classID = in.readID(idSize);
        checkSkipBytes(4); // stack trace
        in.readID(idSize); // nameID
        checkSkipBytes(length - 4 - idSize - 4 - idSize);
        classSerNum2id.put(classSerNum, classID);
    }

    private Object readStaticField(IClass cls, String field)
    {
        for (Field s1 : cls.getStaticFields())
        {
            if (s1.getType() == Type.OBJECT
                            && s1.getName().equals(field)
                            && s1.getValue() instanceof ObjectReference)
            {
                return s1.getValue();
            }
        }
        return null;
    }

    /**
     * Converts a frame ID to a pseudo-object address.
     * @param frameId the ID
     * @return the address
     */
    private long frameIdToAddress(long frameId)
    {
        return stackFrameBase + frameId * stackFrameAlign;
    }

    /*
     * Note the stack frames.
     * Used for methods as classes.
     */
    private void readStackFrame(long filePos, long length) throws IOException
    {
        long frameId = in.readID(idSize);
        long methodName = in.readID(idSize);
        long methodSig = in.readID(idSize);
        in.readID(idSize); // srcFile
        long classSerNum = in.readUnsignedInt();
        in.readInt(); // lineNr can be negative
        Long typeAddressO = classSerNum2id.get(classSerNum);
        String methodNameS = constantPool.get(methodName);
        String methodSigS = constantPool.get(methodSig);
        IClass decClass = handler.lookupClass(typeAddressO);
        long typeAddress = 0;
        if (decClass != null && HprofPreferences.RUNNING_METHODS_AS_CLASSES.equals(METHODSASCLASSES))
        {
            String fullMethodName = decClass.getName() + '.' + methodNameS + methodSigS;
            IClass methodCls = handler.lookupClassByName(fullMethodName, false);
            if (methodCls != null)
            {
                Object dc = readStaticField(methodCls, DECLARING_CLASS);
                if (dc instanceof ObjectReference
                                && ((ObjectReference) dc).getObjectAddress() == decClass.getObjectAddress())
                {
                    IClass methodClazz = handler.lookupClassByName(METHOD, false);
                    if (methodClazz != null)
                    {
                        for (IClass methodCls2 : methodClazz.getAllSubclasses())
                        {
                            if (!methodCls2.getName().equals(fullMethodName))
                                continue;
                            dc = readStaticField(methodCls2, DECLARING_CLASS);
                            if (dc instanceof ObjectReference
                                            && ((ObjectReference) dc).getObjectAddress() == decClass.getObjectAddress())
                            {
                                methodCls = methodCls2;
                                break;
                            }
                        }
                    }
                }
                typeAddress = methodCls.getObjectAddress();
            }
        }
        if (typeAddress == 0)
        {
            IClass frameClazz = handler.lookupClassByName(STACK_FRAME, false);
            if (frameClazz != null)
            {
                typeAddress = frameClazz.getObjectAddress();
            }
            else
            {
                IClass methodClazz = handler.lookupClassByName(METHOD, false);
                if (methodClazz != null)
                    typeAddress = methodClazz.getObjectAddress();
            }
        }
        // Must match pass 1
        long frameAddress = frameIdToAddress(frameId);
        frameAddresses.add(frameAddress);
        byte dummyData[] = new byte[100];
        HeapObject heapObject = HeapObject.forInstance(frameAddress, typeAddress, dummyData, filePos, idSize);
        this.handler.addObject(heapObject);
    }

    private void readDumpSegments(long length) throws SnapshotException, IOException
    {
        try (Stream<HeapObject> heapObjects = StreamSupport.stream(
                        new HeapObjectParser(length), parallel);)
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
                            checkSkipBytes(idSize);
                            break;
                        case Constants.DumpSegment.ROOT_JNI_GLOBAL:
                            checkSkipBytes(idSize * 2);
                            break;
                        case Constants.DumpSegment.ROOT_NATIVE_STACK:
                        case Constants.DumpSegment.ROOT_THREAD_BLOCK:
                            checkSkipBytes(idSize + 4);
                            break;
                        case Constants.DumpSegment.ROOT_THREAD_OBJECT:
                        case Constants.DumpSegment.ROOT_JNI_LOCAL:
                        case Constants.DumpSegment.ROOT_JAVA_FRAME:
                            checkSkipBytes(idSize + 8);
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
        checkSkipBytes(7 * idSize + 8);

        int constantPoolSize = in.readUnsignedShort();
        for (int ii = 0; ii < constantPoolSize; ii++)
        {
            checkSkipBytes(2);
            skipValue(in);
        }

        int numStaticFields = in.readUnsignedShort();
        for (int i = 0; i < numStaticFields; i++)
        {
            checkSkipBytes(idSize);
            skipValue(in);
        }

        int numInstanceFields = in.readUnsignedShort();
        checkSkipBytes((idSize + 1) * numInstanceFields);
    }

    private HeapObject readInstanceDump(long segmentStartPos) throws IOException
    {
        long id = in.readID(idSize);
        checkSkipBytes(4);
        long classID = in.readID(idSize);
        int bytesFollowing = in.readInt();

        byte[] objectData = new byte[bytesFollowing];
        in.readFully(objectData);

        /*
         * An ExportHPROF dump might already contain stack frame
         * pseudo-objects as heap objects.
         * Skip those objects here, as they will also be created
         * from the stack frame.
         */
        if (READFRAMES && frameAddresses.contains(id))
            return null;
        return HeapObject.forInstance(id, classID, objectData, segmentStartPos, idSize);
    }

    private HeapObject readObjectArrayDump(long segmentStartPos) throws IOException
    {
        long id = in.readID(idSize);

        checkSkipBytes(4);
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

        checkSkipBytes(4);
        int size = in.readInt();
        byte elementType = in.readByte();

        if ((elementType < IPrimitiveArray.Type.BOOLEAN) || (elementType > IPrimitiveArray.Type.LONG))
            throw new SnapshotException(Messages.Pass1Parser_Error_IllegalType);

        int elementSize = IPrimitiveArray.ELEMENT_SIZE[elementType];
        checkSkipBytes((long) elementSize * size);

        return HeapObject.forPrimitiveArray(id, elementType, size, segmentStartPos);
    }

    private int checkSkipBytes(int skip) throws IOException
    {
        int left = skip;
        while (left > 0)
        {
            int skipped = in.skipBytes(left);
            if (skipped == 0)
            {
                in.readByte();
                skipped = 1;
            }
            left -= skipped;
        }
        return skip - left;
    }

    private long checkSkipBytes(long skip) throws IOException
    {
        long left = skip;
        while (left > 0)
        {
            int skipped = in.skipBytes(Math.min(left, Integer.MAX_VALUE));
            if (skipped == 0)
            {
                in.readByte();
                skipped = 1;
            }
            left -= skipped;
        }
        return skip - left;
    }
}

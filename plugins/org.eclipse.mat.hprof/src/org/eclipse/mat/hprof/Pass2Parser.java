/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - multiple heap dumps
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.IHprofParserHandler.HeapObject;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.hprof.ui.HprofPreferences.HprofStrictness;
import org.eclipse.mat.parser.io.BufferingRafPositionInputStream;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
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
    private PositionInputStream in;

    public Pass2Parser(IHprofParserHandler handler, SimpleMonitor.Listener monitor,
                    HprofPreferences.HprofStrictness strictnessPreference)
    {
        super(strictnessPreference);
        this.handler = handler;
        this.monitor = monitor;
    }

    public void read(File file, String dumpNrToRead) throws SnapshotException, IOException
    {
        in = new BufferingRafPositionInputStream(file, 0, 8*1024);

        int currentDumpNr = 0;

        try
        {
            version = readVersion(in);
            idSize = in.readInt();
            if (idSize != 4 && idSize != 8)
                throw new SnapshotException(Messages.Pass1Parser_Error_SupportedDumps);
            in.skipBytes(8); // creation date

            long fileSize = file.length();
            long curPos = in.position();

            while (curPos < fileSize)
            {
                if (monitor.isProbablyCanceled())
                    throw new IProgressListener.OperationCanceledException();
                monitor.totalWorkDone(curPos / 1000);

                int record = in.readUnsignedByte();

                in.skipBytes(4); // time stamp

                long length = in.readUnsignedInt();
                if (length < 0)
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_IllegalRecordLength,
                                    length, in.position(), record));

                length = updateLengthIfNecessary(fileSize, curPos, record, length, monitor);

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
        long segmentStartPos = in.position();
        long segmentsEndPos = segmentStartPos + length;

        while (segmentStartPos < segmentsEndPos)
        {
            long workDone = segmentStartPos / 1000;
            if (this.monitor.getWorkDone() < workDone)
            {
                if (this.monitor.isProbablyCanceled())
                    throw new IProgressListener.OperationCanceledException();
                this.monitor.totalWorkDone(workDone);
            }

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
                    heapObject = readInstanceDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.OBJECT_ARRAY_DUMP:
                    heapObject = readObjectArrayDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP:
                    heapObject = readPrimitiveArrayDump(segmentStartPos);
                    break;
                default:
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_InvalidHeapDumpFile,
                                    Integer.toHexString(segmentType), Long.toHexString(segmentStartPos)));
            }
            if (heapObject != null)
            {
                handler.addObject(heapObject);
            }
            segmentStartPos = in.position();
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
        long endPos = in.position() + bytesFollowing;

        //// long classID, long id, remainder of data
        // TODO is there a way to read the remaining bytes early
        // so that we can do full processing later?

        List<IClass> hierarchy = handler.resolveClassHierarchy(classID);

        ClassImpl thisClazz = (ClassImpl) hierarchy.get(0);
        HeapObject heapObject;

        IClass objcl = handler.lookupClass(id);
        Field statics[] = new Field[0];
        if (objcl instanceof ClassImpl)
        {
            // An INSTANCE_DUMP record for a class type
            // This clazz is perhaps of different actual type, not java.lang.Class
            // The true type has already been set in PassParser1 and beforePass2()
            ClassImpl objcls = (ClassImpl) objcl;
            statics = objcls.getStaticFields().toArray(statics);
            // Heap size of each class type object is individual as have statics
            heapObject = new HeapObject(id, thisClazz,
                            objcls.getUsedHeapSize());
            // and extract the class references
            heapObject.references.addAll(objcls.getReferences());
        }
        else
        {
            heapObject = new HeapObject(id, thisClazz,
                            thisClazz.getHeapSizePerInstance());
            heapObject.references.add(thisClazz.getObjectAddress());
        }

        // extract outgoing references
        for (IClass clazz : hierarchy)
        {
            for (FieldDescriptor field : clazz.getFieldDescriptors())
            {
                int type = field.getType();
                // Find match for pseudo-statics
                Field stField = null;
                for (int stidx = 0; stidx < statics.length; ++stidx)
                {
                    if (statics[stidx] != null && statics[stidx].getType() == type && statics[stidx].getName().equals("<"+field.getName()+">")) { //$NON-NLS-1$ //$NON-NLS-2$
                        // Found a field
                        stField = statics[stidx];
                        // Don't use this twice.
                        statics[stidx] = null;
                        break;
                    }
                }
                if (type == IObject.Type.OBJECT)
                {
                    long refId = in.readID(idSize);
                    if (refId != 0)
                    {
                        heapObject.references.add(refId);
                        if (stField != null)
                        {
                            stField.setValue(new ObjectReference(null, refId));
                        }
                    }
                }
                else
                {
                    Object value = readValue(in, null, type);
                    if (stField != null)
                        stField.setValue(value);
                }
            }
        }
        
        if (endPos != in.position())
        {
            boolean unknown = false;
            for (IClass clazz : hierarchy)
            {
                if (clazz.getName().startsWith("unknown-class")) //$NON-NLS-1$
                {
                    unknown = true;
                }
            }
            
            if (endPos >= in.position() && unknown && (strictnessPreference == HprofStrictness.STRICTNESS_WARNING || strictnessPreference == HprofStrictness.STRICTNESS_PERMISSIVE))
            {
                monitor.sendUserMessage(Severity.WARNING, MessageUtil.format(Messages.Pass2Parser_Error_InsufficientBytesRead, thisClazz.getName(), Long.toHexString(id), Long.toHexString(segmentStartPos), Long.toHexString(endPos), Long.toHexString(in.position())), null);
                in.skipBytes(endPos - in.position());
            }
            else
            {
                throw new IOException(MessageUtil.format(Messages.Pass2Parser_Error_InsufficientBytesRead, thisClazz.getName(), Long.toHexString(id), Long.toHexString(segmentStartPos), Long.toHexString(endPos), Long.toHexString(in.position())));
            }
        }

        heapObject.filePosition = segmentStartPos;
        return heapObject;
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

        //// long arrayClassObjectID, long size, long id, long[] ids

        ClassImpl arrayType = (ClassImpl) handler.lookupClass(arrayClassObjectID);
        if (arrayType == null)
            throw new RuntimeException(MessageUtil.format(
                            Messages.Pass2Parser_Error_HandlerMustCreateFakeClassForAddress,
                            Long.toHexString(arrayClassObjectID)));

        long usedHeapSize = handler.getObjectArrayHeapSize(arrayType, size);
        HeapObject heapObject = new HeapObject(id, arrayType, usedHeapSize);
        heapObject.references.add(arrayType.getObjectAddress());
        heapObject.isArray = true;

        for (int ii = 0; ii < size; ii++)
        {
            if (ids[ii] != 0)
                heapObject.references.add(ids[ii]);
        }

        heapObject.filePosition = segmentStartPos;
        return heapObject;
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

        //// byte elementType, int size, long id

        ClassImpl clazz = (ClassImpl) handler.lookupPrimitiveArrayClassByType(elementType);

        long usedHeapSize = handler.getPrimitiveArrayHeapSize(elementType, size);
        HeapObject heapObject = new HeapObject(id, clazz, usedHeapSize);
        heapObject.references.add(clazz.getObjectAddress());
        heapObject.isArray = true;

        heapObject.filePosition = segmentStartPos;
        return heapObject;
    }

}

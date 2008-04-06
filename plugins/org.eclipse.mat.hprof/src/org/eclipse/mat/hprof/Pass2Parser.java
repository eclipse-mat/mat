/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.mat.hprof.IHprofParserHandler.HeapObject;
import org.eclipse.mat.impl.snapshot.internal.SimpleMonitor;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

/**
 * Parser used to read the hprof formatted heap dump
 */

public class Pass2Parser extends AbstractParser
{
    private IHprofParserHandler handler;
    private SimpleMonitor.Listener monitor;

    public Pass2Parser(IHprofParserHandler handler, SimpleMonitor.Listener monitor)
    {
        this.handler = handler;
        this.monitor = monitor;
    }

    public void read(File file) throws IOException
    {
        in = new PositionInputStream(new BufferedInputStream(new FileInputStream(file)));

        try
        {
            version = readVersionHeader();
            identifierSize = in.readInt();
            if (identifierSize != 4 && identifierSize != 8)
                throw new IOException("Only 32bit and 64bit dumps are supported.");
            in.skipBytes(8); // creation date

            while (true)
            {
                if (monitor.isProbablyCanceled())
                    return;
                monitor.totalWorkDone(in.position() / 1000);

                int record;

                try
                {
                    record = in.readUnsignedByte();
                }
                catch (EOFException ignored)
                {
                    // eof while reading the next record is okay
                    break;
                }

                in.skipBytes(4); // time stamp

                long length = readUnsignedInt();
                if (length < 0)
                    throw new IOException(MessageFormat.format("Illegal record length at byte {0}", in.position()));

                switch (record)
                {
                    case Constants.Record.HEAP_DUMP:
                    {
                        readDumpSegments(length);
                        return;
                    }
                    case Constants.Record.HEAP_DUMP_SEGMENT:
                    {
                        if (version.ordinal() >= Version.JDK6.ordinal())
                            readDumpSegments(length);
                        else
                            in.skipBytes(length);
                        break;
                    }
                    default:
                        in.skipBytes(length);
                        break;
                }

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

    private void readDumpSegments(long length) throws IOException
    {
        while (length > 0)
        {
            long segmentStartPos = in.position();

            long workDone = segmentStartPos / 1000;
            if (this.monitor.getWorkDone() < workDone)
            {
                if (this.monitor.isProbablyCanceled())
                    return;
                this.monitor.totalWorkDone(workDone);
            }

            int segmentType = in.readUnsignedByte();
            int size = 0;
            length--;
            switch (segmentType)
            {
                case Constants.DumpSegment.ROOT_UNKNOWN:
                case Constants.DumpSegment.ROOT_STICKY_CLASS:
                case Constants.DumpSegment.ROOT_MONITOR_USED:
                    if (in.skipBytes(identifierSize) != identifierSize)
                        throw new IOException();
                    length -= identifierSize;
                    break;
                case Constants.DumpSegment.ROOT_JNI_GLOBAL:
                    size = identifierSize * 2;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    length -= size;
                    break;
                case Constants.DumpSegment.ROOT_NATIVE_STACK:
                case Constants.DumpSegment.ROOT_THREAD_BLOCK:
                    size = identifierSize + 4;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    length -= size;
                    break;
                case Constants.DumpSegment.ROOT_THREAD_OBJECT:
                case Constants.DumpSegment.ROOT_JNI_LOCAL:
                case Constants.DumpSegment.ROOT_JAVA_FRAME:
                    size = identifierSize + 8;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    length -= size;
                    break;
                case Constants.DumpSegment.CLASS_DUMP:
                    length -= skipClassDump();
                    break;
                case Constants.DumpSegment.INSTANCE_DUMP:
                    length -= readInstanceDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.OBJECT_ARRAY_DUMP:
                    length -= readObjectArrayDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP:
                    length -= readPrimitveArrayDump(segmentStartPos);
                    break;
                default:
                    throw new IOException(MessageFormat.format("Unrecognized segment type {0} at position {1}",
                                    segmentType, segmentStartPos));
            }
        }

        if (length != 0)
            throw new IOException("Invalid dump record length.");
    }

    private int skipClassDump() throws IOException
    {
        int bytesRead = 7 * identifierSize + 8;
        if (in.skipBytes(bytesRead) != bytesRead)
            throw new IOException();

        int constantPoolSize = in.readUnsignedShort();
        bytesRead += 2;
        for (int ii = 0; ii < constantPoolSize; ii++)
        {
            bytesRead += in.skipBytes(2);
            bytesRead += skipValue();
        }

        int numStaticFields = in.readUnsignedShort();
        bytesRead += 2;
        for (int i = 0; i < numStaticFields; i++)
        {
            bytesRead += in.skipBytes(identifierSize);
            bytesRead += skipValue();
        }

        int numInstanceFields = in.readUnsignedShort();
        bytesRead += 2;
        bytesRead += in.skipBytes((identifierSize + 1) * numInstanceFields);

        return bytesRead;
    }

    private int readInstanceDump(long segmentStartPos) throws IOException
    {
        long id = readID();
        in.skipBytes(4);
        long classID = readID();
        int bytesFollowing = in.readInt();

        List<IClass> hierarchy = handler.resolveClassHierarchy(classID);

        ClassImpl thisClazz = (ClassImpl) hierarchy.get(0);
        HeapObject heapObject = new HeapObject(handler.mapAddressToId(id), id, thisClazz, thisClazz
                        .getHeapSizePerInstance());

        heapObject.references.add(thisClazz.getObjectAddress());

        // extract outgoing references
        int readBytes = 0;
        for (IClass clazz : hierarchy)
        {
            for (FieldDescriptor field : clazz.getFieldDescriptors())
            {
                byte type = (byte) field.getSignature().charAt(0);
                if (type == '[' || type == 'L')
                {
                    long refId = readID();
                    readBytes += identifierSize;

                    if (refId != 0)
                        heapObject.references.add(refId);
                }
                else
                {
                    readBytes += skipValue(type);
                }
            }
        }

        if (readBytes != bytesFollowing)
            throw new IOException("Illegal segment length for instance 0x" + Long.toHexString(id));

        handler.addObject(heapObject, segmentStartPos);

        return 2 * identifierSize + 8 + bytesFollowing;
    }

    private int readObjectArrayDump(long segmentStartPos) throws IOException
    {
        long id = readID();

        in.skipBytes(4);
        int size = in.readInt();
        int bytesRead = identifierSize + 8;

        long arrayClassObjectID = readID();
        bytesRead += identifierSize;

        ClassImpl arrayType = (ClassImpl) handler.lookupClass(arrayClassObjectID);
        if (arrayType == null)
            throw new RuntimeException("handler must create fake class for 0x" + Long.toHexString(arrayClassObjectID));

        HeapObject heapObject = new HeapObject(handler.mapAddressToId(id), id, arrayType, ObjectArrayImpl
                        .doGetUsedHeapSize(arrayType, size));
        heapObject.references.add(arrayType.getObjectAddress());
        heapObject.isArray = true;

        for (int ii = 0; ii < size; ii++)
        {
            long refId = readID();
            if (refId != 0)
                heapObject.references.add(refId);
        }

        handler.addObject(heapObject, segmentStartPos);

        bytesRead += (size * identifierSize);
        return bytesRead;
    }

    private int readPrimitveArrayDump(long segmentStartPost) throws IOException
    {
        long id = readID();

        in.skipBytes(4);
        int size = in.readInt();
        int bytesRead = identifierSize + 8;

        byte elementType = in.readByte();
        bytesRead++;

        if ((elementType < IPrimitiveArray.Type.BOOLEAN) || (elementType > IPrimitiveArray.Type.LONG))
            throw new IOException("Illegal primitive object array type");

        String name = IPrimitiveArray.TYPE[(int) elementType];
        ClassImpl clazz = (ClassImpl) handler.lookupClassByName(name, true);
        if (clazz == null)
            throw new RuntimeException("handler must create fake class for " + name);

        HeapObject heapObject = new HeapObject(handler.mapAddressToId(id), id, clazz, PrimitiveArrayImpl
                        .doGetUsedHeapSize(clazz, size, (int) elementType));
        heapObject.references.add(clazz.getObjectAddress());
        heapObject.isArray = true;

        handler.addObject(heapObject, segmentStartPost);

        int elementSize = IPrimitiveArray.ELEMENT_SIZE[(int) elementType];
        bytesRead += in.skipBytes(elementSize * size);
        return bytesRead;
    }

}

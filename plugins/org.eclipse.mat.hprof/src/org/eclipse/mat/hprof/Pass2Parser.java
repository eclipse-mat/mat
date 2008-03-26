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

import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

public class Pass2Parser extends HprofBasics
{

    private int dumpsToSkip;

    long lastRecordPosition;

    protected IHprofParserHandler handler;
    protected SimpleMonitor.Listener monitor;

    public Pass2Parser(PositionInputStream in, int dumpNumber, IHprofParserHandler handler,
                    SimpleMonitor.Listener monitor) throws IOException
    {
        this.in = in;
        this.dumpsToSkip = dumpNumber - 1;
        this.handler = handler;
        this.monitor = monitor;
    }

    public void read() throws IOException
    {
        version = readVersionHeader();
        handler.addProperty(IHprofParserHandler.VERSION_ID, String.valueOf(version));
        handler.addProperty(IHprofParserHandler.VERSION_STRING, HprofBasics.VERSIONS[version]);

        identifierSize = in.readInt();
        handler.addProperty(IHprofParserHandler.IDENTIFIER_SIZE, String.valueOf(identifierSize));

        if (identifierSize != 4 && identifierSize != 8) { throw new IOException(
                        "I'm sorry, but I can't deal with an identifier size of " + identifierSize
                                        + ".  I can only deal with 4 or 8."); }

        long date = in.readLong();
        handler.addProperty(IHprofParserHandler.CREATION_DATE, String.valueOf(date));

        for (;;)
        {
            if (this.monitor.isProbablyCanceled())
                return;
            this.monitor.totalWorkDone(in.position() / 1000);

            int type;
            try
            {
                type = in.readUnsignedByte();
            }
            catch (EOFException ignored)
            {
                // $JL-EXC$
                break;
            }
            in.readInt(); // Timestamp of this record
            long length = readUnsignedInt();
            if (length < 0) { throw new IOException("Bad record length of " + length); }
            switch (type)
            {
                case HPROF_UTF8:
                case HPROF_LOAD_CLASS:
                    in.skipBytes(length);
                    break;

                case HPROF_HEAP_DUMP:
                {
                    if (dumpsToSkip <= 0)
                    {
                        readHeapDump(length);
                        return;
                    }
                    else
                    {
                        dumpsToSkip--;
                        in.skipBytes(length);
                    }
                    break;
                }

                case HPROF_HEAP_DUMP_END:
                {
                    if (version >= VERSION_JDK6)
                    {
                        if (dumpsToSkip <= 0)
                        {
                            in.skipBytes(length); // should be no-op
                            return;
                        }
                        else
                        {
                            // skip this dump (of the end record for a sequence
                            // of dump segments)
                            dumpsToSkip--;
                        }
                    }
                    else
                    {
                        // HPROF_HEAP_DUMP_END only recognized in >= 1.0.2
                        warn("Ignoring unrecognized record type " + type);
                    }
                    in.skipBytes(length); // should be no-op
                    break;
                }

                case HPROF_HEAP_DUMP_SEGMENT:
                {
                    if (version >= VERSION_JDK6)
                    {
                        if (dumpsToSkip <= 0)
                        {
                            // read the dump segment
                            readHeapDump(length);
                        }
                        else
                        {
                            // all segments comprising the heap dump will be
                            // skipped
                            in.skipBytes(length);
                        }
                    }
                    else
                    {
                        // HPROF_HEAP_DUMP_SEGMENT only recognized in >= 1.0.2
                        warn("Ignoring unrecognized record type " + type);
                        in.skipBytes(length);
                    }
                    break;
                }

                case HPROF_HEAP_SUMMARY:
                case HPROF_FRAME:
                case HPROF_TRACE:
                case HPROF_UNLOAD_CLASS:
                case HPROF_ALLOC_SITES:
                case HPROF_START_THREAD:
                case HPROF_END_THREAD:
                case HPROF_CPU_SAMPLES:
                case HPROF_CONTROL_SETTINGS:
                case HPROF_LOCKSTATS_WAIT_TIME:
                case HPROF_LOCKSTATS_HOLD_TIME:
                {
                    // Ignore these record types
                    in.skipBytes(length);
                    break;
                }
                default:
                {
                    in.skipBytes(length);
                    warn("Ignoring unrecognized record type " + type);
                }
            }
        }
    }

    private int readVersionHeader() throws IOException
    {
        int candidatesLeft = VERSIONS.length;
        boolean[] matched = new boolean[VERSIONS.length];
        for (int i = 0; i < candidatesLeft; i++)
        {
            matched[i] = true;
        }

        int pos = 0;
        while (candidatesLeft > 0)
        {
            char c = (char) in.readByte();
            for (int i = 0; i < VERSIONS.length; i++)
            {
                if (matched[i])
                {
                    if (c != VERSIONS[i].charAt(pos))
                    { // Not matched
                        matched[i] = false;
                        --candidatesLeft;
                    }
                    else if (pos == VERSIONS[i].length() - 1)
                    { // Full match
                        return i;
                    }
                }
            }
            ++pos;
        }
        throw new IOException("Version string not recognized at byte " + (pos + 3));
    }

    private void readHeapDump(long bytesLeft) throws IOException
    {
        while (bytesLeft > 0)
        {
            lastRecordPosition = in.position();

            long workDone = lastRecordPosition / 1000;
            if (this.monitor.getWorkDone() < workDone)
            {
                if (this.monitor.isProbablyCanceled())
                    return;
                this.monitor.totalWorkDone(workDone);
            }

            int type = in.readUnsignedByte();
            int size = 0;
            bytesLeft--;
            switch (type)
            {
                case HPROF_GC_ROOT_UNKNOWN:
                {
                    if (in.skipBytes(identifierSize) != identifierSize)
                        throw new IOException();
                    bytesLeft -= identifierSize;
                    break;
                }
                case HPROF_GC_ROOT_THREAD_OBJ:
                {
                    size = identifierSize + 8;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    bytesLeft -= size;
                    break;
                }
                case HPROF_GC_ROOT_JNI_GLOBAL:
                {
                    size = identifierSize * 2;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    bytesLeft -= size;
                    break;
                }
                case HPROF_GC_ROOT_JNI_LOCAL:
                {
                    size = identifierSize + 8;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    bytesLeft -= size;
                    break;
                }
                case HPROF_GC_ROOT_JAVA_FRAME:
                {
                    size = identifierSize + 8;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    bytesLeft -= size;
                    break;
                }
                case HPROF_GC_ROOT_NATIVE_STACK:
                {
                    size = identifierSize + 4;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    bytesLeft -= size;
                    break;
                }
                case HPROF_GC_ROOT_STICKY_CLASS:
                {
                    if (in.skipBytes(identifierSize) != identifierSize)
                        throw new IOException();
                    bytesLeft -= identifierSize;
                    break;
                }
                case HPROF_GC_ROOT_THREAD_BLOCK:
                {
                    size = identifierSize + 4;
                    if (in.skipBytes(size) != size)
                        throw new IOException();
                    bytesLeft -= size;
                    break;
                }
                case HPROF_GC_ROOT_MONITOR_USED:
                {
                    if (in.skipBytes(identifierSize) != identifierSize)
                        throw new IOException();
                    bytesLeft -= identifierSize;
                    break;
                }
                case HPROF_GC_CLASS_DUMP:
                {
                    int bytesRead = readClass();
                    bytesLeft -= bytesRead;
                    break;
                }
                case HPROF_GC_INSTANCE_DUMP:
                {
                    int bytesRead = readInstance();
                    bytesLeft -= bytesRead;
                    break;
                }
                case HPROF_GC_OBJ_ARRAY_DUMP:
                {
                    int bytesRead = readArray(false);
                    bytesLeft -= bytesRead;
                    break;
                }
                case HPROF_GC_PRIM_ARRAY_DUMP:
                {
                    int bytesRead = readArray(true);
                    bytesLeft -= bytesRead;
                    break;
                }
                default:
                {
                    throw new IOException("Unrecognized heap dump sub-record type:  " + type
                                    + " @ last record position " + lastRecordPosition);
                }
            }
        }
        if (bytesLeft != 0)
        {
            warn("Error reading heap dump or heap dump segment:  Byte count is " + bytesLeft + " instead of 0");
            in.skipBytes(bytesLeft);
        }
    }

    private int readClass() throws IOException
    {
        int bytesRead = 7 * identifierSize + 8;
        if (in.skipBytes(bytesRead) != bytesRead)
            throw new IOException();

        int numConstPoolEntries = in.readUnsignedShort();
        bytesRead += 2;
        for (int i = 0; i < numConstPoolEntries; i++)
        {
            /* int index = */in.readUnsignedShort();
            bytesRead += 2;
            bytesRead += readValue(null, in, null);
        }

        int numStatics = in.readUnsignedShort();
        bytesRead += 2;
        for (int i = 0; i < numStatics; i++)
        {
            if (in.skipBytes(identifierSize) != identifierSize)
                throw new IOException();
            bytesRead += identifierSize;
            byte type = in.readByte();
            bytesRead++;
            bytesRead += readValueForType(null, in, type, null);
        }

        int numFields = in.readUnsignedShort();
        bytesRead += 2;

        int s = (identifierSize + 1) * numFields;
        if (in.skipBytes(s) != s)
            throw new IOException();
        bytesRead += s;

        return bytesRead;
    }

    private int readInstance() throws IOException
    {
        long id = readID();
        in.readInt(); // stack trace
        long classID = readID();
        int bytesFollowing = in.readInt();

        List<IClass> hierarchy = handler.resolveClassHierarchy(classID);

        ClassImpl thisClazz = (ClassImpl) hierarchy.get(0);
        HeapObject heapObject = new HeapObject(handler.mapAddressToId(id), id, thisClazz, thisClazz
                        .getHeapSizePerInstance());

        heapObject.references.add(thisClazz.getObjectAddress());

        int readBytes = 0;
        for (IClass clazz : hierarchy)
        {
            for (FieldDescriptor field : clazz.getFieldDescriptors())
            {
                byte type = (byte) field.getSignature().charAt(0);
                switch (type)
                {
                    case '[':
                    case 'L':
                        long ref = readID();
                        readBytes += identifierSize;

                        if (ref != 0)
                            heapObject.references.add(ref);

                        break;

                    default:
                        readBytes += skipValueForTypeSignature(in, type);
                }
            }
        }

        if (readBytes != bytesFollowing)
            throw new IOException("Unexpected amount of data found in HPROF_GC_INSTANCE_DUMP.");

        handler.addObject(heapObject, lastRecordPosition);

        return 2 * identifierSize + 8 + bytesFollowing;
    }

    private int readArray(boolean isPrimitive) throws IOException
    {
        long id = readID();
        in.readInt(); // stack trace
        int arraySize = in.readInt();
        int bytesRead = identifierSize + 8;

        long elementClassID;
        if (isPrimitive)
        {
            elementClassID = in.readByte();
            bytesRead++;
        }
        else
        {
            elementClassID = readID();
            bytesRead += identifierSize;
        }

        byte primitiveSignature = 0x00;
        int elSize = 0;
        if (isPrimitive || version < VERSION_JDK12BETA4)
        {
            if ((elementClassID > 3) && (elementClassID < 12))
            {
                primitiveSignature = IPrimitiveArray.SIGNATURES[(int) elementClassID];
                elSize = IPrimitiveArray.ELEMENT_SIZE[(int) elementClassID];
            }

            if (version >= VERSION_JDK12BETA4 && primitiveSignature == 0x00) { throw new IOException(
                            "Unrecognized typecode:  " + elementClassID); }
        }

        if (primitiveSignature != 0x00)
        {
            // do not read primitive type -> no references
            int size = elSize * arraySize;
            bytesRead += size;
            if (in.skipBytes(size) != size)
                throw new IOException();

            String name = IPrimitiveArray.TYPE[(int) elementClassID];
            ClassImpl clazz = (ClassImpl) handler.lookupClassByName(name, true);
            if (clazz == null) { throw new RuntimeException("handler must create fake class for " + name); }

            HeapObject heapObject = new HeapObject(handler.mapAddressToId(id), id, clazz, PrimitiveArrayImpl
                            .doGetUsedHeapSize(clazz, arraySize, (int) elementClassID));
            heapObject.references.add(clazz.getObjectAddress());
            heapObject.isArray = true;

            handler.addObject(heapObject, lastRecordPosition);
        }
        else
        {
            long arrayClassID = 0;
            if (version >= VERSION_JDK12BETA4)
            {
                // It changed from the ID of the object describing the
                // class of element types to the ID of the object describing
                // the type of the array.
                arrayClassID = elementClassID;
                elementClassID = 0;
            }

            // This is needed because the JDK only creates Class structures
            // for array element types, not the arrays themselves. For
            // analysis, though, we need to pretend that there's a
            // JavaClass for the array type, too.

            ClassImpl arrayType = null;
            if (arrayClassID != 0)
            {
                arrayType = (ClassImpl) handler.lookupClass(arrayClassID);
            }
            else if (elementClassID != 0)
            {
                arrayType = (ClassImpl) handler.lookupClass(elementClassID);
                if (arrayType != null)
                {
                    String name = arrayType.getName() + "[]";
                    arrayType = (ClassImpl) handler.lookupClassByName(name, true);
                }
            }

            if (arrayType == null)
                throw new RuntimeException("handle must create fake class for " + arrayClassID);

            HeapObject heapObject = new HeapObject(handler.mapAddressToId(id), id, arrayType, ObjectArrayImpl
                            .doGetUsedHeapSize(arrayType, arraySize));
            heapObject.references.add(arrayType.getObjectAddress());
            heapObject.isArray = true;

            int size = arraySize * identifierSize;
            bytesRead += size;

            for (int ii = 0; ii < arraySize; ii++)
            {
                long refId = readID();
                if (refId != 0)
                    heapObject.references.add(refId);
            }

            handler.addObject(heapObject, lastRecordPosition);
        }
        return bytesRead;
    }

    private void warn(String msg)
    {
        Logger.getLogger(Pass2Parser.class.getName()).log(Level.WARNING, msg);
    }

}

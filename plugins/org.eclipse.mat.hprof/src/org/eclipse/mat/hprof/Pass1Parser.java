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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.impl.snapshot.internal.SimpleMonitor;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;


/**
 * Parser used to read the hprof formatted heap dump
 */

public class Pass1Parser extends HprofBasics
{
    private static final Pattern PATTERN_OBJ_ARRAY = Pattern.compile("^(\\[+)L(.*);$");
    private static final Pattern PATTERN_PRIMITIVE_ARRAY = Pattern.compile("^(\\[+)(.)$");

    // Hashtable<Integer, ThreadObject>, used to map the thread sequence number
    // (aka "serial number") to the thread object ID for
    // HPROF_GC_ROOT_THREAD_OBJ. ThreadObject is a trivial inner class,
    // at the end of this file.
    private Map<Integer, ThreadObject> threadObjects;
    
    private int currPos;

    private int dumpsToSkip;

    // Hashtable<Integer, String>, maps class object ID to class name
    // (with / converted to .)
    private HashMapLongObject<String> classNameFromObjectID;

    long lastRecordPosition;

    protected IHprofParserHandler handler;
    protected SimpleMonitor.Listener monitor;

    public Pass1Parser(PositionInputStream in, int dumpNumber, IHprofParserHandler handler,
                    SimpleMonitor.Listener monitor) throws IOException
    {
        this.in = in;
        this.dumpsToSkip = dumpNumber - 1;
        this.handler = handler;
        this.monitor = monitor;

        this.classNameFromObjectID = new HashMapLongObject<String>();
        this.threadObjects = new HashMap<Integer, ThreadObject>(43);

    }

    public void read() throws SnapshotException, IOException
    {
        currPos = 4; // 4 because of the magic number

        version = readVersionHeader();
        handler.addProperty(IHprofParserHandler.VERSION_ID, String.valueOf(version));
        handler.addProperty(IHprofParserHandler.VERSION_STRING, HprofBasics.VERSIONS[version]);

        identifierSize = in.readInt();
        currPos += 4;
        handler.addProperty(IHprofParserHandler.IDENTIFIER_SIZE, String.valueOf(identifierSize));

        if (identifierSize != 4 && identifierSize != 8) { throw new IOException(
                        "I'm sorry, but I can't deal with an identifier size of " + identifierSize
                                        + ".  I can only deal with 4 or 8."); }

        long date = in.readLong();
        currPos += 8;
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
            if (length < 0) { throw new IOException("Bad record length of " + length + " at byte " + toHex(currPos + 5)
                            + " of file."); }
            currPos += 9 + length;
            switch (type)
            {
                case HPROF_UTF8:
                {
                    long id = readID();
                    byte[] chars = new byte[(int) (length - identifierSize)];
                    in.readFully(chars);
                    handler.getConstantPool().put(id, new String(chars));
                    break;
                }
                case HPROF_LOAD_CLASS:
                {
                    /* int serialNo = */in.readInt();
                    long classID = readID();
                    /* int stackTraceSerialNo = */in.readInt();
                    long classNameID = readID();

                    String className = getNameFromID(classNameID).replace('/', '.');
                    classNameFromObjectID.put(classID, className);
                    break;
                }

                case HPROF_HEAP_DUMP:
                {
                    if (length == 0)
                        throw new SnapshotException("Not a valid HPROF file: heap dump segment has a size of 0.");
                    
                    
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
                    /*
                     * HPROF_HEAP_SUMMARY heap summary u4 total live bytes u4
                     * total live instances u8 total bytes allocated u8 total
                     * instances allocated
                     */
                    int liveBytes = in.readInt();
                    int liveInstances = in.readInt();
                    long bytesAllocated = in.readLong();
                    long instancesAllocated = in.readLong();

                    handler.addProperty("liveBytes", String.valueOf(liveBytes));
                    handler.addProperty("liveInstances", String.valueOf(liveInstances));
                    handler.addProperty("bytesAllocated", String.valueOf(bytesAllocated));
                    handler.addProperty("instancesAllocated", String.valueOf(instancesAllocated));

                    break;

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
            currPos++;
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
            bytesLeft--;
            switch (type)
            {
                case HPROF_GC_ROOT_UNKNOWN:
                {
                    long id = readID();
                    bytesLeft -= identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.UNKNOWN);
                    break;
                }
                case HPROF_GC_ROOT_THREAD_OBJ:
                {
                    long id = readID();
                    int threadSeq = in.readInt();
                    /*int stackSeq = */in.readInt();
                    bytesLeft -= identifierSize + 8;
                    threadObjects.put(threadSeq, new ThreadObject(id));
                    handler.addGCRoot(id, 0, GCRootInfo.Type.THREAD_OBJ);
                    break;
                }
                case HPROF_GC_ROOT_JNI_GLOBAL:
                {
                    long id = readID();
                    /* long globalRefId = */readID();
                    bytesLeft -= 2 * identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.NATIVE_STACK);
                    break;
                }
                case HPROF_GC_ROOT_JNI_LOCAL:
                {
                    long id = readID();
                    int threadSeq = in.readInt();
                    /* int depth = */in.readInt();
                    bytesLeft -= identifierSize + 8;
                    ThreadObject to = getThreadObjectFromSequence(threadSeq);
                    handler.addGCRoot(id, to.threadId, GCRootInfo.Type.NATIVE_LOCAL);
                    break;
                }
                case HPROF_GC_ROOT_JAVA_FRAME:
                {
                    long id = readID();
                    int threadSeq = in.readInt();
                    /* int depth = */in.readInt();
                    bytesLeft -= identifierSize + 8;
                    ThreadObject to = getThreadObjectFromSequence(threadSeq);
                    handler.addGCRoot(id, to.threadId /* ? */, GCRootInfo.Type.JAVA_LOCAL);
                    break;
                }
                case HPROF_GC_ROOT_NATIVE_STACK:
                {
                    long id = readID();
                    int threadSeq = in.readInt();
                    bytesLeft -= identifierSize + 4;
                    ThreadObject to = getThreadObjectFromSequence(threadSeq);
                    handler.addGCRoot(id, to.threadId, GCRootInfo.Type.NATIVE_STACK);
                    break;
                }
                case HPROF_GC_ROOT_STICKY_CLASS:
                {
                    long id = readID();
                    bytesLeft -= identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.SYSTEM_CLASS);
                    break;
                }
                case HPROF_GC_ROOT_THREAD_BLOCK:
                {
                    long id = readID();
                    int threadSeq = in.readInt();
                    bytesLeft -= identifierSize + 4;
                    /* ThreadObject to = */getThreadObjectFromSequence(threadSeq);
                    handler.addGCRoot(id, 0, GCRootInfo.Type.THREAD_BLOCK);
                    break;
                }
                case HPROF_GC_ROOT_MONITOR_USED:
                {
                    long id = readID();
                    bytesLeft -= identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.BUSY_MONITOR);
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
                    throw new IOException("Unrecognized heap dump sub-record type:  " + type);
                }
            }
        }
        if (bytesLeft != 0)
        {
            warn("Error reading heap dump or heap dump segment:  Byte count is " + bytesLeft + " instead of 0");
            in.skipBytes(bytesLeft);
        }
    }

    private ThreadObject getThreadObjectFromSequence(int threadSeq) throws IOException
    {
        ThreadObject to = (ThreadObject) threadObjects.get(threadSeq);
        if (to == null) { throw new IOException("Thread " + threadSeq + " not found for JNI local ref"); }
        return to;
    }

    private String getNameFromID(long id) throws IOException
    {
        if (id == 0L) { return ""; }
        String result = (String) handler.getConstantPool().get(id);
        if (result == null)
        {
            warn("Name not found at " + toHex(id));
            return "unresolved name " + toHex(id);
        }
        return result;
    }

    private int readClass() throws IOException
    {

        long id = readID();

        in.readInt(); // stack trace

        long superId = readID();
        long classLoaderId = readID();
        int bytesRead = 3 * identifierSize + 4;

        /* long signersId = readID(); */
        /* long protDomainId = readID(); */
        /* long reserved1 = readID(); */
        /* long reserved2 = readID(); */
        /* int instanceSize = in.readInt(); */
        bytesRead += in.skipBytes(this.identifierSize * 4 + 4);

        int numConstPoolEntries = in.readUnsignedShort();
        bytesRead += 2;
        for (int i = 0; i < numConstPoolEntries; i++)
        {
            /* int index = */in.readUnsignedShort(); // unused
            bytesRead += 2;
            bytesRead += readValue(null, in, null); // We ignore the values
        }

        int numStatics = in.readUnsignedShort();
        bytesRead += 2;
        Object[] valueBin = new Object[1];
        Field[] statics = new Field[numStatics];

        for (int i = 0; i < numStatics; i++)
        {
            long nameId = readID();
            bytesRead += identifierSize;
            byte type = in.readByte();
            bytesRead++;
            bytesRead += readValueForType(null, in, type, valueBin);
            String fieldName = getNameFromID(nameId);
            if (version >= VERSION_JDK12BETA4)
            {
                type = signatureFromTypeId(type);
            }
            String signature = "" + ((char) type);
            statics[i] = new Field(fieldName, signature, valueBin[0]);

        }
        int numFields = in.readUnsignedShort();
        bytesRead += 2;
        FieldDescriptor[] fields = new FieldDescriptor[numFields];

        for (int i = 0; i < numFields; i++)
        {
            long nameId = readID();
            bytesRead += identifierSize;
            byte type = in.readByte();
            bytesRead++;
            String fieldName = getNameFromID(nameId);
            if (version >= VERSION_JDK12BETA4)
            {
                type = signatureFromTypeId(type);
            }
            String signature = "" + ((char) type);
            fields[i] = new FieldDescriptor(fieldName, signature);
        }
        String name = (String) classNameFromObjectID.get(id);
        if (name == null)
        {
            warn("Class name not found for " + toHex(id));
            name = "unknown-name@0x" + toHex(id);
        }

        if (name.charAt(0) == '[') // quick check if array at hand
        {
            // fix object class names
            Matcher matcher = PATTERN_OBJ_ARRAY.matcher(name);
            if (matcher.matches())
            {
                int l = matcher.group(1).length();
                name = matcher.group(2);
                for (int ii = 0; ii < l; ii++)
                    name += "[]";
            }

            // primitive arrays
            matcher = PATTERN_PRIMITIVE_ARRAY.matcher(name);
            if (matcher.matches())
            {
                int count = matcher.group(1).length() - 1;
                name = PrimitiveArrayImpl.determineReadableName(matcher.group(2).charAt(0));
                for (int ii = 0; ii < count; ii++)
                    name += "[]";
            }
        }

        ClassImpl clazz = new ClassImpl(id, name, superId, classLoaderId, statics, fields);

        handler.addClass(clazz, lastRecordPosition);

        return bytesRead;
    }

    private String toHex(long addr)
    {
        return Long.toHexString(addr);
    }

    private int readInstance() throws IOException
    {
        long id = readID();

        handler.reportInstance(id, lastRecordPosition);

        if (in.skipBytes(identifierSize + 4) != identifierSize + 4)
            throw new IOException();
        int bytesFollowing = in.readInt();
        if (in.skipBytes(bytesFollowing) != bytesFollowing)
            throw new IOException();
        return 2 * identifierSize + 8 + bytesFollowing;
    }

    private int readArray(boolean isPrimitive) throws IOException
    {
        long id = readID();

        handler.reportInstance(id, lastRecordPosition);
        in.readInt(); // stackTrace
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
            long size = elSize * arraySize;
            bytesRead += size;
            in.skipBytes(size);

            String name = IPrimitiveArray.TYPE[(int) elementClassID];
            IClass clazz = handler.lookupClassByName(name, true);
            if (clazz == null)
                handler.reportRequiredPrimitiveArray((int) elementClassID);
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

            IClass arrayType = null;
            if (arrayClassID != 0)
            {
                arrayType = handler.lookupClass(arrayClassID);
            }
            else if (elementClassID != 0)
            {
                arrayType = handler.lookupClass(elementClassID);
                if (arrayType != null)
                {
                    String name = arrayType.getName() + "[]";
                    arrayType = handler.lookupClassByName(name, true);
                }
            }

            // create fake array class
            // --> creation is delayed after pass 1 as the class could be read
            // later
            if (arrayType == null)
            {
                if (arrayClassID != 0)
                    handler.reportRequiredObjectArray(arrayClassID);
                else
                    handler.reportRequiredObjectArray(elementClassID);
            }

            long size = arraySize * identifierSize;
            bytesRead += size;
            in.skipBytes(size);
        }
        return bytesRead;
    }

    private void warn(String msg)
    {
        Logger.getLogger(Pass1Parser.class.getName()).log(Level.WARNING, msg);
    }

    protected static class ThreadObject
    {
        public long threadId;

        ThreadObject(long threadId)
        {
            this.threadId = threadId;
        }
    }

}

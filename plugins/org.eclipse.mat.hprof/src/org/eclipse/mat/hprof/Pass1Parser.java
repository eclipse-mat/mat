package org.eclipse.mat.hprof;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.impl.snapshot.internal.SimpleMonitor;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.util.IProgressListener;

public class Pass1Parser extends AbstractParser
{
    private static final Pattern PATTERN_OBJ_ARRAY = Pattern.compile("^(\\[+)L(.*);$");
    private static final Pattern PATTERN_PRIMITIVE_ARRAY = Pattern.compile("^(\\[+)(.)$");

    private HashMapLongObject<String> class2name = new HashMapLongObject<String>();
    private HashMapLongObject<Long> threadSeq2id = new HashMapLongObject<Long>();
    private IHprofParserHandler handler;
    private SimpleMonitor.Listener monitor;

    public Pass1Parser(IHprofParserHandler handler, SimpleMonitor.Listener monitor) throws IOException
    {
        this.handler = handler;
        this.monitor = monitor;
    }

    public void read(File file) throws IOException
    {
        in = new PositionInputStream(new BufferedInputStream(new FileInputStream(file)));

        try
        {
            // version & header
            version = readVersionHeader();
            monitor.sendUserMessage(IProgressListener.Severity.INFO, MessageFormat.format(
                            "Heap {0} has HPROF Version {1}", file.getAbsolutePath(), version), null);
            handler.addProperty(IHprofParserHandler.VERSION, version.toString());

            // identifierSize (32 or 64 bit)
            identifierSize = in.readInt();
            if (identifierSize != 4 && identifierSize != 8)
                throw new IOException("Only 32bit and 64bit dumps are supported.");
            handler.addProperty(IHprofParserHandler.IDENTIFIER_SIZE, String.valueOf(identifierSize));

            // creation date
            long date = in.readLong();
            handler.addProperty(IHprofParserHandler.CREATION_DATE, String.valueOf(date));

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
                    throw new IOException(MessageFormat.format("Illegal record length at byte $0", in.position()));

                switch (record)
                {
                    case Constants.Record.STRING_IN_UTF8:
                    {
                        long id = readID();
                        byte[] chars = new byte[(int) (length - identifierSize)];
                        in.readFully(chars);
                        handler.getConstantPool().put(id, new String(chars));
                        break;
                    }
                    case Constants.Record.LOAD_CLASS:
                    {
                        in.skipBytes(4);
                        long classID = readID();
                        in.skipBytes(4);
                        long nameID = readID();

                        String className = getNameFromAddress(nameID).replace('/', '.');
                        class2name.put(classID, className);
                        break;
                    }
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
            length--;

            long id = 0;
            int threadSeq = 0;

            switch (segmentType)
            {
                case Constants.DumpSegment.ROOT_UNKNOWN:
                    id = readID();
                    length -= identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.UNKNOWN);
                    break;
                case Constants.DumpSegment.ROOT_THREAD_OBJECT:
                    id = readID();
                    threadSeq = in.readInt();
                    in.skipBytes(4); // stackSeq
                    length -= identifierSize + 8;
                    threadSeq2id.put(threadSeq, id);
                    handler.addGCRoot(id, 0, GCRootInfo.Type.THREAD_OBJ);
                    break;
                case Constants.DumpSegment.ROOT_JNI_GLOBAL:
                    id = readID();
                    in.skipBytes(identifierSize);
                    length -= 2 * identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.NATIVE_STACK);
                    break;
                case Constants.DumpSegment.ROOT_JNI_LOCAL:
                    id = readID();
                    threadSeq = in.readInt();
                    in.skipBytes(4);
                    length -= identifierSize + 8;
                    handler.addGCRoot(id, threadSeq2id.get(threadSeq), GCRootInfo.Type.NATIVE_LOCAL);
                    break;
                case Constants.DumpSegment.ROOT_JAVA_FRAME:
                    id = readID();
                    threadSeq = in.readInt();
                    in.skipBytes(4);
                    length -= identifierSize + 8;
                    handler.addGCRoot(id, threadSeq2id.get(threadSeq), GCRootInfo.Type.JAVA_LOCAL);
                    break;
                case Constants.DumpSegment.ROOT_NATIVE_STACK:
                    id = readID();
                    threadSeq = in.readInt();
                    length -= identifierSize + 4;
                    handler.addGCRoot(id, threadSeq2id.get(threadSeq), GCRootInfo.Type.NATIVE_STACK);
                    break;
                case Constants.DumpSegment.ROOT_STICKY_CLASS:
                    id = readID();
                    length -= identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.SYSTEM_CLASS);
                    break;
                case Constants.DumpSegment.ROOT_THREAD_BLOCK:
                    id = readID();
                    in.skipBytes(4);
                    length -= identifierSize + 4;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.THREAD_BLOCK);
                    break;
                case Constants.DumpSegment.ROOT_MONITOR_USED:
                    id = readID();
                    length -= identifierSize;
                    handler.addGCRoot(id, 0, GCRootInfo.Type.BUSY_MONITOR);
                    break;
                case Constants.DumpSegment.CLASS_DUMP:
                    length -= readClassDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.INSTANCE_DUMP:
                    length -= readInstanceDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.OBJECT_ARRAY_DUMP:
                    length -= readObjectArrayDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP:
                    length -= readPrimitiveArrayDump(segmentStartPos);
                    break;
                default:
                    throw new IOException(MessageFormat.format("Unrecognized segment type {0} at position {1}",
                                    segmentType, segmentStartPos));
            }

        }

        if (length != 0)
            throw new IOException("Invalid dump record length.");
    }

    private int readClassDump(long segmentStartPos) throws IOException
    {
        long address = readID();
        in.skipBytes(4); // stack trace serial number
        long superClassObjectId = readID();
        long classLoaderObjectId = readID();
        int bytesRead = 3 * identifierSize + 4;

        // skip signers, protection domain, reserved ids (2), instance size
        bytesRead += in.skipBytes(this.identifierSize * 4 + 4);

        // constant pool: u2 ( u2 u1 value )*
        int constantPoolSize = in.readUnsignedShort();
        bytesRead += 2;
        for (int ii = 0; ii < constantPoolSize; ii++)
        {
            bytesRead += in.skipBytes(2); // index
            bytesRead += skipValue(); // value
        }

        // static fields: u2 num ( name ID, u1 type, value)
        int numStaticFields = in.readUnsignedShort();
        bytesRead += 2;
        Field[] statics = new Field[numStaticFields];

        Object[] value = new Object[1];
        for (int ii = 0; ii < numStaticFields; ii++)
        {
            long nameId = readID();
            bytesRead += identifierSize;
            String name = getNameFromAddress(nameId);

            byte type = in.readByte();
            bytesRead++;
            if (version.ordinal() >= Version.JDK12BETA4.ordinal())
                type = getSignatureFromType(type);

            bytesRead += readValue(null, type, value);

            statics[ii] = new Field(name, String.valueOf((char) type), value[0]);
        }

        // instance fields: u2 num ( name ID, u1 type )
        int numInstanceFields = in.readUnsignedShort();
        bytesRead += 2;
        FieldDescriptor[] fields = new FieldDescriptor[numInstanceFields];

        for (int i = 0; i < numInstanceFields; i++)
        {
            long nameId = readID();
            bytesRead += identifierSize;
            String name = getNameFromAddress(nameId);

            byte type = in.readByte();
            bytesRead++;
            if (version.ordinal() >= Version.JDK12BETA4.ordinal())
                type = getSignatureFromType(type);

            fields[i] = new FieldDescriptor(name, String.valueOf((char) type));
        }

        // get name
        String className = (String) class2name.get(address);
        if (className == null)
            className = "unknown-name@0x" + Long.toHexString(address);

        if (className.charAt(0) == '[') // quick check if array at hand
        {
            // fix object class names
            Matcher matcher = PATTERN_OBJ_ARRAY.matcher(className);
            if (matcher.matches())
            {
                int l = matcher.group(1).length();
                className = matcher.group(2);
                for (int ii = 0; ii < l; ii++)
                    className += "[]";
            }

            // primitive arrays
            matcher = PATTERN_PRIMITIVE_ARRAY.matcher(className);
            if (matcher.matches())
            {
                int count = matcher.group(1).length() - 1;
                className = "unknown[]";

                char signature = matcher.group(2).charAt(0);
                for (int ii = 0; ii < IPrimitiveArray.SIGNATURES.length; ii++)
                {
                    if (IPrimitiveArray.SIGNATURES[ii] == (byte) signature)
                    {
                        className = IPrimitiveArray.TYPE[ii];
                        break;
                    }
                }

                for (int ii = 0; ii < count; ii++)
                    className += "[]";
            }
        }

        ClassImpl clazz = new ClassImpl(address, className, superClassObjectId, classLoaderObjectId, statics, fields);
        handler.addClass(clazz, segmentStartPos);

        return bytesRead;
    }

    private int readInstanceDump(long segmentStartPos) throws IOException
    {
        long id = readID();
        handler.reportInstance(id, segmentStartPos);
        if (in.skipBytes(identifierSize + 4) != identifierSize + 4)
            throw new IOException();

        int len = in.readInt();
        if (in.skipBytes(len) != len)
            throw new IOException();
        return 2 * identifierSize + 8 + len;
    }

    private int readObjectArrayDump(long segmentStartPos) throws IOException
    {
        long address = readID();
        handler.reportInstance(address, segmentStartPos);

        in.skipBytes(4);
        int size = in.readInt();
        int bytesRead = identifierSize + 8;

        long arrayClassObjectID = readID();
        bytesRead += identifierSize;

        // check if class needs to be created
        IClass arrayType = handler.lookupClass(arrayClassObjectID);
        if (arrayType == null)
            handler.reportRequiredObjectArray(arrayClassObjectID);

        bytesRead += in.skipBytes(size * identifierSize);
        return bytesRead;
    }

    private int readPrimitiveArrayDump(long segmentStartPos) throws IOException
    {
        long address = readID();
        handler.reportInstance(address, segmentStartPos);

        in.skipBytes(4);
        int size = in.readInt();
        int bytesRead = identifierSize + 8;

        byte elementType = in.readByte();
        bytesRead++;

        if ((elementType < IPrimitiveArray.Type.BOOLEAN) || (elementType > IPrimitiveArray.Type.LONG))
            throw new IOException("Illegal primitive object array type");

        // check if class needs to be created
        String name = IPrimitiveArray.TYPE[(int) elementType];
        IClass clazz = handler.lookupClassByName(name, true);
        if (clazz == null)
            handler.reportRequiredPrimitiveArray((int) elementType);

        int elementSize = IPrimitiveArray.ELEMENT_SIZE[(int) elementType];
        bytesRead += in.skipBytes(elementSize * size);
        return bytesRead;
    }

    private byte getSignatureFromType(byte type) throws IOException
    {
        if (type == 2)
            return (byte) 'L';
        if (type < 4 || type > 11)
            throw new IOException("Unknown type of " + type);
        return IPrimitiveArray.SIGNATURES[type];
    }

    private String getNameFromAddress(long address) throws IOException
    {
        if (address == 0L)
            return "";

        String result = handler.getConstantPool().get(address);
        return result == null ? "Unresolved Name 0x" + Long.toHexString(address) : result;
    }

}

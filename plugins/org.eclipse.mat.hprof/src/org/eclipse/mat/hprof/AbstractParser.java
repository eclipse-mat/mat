package org.eclipse.mat.hprof;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.ObjectReference;

// Hprof binary format as defined here:
// https://heap-snapshot.dev.java.net/files/documents/4282/31543/hprof-binary-format.html

/* package */abstract class AbstractParser
{
    enum Version
    {
        JDK12BETA3("JAVA PROFILE 1.0"), //
        JDK12BETA4("JAVA PROFILE 1.0.1"), //
        JDK6("JAVA PROFILE 1.0.2");

        private String label;

        private Version(String label)
        {
            this.label = label;
        }

        public static final Version byLabel(String label)
        {
            for (Version v : Version.values())
            {
                if (v.label.equals(label))
                    return v;
            }
            return null;
        }

        public String getLabel()
        {
            return label;
        }
    }

    interface Constants
    {
        interface Record
        {
            int STRING_IN_UTF8 = 0x01;
            int LOAD_CLASS = 0x02;
            int UNLOAD_CLASS = 0x03;
            int STACK_FRAME = 0x04;
            int STACK_TRACE = 0x05;
            int ALLOC_SITES = 0x06;
            int HEAP_SUMMARY = 0x07;
            int START_THREAD = 0x0a;
            int END_THREAD = 0x0b;
            int HEAP_DUMP = 0x0c;
            int HEAP_DUMP_SEGMENT = 0x1c;
            int HEAP_DUMP_END = 0x2c;
            int CPU_SAMPLES = 0x0d;
            int CONTROL_SETTINGS = 0x0e;
        }

        interface DumpSegment
        {
            int ROOT_UNKNOWN = 0xff;
            int ROOT_JNI_GLOBAL = 0x01;
            int ROOT_JNI_LOCAL = 0x02;
            int ROOT_JAVA_FRAME = 0x03;
            int ROOT_NATIVE_STACK = 0x04;
            int ROOT_STICKY_CLASS = 0x05;
            int ROOT_THREAD_BLOCK = 0x06;
            int ROOT_MONITOR_USED = 0x07;
            int ROOT_THREAD_OBJECT = 0x08;
            int CLASS_DUMP = 0x20;
            int INSTANCE_DUMP = 0x21;
            int OBJECT_ARRAY_DUMP = 0x22;
            int PRIMITIVE_ARRAY_DUMP = 0x23;
        }
    }

    protected PositionInputStream in;
    protected Version version;
    protected int identifierSize;

    /* package */AbstractParser()
    {}

    protected Version readVersionHeader() throws IOException
    {
        StringBuilder version = new StringBuilder();

        int bytesRead = 0;
        while (bytesRead < 20)
        {
            byte b = in.readByte();
            bytesRead++;

            if (b != 0)
            {
                version.append((char) b);
            }
            else
            {
                Version answer = Version.byLabel(version.toString());
                if (answer == null)
                    throw new IOException(MessageFormat.format("Unknown HPROF Version ({0})", version.toString()));
                if (answer == Version.JDK12BETA3) // not supported by MAT
                    throw new IOException(MessageFormat.format("Unsupported HPROF Version {0}", answer.getLabel()));
                return answer;
            }
        }

        throw new IOException("Invalid HPROF file header.");
    }

    protected long readUnsignedInt() throws IOException
    {
        return (0x0FFFFFFFFL & (long) in.readInt());
    }

    protected long readID() throws IOException
    {
        return identifierSize == 4 ? (0x0FFFFFFFFL & (long) in.readInt()) : in.readLong();
    }

    protected int readValue(ISnapshot snapshot, Object[] result) throws IOException
    {
        byte type = in.readByte();
        return 1 + readValue(snapshot, type, result);
    }

    protected int readValue(ISnapshot snapshot, byte type, Object[] result) throws IOException
    {
        switch (type)
        {
            case 2:
            case '[':
            case 'L':
                long id = readID();
                result[0] = id == 0 ? null : new ObjectReference(snapshot, id);
                return identifierSize;
            case 4:
            case 'Z':
                result[0] = in.readByte() != 0;
                return 1;
            case 8:
            case 'B':
                result[0] = in.readByte();
                return 1;
            case 9:
            case 'S':
                result[0] = in.readShort();
                return 2;
            case 5:
            case 'C':
                result[0] = in.readChar();
                return 2;
            case 10:
            case 'I':
                result[0] = in.readInt();
                return 4;
            case 6:
            case 'F':
                result[0] = in.readFloat();
                return 4;
            case 11:
            case 'J':
                result[0] = in.readLong();
                return 8;
            case 7:
            case 'D':
                result[0] = in.readDouble();
                return 8;
            default:
                throw new IOException("Bad Signature:  " + type);
        }
    }

    protected int skipValue() throws IOException
    {
        byte type = in.readByte();
        return 1 + skipValue(type);
    }

    protected int skipValue(byte type) throws IOException
    {
        int size = 0;

        switch (type)
        {
            case 2:
            case '[':
            case 'L':
                size = identifierSize;
                break;
            case 4:
            case 'Z':
            case 8:
            case 'B':
                size = 1;
                break;
            case 9:
            case 'S':
            case 5:
            case 'C':
                size = 2;
                break;
            case 10:
            case 'I':
            case 6:
            case 'F':
                size = 4;
                break;
            case 11:
            case 'J':
            case 7:
            case 'D':
                size = 8;
                break;
            default:
                throw new IOException("Bad Signature:  " + type);

        }

        if (in.skipBytes(size) != size)
            throw new IOException();
        return size;
    }

}

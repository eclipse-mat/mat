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

import java.io.DataInput;
import java.io.IOException;

import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;


public class HprofBasics
{

    public final static int MAGIC_NUMBER = 0x4a415641;
    // That's "JAVA", the first part of "JAVA PROFILE ..."
    final static String[] VERSIONS = { " PROFILE 1.0\0", " PROFILE 1.0.1\0", " PROFILE 1.0.2\0", };

    final static int VERSION_JDK12BETA3 = 0;
    final static int VERSION_JDK12BETA4 = 1;
    final static int VERSION_JDK6 = 2;

    // These version numbers are indices into VERSIONS. The instance data
    // member version is set to one of these, and it drives decisions when
    // reading the file.
    //
    // Version 1.0.1 added HPROF_GC_PRIM_ARRAY_DUMP, which requires no
    // version-sensitive parsing.
    //
    // Version 1.0.1 changed the type of a constant pool entry from a signature
    // to a typecode.
    //
    // Version 1.0.2 added HPROF_HEAP_DUMP_SEGMENT and HPROF_HEAP_DUMP_END
    // to allow a large heap to be dumped as a sequence of heap dump segments.
    //
    // The HPROF agent in J2SE 1.2 through to 5.0 generate a version 1.0.1
    // file. In Java SE 6.0 the version is either 1.0.1 or 1.0.2 depending on
    // the size of the heap (normally it will be 1.0.1 but for multi-GB
    // heaps the heap dump will not fit in a HPROF_HEAP_DUMP record so the
    // dump is generated as version 1.0.2).

    //
    // Record types:
    //
    static final int HPROF_UTF8 = 0x01;
    static final int HPROF_LOAD_CLASS = 0x02;
    static final int HPROF_UNLOAD_CLASS = 0x03;
    static final int HPROF_FRAME = 0x04;
    static final int HPROF_TRACE = 0x05;
    static final int HPROF_ALLOC_SITES = 0x06;
    static final int HPROF_HEAP_SUMMARY = 0x07;

    static final int HPROF_START_THREAD = 0x0a;
    static final int HPROF_END_THREAD = 0x0b;

    static final int HPROF_HEAP_DUMP = 0x0c;

    static final int HPROF_CPU_SAMPLES = 0x0d;
    static final int HPROF_CONTROL_SETTINGS = 0x0e;
    static final int HPROF_LOCKSTATS_WAIT_TIME = 0x10;
    static final int HPROF_LOCKSTATS_HOLD_TIME = 0x11;

    static final int HPROF_GC_ROOT_UNKNOWN = 0xff;
    static final int HPROF_GC_ROOT_JNI_GLOBAL = 0x01;
    static final int HPROF_GC_ROOT_JNI_LOCAL = 0x02;
    static final int HPROF_GC_ROOT_JAVA_FRAME = 0x03;
    static final int HPROF_GC_ROOT_NATIVE_STACK = 0x04;
    static final int HPROF_GC_ROOT_STICKY_CLASS = 0x05;
    static final int HPROF_GC_ROOT_THREAD_BLOCK = 0x06;
    static final int HPROF_GC_ROOT_MONITOR_USED = 0x07;
    static final int HPROF_GC_ROOT_THREAD_OBJ = 0x08;

    static final int HPROF_GC_CLASS_DUMP = 0x20;
    static final int HPROF_GC_INSTANCE_DUMP = 0x21;
    static final int HPROF_GC_OBJ_ARRAY_DUMP = 0x22;
    static final int HPROF_GC_PRIM_ARRAY_DUMP = 0x23;

    static final int HPROF_HEAP_DUMP_SEGMENT = 0x1c;
    static final int HPROF_HEAP_DUMP_END = 0x2c;

    final static int T_CLASS = 2;

    int identifierSize;
    int version; // The version of .hprof being read
    protected PositionInputStream in;

    /* package */HprofBasics()
    {}

    public HprofBasics(int idSize, int version)
    {
        this.identifierSize = idSize;
        this.version = version;
    }

    byte signatureFromTypeId(byte typeId) throws IOException
    {
        if (typeId == T_CLASS) { return (byte) 'L'; }
        // handle primitive arrays
        if ((typeId < 4) || (typeId > 11)) { throw new IOException("Invalid type id of " + typeId); }
        return IPrimitiveArray.SIGNATURES[typeId];
    }

    long readID(DataInput in) throws IOException
    {
        return (identifierSize == 4) ? (0x0FFFFFFFFL & (long) in.readInt()) : in.readLong();
    }

    int readValue(ISnapshot snapshot, DataInput in, Object[] resultArr) throws IOException
    {
        byte type = in.readByte();
        return 1 + readValueForType(snapshot, in, type, resultArr);
    }

    int readValueForType(ISnapshot snapshot, DataInput in, byte type, Object[] resultArr) throws IOException
    {
        if (version >= VERSION_JDK12BETA4)
        {
            type = signatureFromTypeId(type);
        }
        return readValueForTypeSignature(snapshot, in, type, resultArr);
    }

    int readValueForTypeSignature(ISnapshot snapshot, DataInput in, byte type, Object[] resultArr) throws IOException
    {
        switch (type)
        {
            case '[':
            case 'L':
            {
                long id = readID(in);
                if (resultArr != null)
                {
                    resultArr[0] = id == 0 ? null : new ObjectReference(snapshot, id);
                }
                return identifierSize;
            }
            case 'Z':
            {
                int b = in.readByte();
                if (resultArr != null)
                {
                    resultArr[0] = Boolean.valueOf(b != 0);
                }
                return 1;
            }
            case 'B':
            {
                byte b = in.readByte();
                if (resultArr != null)
                {
                    resultArr[0] = Byte.valueOf(b);
                }
                return 1;
            }
            case 'S':
            {
                short s = in.readShort();
                if (resultArr != null)
                {
                    resultArr[0] = Short.valueOf(s);
                }
                return 2;
            }
            case 'C':
            {
                char ch = in.readChar();
                if (resultArr != null)
                {
                    resultArr[0] = Character.valueOf(ch);
                }
                return 2;
            }
            case 'I':
            {
                int val = in.readInt();
                if (resultArr != null)
                {
                    resultArr[0] = Integer.valueOf(val);
                }
                return 4;
            }
            case 'J':
            {
                long val = in.readLong();
                if (resultArr != null)
                {
                    resultArr[0] = Long.valueOf(val);
                }
                return 8;
            }
            case 'F':
            {
                float val = in.readFloat();
                if (resultArr != null)
                {
                    resultArr[0] = Float.valueOf(val);
                }
                return 4;
            }
            case 'D':
            {
                double val = in.readDouble();
                if (resultArr != null)
                {
                    resultArr[0] = Double.valueOf(val);
                }
                return 8;
            }
            default:
            {
                throw new IOException("Bad value signature:  " + type);
            }
        }
    }

    int skipValueForTypeSignature(DataInput in, byte type) throws IOException
    {
        int size = 0;

        switch (type)
        {
            case '[':
            case 'L':
                size = identifierSize;
                break;
            case 'Z':
            case 'B':
                size = 1;
                break;
            case 'S':
            case 'C':
                size = 2;
                break;
            case 'I':
            case 'F':
                size = 4;
                break;
            case 'J':
            case 'D':
                size = 8;
                break;
            default:
            {
                throw new IOException("Bad value signature:  " + type);
            }
        }

        if (in.skipBytes(size) != size)
            throw new IOException();
        return size;
    }

    public int getVersion()
    {
        return version;
    }

    protected long readID() throws IOException
    {
        return (identifierSize == 4) ? (0x0FFFFFFFFL & (long) in.readInt()) : in.readLong();
    }

    protected long readUnsignedInt() throws IOException
    {
        return (0x0FFFFFFFFL & (long) in.readInt());
    }

}

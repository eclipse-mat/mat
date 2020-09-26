/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others.
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

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.hprof.ui.HprofPreferences.HprofStrictness;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor.Listener;

// Hprof binary format as defined here:
// https://heap-snapshot.dev.java.net/files/documents/4282/31543/hprof-binary-format.html

/* package */abstract class AbstractParser
{
    /* package */enum Version
    {
        JDK12BETA3("JAVA PROFILE 1.0"), //$NON-NLS-1$
        JDK12BETA4("JAVA PROFILE 1.0.1"), //$NON-NLS-1$
        JDK6("JAVA PROFILE 1.0.2");//$NON-NLS-1$

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

    protected Version version;
    // The size of identifiers in the dump file
    protected int idSize;
    protected final HprofPreferences.HprofStrictness strictnessPreference;

    /* package */AbstractParser(HprofPreferences.HprofStrictness strictnessPreference)
    {
        this.strictnessPreference = strictnessPreference;
    }

    /* protected */static Version readVersion(IPositionInputStream in) throws IOException
    {
        StringBuilder version = new StringBuilder();

        int bytesRead = 0;
        while (bytesRead < 20)
        {
            byte b = (byte) in.read();
            bytesRead++;

            if (b != 0)
            {
                version.append((char) b);
            }
            else
            {
                Version answer = Version.byLabel(version.toString());
                if (answer == null)
                {
                    if (bytesRead <= 13) // did not read "JAVA PROFILE "
                        throw new IOException(Messages.AbstractParser_Error_NotHeapDump);
                    else
                        throw new IOException(MessageUtil.format(Messages.AbstractParser_Error_UnknownHPROFVersion,
                                        version.toString()));
                }

                if (answer == Version.JDK12BETA3) // not supported by MAT
                    throw new IOException(MessageUtil.format(Messages.AbstractParser_Error_UnsupportedHPROFVersion,
                                    answer.getLabel()));
                return answer;
            }
        }

        throw new IOException(Messages.AbstractParser_Error_InvalidHPROFHeader);
    }

    /*
     * Used for content describers, don't throw exceptions.
     */
    /* protected */static Version readVersion(InputStream in) throws IOException
    {
        StringBuilder version = new StringBuilder();

        int bytesRead = 0;
        while (bytesRead < 20)
        {
            byte b = (byte) in.read();
            bytesRead++;

            if (b != 0)
            {
                version.append((char) b);
            }
            else
            {
                Version answer = Version.byLabel(version.toString());
                if (answer == null)
                {
                    return null;
                }

                if (answer == Version.JDK12BETA3) // not supported by MAT
                    return null;
                return answer;
            }
        }

        return null;
    }

    protected Object readValue(IPositionInputStream in, ISnapshot snapshot) throws IOException
    {
        byte type = in.readByte();
        return readValue(in, snapshot, type);
    }

    protected Object readValue(IPositionInputStream in, ISnapshot snapshot, int type) throws IOException
    {
        switch (type)
        {
            case IObject.Type.OBJECT:
                long id = in.readID(idSize);
                return id == 0 ? null : new ObjectReference(snapshot, id);
            case IObject.Type.BOOLEAN:
                return in.readByte() != 0;
            case IObject.Type.CHAR:
                return in.readChar();
            case IObject.Type.FLOAT:
                return in.readFloat();
            case IObject.Type.DOUBLE:
                return in.readDouble();
            case IObject.Type.BYTE:
                return in.readByte();
            case IObject.Type.SHORT:
                return in.readShort();
            case IObject.Type.INT:
                return in.readInt();
            case IObject.Type.LONG:
                return in.readLong();
            default:
                throw new IOException(MessageUtil.format(Messages.AbstractParser_Error_IllegalType, type, in.position()));
        }
    }

    public static Object readValue(IPositionInputStream in, ISnapshot snapshot, int type, int idSize) throws IOException
    {
        switch (type)
        {
            case IObject.Type.OBJECT:
                long id = in.readID(idSize);
                return id == 0 ? null : new ObjectReference(snapshot, id);
            case IObject.Type.BOOLEAN:
                return in.readByte() != 0;
            case IObject.Type.CHAR:
                return in.readChar();
            case IObject.Type.FLOAT:
                return in.readFloat();
            case IObject.Type.DOUBLE:
                return in.readDouble();
            case IObject.Type.BYTE:
                return in.readByte();
            case IObject.Type.SHORT:
                return in.readShort();
            case IObject.Type.INT:
                return in.readInt();
            case IObject.Type.LONG:
                return in.readLong();
            default:
                throw new IOException(MessageUtil.format(Messages.AbstractParser_Error_IllegalType, type, in.position()));
        }
    }

    protected void skipValue(IPositionInputStream in) throws IOException
    {
        byte type = in.readByte();
        skipValue(in, type);
    }

    protected void skipValue(IPositionInputStream in, int type) throws IOException
    {
        if (type == IObject.Type.OBJECT)
            in.skipBytes(idSize);
        else
            in.skipBytes(IPrimitiveArray.ELEMENT_SIZE[type]);
    }

    /**
     * Usually the HPROF file contains exactly one heap dump. However, when
     * acquiring heap dumps via the legacy HPROF agent, the dump file can
     * possibly contain multiple heap dumps. Currently there is no API and no UI
     * to determine which dump to use. As this happens very rarely, we decided
     * to go with the following mechanism: use only the first dump unless the
     * user provides a dump number via environment variable. Once the dump has
     * been parsed, the same dump is reopened regardless of the environment
     * variable.
     * MAT_HPROF_DUMP_NR is a 0 offset number, or direct id
     * The returned value is an 0 offset number or 1 offset id, e.g. #1
     */
    protected String determineDumpNumber()
    {
        String dumpNr = System.getProperty("MAT_HPROF_DUMP_NR"); //$NON-NLS-1$
        return dumpNr; 
    }

    protected String dumpIdentifier(int n)
    {
        return "#" + (n+1); //$NON-NLS-1$
    }

    protected boolean dumpMatches(int n, String match)
    {
        if (match == null && n == 0)
            return true;
        if (dumpIdentifier(n).equals(match))
            return true;
        try
        {
            int nm = Integer.parseInt(match);
            return nm == n;
        }
        catch (NumberFormatException e)
        {
        }
        return false;
    }
 
    /**
     * It seems the HPROF file writes the length field as an unsigned int.
     */
    private final static long MAX_UNSIGNED_4BYTE_INT = 4294967296L;

    /**
     * It seems the HPROF spec only allows 4 bytes for record length, so a
     * record length greater than 4GB will be overflowed and will be useless and
     * throw off the rest of the processing. There's no good way to tell the
     * overflow has occurred but if the strictness preference has been set to
     * permissive, we can check the most common case of a heap dump record that
     * should run to the end of the file.
     * 
     * @param fileSize
     *            The total file size.
     * @param curPos
     *            The current position of the input stream.
     * @param record
     *            The record identifier.
     * @param length
     *            The length read from the record.
     * @param monitor
     *            The listener to send any warnings.
     * @return The updated length or the original length if no update is made.
     */
    protected long updateLengthIfNecessary(long fileSize, long curPos, int record, long length, Listener monitor)
    {
        // Sometimes the HPROF file is truncated during the write and the
        // length field is never updated from 0. Presume it goes to the end .
        if (length == 0 && (
                        strictnessPreference == HprofStrictness.STRICTNESS_WARNING ||
                        strictnessPreference == HprofStrictness.STRICTNESS_PERMISSIVE))
        {
            long length1 = fileSize - curPos - 9;
            if (length1 > 0)
            {
                monitor.sendUserMessage(Severity.WARNING, MessageUtil.format(
                                Messages.AbstractParser_GuessingRecordLength,
                                Integer.toHexString(record),
                                Long.toHexString(curPos), length, length1), null);
                length = length1;
            }
        }
        // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=404679
        //
        // We do this check no matter the strictness preference. Since we're
        // checking based on an exact overflow calculation and we're only
        // inferring the heap dump record if it goes all the way to the end of
        // the file, it seems this can be "safely" done all the time.
        if (
        // strictnessPreference == HprofStrictness.STRICTNESS_PERMISSIVE &&
        record == Constants.Record.HEAP_DUMP)
        {
            long bytesLeft = fileSize - curPos - 9;
            if (bytesLeft >= MAX_UNSIGNED_4BYTE_INT)
            {
                // We can be more confident in this guess by assuming that this
                // record goes to the end of the file, so we can actually
                // emulate the overflow and see if that matches up.
                //
                if ((bytesLeft - length) % MAX_UNSIGNED_4BYTE_INT == 0)
                {
                    monitor.sendUserMessage(Severity.WARNING, MessageUtil.format(
                                    Messages.Pass1Parser_GuessingLengthOverflow, Integer.toHexString(record),
                                    Long.toHexString(curPos), length, bytesLeft), null);
                    length = bytesLeft;
                }
            }
        }
        return length;
    }

}

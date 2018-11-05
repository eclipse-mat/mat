/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson/IBM Corporation - initial API and implementation
 *******************************************************************************/

/**
 * Create a HPROF format file from a snapshot, whatever the original dump format.
 *
 * Problems with current snapshot API:
 * finding thread to GCroots linkage
 * - have to presume a ROOT_THREAD_OBJECT and getOutboundReferences and use
 *   ThreadToLocalReference
 * parsing stackframes to classes, methods
 * - have to parse out string format
 * converting class name in stackframe to class
 * - could be multiple classes with the same name, information loss
 * converting object and stack frame to GC root with type
 * - If JNI local reference and Java local reference to same object in different frame, could get the type swapped.
 *
 */

package org.eclipse.mat.hprof;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.hprof.AbstractParser.Constants;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.GCRootInfo.Type;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SilentProgressListener;

@CommandName("export_hprof")
@Icon("/icons/export_hprof.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/exportdump.html")
public class ExportHprof implements IQuery
{
    /** A dummy stack trace */
    private static final int UNKNOWN_STACK_TRACE_SERIAL = 1;

    /** No stack frame available */
    private static final int UNKNOWN_STACK_FRAME_SERIAL = -1;

    private static final Charset UTF8 = Charset.forName("UTF-8"); //$NON-NLS-1$

    @Argument
    public ISnapshot snapshot;

    @Argument(advice = Advice.SAVE)
    public File output;

    public enum RedactType
    {
        NONE("none"), //$NON-NLS-1$
        NAMES("names"), //$NON-NLS-1$
        BASIC("basic"), //$NON-NLS-1$
        FULL("full"); //$NON-NLS-1$
        String type;

        private RedactType(String s)
        {
            type = s;
        }
    }

    @Argument(isMandatory = false)
    public RedactType redact = RedactType.NONE;

    @Argument(isMandatory = false, advice = Advice.SAVE, flag = "map")
    public File mapFile;

    @Argument(isMandatory = false, advice = Advice.CLASS_NAME_PATTERN, flag = "skip")
    public Pattern skipPattern = Pattern.compile("java\\..*|boolean|byte|char|short|int|long|float|double|void"); //$NON-NLS-1$

    @Argument(isMandatory = false, advice = Advice.CLASS_NAME_PATTERN, flag = "avoid")
    public Pattern avoidPattern = Pattern.compile(Messages.ExportHprof_AvoidExample);

    @Argument
    public boolean undo;

    @Argument(flag = Argument.UNFLAGGED, isMandatory = false)
    public IHeapObjectArgument objects;

    /** How big a heap dump segment can grow before it needs to be split */
    private static final long MAX_SEGMENT = 0xffffffffL;

    /** Strings to HPROF ID */
    HashMap<String, Integer> stringToID = new HashMap<String, Integer>();
    int nextStringID = 1;

    /** Thread object ID to HPROF thread serial number */
    HashMap<Integer, Integer> threadToSerial = new HashMap<Integer, Integer>();

    /** Thread object ID to HPROF stack serial number */
    HashMap<Integer, Integer> threadToStack = new HashMap<Integer, Integer>();

    /** Whether to include this object in the dump */
    BitField include;

    /** Keep count for a final result */
    int totalClasses;

    /** Keep count for a final result */
    int totalObjects;

    /** Keep count for a final result */
    int totalRoots;

    /** Keep count for a final result */
    int totalClassloaders;

    /** Keep count for a final result */
    long totalBytes;

    /** keep track for totals */
    SetInt classloaders = new SetInt();

    /**
     * Stream which discards the output.
     */
    static class NullStream extends OutputStream
    {

        @Override
        public void write(int b) throws IOException
        {
        }

    }

    /**
     * DataOutputStream which can have a long size.
     */
    static class DataOutputStream3 implements DataOutput, Closeable
    {
        final DataOutputStream2 out;
        protected long written = 0;

        public DataOutputStream3(OutputStream out)
        {
            this.out = new DataOutputStream2(out);
        }

        public void write(int b) throws IOException
        {
            out.write(b);
            written += 1;
        }

        public void write(byte[] b) throws IOException
        {
            out.write(b);
            written += b.length;
        }

        public void write(byte[] b, int off, int len) throws IOException
        {
            out.write(b, off, len);
            written += len;
        }

        public void writeBoolean(boolean v) throws IOException
        {
            out.writeBoolean(v);
            written += 1;
        }

        public void writeByte(int v) throws IOException
        {
            out.writeByte(v);
            written += 1;
        }

        public void writeShort(int v) throws IOException
        {
            out.writeShort(v);
            written += 2;
        }

        public void writeChar(int v) throws IOException
        {
            out.writeChar(v);
            written += 2;
        }

        public void writeInt(int v) throws IOException
        {
            out.writeInt(v);
            written += 4;
        }

        public void writeLong(long v) throws IOException
        {
            out.writeLong(v);
            written += 8;
        }

        public void writeFloat(float v) throws IOException
        {
            out.writeFloat(v);
            written += 4;
        }

        public void writeDouble(double v) throws IOException
        {
            out.writeDouble(v);
            written += 8;
        }

        public void writeBytes(String s) throws IOException
        {
            out.writeBytes(s);
            written += s.length();
        }

        public void writeChars(String s) throws IOException
        {
            out.writeChars(s);
            written += 2 * s.length();
        }

        public void writeUTF(String s) throws IOException
        {
            out.reset();
            int m1 = out.size();
            out.writeUTF(s);
            int m2 = out.size();
            written += m2 - m1;
        }

        public long size()
        {
            return written;
        }

        public void close() throws IOException
        {
            out.close();
        }
    }

    /**
     * DataOutputStream which can have bytes written reset
     */
    static class DataOutputStream2 extends DataOutputStream {

        public DataOutputStream2(OutputStream out)
        {
            super(out);
        }
        public void reset()
        {
            written = 0;
        }
    }

    /** The size of the ID fields in the HPROF file */
    int idsize = 8;

    /** Progress monitor work per class for dumping objects */
    private static final int WORK_OBJECT = 3;

    Remap remap;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int ct = snapshot.getSnapshotInfo().getNumberOfClasses();

        /*
         * Stages and work items per class
         * load classes     1 11%
         * prepare classes  1 22%
         * dump classes     1 33%
         * prepare objects  3 67%
         * dump objects     3 100%
         * Won't work so well for objects argument.
         */
        int ct2 = initObjs();
        if (ct2 <= 0)
            ct2 = ct;
        int totalWork = 3 * ct + 2 * WORK_OBJECT * ct2;
        listener.beginTask(MessageUtil.format(Messages.ExportHprof_ExportTo, output.getName()), totalWork);

        remap = new Remap(skipPattern, avoidPattern, redact == RedactType.NAMES, undo || mapFile == null);
        remap.loadMapping(mapFile, undo);

        long startTime;
        DataOutputStream3 os = new DataOutputStream3(new BufferedOutputStream(new FileOutputStream(output), 1024 * 64));
        try
        {
            os.writeBytes(AbstractParser.Version.JDK6.getLabel()+"\0"); //$NON-NLS-1$
            idsize = snapshot.getSnapshotInfo().getIdentifierSize();
            os.writeInt(idsize);
            startTime = System.currentTimeMillis();
            os.writeLong(startTime);

            // os.writeByte(Constants.Record.HEAP_SUMMARY);

            DataOutputStream3 os2 = new DataOutputStream3(new NullStream());

            loadClasses(os, os2, startTime, listener);

            // Keep track of new strings
            int firstId = nextStringID;
            long mark1 = os2.size();
            listener.subTask(Messages.ExportHprof_PrepareClasses);
            dumpClasses(os2, listener);
            listener.subTask(Messages.ExportHprof_PrepareGCRoots);
            gcRoots(os2);
            dumpThreadRoots(os2);
            long mark2 = os2.size();
            long sizeseg1 = mark2 - mark1;

            listener.subTask(Messages.ExportHprof_PrepareThreadStacks);
            // Find all the strings
            dumpThreadStacks(os2, startTime);

            // Write out new Strings from the classes etc.
            listener.subTask(Messages.ExportHprof_DumpStrings);
            for (Map.Entry<String, Integer> e : stringToID.entrySet())
            {
                String ss = e.getKey();
                int id = e.getValue();
                if (id < firstId)
                    continue;
                writeStringUTF(os, os2, startTime, ss, id);
            }

            listener.subTask(Messages.ExportHprof_DumpThreadStacks);
            dumpThreadStacks(os, startTime);

            int segnum = 1;
            long seg1Start = os.size();
            os.writeByte(Constants.Record.HEAP_DUMP_SEGMENT);
            os.writeInt((int) (System.currentTimeMillis() - startTime));
            os.writeInt((int)sizeseg1);

            long markseg1a = os.size();
            listener.subTask(MessageUtil.format(Messages.ExportHprof_DumpClasses, segnum));
            dumpClasses(os, listener);
            listener.subTask(MessageUtil.format(Messages.ExportHprof_DumpGCRoots, segnum));
            gcRoots(os);
            dumpThreadRoots(os);
            long markseg1b = os.size();
            long sizeseg1_a = markseg1b - markseg1a;

            if (sizeseg1_a != sizeseg1)
            {
                listener.sendUserMessage(IProgressListener.Severity.WARNING, 
                                MessageUtil.format(Messages.ExportHprof_SegmentSizeMismatch, segnum, sizeseg1, sizeseg1_a, Long.toHexString(seg1Start)), null);
            }

            /*
             * Possibly multiple segments needed for objects
             */
            int st = 0;
            do
            {
                ++segnum;

                DataOutputStream3 os3 = new DataOutputStream3(new NullStream());
                long m1 = os2.size();
                listener.subTask(MessageUtil.format(Messages.ExportHprof_PrepareObjects, segnum));
                int end = dumpObjects(os2, os3, st, Integer.MAX_VALUE, listener);
                long m2 = os2.size();
                long s2 = m2 - m1;
                os3.close();

                long sizel = s2;

                long segStart = os.size();
                if (sizel > 0xffffffffL)
                {
                    // Too big, but carry on
                    listener.sendUserMessage(IProgressListener.Severity.WARNING,
                                    MessageUtil.format(Messages.ExportHprof_SegmentTooLong, segnum, Long.toHexString(segStart), sizel), null);
                }

                os.writeByte(Constants.Record.HEAP_DUMP_SEGMENT);
                os.writeInt((int) (System.currentTimeMillis() - startTime));
                os.writeInt((int)sizel);

                long checkmark1 = os.size();
                listener.subTask(MessageUtil.format(Messages.ExportHprof_DumpObjects, segnum));
                st = dumpObjects(os, os2, st, end, listener);
                long checkmark2 = os.size();
                long size2 = checkmark2 - checkmark1;
                if (size2 != sizel)
                {
                    listener.sendUserMessage(IProgressListener.Severity.WARNING,
                                    MessageUtil.format(Messages.ExportHprof_SegmentSizeMismatch, segnum, sizel, size2, Long.toHexString(segStart)), null);
                }
            } while (st > 0);

            os.writeByte(Constants.Record.HEAP_DUMP_END);
            os.writeInt((int) (System.currentTimeMillis() - startTime));
            os.writeInt(0);

            os2.close();
        }
        finally
        {
            os.close();
        }

        String comments = MessageUtil.format(Messages.ExportHprof_RemapProperties, output.getName(), new File(snapshot.getSnapshotInfo().getPath()).getName());
        remap.saveMapping(mapFile, undo, comments);


        listener.done();

        /*
         * Report the result as a heap dump overview
         */
        int nclasses = totalClasses / 2;
        int nobjects = totalObjects / 2;
        int nroots = totalRoots / 2;
        int nclassloaders = totalClassloaders / 2;
        long nused = totalBytes / 2;

        SnapshotQuery sq = SnapshotQuery.lookup("heap_dump_overview", snapshot); //$NON-NLS-1$
        String name = output.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0)
        {
            name = name.substring(0, dot);
        }
        String prefix = (new File(output.getParentFile(), name)).getPath();
        SnapshotInfo si = new SnapshotInfo(output.getPath(), prefix, null, idsize, new Date(startTime), nobjects, nroots, nclasses, nclassloaders, nused);
        String format = (new HprofContentDescriber()).getSupportedOptions()[0].getLocalName();
        si.setProperty("$heapFormat", format); //$NON-NLS-1$
        sq.setArgument("info", si); //$NON-NLS-1$
        IResult ret = sq.execute(new SilentProgressListener(listener));
        return ret;
    }

    /**
     * Include an object in the output dump?
     * @param obj
     * @return
     */
    boolean includeObject(int obj)
    {
        return include == null || include.get(obj);
    }

    /**
     * Set up the tests for whether to include objects.
     */
    int initObjs()
    {
        int n = 0;
        if (objects != null)
        {
            include = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
            for (int ia[] : objects)
            {
                ++n;
                for (int i : ia)
                {
                    include.set(i);
                }
            }
        }
        return n;
    }

    /**
     * Generate a dummy stack trace for when objects/classes
     * etc. were allocated. Needed for jhat.
     */
    private int dummyStackTrace(DataOutput os, int dummyThread, long startTime) throws IOException
    {
        os.writeByte(Constants.Record.STACK_TRACE);
        os.writeInt((int) (System.currentTimeMillis() - startTime));
        os.writeInt(3 * 4);
        os.writeInt(UNKNOWN_STACK_TRACE_SERIAL);
        os.writeInt(dummyThread);
        os.writeInt(0); // No frames
        return UNKNOWN_STACK_TRACE_SERIAL;
    }

    /**
     * Dump the GC roots
     *
     * @param os
     * @throws SnapshotException
     * @throws IOException
     */
    private void gcRoots(DataOutput os) throws SnapshotException, IOException
    {
        int nextThreadSerial = 1;
        ++nextThreadSerial;
        for (int i : snapshot.getGCRoots())
        {
            int roots = 0;
            if (!includeObject(i))
                continue;
            GCRootInfo gi[] = snapshot.getGCRootInfo(i);
            for (GCRootInfo gri : gi)
            {
                int tp;
                long contextAddr;
                switch (gri.getType())
                {
                    case Type.BUSY_MONITOR:
                        tp = Constants.DumpSegment.ROOT_MONITOR_USED;
                        os.writeByte(tp);
                        writeID(os, gri.getObjectAddress());
                        ++roots;
                        break;
                    case Type.THREAD_OBJ:
                        tp = Constants.DumpSegment.ROOT_THREAD_OBJECT;
                        os.writeByte(tp);
                        writeID(os, gri.getObjectAddress());
                        os.writeInt(nextThreadSerial);
                        threadToSerial.put(gri.getObjectId(), nextThreadSerial);
                        // System.out.println("Thread "+gri.getObjectId()+"
                        // "+nextThreadSerial);
                        ++nextThreadSerial;
                        Integer stackserial = threadToStack.get(gri.getObjectId());
                        if (stackserial == null)
                            stackserial = UNKNOWN_STACK_TRACE_SERIAL;
                        os.writeInt(stackserial); // Stack trace
                        ++roots;
                        break;
                    case Type.JAVA_LOCAL:
                        contextAddr = gri.getContextAddress();
                        if (contextAddr == 0)
                        {
                            tp = Constants.DumpSegment.ROOT_JAVA_FRAME;
                            os.writeByte(tp);
                            writeID(os, gri.getObjectAddress());
                            os.writeInt(0); // thread serial
                            os.writeInt(UNKNOWN_STACK_FRAME_SERIAL); // stack frame number
                            ++roots;
                        }
                        break;
                    case Type.NATIVE_LOCAL:
                    case Type.NATIVE_STATIC:
                    case Type.NATIVE_STACK:
                        contextAddr = gri.getContextAddress();
                        if (contextAddr == 0)
                        {
                            tp = Constants.DumpSegment.ROOT_JNI_GLOBAL;
                            os.writeByte(tp);
                            writeID(os, gri.getObjectAddress());
                            writeID(os, 0); // JNI global ref ID
                            ++roots;
                        }
                        break;
                    case Type.SYSTEM_CLASS:
                        tp = Constants.DumpSegment.ROOT_STICKY_CLASS;
                        os.writeByte(tp);
                        writeID(os, gri.getObjectAddress());
                        ++roots;
                        break;
                    case Type.THREAD_BLOCK:
                        contextAddr = gri.getContextAddress();
                        if (contextAddr == 0)
                        {
                            tp = Constants.DumpSegment.ROOT_THREAD_BLOCK;
                            os.writeByte(tp);
                            writeID(os, gri.getObjectAddress());
                            os.writeInt(0); // thread serial
                            ++roots;
                        }
                        break;
                    case Type.UNREACHABLE:
                    case Type.FINALIZABLE:
                    case Type.UNKNOWN:
                    default:
                        tp = Constants.DumpSegment.ROOT_UNKNOWN;
                        os.writeByte(tp);
                        writeID(os, gri.getObjectAddress());
                        ++roots;
                        break;
                }
            }
            if (roots > 0)
            {
                ++totalRoots;
            }
        }
    }

    /**
     * Class to hold results of parsing stack frames.
     */
    static class Frame
    {
        String clazz;
        String method;
        String signature;
        String sourceFile;
        int lineNumber;
        public Frame(String clazz, String method, String signature, String sourceFile, int lineNumber)
        {
            this.clazz = clazz;
            this.method = method;
            this.signature = signature;
            this.sourceFile = sourceFile;
            this.lineNumber = lineNumber;
        }

        /**
         * Parse lines such as:
         * at org.eclipse.core.internal.jobs.Worker.run()V (Worker.java:55)
         * at org.eclipse.swt.widgets.Display.sleep()Z (Display.java:4534)
         * at org.eclipse.ui.internal.Workbench.lambda$3(Lorg/eclipse/swt/widgets/Display;Lorg/eclipse/ui/application/WorkbenchAdvisor;[I)V (Workbench.java:683) at
         * at org.eclipse.swt.internal.win32.OS.WaitMessage()Z (Native Method)
         * at org.eclipse.swt.internal.win32.OS.WaitMessage()Z (Compiled Code)
         * at org.eclipse.swt.internal.win32.OS.WaitMessage()Z (Unknown source)
         * at org.eclipse.swt.widgets.Display.sleep()Z (Display.java:4534(Compiled Code))
         * at java.io.FileInputStream.readBytes([BII)I (Native Method)
         * at java.io.FileInputStream.read([BII)I (FileInputStream.java:256)
         * at java.io.BufferedInputStream.fill()V (BufferedInputStream.java:246)
         * at java.io.BufferedInputStream.read()I (BufferedInputStream.java:265)
         * at java.io.BufferedInputStream.read()I (BufferedInputStream.java:265(Compiled Code))
         * at
         * The last sometimes appears from DTFJ dumps in error.
         *
         * @param frame
         * @return a new Frame object holding the parsed components
         */
        public static Frame parse(String frame)
        {
            String parts[] = frame.split("\\s+", 3); //$NON-NLS-1$
            String mn = parts.length >= 2 ? parts[1] : ""; //$NON-NLS-1$
            String source = parts.length >= 3 ? parts[2] : ""; //$NON-NLS-1$
            int b = mn.indexOf('(');
            String method;
            String classname;
            String sig;
            if (b < 0)
            {
                b = mn.length();
            }
            int c = mn.lastIndexOf('.', b);
            if (c >= 0)
            {
                classname = mn.substring(0, c);
            }
            else
            {
                classname = ""; //$NON-NLS-1$
            }
            method = mn.substring(c + 1, b);
            sig = mn.substring(b);

            if (source.startsWith("(")) //$NON-NLS-1$
                source = source.substring(1);
            if (source.endsWith(")")) //$NON-NLS-1$
                source = source.substring(0, source.length() - 1);
            int cl = source.indexOf(':');
            String sourcefile;
            int linenum = 0;
            if (cl >= 0)
            {
                sourcefile = source.substring(0, cl);
                int cn1 = cl + 1;
                while (cn1 < source.length() && source.charAt(cn1) >= '0' && source.charAt(cn1) <= '9')
                {
                    ++cn1;
                }
                if (cn1 > cl + 1)
                {
                    linenum = Integer.parseInt(source.substring(cl + 1, cn1));
                }
            }
            else
            {
                int br = source.indexOf('(');
                if (br >= 0)
                    sourcefile = source.substring(0, br);
                else
                    sourcefile = ""; //$NON-NLS-1$
                if (source.contains("Compiled Code")) //$NON-NLS-1$
                {
                    linenum = -2;
                }
                else if (source.contains("Native Method")) //$NON-NLS-1$
                {
                    linenum = -3;
                }
                else
                {
                    linenum = -1;
                }
            }
            return new Frame(classname, method, sig, sourcefile, linenum);
        }
    }

    /**
     * Dump the thread stacks
     * @param os the main output stream
     * @param startTime
     * @throws SnapshotException
     * @throws IOException
     */
    private void dumpThreadStacks(DataOutput os, long startTime)
                    throws SnapshotException, IOException
    {
        dummyStackTrace(os, UNKNOWN_STACK_TRACE_SERIAL, 1);
        int frameid = 1;
        int serialid = UNKNOWN_STACK_TRACE_SERIAL + 1;
        // Find the threads
        for (int i : snapshot.getGCRoots())
        {
            if (!includeObject(i))
                continue;
            for (GCRootInfo gr : snapshot.getGCRootInfo(i))
            {
                if (gr.getType() == GCRootInfo.Type.THREAD_OBJ)
                {
                    IThreadStack its = snapshot.getThreadStack(i);
                    if (its == null)
                        continue;
                    // Find the stack frames
                    int firstframeid = frameid;
                    for (IStackFrame isf : its.getStackFrames())
                    {
                        String frametext = isf.getText();
                        Frame f = Frame.parse(frametext);
                        String classname = f.clazz;
                        String method = f.method;
                        String sig = f.signature;
                        String sourcefile = f.sourceFile;
                        method = remap.renameMethodName(classname, method, false);
                        sig = remap.renameSignature(sig);
                        sourcefile = remap.renameFileName(classname, sourcefile);
                        int linenum = f.lineNumber;
                        Collection<IClass> cls = snapshot.getClassesByName(classname, false);
                        int clsid;
                        if (cls == null || cls.isEmpty())
                        {
                            clsid = 0;
                        }
                        else
                        {
                            IClass cls1 = cls.iterator().next();
                            clsid = cls1.getObjectId();
                        }
                        os.writeByte(Constants.Record.STACK_FRAME);
                        os.writeInt((int) (System.currentTimeMillis() - startTime));
                        os.writeInt(4 * idsize + 2 * 4);
                        writeID(os, frameid);
                        writeString(os, method);
                        writeString(os, sig);
                        writeString(os, sourcefile);
                        os.writeInt(clsid);
                        os.writeInt(linenum);
                        ++frameid;
                    }
                    os.writeByte(Constants.Record.STACK_TRACE);
                    os.writeInt((int) (System.currentTimeMillis() - startTime));
                    os.writeInt(3 * 4 + (frameid - firstframeid) * idsize);
                    os.writeInt(serialid);
                    Integer prev = threadToStack.put(i, serialid);
                    //if (prev != null && prev != serialid)
                    //    throw new IllegalStateException("thread " + i + "0x" + Long.toHexString(gr.getObjectAddress()) + " " + serialid + " != " + prev); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    ++serialid;
                    os.writeInt(threadToSerial.get(i));
                    os.writeInt(frameid - firstframeid);
                    for (int j = firstframeid; j < frameid; ++j)
                    {
                        writeID(os, j);
                    }

                }
            }
        }
    }

    /**
     * Write out the body of a load class definition.
     * @param os
     * @param startTime
     * @param cls
     * @throws IOException
     */
    private void loadClassBody(DataOutput os, long startTime, IClass cls) throws IOException
    {
        os.writeInt(cls.getObjectId()); // Class serial id
        writeID(os, cls.getObjectAddress());
        os.writeInt(UNKNOWN_STACK_TRACE_SERIAL); // stack trace serial number
        String classname = cls.getName();
        classname = remap.renameClassName(classname);
        writeString(os, classname);
    }

    /**
     * Write out the whole load class, including the header and size
     * @param os
     * @param os2 Uses to measure the size of the body
     * @param startTime
     * @param cls
     * @throws IOException
     */
    private void loadClass(DataOutput os, DataOutputStream3 os2, long startTime, IClass cls) throws IOException
    {
        int str = nextStringID;
        long mark = os2.size();
        loadClassBody(os2, startTime, cls);
        long end = os2.size();
        if (nextStringID != str)
        {
            String classname = cls.getName();
            classname = remap.renameClassName(classname);
            writeStringUTF(os, os2, startTime, classname, str);
        }

        loadClass(os, cls, startTime, (int) (end - mark));
    }

    /**
     * Generate load class definitions for all the classes.
     * @param os
     * @param os2
     * @param startTime
     * @param listener
     * @throws IOException
     * @throws SnapshotException
     */
    private void loadClasses(DataOutput os, DataOutputStream3 os2, long startTime, IProgressListener listener)
                    throws IOException, SnapshotException
    {
        for (IClass cls : snapshot.getClasses())
        {
            if (includeObject(cls.getObjectId()))
            {
                loadClass(os, os2, startTime, cls);
            }
            listener.worked(1);
            if (listener.isCanceled())
                throw new OperationCanceledException();
        }
        return;
    }

    /**
     * Write out a single string, as UTF-8.
     * @param os
     * @param os2
     * @param startTime
     * @param ss
     * @param id
     * @throws IOException
     */
    private void writeStringUTF(DataOutput os, DataOutputStream3 os2, long startTime, String ss, int id)
                    throws IOException
    {
        os.writeByte(Constants.Record.STRING_IN_UTF8);
        os.writeInt((int) (System.currentTimeMillis() - startTime));
        long mark = os2.size();
        writeID(os2, id);
        byte utf[] = ss.getBytes(UTF8);
        os2.write(utf);
        long reclen = os2.size() - mark;
        os.writeInt((int)reclen);
        writeID(os, id);
        os.write(utf);
    }

    /**
     * Dump all the classes into a heap dump segment.
     * @param os
     * @param listener
     * @throws IOException
     * @throws SnapshotException
     */
    private void dumpClasses(DataOutput os, IProgressListener listener) throws IOException, SnapshotException
    {
        for (IClass cls : snapshot.getClasses())
        {
            if (includeObject(cls.getObjectId()))
            {
                dumpClass(os, cls);
                ++totalClasses;
            }
            listener.worked(1);
            if (listener.isCanceled())
                throw new OperationCanceledException();
        }
        // As well as actual used class loader types, include any classes extending the java classloader
        for (IClass cls: snapshot.getClassesByName(IClass.JAVA_LANG_CLASSLOADER, true))
        {
            for (int i : cls.getObjectIds())
            {
                classloaders.add(i);
            }
        }
        return;
    }

    private void loadClass(DataOutput os, IClass cls, long startTime, int len) throws IOException
    {
        os.writeByte(Constants.Record.LOAD_CLASS);
        os.writeInt((int) (System.currentTimeMillis() - startTime));
        os.writeInt(len);
        loadClassBody(os, startTime, cls);
    }

    private void dumpClass(DataOutput os, IClass cls) throws IOException
    {
        os.writeByte(Constants.DumpSegment.CLASS_DUMP);
        writeID(os, cls.getObjectAddress());
        os.writeInt(UNKNOWN_STACK_TRACE_SERIAL); // stack trace serial number
        IClass sup = cls.getSuperClass();
        writeID(os, sup != null ? sup.getObjectAddress() : 0);
        writeID(os, cls.getClassLoaderAddress());
        // Remember the type of the loader as a possible type for all class loaders
        classloaders.add(cls.getClassLoaderId());
        writeID(os, 0); // signers
        writeID(os, 0); // protection domain
        writeID(os, 0); // reserved
        writeID(os, 0); // reserved
        os.writeInt((int) cls.getHeapSizePerInstance());
        os.writeShort(0); // constant pool
        // Static fields
        List<Field> statics = cls.getStaticFields();
        os.writeShort((short) statics.size());
        for (Field fld : statics)
        {
            String fieldName = fld.getName();
            fieldName = remap.renameMethodName(cls.getName(), fieldName, true);
            writeString(os, fieldName);
            writeField(os, fld, true);
        }
        // Instance fields
        List<FieldDescriptor> fields = cls.getFieldDescriptors();
        os.writeShort((short) fields.size());
        for (FieldDescriptor fld : fields)
        {
            String fieldName = fld.getName();
            fieldName = remap.renameMethodName(cls.getName(), fieldName, false);
            writeString(os, fieldName);
            int ty = fld.getType();
            switch (ty)
            {
                case IObject.Type.BOOLEAN:
                    os.writeByte(IObject.Type.BOOLEAN);
                    break;
                case IObject.Type.BYTE:
                    os.writeByte(IObject.Type.BYTE);
                    break;
                case IObject.Type.CHAR:
                    os.writeByte(IObject.Type.CHAR);
                    break;
                case IObject.Type.SHORT:
                    os.writeByte(IObject.Type.SHORT);
                    break;
                case IObject.Type.INT:
                    os.writeByte(IObject.Type.INT);
                    break;
                case IObject.Type.FLOAT:
                    os.writeByte(IObject.Type.FLOAT);
                    break;
                case IObject.Type.LONG:
                    os.writeByte(IObject.Type.LONG);
                    break;
                case IObject.Type.DOUBLE:
                    os.writeByte(IObject.Type.DOUBLE);
                    break;
                case IObject.Type.OBJECT:
                    os.writeByte(IObject.Type.OBJECT);
                    break;
                default:
                    // Error
                    break;
            }
        }
        // Check this
        totalBytes += cls.getUsedHeapSize();
    }

    private void writeField(DataOutput os, Field fld, boolean addType) throws IOException
    {
        int ty = fld.getType();
        switch (ty)
        {
            case IObject.Type.BOOLEAN:
                if (addType)
                    os.writeByte(IObject.Type.BOOLEAN);
                int booleanValue = redact == RedactType.FULL ? 0 : ((Boolean) fld.getValue()).booleanValue() ? 1 : 0;
                os.writeByte(booleanValue);
                break;
            case IObject.Type.BYTE:
                if (addType)
                    os.writeByte(IObject.Type.BYTE);
                byte byteValue = redact != RedactType.NONE && redact != RedactType.NAMES ? 0 : ((Byte) fld.getValue()).byteValue();
                os.writeByte(byteValue);
                break;
            case IObject.Type.CHAR:
                if (addType)
                    os.writeByte(IObject.Type.CHAR);
                char charValue = redact != RedactType.NONE && redact != RedactType.NAMES ? 0 : ((Character) fld.getValue()).charValue();
                os.writeChar(charValue);
                break;
            case IObject.Type.SHORT:
                if (addType)
                    os.writeByte(IObject.Type.SHORT);
                short shortValue = redact == RedactType.FULL ? 0 : ((Short) fld.getValue()).shortValue();
                os.writeShort(shortValue);
                break;
            case IObject.Type.INT:
                if (addType)
                    os.writeByte(IObject.Type.INT);
                int intValue = redact == RedactType.FULL ? 0 : ((Integer) fld.getValue()).intValue();
                os.writeInt(intValue);
                break;
            case IObject.Type.FLOAT:
                if (addType)
                    os.writeByte(IObject.Type.FLOAT);
                float floatValue = redact == RedactType.FULL ? 0.0f : ((Float) fld.getValue()).floatValue();
                os.writeFloat(floatValue);
                break;
            case IObject.Type.LONG:
                if (addType)
                    os.writeByte(IObject.Type.LONG);
                long longValue = redact == RedactType.FULL ? 0L : ((Long) fld.getValue()).longValue();
                os.writeLong(longValue);
                break;
            case IObject.Type.DOUBLE:
                if (addType)
                    os.writeByte(IObject.Type.DOUBLE);
                double doubleValue = redact == RedactType.FULL ? 0.0 : ((Double) fld.getValue()).doubleValue();
                os.writeDouble(doubleValue);
                break;
            case IObject.Type.OBJECT:
                if (addType)
                    os.writeByte(IObject.Type.OBJECT);
                ObjectReference value = (ObjectReference) fld.getValue();
                if (value != null)
                    writeID(os, value.getObjectAddress());
                else
                    writeID(os, 0);
                break;
            default:
                // Error
                break;
        }
    }

    private void dumpThreadRoots(DataOutput os)
                    throws IOException, SnapshotException
    {
        for (IClass cls : snapshot.getClasses())
        {
            dumpThreadRoots(os, cls);
        }
    }

    /**
     * Dump objects from start to end (exclusive)
     * @param os
     * @param os2
     * @param start Start object (inclusive)
     * @param end End object (exclusive)
     * @param listener
     * @return next start position, or -1 for no more objects to do
     * @throws IOException
     * @throws SnapshotException
     */
    private int dumpObjects(DataOutputStream3 os, DataOutputStream3 os2, int start, int end, IProgressListener listener)
                    throws IOException, SnapshotException
    {
        int i = 0;
        if (objects != null)
        {
            for (int objs[] : objects)
            {
                i = dumpObjects(os, os2, start, end, i, objs, listener);
                if (i < 0)
                    return -i;
                if (listener.isCanceled())
                    throw new OperationCanceledException();
            }
        }
        else
        {
            for (IClass cls : snapshot.getClasses())
            {
                int objs[] = cls.getObjectIds();
                i = dumpObjects(os, os2, start, end, i, objs, listener);
                if (i < 0)
                    return -i;
                if (listener.isCanceled())
                    throw new OperationCanceledException();
            }
        }
        return -1;
    }

    /**
     * Dump objects from start to end (exclusive)
     * @param os
     * @param os2
     * @param start Start object (inclusive)
     * @param end End object (exclusive)
     * @param i current position
     * @param objs an array of the objects
     * @param listener
     * @return next start position, or -1 for no more objects to do
     * @throws IOException
     * @throws SnapshotException
     */
    private int dumpObjects(DataOutputStream3 os, DataOutputStream3 os2, int start, int end, int i,
                    int[] objs, IProgressListener listener) throws SnapshotException, IOException
    {
        int numberOfObjects = objs.length;
        if (i + numberOfObjects <= start)
        {
            // Skipping class where none of the objects will be used
            i += numberOfObjects;
            // Try to keep the progress meter moving
            if (i == start && numberOfObjects == 0 )
                listener.worked(WORK_OBJECT);
        }
        else
        {
            int j = 0;
            for (int o : objs)
            {
                if (i < start)
                {
                    // skipping some initial objects
                    ++i;
                }
                else
                {
                    // Use these objects
                    // check for overflow if there is an unlimited end and this not the first object
                    if (this.dumpObject(os, os2, snapshot.getObject(o), i > start && end == Integer.MAX_VALUE))
                    {
                        // Success, enough room
                        ++totalObjects;
                        ++i;
                        ++j;
                        progress(numberOfObjects, j, listener);

                        if (i >= end && end >= 0 || os.size() > MAX_SEGMENT)
                        {
                            // Give up here if we have dumped all we should
                            // or we have overflowed
                            // Negative indicates return from caller too
                            return -i;
                        }
                    }
                    else
                    {
                        // No room for this object, so return and say so
                        // Negative indicates return from caller too
                        return -i;
                    }
                }
            }
            // Try to keep the progress meter moving
            if (numberOfObjects == 0)
                listener.worked(WORK_OBJECT);
        }
        return i;
    }

    private void progress(int numberOfObjects, int j, IProgressListener listener)
    {
        // 1 : 1,1,1
        // 2 : 1,2,2
        // 3 : 1,2,3
        // 4 : 2,3,4
        // 5 : 2,4,5
        // More rapid progress indicator
        for (int k = 1; k <= WORK_OBJECT; ++k)
        {
            if (j == (k * numberOfObjects + WORK_OBJECT - 1) / WORK_OBJECT)
                listener.worked(1);
        }
        if (listener.isCanceled())
            throw new OperationCanceledException();
    }

    /**
     * Find the stack frame in which an object is referenced.
     * Remove it from the list.
     * @param id
     * @param objid
     * @return
     */
    public int findID(int id, int objid[][])
    {
        for (int i = 0; i < objid.length; ++i)
        {
            for (int j = 0; j < objid[i].length; ++j)
            {
                if (objid[i][j] == id)
                {
                    // Remove from the list so the same object will be found
                    // in other frames
                    objid[i][j] = -1;
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Process local roots
     * @param os
     * @param g
     * @param objs
     * @throws IOException
     */
    private void processRoot(DataOutput os, GCRootInfo g, int objs[][]) throws IOException
    {
        if (!includeObject(g.getObjectId()))
            return;
        switch (g.getType())
        {
            case GCRootInfo.Type.NATIVE_LOCAL:
                Integer serial = threadToSerial.get(g.getContextId());
                if (serial != null)
                {
                    os.writeByte(Constants.DumpSegment.ROOT_JNI_LOCAL);
                    writeID(os, g.getObjectAddress());
                    os.writeInt(serial);
                    os.writeInt(findID(g.getObjectId(), objs)); // stack frame
                                                                // number
                }
                break;
            case GCRootInfo.Type.JAVA_LOCAL:
                serial = threadToSerial.get(g.getContextId());
                if (serial != null)
                {
                    os.writeByte(Constants.DumpSegment.ROOT_JAVA_FRAME);
                    writeID(os, g.getObjectAddress());
                    os.writeInt(serial);
                    os.writeInt(findID(g.getObjectId(), objs)); // stack frame
                                                                // number
                }
                break;
            case GCRootInfo.Type.NATIVE_STACK:
                serial = threadToSerial.get(g.getContextId());
                if (serial != null)
                {
                    os.writeByte(Constants.DumpSegment.ROOT_NATIVE_STACK);
                    // System.out.println("Thread found "+g.getContextId()+"
                    // "+serial+" "+g.getContextAddress());
                    writeID(os, g.getObjectAddress());
                    os.writeInt(serial);
                }
                break;
            case GCRootInfo.Type.THREAD_BLOCK:
                serial = threadToSerial.get(g.getContextId());
                if (serial != null)
                {
                    os.writeByte(Constants.DumpSegment.ROOT_THREAD_BLOCK);
                    writeID(os, g.getObjectAddress());
                    os.writeInt(serial);
                }
                break;
            case GCRootInfo.Type.JAVA_STACK_FRAME:
                serial = threadToSerial.get(g.getContextId());
                if (serial != null)
                {
                    os.writeByte(Constants.DumpSegment.ROOT_JAVA_FRAME);
                    writeID(os, g.getObjectAddress());
                    os.writeInt(serial);
                    // Probably won't have a frame number??
                    os.writeInt(findID(g.getObjectId(), objs)); // stack frame
                                                                // number
                }
                break;
            default:
                break;
        }
    }

    public void dumpThreadRoots(DataOutput os, IClass cls) throws SnapshotException, IOException
    {
        for (int oid : cls.getObjectIds())
        {
            if (!includeObject(cls.getObjectId()))
            {
                // Skip the thread
                continue;
            }
            GCRootInfo gp[] = snapshot.getGCRootInfo(oid);
            if (gp != null)
            {
                for (GCRootInfo g : gp)
                {
                    // System.out.println("Root
                    // "+GCRootInfo.getTypeAsString(g.getType())+"
                    // 0x"+Long.toHexString(g.getObjectAddress())+"
                    // 0x"+Long.toHexString(g.getContextAddress()));
                    switch (g.getType())
                    {
                        case GCRootInfo.Type.THREAD_OBJ:
                            IObject io = snapshot.getObject(oid);
                            IThreadStack its = snapshot.getThreadStack(oid);
                            int objs[][];
                            if (its != null)
                            {
                                IStackFrame fms[] = its.getStackFrames();
                                objs = new int[fms.length][];
                                for (int i = 0; i < fms.length; ++i)
                                {
                                    objs[i] = fms[i].getLocalObjectsIds().clone();
                                }
                            } else {
                                objs = new int[0][0];
                            }
                            processThreadLocalRefs(os, io, objs);
                            break;
                        default:
                            processRoot(os, g, new int[0][0]);
                            break;
                    }
                }
            }
        }
    }

    private void processThreadLocalRefs(DataOutput os, IObject io, int[][] objs) throws IOException, SnapshotException
    {
        for (NamedReference nr : io.getOutboundReferences())
        {
            if (nr instanceof ThreadToLocalReference)
            {
                ThreadToLocalReference tlr = (ThreadToLocalReference) nr;
                for (GCRootInfo g2 : tlr.getGcRootInfo())
                {
                    processRoot(os, g2, objs);
                    if (g2.getType() == Type.JAVA_STACK_FRAME)
                    {
                        // Another layer: Thread -> JAVA_STACK_FRAME -> objects
                        processThreadLocalRefs(os, tlr.getObject(), objs);
                    }
                }
            }
        }
    }

    /**
     * Output all objects of a particular type.
     * @param os the main out stream
     * @param os2 a temporary length measuring stream
     * @param cls the type of the object to dump
     * @param io the object to dump
     * @param check whether to check for segment overflow
     * @throws IOException
     * @throws SnapshotException
     */
    private boolean dumpObject(DataOutputStream3 os, DataOutputStream3 os2, IObject io, boolean check)
                    throws IOException, SnapshotException
    {
        if (io instanceof IInstance)
        {
            IInstance ii = (IInstance) io;
            if (ii.getObjectAddress() == 0)
            {
                // skip Bootstrap class loader as it has no fields
                if (classloaders.contains(io.getObjectId()))
                    ++totalClassloaders;
                return true;
            }
            IClass cls = io.getClazz();
            long mark1 = os2.size();
            dumpInstance(os2, cls, ii);
            long mark2 = os2.size();
            long size = mark2 - mark1;

            if (check && os.size() + 1L + idsize + 4 + idsize + 4 + size > MAX_SEGMENT)
            {
                // Overflow
                return false;
            }

            os.writeByte(Constants.DumpSegment.INSTANCE_DUMP);
            writeID(os, ii.getObjectAddress());
            os.writeInt(UNKNOWN_STACK_TRACE_SERIAL); // stack trace serial number
            writeID(os, cls.getObjectAddress());
            os.writeInt((int)size);
            dumpInstance(os, cls, ii);
            if (classloaders.contains(io.getObjectId()))
                ++totalClassloaders;
            totalBytes += cls.getHeapSizePerInstance();
        }
        else if (io instanceof IPrimitiveArray)
        {
            return dumpPrimitiveArray(os, (IPrimitiveArray)io, check);
        }
        else if (io instanceof IObjectArray)
        {
            return dumpObjectArray(os, (IObjectArray)io, check);
        } else if (io instanceof IClass){
            // Classes are IObject but not necessarily IInstance
            return dumpClassObject(os, (IClass)io, check);
        }
        return true;
    }

    /**
     * Sometimes it might be desireable to dump an IClass also as an instance.
     * This is likely to be incompatible with other HPROF tools, but might be necessary to indicate
     * the type of an object is something other than java.lang.Class, 
     * or to output per instance fields declared in java.lang.Class for other classes.
     * CLASS_DUMP only has constant pool, declared fields and static fields - but not fields
     * declared by java.lang.Class.
     * @param os
     * @param io
     * @param check
     * @return
     * @throws IOException
     */
    private boolean dumpClassObject(DataOutputStream3 os, IClass io, boolean check) throws IOException
    {
        IClass cls = io.getClazz();
        String cn = cls.getName();
        String rnc = remap.mapClass(cn);
        String newclsname = rnc != null ? rnc : cn;
        if (IClass.JAVA_LANG_CLASS.equals(newclsname))
        {
            // Most types are of class java.lang.Class, and don't need this extra information.
            return true;
        }
        // Classes are IObject but not necessarily IInstance
        int size = (int)cls.getHeapSizePerInstance();
        int size2 = 0;
        for (IClass cls2 = cls; cls2 != null; cls2 = cls2.getSuperClass())
        {
            for (FieldDescriptor fd : cls2.getFieldDescriptors()) {
                int se;
                switch (fd.getType())
                {
                    case IObject.Type.OBJECT:
                        // TODO - retrieve per instance java.lang.Class fields
                        se = idsize;
                        break;
                    default:
                        se = IPrimitiveArray.ELEMENT_SIZE[fd.getType()];
                        break;
                }
                 size2 += se;
            }
        }
        size = size2;
        if (check && os.size() + 1L + idsize + 4 + idsize + 4 + size > MAX_SEGMENT)
        {
            // Overflow
            return false;
        }

        os.writeByte(Constants.DumpSegment.INSTANCE_DUMP);
        writeID(os, io.getObjectAddress());
        os.writeInt(UNKNOWN_STACK_TRACE_SERIAL); // stack trace serial number
        writeID(os, cls.getObjectAddress());
        os.writeInt((int)size);
        os.write(new byte[size]);
        return true;
    }
    
    private boolean dumpObjectArray(DataOutputStream3 os, IObjectArray ii, boolean check) throws IOException
    {
        if (check && os.size() + 1L + idsize + 4 + 4 + ii.getLength() * idsize > MAX_SEGMENT)
        {
            // This object would overflow
            return false;
        }

        os.writeByte(Constants.DumpSegment.OBJECT_ARRAY_DUMP);
        writeID(os, ii.getObjectAddress());
        os.writeInt(UNKNOWN_STACK_TRACE_SERIAL); // stack trace serial number
        os.writeInt(ii.getLength());
        writeID(os, ii.getClazz().getObjectAddress());
        long l[] = ii.getReferenceArray();
        for (int i = 0; i < ii.getLength(); ++i)
        {
            writeID(os, l[i]);
        }
        totalBytes += ii.getUsedHeapSize();
        return true;
    }

    private boolean dumpPrimitiveArray(DataOutputStream3 os, IPrimitiveArray ii, boolean check) throws IOException
    {
        if (check && os.size() + 1L + idsize + 4 + 4 + 1 + ii.getLength() * (1L << (ii.getType() & 3) ) > MAX_SEGMENT)
        {
            return false;
        }

        os.writeByte(Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP);
        writeID(os, ii.getObjectAddress());
        os.writeInt(UNKNOWN_STACK_TRACE_SERIAL); // stack trace serial number
        os.writeInt(ii.getLength());
        os.writeByte(ii.getType());
        // For safety, don't even read value for full redaction
        Object a = redact == RedactType.FULL ? null : ii.getValueArray();
        if (ii.getType() == IObject.Type.BOOLEAN)
        {
            for (int i = 0; i < ii.getLength(); ++i)
            {
                int booleanValue = redact == RedactType.FULL ? 0 : ((boolean[]) a)[i] ? 1 : 0;
                os.writeByte(booleanValue);
            }
        }
        else if (ii.getType() == IObject.Type.BYTE)
        {
            if (redact == RedactType.NAMES)
            {
                String s = new String((byte[])a, UTF8);
                String newstr = remap.mapClass(s);
                if (newstr == null)
                {
                    newstr = remap.mapField(s);
                }
                if (newstr == null)
                {
                    newstr = remap.mapSignature(s);
                    if (newstr != null)
                        System.out.println("Found "+s+" "+newstr);
                }
                if (newstr != null)
                {
                    byte b[] = newstr.getBytes(UTF8);
                    if (b.length == ii.getLength())
                    {
                        // Mapped with exact length
                        a = b;
                    }
                    else
                    {
                        // Length problem, shouldn't happen
                        byte b2[] = new byte[ii.getLength()];
                        System.arraycopy(b, 0, b2, 0, Math.min(b.length, ii.getLength()));
                        a = b2;
                    }
                }
            }
            else if (redact != RedactType.NONE)
            {
                a = new byte[ii.getLength()];
            }
            os.write((byte[]) a);
        }
        else if (ii.getType() == IObject.Type.SHORT)
        {
            for (int i = 0; i < ii.getLength(); ++i)
            {
                short shortValue = redact == RedactType.FULL ? 0 : ((short[]) a)[i];
                os.writeShort(shortValue);
            }
        }
        else if (ii.getType() == IObject.Type.CHAR)
        {
            if (redact == RedactType.NAMES)
            {
                String s = new String((char[])a);
                String newstr = remap.mapClass(s);
                if (newstr == null)
                {
                    newstr = remap.mapField(s);
                }
                if (newstr == null)
                {
                    newstr = remap.mapSignature(s);
                }
                if (newstr != null)
                {
                    char b[] = newstr.toCharArray();
                    if (b.length == ii.getLength())
                    {
                        // Mapped with exact length
                        a = b;
                    }
                    else
                    {
                        // Length problem, shouldn't happen
                        char b2[] = new char[ii.getLength()];
                        System.arraycopy(b, 0, b2, 0, Math.min(b.length, ii.getLength()));
                        a = b2;
                    }
                }
            }
            for (int i = 0; i < ii.getLength(); ++i)
            {
                char shortValue = redact != RedactType.NONE && redact != RedactType.NAMES ? 0 : ((char[]) a)[i];
                os.writeChar(shortValue);
            }
        }
        else if (ii.getType() == IObject.Type.INT)
        {
            for (int i = 0; i < ii.getLength(); ++i)
            {
                int intValue = redact != RedactType.NONE && redact != RedactType.NAMES ? 0 : ((int[]) a)[i];
                os.writeInt(intValue);
            }
        }
        else if (ii.getType() == IObject.Type.LONG)
        {
            for (int i = 0; i < ii.getLength(); ++i)
            {
                long longValue = redact == RedactType.FULL ? 0L : ((long[]) a)[i];
                os.writeLong(longValue);
            }
        }
        else if (ii.getType() == IObject.Type.FLOAT)
        {
            for (int i = 0; i < ii.getLength(); ++i)
            {
                float floatValue = redact == RedactType.FULL ? 0.0f : ((float[]) a)[i];
                os.writeFloat(floatValue);
            }
        }
        else if (ii.getType() == IObject.Type.DOUBLE)
        {
            for (int i = 0; i < ii.getLength(); ++i)
            {
                double doubleValue = redact == RedactType.FULL ? 0.0 : ((double[]) a)[i];
                os.writeDouble(doubleValue);
            }
        }
        totalBytes += ii.getUsedHeapSize();
        return true;
    }

    /**
     * Output a single plain object.
     * @param os
     * @param cls
     * @param ii
     * @throws IOException
     */
    private void dumpInstance(DataOutputStream3 os, IClass cls, IInstance ii) throws IOException
    {
        List<Field> allf = new ArrayList<Field>(ii.getFields());
        for (IClass cls1 = cls; cls1 != null; cls1 = cls1.getSuperClass())
        {
            // Index by each descriptor
            for (FieldDescriptor f : cls1.getFieldDescriptors())
            {
                boolean found = false;
                // Find each field matching the descriptor
                for (ListIterator<Field> it = allf.listIterator(); it.hasNext();)
                {
                    Field fld = it.next();
                    if (f.getName().equals(fld.getName()))
                    {
                        it.remove(); // In case fields are defined in superclass
                                     // too
                        found = true;
                        writeField(os, fld, false);
                        break;
                    }
                }
                if (!found)
                    throw new IllegalStateException("missing field value " + f); //$NON-NLS-1$
            }
        }
    }

    /**
     * Write an ID of an object as an appropriate size.
     * @param os
     * @param addr
     * @throws IOException
     */
    private void writeID(DataOutput os, long addr) throws IOException
    {
        if (idsize == 4)
        {
            os.writeInt((int)addr);
        }
        else
        {
            os.writeLong(addr);
        }
    }

    /**
     * Write a string to the output stream as an ID.
     * Add ID to the map if new, otherwise use the
     * existing ID.
     * Something else will have to output the string definition.
     * @param os
     * @param s
     * @throws IOException
     */
    private void writeString(DataOutput os, String s) throws IOException
    {
        long id;
        if (stringToID.containsKey(s))
        {
            id = stringToID.get(s);
        }
        else
        {
            id = nextStringID++;
            stringToID.put(s, (int) id);
        }
        writeID(os, id);
    }

    /**
     * Remaps class names.
     * Separate class to isolate the generation of names from the actual
     * contents of the snapshot.
     *
     */
    public static class Remap {

        Pattern skipPattern, avoidPattern;
        boolean undo;
        boolean matchFields;

        int remapFail = 0;

        /** 
         * Random number generator.
         * Doesn't need to be particular secure as it just generates replacement names.
         * The replacement name can't be remapped to the original name from the random number generator.
         */
        private Random rnd = new Random();

        /**
         * Create a remapper
         * @param skipPattern Remap names unless they match this
         * @param avoidPattern Avoid remapping to names which match this
         * @param matchFields TODO
         * @param undo Just use existing remappings
         */
        public Remap(Pattern skipPattern, Pattern avoidPattern, boolean matchFields, boolean undo)
        {
            this.skipPattern = skipPattern;
            this.avoidPattern = avoidPattern;
            this.matchFields = matchFields;
            this.undo = undo;
        }

        /*
         * The following section is for remapping class names to hide potential sensitive names.
         */

        /**
         * Load the existing mapping table from a mapping properties file.
         * Properties file format:
         * original.package.Classname=new.package.Classname
         * @param mapFile the Java format properties file
         * @param undo whether to reverse the mappings contained in the file
         * @throws IOException
         */
        public void loadMapping(File mapFile, boolean undo) throws IOException
        {
            if (mapFile != null && mapFile.canRead())
            {
                Properties p = new Properties();
                // Properties always written in ISO8859_1, so use stream
                FileInputStream rdr = new FileInputStream(mapFile);
                try {
                    p.load(rdr);
                }
                finally
                {
                    rdr.close();
                }
                for (Map.Entry<Object,Object> e : p.entrySet())
                {
                    String key = e.getKey().toString();
                    String value = e.getValue().toString();
                    if (undo)
                    {
                        // Reverse the mapping
                        obfuscated.put(value, key);
                        used.add(key);
                        String fieldOld = fieldName(key);
                        String fieldNew = fieldName(value);
                        usedField.put(fieldNew, fieldOld);
                    }
                    else
                    {
                        // Mapping file shows old to new
                        obfuscated.put(key, value);
                        used.add(value);
                        String fieldOld = fieldName(key);
                        String fieldNew = fieldName(value);
                        usedField.put(fieldOld, fieldNew);
                    }
                }
            }
        }

        private static class SortedProperties extends Properties
        {
            private static final long serialVersionUID = 1L;

            @Override
            public Enumeration<Object> keys()
            {
                List<Object> list = Collections.list(super.keys());
                Collections.sort(list, new Comparator<Object>()
                {
                    public int compare(Object o1, Object o2)
                    {
                        return o1.toString().compareTo(o2.toString());
                    }
                });
                // Sorted, not sure about locking, but okay for this
                // purpose
                return Collections.enumeration(list);
            }
        };

        public void saveMapping(File mapFile, boolean undo, String comments) throws IOException
        {
            // Only save if we are not doing a reverse mapping, and it is okay to write to the file
            if (!undo && mapFile != null && (mapFile.canWrite() || !mapFile.exists()))
            {
                // Sorted keys properties file
                Properties p = new SortedProperties();
                for (Map.Entry<String,String> e : obfuscated.entrySet())
                {
                    p.setProperty(e.getKey(), e.getValue());
                }
                // Properties always written in ISO8859_1, so use stream
                FileOutputStream wrt = new FileOutputStream(mapFile);
                try {
                    p.store(wrt, comments);
                }
                finally
                {
                    wrt.close();
                }
            }
        }

        /**
         * Is the class name one which should have a new name invented?
         * @param cn
         * @return
         */
        public boolean isRemapped(String cn)
        {
            return !undo && (skipPattern == null || !skipPattern.matcher(cn).matches());
        }

        /**
         * Return the renamed version of a class
         * @param cn
         * @return null if not renamed
         */
        public String mapClass(String cn)
        {
            return obfuscated.get(cn);
        }

        /**
         * Return the renamed version of a simple field/method
         * @param cn
         * @return null if not renamed
         */
        public String mapField(String cn)
        {
            return usedField.get(cn);
        }

        /**
         * Return the renamed version of a method/type signature
         * @param cn
         * @return null if not renamed
         */
        public String mapSignature(String sig)
        {
            // Quick test for signature with class name
            if (!sig.matches("\\p{Print}*L\\p{Print}+;\\p{Print}*")) //$NON-NLS-1$
                return null;
            // Split up around possible class names
            String words[] = sig.split("[;<\\(\\)\\[\\]+*]+"); //$NON-NLS-1$
            String wordsReplace[] = new String[words.length];
            int found = 0;
            for (int i = 0; i < words.length; ++i)
            {
                String w = words[i];
                // Remove any simple types in front of the name
                String w2 = w.replaceFirst("[VZBCIJFD]*", ""); //$NON-NLS-1$ //$NON-NLS-2$
                // True encoded class?
                if (!w2.startsWith("L")) //$NON-NLS-1$
                {
                    wordsReplace[i] = w;
                    continue;
                }
                w2 = w2.substring(1);
                if (!obfuscated.containsKey(w2))
                {
                    String w3 = w2.replace('/', '.');
                    if (!obfuscated.containsKey(w3))
                    {
                        wordsReplace[i] = w;
                    }
                    else 
                    {
                        words[i] = "L" + w2; //$NON-NLS-1$
                        wordsReplace[i] =  "L" + obfuscated.get(w3).replace('.', '/');  //$NON-NLS-1$
                        ++found;
                    }
                }
                else
                {
                    words[i] = "L" + w2; //$NON-NLS-1$
                    wordsReplace[i] = "L" + obfuscated.get(w2); //$NON-NLS-1$
                    ++found;
                }
            }
            if (found == 0)
            {
                // No changes
                return null;
            }
            int j = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.length; ++i)
            {
                int k = sig.indexOf(words[i], j);
                if (k < 0)
                    return null;
                sb.append(sig.substring(j, k));
                sb.append(wordsReplace[i]);
                j = k + words[i].length();
            }
            sb.append(sig.substring(j));
            return sb.toString();
        }

        /**
         * Whether to avoid generating this particular new name.
         * @param cn
         * @return
         */
        private boolean isAvoid(String cn)
        {
            return avoidPattern != null && avoidPattern.matcher(cn).matches();
        }

        /**
         * java.lang.String: java.=rurl. java.lang.rurl.morl.
         * java.lang.String=rurl.morl.Glaeck java.lang.Integer=rurl.morl.Wrirurl
         * java.lang.String.split(Ljava.lang.String;)Ljava.lang.String;
         */
        private Map<String, String> obfuscated = new HashMap<String, String>();

        /** Holds whether the new name has been used, to prevent conflicts */
        private Set<String> used = new HashSet<String>();

        /** Holds whether the field name has been mapped, so should be zeroed elsewhere */
        private Map<String, String> usedField = new HashMap<String,String>();

        /**
         * Rename a file name based on a class name.
         * @param classname
         * @param filename
         * @return
         */
        public String renameFileName(String classname, String filename)
        {
            String clsnw = renameClassName(classname);
            String old = baseName(classname);
            String nw = baseName(clsnw);
            return filename.replaceFirst(old, nw);
        }

        /**
         * Extract the last part of a class name.
         * Up to a $ for an inner class, 
         * up to dot for an ordinary class 
         * @param classname
         * @return
         */
        private String baseName(String classname)
        {
            int dot = classname.lastIndexOf('.');
            int dol = classname.indexOf('$', dot);
            if (dol < 0)
                dol = classname.length();
            String fn = classname.substring(dot + 1, dol);
            return fn;
        }

        /**
         * Rename a method signature.
         * Extract the class names from the Lpack1.class1;II)VLpack2.class2;
         * @param signature
         * @return renamed signature
         */
        public String renameSignature(String signature)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < signature.length(); ++i)
            {
                char ch = signature.charAt(i);
                sb.append(ch);
                if (ch == 'L')
                {
                    int semi = sb.indexOf(";", i); //$NON-NLS-1$
                    if (semi >= 0)
                    {
                        String cn = signature.substring(i + 1, semi);
                        String newcn = renameClassName(cn);
                        sb.append(newcn);
                        sb.append(";"); //$NON-NLS-1$
                        i = semi;
                    }
                    else
                    {
                        sb.append(signature.substring(i + 1));
                        break;
                    }
                }
            }
            return sb.toString();
        }

        // Avoid using = or : or # or ! as have to be escaped in properties files
        private static final String methodSep = "@"; //$NON-NLS-1$

        /**
         * Generate a new method name.
         * Remember it as package.class^method
         *
         * - ? Some unusual Javac generated methods:
         * HistogramQuery$Grouping.values()
         * HistogramQuery.$SWITCH_TABLE$org$eclipse$mat$inspections$HistogramQuery$Grouping()
         *
         * @param className
         * @param method
         * @param upper static field in upper case, else all lower case.
         * @return
         */
        public String renameMethodName(String className, String method, boolean upper)
        {
            String mn = className + methodSep + method;
            if (obfuscated.containsKey(mn))
            {
                String newmn = obfuscated.get(mn);
                return newmn.substring(newmn.indexOf(methodSep) + 1);
            }
            if (!isRemapped(className))
                return method;
            String newcls = renameClassName(className);
            String newmn = remap(mn, newcls + methodSep, method, "", true, upper); //$NON-NLS-1$
            return newmn.substring(newmn.indexOf(methodSep) + 1);
        }

        private String fieldName(String classField)
        {
            String fns[] = classField.split(Pattern.quote(methodSep), 2);
            if (fns.length >= 2)
            {
                return fns[1];
            }
            else
            {
                return ""; //$NON-NLS-1$
            }
        }

        /**
         * Renames a class.
         * Break into component parts, reusing existing mapping for package if
         * already used.
         * Removes array suffixes and uses base class name.
         * Translates inner classes with '$' piece by piece, reusing existing
         * mapping of outer class.
         *
         * @param classname
         * @return the renamed class name, or the original name if no renaming is to be done for this class
         */

        public String renameClassName(String classname)
        {
            if (obfuscated.containsKey(classname))
                return obfuscated.get(classname);
            // Remap arrays preserving base class
            if (classname.endsWith("[]")) //$NON-NLS-1$
            {
                String baseclassname = classname.replace("[]", ""); //$NON-NLS-1$//$NON-NLS-2$
                return renameClassName(baseclassname) + classname.substring(baseclassname.length());
            }
            else if (classname.startsWith("[")) //$NON-NLS-1$
            {
                String baseclassname = classname.replace("[", ""); //$NON-NLS-1$//$NON-NLS-2$
                return classname.substring(0, classname.length() - baseclassname.length()) + renameClassName(baseclassname);
            }
            // E.g. If only com.sun. is renamed, don't rename com.
            if (!isRemapped(classname))
                return classname;
            String pack;
            String newpack;
            String cn;
            String last = ""; //$NON-NLS-1$
            if (classname.endsWith(".")) //$NON-NLS-1$
            {
                // Package name
                last = "."; //$NON-NLS-1$
                int i = classname.lastIndexOf('.', classname.length() - 2);
                if (i >= 0)
                {
                    pack = classname.substring(0, i + 1);
                    newpack = renameClassName(pack);
                    cn = classname.substring(i + 1, classname.length() - 1);
                }
                else
                {
                    pack = ""; //$NON-NLS-1$
                    newpack = ""; //$NON-NLS-1$
                    cn = classname.substring(0, classname.length() - 1);
                }
            }
            else
            {
                // Ordinary class name
                int i = classname.lastIndexOf('.', classname.length() - 2);
                int j = classname.lastIndexOf('$', classname.length() - 2);
                if (j > i)
                {
                    // Without $
                    pack = classname.substring(0, j);
                    newpack = renameClassName(pack) + "$"; //$NON-NLS-1$
                    cn = classname.substring(j + 1);
                }
                else if (i >= 0)
                {
                    // With the dot
                    pack = classname.substring(0, i + 1);
                    newpack = renameClassName(pack);
                    cn = classname.substring(i + 1);
                }
                else
                {
                    pack = ""; //$NON-NLS-1$
                    newpack = ""; //$NON-NLS-1$
                    cn = classname;
                }
            }

            return remap(classname, newpack, cn, last, false, false);
        }


        /**
         * Map a class name or package name to a new name
         * @param classname the new fully qualified class name, include "." for just a package name
         * @param newpack The new package name (already replaced), excluding cn
         * @param cn The class name or last package component
         * @param last The suffix to add onto the name ("." for package, "$" for inner class)
         * @param field Is this a field name (do not title case)
         * @param upper If a field name, then upper case
         * @return
         */
        private String remap(String classname, String newpack, String cn, String last, boolean field, boolean upper)
        {
            /*
             * 0 => 0-9 unchanged
             * 0 + fields => field map
             * 1-49 => word
             * 50-99 => random lower case string
             * 100-149 => random mixed case string
             * 150-199 => random mixed case string, don't use avoid pattern
             */
            for (int k = 0; k < 200; ++k)
            {
                String np;
                int ln = cn.length();
                // Try not mapping numeric
                if (k == 0 && cn.matches("[0-9]+")) //$NON-NLS-1$
                {
                    np = cn;
                }
                else if (k == 0 && matchFields && field && usedField.containsKey(cn))
                {
                    // Match field values across classes, keep the same names
                    np = usedField.get(cn);
                }
                else if (k < 50)
                {
                    np = randomWordsLen(ln, field && upper);
                }
                else if (k < 100)
                    np = randomString(ln);
                else
                    np = randomString2(ln);

                if (field)
                {
                    // otherwise lower/mixed
                    if (upper)
                    {
                        // static field in upper case
                        np = np.toUpperCase(Locale.ENGLISH);
                    }
                }
                else if (!last.equals(".")) //$NON-NLS-1$
                {
                    // Class name in title case
                    np = titleCase(np);
                }
                else
                {
                    // package in lower case
                    np = np.toLowerCase(Locale.ENGLISH);
                }
                String newcn = newpack + np + last;
                // Check if already used, or could clash with the unmapped names
                // Also avoid unwanted strings, unless we get desperate! 
                if (!used.contains(newcn) && isRemapped(newcn) && (k >= 150 || !isAvoid(newcn)))
                {
                    // found new mapping
                    obfuscated.put(classname, newcn);
                    used.add(newcn);
                    if (field)
                        usedField.put(cn, np);
                    return newcn;
                }
            }
            // Failed to find suitable mapping, record to avoid slow lookup later
            // found new mapping
            String newcn = newpack + cn + last;
            obfuscated.put(classname, newcn);
            used.add(newcn);
            if (field)
                usedField.put(cn, cn);
            ++remapFail;
            return newcn;
        }

        /**
         * Title case a string.
         * @param np lower/mixed case version
         * @return Upper case first letter
         */
        private String titleCase(String np)
        {
            if (np.length() < 1)
                return np;
            np = Character.toTitleCase(np.charAt(0)) + np.substring(1);
            return np;
        }

        /**
         * Generate a random word of given length 
         * @param length the length in chars
         * @param unders whether to separate words with underscore
         * @return
         */
        private String randomWordsLen(int length, boolean unders)
        {
            final int minlen = 5;
            // Capitals are hard to read, so shorten the words between underscores
            final int maxlen = unders ? 11: 15;
            if (length > maxlen)
            {
                // Split the word
                int sp = minlen + rnd.nextInt(Math.min(maxlen, length - minlen - (unders ? 1 : 0)) + 1 - minlen);
                String w = randomWordLen(sp);
                if (unders)
                    return w = w + "_" + randomWordsLen(length - sp - 1, unders); //$NON-NLS-1$
                else
                    return w + titleCase(randomWordsLen(length - sp, unders));
            }
            return randomWordLen(length);
        }

        /**
         * Generate a random word
         * @param length
         * @return
         */
        private String randomWordLen(int length)
        {
            if (length < 3)
                return randomString(length);
            // Minimum number of parts
            int p1 = (length - 2) / 4;
            // Maximum number of parts
            int p2 = (length - 1) / 2;
            String ret;
            do
            {
                // Decide whether to start with a vowel
                boolean vowel = rnd.nextFloat() < 0.15f;
                do
                {
                    // Choose the number of parts
                    int pt = p1 + rnd.nextInt(p2 - p1 + 1);
                    ret = randomWord(pt);
                }
                while (ret.length() != (vowel ? length - 1 : length));
                ret = addVowel(ret, vowel);
            }
            while (tryAgain(ret));
            return ret;
        }

        /**
         * Check whether the word is unsuitable.
         * @param tocheck
         * @return
         */
        private boolean tryAgain(String tocheck)
        {
            StringBuilder sb = new StringBuilder(tocheck);
            String rev = sb.reverse().toString().toLowerCase(Locale.ENGLISH);
            for (String ts : names4)
            {
                if (rev.indexOf(ts) >= 0)
                    return true;
            }
            return false;
        }

        /** Starting vowels */
        @SuppressWarnings("nls")
        private static final String names0[] = { "a", "e", "i", "o", "u" };

        /** Starting / middle consonants */
        @SuppressWarnings("nls")
        private static final String names1[] = { "b", "bl", "br", "c", "cl", "cr", "d", "dr", "f", "fl", "fr", "g", "gl", "gr", "h", //$NON-NLS-1$
                        "j", "k", "kl", "kn", "kr", "kw", "l", "m", "n", "p", "pl", "pr", "qu", "r", "s", "sh", "st", "t", "th", //$NON-NLS-1$
                        "tr", "v", "w", "wr", "x", "y", "z" }; //$NON-NLS-1$

        /** middle vowels */
        @SuppressWarnings("nls")
        private static final String names2[] = { "a", "ae", "ai", "e", "ea", "ee", "ei", "eo", "eu", "i", "ia", "ie", "io", "o", "oa", //$NON-NLS-1$
                        "oe", "oo", "ou", "u", "ua" }; //$NON-NLS-1$

        /** endings */
        @SuppressWarnings("nls")
        private static final String names3[] = { "b", "ch", "ck", "d", "de", "ff", "g", "gh", "k", "l", "le", "ly", "m", "n", "nd", "ne", "ng", "nk", //$NON-NLS-1$
                        "p", "r", "rb", "rd", "re", "rf", "rk", "rl", "rm", "rn", "rp", "rt", "ry", "s", "sh", "st", "sy", //$NON-NLS-1$
                        "t", "te", "th", "ts", "ve", "w", "y", "z" }; //$NON-NLS-1$

        /** excludes */
        @SuppressWarnings("nls")
        private static final String names4[] = {"tihs", "ssip", "kcuf", "tnuc", "kcoc", "stit", "knaw", "ggin"}; //$NON-NLS-1$

        /**
         * Perhaps add a vowel prefix to a string
         * @param base
         * @param add whether to add
         * @return
         */
        private String addVowel(String base, boolean add)
        {
            if (add)
            {
                base = names0[rnd.nextInt(names0.length)] + base;
            }
            return base;
        }

        /**
         * length varies from 2*parts+1 to 4*part+2
         *
         * @param parts
         * @return random word
         */
        private String randomWord(int parts)
        {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < parts; ++j)
            {
                sb.append(names1[rnd.nextInt(names1.length)]);
                sb.append(names2[rnd.nextInt(names2.length)]);
            }
            sb.append(names3[rnd.nextInt(names3.length)]);
            return sb.toString();
        }

        /**
         * Random lower case string
         * 
         * @param length
         * @return random lower case string
         */
        private String randomString(int length)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; ++i)
            {
                sb.append((char) ('a' + rnd.nextInt(26)));
            }
            return sb.toString();
        }

        /**
         * Random mixed case string
         *
         * @param length
         * @return random upper and lower case string
         */
        private String randomString2(int length)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; ++i)
            {
                sb.append((char) ('a' + rnd.nextInt(26) + (rnd.nextBoolean() ? 'A' - 'a' : 0)));
            }
            return sb.toString();
        }

    }
}

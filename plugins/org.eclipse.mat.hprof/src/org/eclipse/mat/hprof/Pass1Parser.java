/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - additional debug information
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.HashMapLongObject.Entry;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.parser.io.DefaultPositionInputStream;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.snapshot.MultipleSnapshotsException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObject.Type;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor;

public class Pass1Parser extends AbstractParser
{
    private static final Pattern PATTERN_OBJ_ARRAY = Pattern.compile("^(\\[+)L(.*);$"); //$NON-NLS-1$
    private static final Pattern PATTERN_PRIMITIVE_ARRAY = Pattern.compile("^(\\[+)(.)$"); //$NON-NLS-1$

    // New size of classes including per-instance fields
    private final boolean NEWCLASSSIZE = HprofPreferences.useAdditionalClassReferences();

    private HashMapLongObject<String> class2name = new HashMapLongObject<String>();
    private HashMapLongObject<Long> thread2id = new HashMapLongObject<Long>();
    private HashMapLongObject<StackFrame> id2frame = new HashMapLongObject<StackFrame>();
    private HashMapLongObject<StackTrace> serNum2stackTrace = new HashMapLongObject<StackTrace>();
    private HashMapLongObject<Long> classSerNum2id = new HashMapLongObject<Long>();
    private HashMapLongObject<Long> class2type = new HashMapLongObject<Long>();
    private HashMapLongObject<List<JavaLocal>> thread2locals = new HashMapLongObject<List<JavaLocal>>();
    private HashMapLongObject<String> constantPool = new HashMapLongObject<String>();
    private IHprofParserHandler handler;
    private SimpleMonitor.Listener monitor;
    private long previousArrayStart;
    private long previousArrayUncompressedEnd;
    private boolean foundCompressed;
    private final boolean verbose = Platform.inDebugMode() && HprofPlugin.getDefault().isDebugging()
                    && Boolean.parseBoolean(Platform.getDebugOption("org.eclipse.mat.hprof/debug/parser")); //$NON-NLS-1$
    private PositionInputStream in;

    public Pass1Parser(IHprofParserHandler handler, SimpleMonitor.Listener monitor,
                    HprofPreferences.HprofStrictness strictnessPreference)
    {
        super(strictnessPreference);
        this.handler = handler;
        this.monitor = monitor;
    }

    public void read(File file, String dumpNrToRead) throws SnapshotException, IOException
    {
        // See http://java.net/downloads/heap-snapshot/hprof-binary-format.html
        in = new DefaultPositionInputStream(new BufferedInputStream(new FileInputStream(file)));

        int currentDumpNr = 0;
        List<MultipleSnapshotsException.Context> ctxs = new ArrayList<MultipleSnapshotsException.Context>();
        boolean foundDump = false;

        try
        {
            // header & version
            version = readVersion(in);
            handler.addProperty(IHprofParserHandler.VERSION, version.toString());

            // identifierSize (32 or 64 bit)
            idSize = in.readInt();
            if (idSize != 4 && idSize != 8)
                throw new SnapshotException(Messages.Pass1Parser_Error_SupportedDumps);
            handler.addProperty(IHprofParserHandler.IDENTIFIER_SIZE, String.valueOf(idSize));

            // creation date
            long date = in.readLong();

            long prevTimeOffset = 0;
            long timeWrap = 0;

            long fileSize = file.length();
            long curPos = in.position();

            while (curPos < fileSize)
            {
                if (monitor.isProbablyCanceled())
                    throw new IProgressListener.OperationCanceledException();
                monitor.totalWorkDone(curPos / 1000);

                int record = in.readUnsignedByte();

                long timeOffset = in.readUnsignedInt(); // time stamp in microseconds
                if (timeOffset < prevTimeOffset)
                {
                    // Wrap after 4294 seconds
                    timeWrap += 1L << 32;
                }
                prevTimeOffset = timeOffset;

                long length = in.readUnsignedInt();
                if (verbose)
                    System.out.println("Read record type " + record + ", length " + length + " at position 0x" + Long.toHexString(curPos)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                length = updateLengthIfNecessary(fileSize, curPos, record, length, monitor);

                if (length < 0)
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_IllegalRecordLength,
                                    length, Long.toHexString(in.position() - 4), Integer.toHexString(record), Long.toHexString(curPos)));

                if (curPos + length - 9 > fileSize)
                {
                    switch (strictnessPreference)
                    {
                        case STRICTNESS_STOP:
                            throw new SnapshotException(Messages.HPROFStrictness_Stopped, new SnapshotException(
                                            MessageUtil.format(Messages.Pass1Parser_Error_invalidHPROFFile, length,
                                                            fileSize - curPos - 9, Integer.toHexString(record), Long.toHexString(curPos))));
                        case STRICTNESS_WARNING:
                        case STRICTNESS_PERMISSIVE:
                            monitor.sendUserMessage(Severity.WARNING,
                                            MessageUtil.format(Messages.Pass1Parser_Error_invalidHPROFFile, length,
                                                            fileSize - curPos - 9, Integer.toHexString(record), Long.toHexString(curPos)), null);
                            break;
                        default:
                            throw new SnapshotException(Messages.HPROFStrictness_Unhandled_Preference);
                    }
                }

                switch (record)
                {
                    case Constants.Record.STRING_IN_UTF8:
                        if (((int) (length - idSize) < 0))
                            throw new SnapshotException(MessageUtil.format(
                                            Messages.Pass1Parser_Error_IllegalRecordLength, length, Long.toHexString(in.position() - 4),
                                            Integer.toHexString(record), Long.toHexString(curPos)));
                        readString(length);
                        break;
                    case Constants.Record.LOAD_CLASS:
                        readLoadClass();
                        break;
                    case Constants.Record.UNLOAD_CLASS:
                        readUnloadClass();
                        break;
                    case Constants.Record.STACK_FRAME:
                        readStackFrame(length);
                        break;
                    case Constants.Record.STACK_TRACE:
                        readStackTrace(length);
                        break;
                    case Constants.Record.HEAP_DUMP:
                    case Constants.Record.HEAP_DUMP_SEGMENT:
                        long dumpTime = date + (timeWrap + timeOffset) / 1000;
                        if (dumpMatches(currentDumpNr, dumpNrToRead))
                        {
                            if (!foundDump)
                            {
                                handler.addProperty(IHprofParserHandler.CREATION_DATE, String.valueOf(dumpTime));
                                foundDump = true;
                            }
                            readDumpSegments(length);
                        }
                        else
                            in.skipBytes(length);
                        if (ctxs.size() < currentDumpNr + 1)
                        {
                            MultipleSnapshotsException.Context ctx = new MultipleSnapshotsException.Context(dumpIdentifier(currentDumpNr));
                            ctx.setDescription(MessageUtil.format(Messages.Pass1Parser_HeapDumpCreated, new Date(dumpTime)));
                            ctxs.add(ctx);
                        }

                        if (record == Constants.Record.HEAP_DUMP)
                            currentDumpNr++;

                        break;
                    case Constants.Record.HEAP_DUMP_END:
                        currentDumpNr++;
                        in.skipBytes(length);
                        break;
                    case Constants.Record.ALLOC_SITES:
                    case Constants.Record.HEAP_SUMMARY:
                    case Constants.Record.START_THREAD:
                    case Constants.Record.END_THREAD:
                    case Constants.Record.CPU_SAMPLES:
                    case Constants.Record.CONTROL_SETTINGS:
                        in.skipBytes(length);
                        break;
                    default:
                        switch (strictnessPreference)
                        {
                            case STRICTNESS_STOP:
                                throw new SnapshotException(Messages.HPROFStrictness_Stopped, new SnapshotException(
                                                MessageUtil.format(Messages.Pass1Parser_UnexpectedRecord,
                                                                Integer.toHexString(record), length, Long.toHexString(curPos))));
                            case STRICTNESS_WARNING:
                            case STRICTNESS_PERMISSIVE:
                                monitor.sendUserMessage(
                                                Severity.WARNING,
                                                MessageUtil.format(Messages.Pass1Parser_UnexpectedRecord,
                                                                Integer.toHexString(record), length, Long.toHexString(curPos)), null);
                                in.skipBytes(length);
                                break;
                            default:
                                throw new SnapshotException(Messages.HPROFStrictness_Unhandled_Preference);
                        }
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

        if (!foundDump)
            throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_NoHeapDumpIndexFound,
                            currentDumpNr, file.getName(), dumpNrToRead));

        if (currentDumpNr > 1)
        {
            if (dumpNrToRead == null)
            {
                MultipleSnapshotsException mse = new MultipleSnapshotsException(MessageUtil.format(Messages.Pass1Parser_HeapDumpsFound, currentDumpNr));
                for (MultipleSnapshotsException.Context runtime : ctxs)
                {
                    mse.addContext(runtime);
                }
                throw mse;
            }
            monitor.sendUserMessage(IProgressListener.Severity.INFO, MessageUtil.format(
                            Messages.Pass1Parser_Info_UsingDumpIndex, currentDumpNr, file.getName(), dumpNrToRead),
                            null);
        }

        if (serNum2stackTrace.size() > 0)
            dumpThreads();

    }

    private void readString(long length) throws IOException
    {
        long id = in.readID(idSize);
        byte[] chars = new byte[(int) (length - idSize)];
        in.readFully(chars);
        constantPool.put(id, new String(chars, "UTF-8")); //$NON-NLS-1$
    }

    private void readLoadClass() throws IOException
    {
        long classSerNum = in.readUnsignedInt(); // used in stacks frames
        long classID = in.readID(idSize);
        in.skipBytes(4); // stack trace
        long nameID = in.readID(idSize);

        String className = getStringConstant(nameID).replace('/', '.');
        class2name.put(classID, className);
        classSerNum2id.put(classSerNum, classID);
    }

    private void readUnloadClass() throws IOException
    {
        long classSerNum = in.readUnsignedInt(); // used in stacks frames
        long classID = classSerNum2id.get(classSerNum);
        // class2name only holds active classes
        class2name.remove(classID);
    }

    private void readStackFrame(long length) throws IOException
    {
        long frameId = in.readID(idSize);
        long methodName = in.readID(idSize);
        long methodSig = in.readID(idSize);
        long srcFile = in.readID(idSize);
        long classSerNum = in.readUnsignedInt();
        int lineNr = in.readInt(); // can be negative
        StackFrame frame = new StackFrame(frameId, lineNr, getStringConstant(methodName), getStringConstant(methodSig),
                        getStringConstant(srcFile), classSerNum);
        id2frame.put(frameId, frame);
    }

    private void readStackTrace(long length) throws IOException
    {
        long stackTraceNr = in.readUnsignedInt();
        long threadNr = in.readUnsignedInt();
        long frameCount = in.readUnsignedInt();
        long[] frameIds = new long[(int) frameCount];
        for (int i = 0; i < frameCount; i++)
        {
            frameIds[i] = in.readID(idSize);
        }
        StackTrace stackTrace = new StackTrace(stackTraceNr, threadNr, frameIds);
        serNum2stackTrace.put(stackTraceNr, stackTrace);
    }

    private void readDumpSegments(long length) throws IOException, SnapshotException
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
            if (verbose)
                System.out.println("    Read heap sub-record type " + segmentType + " at position 0x" + Long.toHexString(segmentStartPos)); //$NON-NLS-1$ //$NON-NLS-2$
            switch (segmentType)
            {
                case Constants.DumpSegment.ROOT_UNKNOWN:
                    readGC(GCRootInfo.Type.UNKNOWN, 0);
                    break;
                case Constants.DumpSegment.ROOT_THREAD_OBJECT:
                    readGCThreadObject(GCRootInfo.Type.THREAD_OBJ);
                    break;
                case Constants.DumpSegment.ROOT_JNI_GLOBAL:
                    readGC(GCRootInfo.Type.NATIVE_STATIC, idSize);
                    break;
                case Constants.DumpSegment.ROOT_JNI_LOCAL:
                    readGCWithThreadContext(GCRootInfo.Type.NATIVE_LOCAL, true);
                    break;
                case Constants.DumpSegment.ROOT_JAVA_FRAME:
                    readGCWithThreadContext(GCRootInfo.Type.JAVA_LOCAL, true);
                    break;
                case Constants.DumpSegment.ROOT_NATIVE_STACK:
                    readGCWithThreadContext(GCRootInfo.Type.NATIVE_STACK, false);
                    break;
                case Constants.DumpSegment.ROOT_STICKY_CLASS:
                    readGC(GCRootInfo.Type.SYSTEM_CLASS, 0);
                    break;
                case Constants.DumpSegment.ROOT_THREAD_BLOCK:
                    readGCWithThreadContext(GCRootInfo.Type.THREAD_BLOCK, false);
                    break;
                case Constants.DumpSegment.ROOT_MONITOR_USED:
                    readGC(GCRootInfo.Type.BUSY_MONITOR, 0);
                    break;
                case Constants.DumpSegment.CLASS_DUMP:
                    readClassDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.INSTANCE_DUMP:
                    readInstanceDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.OBJECT_ARRAY_DUMP:
                    readObjectArrayDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP:
                    readPrimitiveArrayDump(segmentStartPos);
                    break;
                default:
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_InvalidHeapDumpFile,
                                    Integer.toHexString(segmentType), Long.toHexString(segmentStartPos)));
            }

            segmentStartPos = in.position();
        }
        if (verbose)
            System.out.println("    Finished heap sub-records."); //$NON-NLS-1$
        if (segmentStartPos != segmentsEndPos)
        {
            switch (strictnessPreference)
            {
                case STRICTNESS_STOP:
                    throw new SnapshotException(Messages.HPROFStrictness_Stopped,
                                    new SnapshotException(
                                                    MessageUtil.format(Messages.Pass1Parser_UnexpectedEndPosition,
                                                                    Long.toHexString(segmentsEndPos - length), length,
                                                                    Long.toHexString(segmentStartPos),
                                                                    Long.toHexString(segmentsEndPos))));
                case STRICTNESS_WARNING:
                case STRICTNESS_PERMISSIVE:
                    monitor.sendUserMessage(Severity.WARNING, MessageUtil.format(
                                    Messages.Pass1Parser_UnexpectedEndPosition,
                                    Long.toHexString(segmentsEndPos - length), length,
                                    Long.toHexString(segmentStartPos), Long.toHexString(segmentsEndPos)), null);
                    break;
                default:
                    throw new SnapshotException(Messages.HPROFStrictness_Unhandled_Preference);
            }
        }
    }

    private void readGCThreadObject(int gcType) throws IOException
    {
        long id = in.readID(idSize);
        int threadSerialNo = in.readInt();
        thread2id.put(threadSerialNo, id);
        handler.addGCRoot(id, 0, gcType);

        in.skipBytes(4);
    }

    private void readGC(int gcType, int skip) throws IOException
    {
        long id = in.readID(idSize);
        handler.addGCRoot(id, 0, gcType);

        if (skip > 0)
            in.skipBytes(skip);
    }

    private void readGCWithThreadContext(int gcType, boolean hasLineInfo) throws IOException
    {
        long id = in.readID(idSize);
        int threadSerialNo = in.readInt();
        Long tid = thread2id.get(threadSerialNo);
        if (tid != null)
        {
            handler.addGCRoot(id, tid, gcType);
        }
        else
        {
            handler.addGCRoot(id, 0, gcType);
        }

        if (hasLineInfo)
        {
            int lineNumber = in.readInt();
            List<JavaLocal> locals = thread2locals.get(threadSerialNo);
            if (locals == null)
            {
                locals = new ArrayList<JavaLocal>();
                thread2locals.put(threadSerialNo, locals);
            }
            locals.add(new JavaLocal(id, lineNumber, gcType));
        }
    }

    private void readClassDump(long segmentStartPos) throws IOException
    {
        long address = in.readID(idSize);
        in.skipBytes(4); // stack trace serial number
        long superClassObjectId = in.readID(idSize);
        long classLoaderObjectId = in.readID(idSize);

        // read signers, protection domain, reserved ids (2)
        long signersId = in.readID(idSize);
        long protectionDomainId = in.readID(idSize);
        long reserved1Id = in.readID(idSize);
        long reserved2Id = in.readID(idSize);
        int extraDummyStatics = 0;
        if (NEWCLASSSIZE)
        {
            // Always add signers / protectionDomain
            extraDummyStatics += 2;
            if (reserved1Id != 0)
                ++extraDummyStatics;
            if (reserved2Id != 0)
                ++extraDummyStatics;
        }

        // instance size
        int instsize = in.readInt();

        // constant pool: u2 ( u2 u1 value )*
        int constantPoolSize = in.readUnsignedShort();
        Field[] constantPool = new Field[constantPoolSize];
        for (int ii = 0; ii < constantPoolSize; ii++)
        {
            int index = in.readUnsignedShort(); // index
            String name = "<constant pool["+index+"]>"; //$NON-NLS-1$ //$NON-NLS-2$
            byte type = in.readByte();

            Object value = readValue(in, null, type);
            constantPool[ii] = new Field(name, type, value);
        }

        // static fields: u2 num ( name ID, u1 type, value)
        int numStaticFields = in.readUnsignedShort();
        Field[] statics = new Field[numStaticFields + extraDummyStatics];

        for (int ii = 0; ii < numStaticFields; ii++)
        {
            long nameId = in.readID(idSize);
            String name = getStringConstant(nameId);

            byte type = in.readByte();

            Object value = readValue(in, null, type);
            statics[ii] = new Field(name, type, value);
        }

        if (NEWCLASSSIZE)
        {
            int si = numStaticFields;
            statics[si++] = new Field("<signers>", Type.OBJECT, signersId == 0 ? null : new ObjectReference(null, signersId)); //$NON-NLS-1$
            statics[si++] = new Field("<protectionDomain>", Type.OBJECT, protectionDomainId == 0 ? null : new ObjectReference(null, protectionDomainId)); //$NON-NLS-1$
            if (reserved1Id != 0)
                statics[si++] = new Field("<reserved1>", Type.OBJECT, reserved1Id == 0 ? null : new ObjectReference(null, reserved1Id)); //$NON-NLS-1$
            if (reserved2Id != 0)
                statics[si++] = new Field("<reserved2>", Type.OBJECT, reserved2Id == 0 ? null : new ObjectReference(null, reserved2Id)); //$NON-NLS-1$
            Field all[] = new Field[statics.length + constantPool.length];
            System.arraycopy(statics,  0,  all,  0,  statics.length);
            System.arraycopy(constantPool,  0,  all,  statics.length, constantPool.length);
            statics = all;
        }

        // instance fields: u2 num ( name ID, u1 type )
        int numInstanceFields = in.readUnsignedShort();
        FieldDescriptor[] fields = new FieldDescriptor[numInstanceFields];

        for (int ii = 0; ii < numInstanceFields; ii++)
        {
            long nameId = in.readID(idSize);
            String name = getStringConstant(nameId);

            byte type = in.readByte();
            fields[ii] = new FieldDescriptor(name, type);
        }

        // get name
        String className = class2name.get(address);
        if (className == null)
            className = "unknown-name@0x" + Long.toHexString(address); //$NON-NLS-1$

        if (className.charAt(0) == '[') // quick check if array at hand
        {
            // fix object class names
            Matcher matcher = PATTERN_OBJ_ARRAY.matcher(className);
            if (matcher.matches())
            {
                int l = matcher.group(1).length();
                className = matcher.group(2);
                for (int ii = 0; ii < l; ii++)
                    className += "[]"; //$NON-NLS-1$
            }

            // primitive arrays
            matcher = PATTERN_PRIMITIVE_ARRAY.matcher(className);
            if (matcher.matches())
            {
                int count = matcher.group(1).length() - 1;
                className = "unknown[]"; //$NON-NLS-1$

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
                    className += "[]"; //$NON-NLS-1$
            }
        }

        ClassImpl clazz = new ClassImpl(address, className, superClassObjectId, classLoaderObjectId, statics, fields);
        // This will be replaced by a size calculated from the field sizes
        clazz.setHeapSizePerInstance(instsize);
        handler.addClass(clazz, segmentStartPos);
        
        // Just in case the superclass is missing
        if (superClassObjectId != 0 && handler.lookupClass(superClassObjectId) == null)
        {
            // Try to calculate how big the superclass should be
            int ownFieldsSize = 0;
            for (FieldDescriptor field : clazz.getFieldDescriptors())
            {
                int type = field.getType();
                if (type == IObject.Type.OBJECT)
                    ownFieldsSize += idSize;
                else
                    ownFieldsSize += IPrimitiveArray.ELEMENT_SIZE[type];
            }
            int supersize = Math.max(instsize - ownFieldsSize, 0);
            // A real size of an instance will override this
            handler.reportRequiredClass(superClassObjectId, supersize);
        }

        // Check / set types of classes
        if (class2type.containsKey(address))
        {
            // Already seen an instance dump for class type
            long typeId = class2type.get(address);
            IClass type = handler.lookupClass(typeId);
            if (type instanceof ClassImpl)
            {
                clazz.setClassInstance((ClassImpl)type);
            }
        }
        for (Iterator<Entry<Long>>it = class2type.entries(); it.hasNext(); )
        {
            Entry<Long>e = it.next();
            if (e.getValue() == address)
            {
                // Existing class has this class as its type
                IClass base = handler.lookupClass(e.getKey());
                if (base instanceof ClassImpl)
                {
                    ClassImpl baseCls = (ClassImpl)base;
                    baseCls.setClassInstance(clazz);
                }
            }
        }
    }

    private void readInstanceDump(long segmentStartPos) throws IOException
    {
        long address = in.readID(idSize);
        handler.reportInstance(address, segmentStartPos);
        in.skipBytes(4); // stack trace serial
        long classID = in.readID(idSize);
        int payload = in.readInt();
        // check if class needs to be created
        IClass instanceType = handler.lookupClass(classID);
        if (instanceType == null)
            handler.reportRequiredClass(classID, payload);
        else
        {
            // If this is a instance record for a class
            IClass instanceCls = handler.lookupClass(address);
            if (instanceCls instanceof ClassImpl)
            {
                // Set the type here
                ClassImpl instClsImpl = (ClassImpl)instanceCls;
                instClsImpl.setClassInstance((ClassImpl) instanceType);
            }
        }
        // Is this actually a class?
        if (class2name.containsKey(address))
        {
            // record its type
            class2type.put(address, classID);
        }

        in.skipBytes(payload);
    }

    private void readObjectArrayDump(long segmentStartPos) throws IOException
    {
        long address = in.readID(idSize);
        if (!foundCompressed && idSize == 8 && address > previousArrayStart && address < previousArrayUncompressedEnd)
        {
            monitor.sendUserMessage(
                            Severity.INFO,
                            MessageUtil.format(Messages.Pass1Parser_DetectedCompressedReferences,
                                            Long.toHexString(address), Long.toHexString(previousArrayStart)), null);
            handler.addProperty(IHprofParserHandler.REFERENCE_SIZE, "4"); //$NON-NLS-1$
            foundCompressed = true;
        }

        handler.reportInstance(address, segmentStartPos);

        in.skipBytes(4); // stack trace serial
        int size = in.readInt();
        long arrayClassObjectID = in.readID(idSize);

        // check if class needs to be created
        IClass arrayType = handler.lookupClass(arrayClassObjectID);
        if (arrayType == null)
            handler.reportRequiredObjectArray(arrayClassObjectID);

        in.skipBytes((long) size * idSize);
        previousArrayStart = address;
        previousArrayUncompressedEnd = address + 16 + (long)size * 8;
    }

    private void readPrimitiveArrayDump(long segmentStartPos) throws SnapshotException, IOException
    {
        long address = in.readID(idSize);
        handler.reportInstance(address, segmentStartPos);

        in.skipBytes(4);
        int size = in.readInt();
        byte elementType = in.readByte();

        if ((elementType < IPrimitiveArray.Type.BOOLEAN) || (elementType > IPrimitiveArray.Type.LONG))
            throw new SnapshotException(Messages.Pass1Parser_Error_IllegalType);

        // check if class needs to be created
        String name = IPrimitiveArray.TYPE[elementType];
        IClass clazz = handler.lookupClassByName(name, true);
        if (clazz == null)
            handler.reportRequiredPrimitiveArray(elementType);

        int elementSize = IPrimitiveArray.ELEMENT_SIZE[elementType];
        in.skipBytes((long) elementSize * size);
    }

    private String getStringConstant(long address)
    {
        if (address == 0L)
            return ""; //$NON-NLS-1$

        String result = constantPool.get(address);
        return result == null ? MessageUtil.format(Messages.Pass1Parser_Error_UnresolvedName, Long.toHexString(address)) : result;
    }

    private void dumpThreads()
    {
        // noticed that one stack trace with empty stack is always reported,
        // even if the dump has no call stacks info
        if (serNum2stackTrace == null || serNum2stackTrace.size() <= 1)
            return;

        PrintWriter out = null;
        String outputName = handler.getSnapshotInfo().getPrefix() + "threads"; //$NON-NLS-1$
        try
        {
            out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputName), "UTF-8")); //$NON-NLS-1$

            Iterator<StackTrace> it = serNum2stackTrace.values();
            while (it.hasNext())
            {
                StackTrace stack = it.next();
                Long tid = thread2id.get(stack.threadSerialNr);
                if (tid == null)
                    continue;
                String threadId = tid == null ? "<unknown>" : "0x" + Long.toHexString(tid); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("Thread " + threadId); //$NON-NLS-1$
                out.println(stack);
                out.println("  locals:"); //$NON-NLS-1$
                List<JavaLocal> locals = thread2locals.get(stack.threadSerialNr);
                if (locals != null)
                {
                    for (JavaLocal javaLocal : locals)
                    {
                        out.println("    objectId=0x" + Long.toHexString(javaLocal.objectId) + ", line=" + javaLocal.lineNumber); //$NON-NLS-1$ //$NON-NLS-2$

                    }
                }
                out.println();
            }
            out.flush();
            this.monitor.sendUserMessage(Severity.INFO,
                            MessageUtil.format(Messages.Pass1Parser_Info_WroteThreadsTo, outputName), null);
        }
        catch (IOException e)
        {
            this.monitor.sendUserMessage(Severity.WARNING,
                            MessageUtil.format(Messages.Pass1Parser_Error_WritingThreadsInformation), e);
        }
        finally
        {
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (Exception ignore)
                {
                    // $JL-EXC$
                }
            }
        }

    }

    private class StackFrame
    {
        long frameId;
        String method;
        String methodSignature;
        String sourceFile;
        long classSerNum;

        /*
         * > 0 line number 0 no line info -1 unknown location -2 compiled method
         * -3 native method
         */
        int lineNr;

        public StackFrame(long frameId, int lineNr, String method, String methodSignature, String sourceFile,
                        long classSerNum)
        {
            super();
            this.frameId = frameId;
            this.lineNr = lineNr;
            this.method = method;
            this.methodSignature = methodSignature;
            this.sourceFile = sourceFile;
            this.classSerNum = classSerNum;
        }

        @Override
        public String toString()
        {
            String className = null;
            Long classId = classSerNum2id.get(classSerNum);
            if (classId == null)
            {
                className = "<UNKNOWN CLASS>"; //$NON-NLS-1$
            }
            else
            {
                className = class2name.get(classId);
            }

            String sourceLocation = ""; //$NON-NLS-1$
            if (lineNr > 0)
            {
                sourceLocation = "(" + sourceFile + ":" + String.valueOf(lineNr) + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            else if (lineNr == 0 || lineNr == -1)
            {
                sourceLocation = "(Unknown Source)"; //$NON-NLS-1$
            }
            else if (lineNr == -2)
            {
                sourceLocation = "(Compiled method)"; //$NON-NLS-1$
            }
            else if (lineNr == -3)
            {
                sourceLocation = "(Native Method)"; //$NON-NLS-1$
            }

            return "  at " + className + "." + method + methodSignature + " " + sourceLocation; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

    }

    private class StackTrace
    {
        private long threadSerialNr;
        private long[] frameIds;

        public StackTrace(long serialNr, long threadSerialNr, long[] frameIds)
        {
            super();
            this.frameIds = frameIds;
            this.threadSerialNr = threadSerialNr;
        }

        @Override
        public String toString()
        {
            StringBuilder b = new StringBuilder();
            for (long frameId : frameIds)
            {
                StackFrame frame = id2frame.get(frameId);
                if (frame != null)
                {
                    b.append(frame.toString());
                    b.append("\r\n"); //$NON-NLS-1$
                }

            }
            return b.toString();
        }

    }

    private class JavaLocal
    {
        private long objectId;
        private int lineNumber;
        private int type;

        public JavaLocal(long objectId, int lineNumber, int type)
        {
            super();
            this.lineNumber = lineNumber;
            this.objectId = objectId;
            this.type = type;
        }

        public int getType()
        {
            return type;
        }
    }
}

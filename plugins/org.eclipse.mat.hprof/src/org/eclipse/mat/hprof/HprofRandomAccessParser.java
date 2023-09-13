/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG, Netflix, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *    IBM Corporation (Andrew Johnson) - compressed dumps
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.AbstractParser.Constants.Record;
import org.eclipse.mat.hprof.describer.Version;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.io.BufferedRandomAccessInputStream;
import org.eclipse.mat.parser.model.AbstractObjectImpl;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.ClassLoaderImpl;
import org.eclipse.mat.parser.model.InstanceImpl;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.MessageUtil;

public class HprofRandomAccessParser extends AbstractParser
{
    public static final int LAZY_LOADING_LIMIT = 256;
    private final IPositionInputStream in;

    public HprofRandomAccessParser(File file, String prefix, Version version, int identifierSize, long len,
                    HprofPreferences.HprofStrictness strictnessPreference) throws IOException
    {
        super(strictnessPreference);
        RandomAccessFile raf = new RandomAccessFile(file, "r"); //$NON-NLS-1$
        boolean gzip = CompressedRandomAccessFile.isGZIP(raf);
        if (gzip)
        {
            ChunkedGZIPRandomAccessFile cgraf = ChunkedGZIPRandomAccessFile.get(raf, file, prefix);
            raf.close();

            if (cgraf != null)
            {
                raf = cgraf;
            }
            else
            {
                long requested = len / 10;
                long maxFree = CompressedRandomAccessFile.checkMemSpace(requested);
                // If we are very memory constrained use a file cache
                if (requested > maxFree && FileCacheCompressedRandomAccessFile.isDiskSpace(file, len))
                    raf = new FileCacheCompressedRandomAccessFile(file);
                else
                    raf = new CompressedRandomAccessFile(file, true, len);
            }
        }
        this.in = new DefaultPositionInputStream(new BufferedRandomAccessInputStream(raf, 512));
        this.version = version;
        this.idSize = identifierSize;
    }

    public synchronized void close() throws IOException
    {
        in.close();
    }

    public synchronized IObject read(int objectId, long position, ISnapshot dump, IOne2LongIndex o2hprof) throws IOException, SnapshotException
    {
        in.seek(position);
        int segmentType = in.readUnsignedByte();
        if (objectId == -1)
        {
            segmentType = skipRecords(segmentType);
        }
        switch (segmentType)
        {
            case Constants.Record.STACK_FRAME:
                return readStackFrame(objectId, dump);
            case Constants.DumpSegment.INSTANCE_DUMP:
                return readInstanceDump(objectId, dump);
            case Constants.DumpSegment.OBJECT_ARRAY_DUMP:
                return readObjectArrayDump(objectId, dump);
            case Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP:
                return readPrimitiveArrayDump(objectId, dump);
            default:
                throw new IOException(MessageUtil.format(Messages.HprofRandomAccessParser_Error_IllegalDumpSegment,
                                segmentType, Long.toHexString(position)));
        }

    }

    private IObject readStackFrame(int objectId, ISnapshot dump) throws SnapshotException, IOException
    {
        in.readUnsignedInt(); // time
        in.readUnsignedInt(); // length
        long frameId = in.readID(idSize);
        in.readID(idSize); // methodName
        in.readID(idSize); // methodSig
        in.readID(idSize); // srcFile
        in.readUnsignedInt(); // classSerNum
        int lineNr = in.readInt(); // can be negative
        IClass classImpl = dump.getClassOf(objectId);
        List<Field>fields = new ArrayList<Field>();
        Field f = new Field(LINE_NUMBER, IObject.Type.INT, lineNr);
        fields.add(f);
        int compLevel = lineNr == -2 ? 1 : 0;
        f = new Field(COMPILATION_LEVEL, IObject.Type.INT, compLevel);
        fields.add(f);
        boolean nativ = lineNr == -3;
        f = new Field(NATIVE, IObject.Type.BOOLEAN, nativ);
        fields.add(f);
        f = new Field(LOCATION_ADDRESS, IObject.Type.LONG, (long)frameId);
        fields.add(f);
        // Identify more stack frame information from the .threads index
        int thrds[] = dump.getInboundRefererIds(objectId);
     l: for (int thrd : thrds)
        {
            IThreadStack stack = dump.getThreadStack(thrd);
            if (stack != null)
            {
                for (int fm = 0; fm < stack.getStackFrames().length; ++fm)
                {
                    IStackFrame frm = stack.getStackFrames()[fm];
                    for (int oo : frm.getLocalObjectsIds())
                    {
                        if (oo == objectId)
                        {
                            f = new Field(FRAME_NUMBER, IObject.Type.INT, fm);
                            fields.add(f);
                            f = new Field(STACK_DEPTH, IObject.Type.INT, stack.getStackFrames().length - fm);
                            fields.add(f);
                            // For HPROF from DTFJ, <method> might declare FILE_NAME field
                            for (IClass cls = classImpl; cls != null; cls = cls.getSuperClass())
                            {
                                for (FieldDescriptor fd : cls.getFieldDescriptors())
                                {
                                    if (fd.getName().equals(METHOD_NAME))
                                    {
                                        String stkLine = frm.getText().replaceFirst("\\s*at\\s+([^ ]+).*", "$1");  //$NON-NLS-1$ //$NON-NLS-2$
                                        f = new Field(METHOD_NAME, IObject.Type.OBJECT, stkLine);
                                        fields.add(f);
                                    }
                                    else if (fd.getName().equals(FILE_NAME))
                                    {
                                        String stkLine = frm.getText().replaceFirst("\\s*at\\s+[^ ]+\\s+\\(([^:()]*).*", "$1");  //$NON-NLS-1$ //$NON-NLS-2$
                                        // E.g. from: at com.ibm.misc.SignalDispatcher.waitForSignal()I ((Native Method))
                                        // resolver needs null not blank
                                        if (stkLine.isEmpty())
                                            stkLine = null;
                                        f = new Field(FILE_NAME, IObject.Type.OBJECT, stkLine);
                                        fields.add(f);
                                    }
                                }
                            }
                            break l;
                        }
                    }
                }
            }
        }
        long objectAddress = dump.mapIdToAddress(objectId);
        return new InstanceImpl(objectId, objectAddress, (ClassImpl)classImpl, fields);
    }

    public List<IClass> resolveClassHierarchy(ISnapshot snapshot, IClass clazz) throws SnapshotException
    {
        List<IClass> answer = new ArrayList<IClass>();
        answer.add(clazz);
        while (clazz.hasSuperClass())
        {
            clazz = (IClass) snapshot.getObject(clazz.getSuperClassId());
            if (clazz == null)
                return null;
            answer.add(clazz);
        }

        return answer;
    }

    /**
     * A reference to an object via its address
     * which can handle the address not being in the index.
     */
    static class ObjectAddressReference extends ObjectReference
    {
        private static final long serialVersionUID = 1L;
        transient IOne2LongIndex o2hprof;
        transient ISnapshot snapshot;
        transient HprofRandomAccessParser parser;
        /**
         * Construct an {@link ObjectAddressReference} which can be used to retrieve an
         * object by address even if it has not been indexed.
         * Used as a proxy by {@link ObjectReference} which can change the {@link ObjectReference#address}
         * private field.
         * @param snapshot the snapshot
         * @param parser the HPROF parser used to build the IObject if required
         * @param o2hprof the index from object ID to HPROF file position
         * @param address the address of the object
         */
        public ObjectAddressReference(ISnapshot snapshot, HprofRandomAccessParser parser, IOne2LongIndex o2hprof, long address)
        {
            super(snapshot, address);
            this.snapshot = snapshot;
            this.o2hprof = o2hprof;
            this.parser = parser;
        }
        @Override
        public int getObjectId() throws SnapshotException
        {
            try
            {
                int id = super.getObjectId();
                return id;
            }
            catch (SnapshotException e)
            {
                return -1;
            }
        }
        /**
         * Actually construct an IObject.
         * First try constructing by object ID.
         * If that fails then construct by address.
         * Find the indexed objects before and after this.
         * Parse the HPROF file between these two points looking for an
         * object of the correct address.
         */
        @Override
        public IObject getObject() throws SnapshotException
        {
            SnapshotException e1 = null;
            try
            {
                int id = super.getObjectId();
                if (id >= 0)
                {
                    IObject o = super.getObject();
                    return o;
                }
            }
            catch (SnapshotException e)
            {
                e1 = e;
            }
            // Find the object IDs before and after this object
            int low = 0;
            int high = o2hprof.size() - 1;
            for (;low + 1 < high;)
            {
                int mid = (low + high) >>> 1;
                long midAddr = snapshot.mapIdToAddress(mid);
                if (getObjectAddress() < midAddr)
                {
                    high = mid;
                }
                else if (getObjectAddress() > midAddr)
                {
                    low = mid;
                }
                else
                {
                    low = mid;
                    high = mid;
                }
            }
            // Some objects don't have a position (classes?), so find valid file positions
            long pos = 0;
            while ((pos = o2hprof.get(low)) == 0 && low > 0)
                --low;
            if (pos == 0)
            {
                // There might be discarded objects before the first
                Long lpos = (Long)snapshot.getSnapshotInfo().getProperty(HprofHeapObjectReader.HPROF_HEAP_START);
                if (lpos != null)
                    pos = lpos;
                else while ((pos = o2hprof.get(low)) == 0 && low < o2hprof.size() - 1)
                    ++low;
            }
            long posHigh = pos;
            while (high >= 0 && (posHigh = o2hprof.get(high)) == 0 && high < o2hprof.size() - 1)
                ++high;
            if (posHigh == 0)
            {
                // There might be discarded objects after the last
                Long olen = (Long)snapshot.getSnapshotInfo().getProperty(HprofHeapObjectReader.HPROF_LENGTH_PROPERTY);
                posHigh = (olen != null) ? olen : Long.MAX_VALUE;
            }
            // Reparse the HPROF file looking for an object of the right address
            try
            {
                do
                {
                    IObject o;
                    synchronized (parser)
                    {
                        // Need final position without interference
                        o = parser.read(-1, pos, snapshot, o2hprof);
                        pos = parser.in.position();
                    }
                    if (o.getObjectAddress() == getObjectAddress())
                    {
                        // Class was discarded from the snapshot
                        if (o.getClazz() == null)
                            throw new IOException();
                        ((AbstractObjectImpl)o).setSnapshot(snapshot);
                        return o;
                    }
                    // Gone past the object address we want
                    if (o.getObjectAddress() > getObjectAddress())
                        break;
                }
                while (pos < posHigh);
            }
            catch (IOException e2)
            {
                if (e2 instanceof EOFException)
                    throw e1;
                throw new SnapshotException(e2);
            }
            // Not found, so throw the original exception
            throw e1;
        }
    }

    private IObject readInstanceDump(int objectId, ISnapshot dump) throws IOException, SnapshotException
    {
        long address = in.readID(idSize);
        IClass oclazz;
        if (objectId >= 0)
        {
            // Skip serial number, class ID, length
            if (checkSkipBytes(8 + idSize) != 8 + idSize)
                throw new IOException();
            oclazz = dump.getClassOf(objectId);
        }
        else
        {
            // skip serial number
            if (checkSkipBytes(4) != 4)
                throw new IOException();
            // class ID
            long classAddr = in.readID(idSize);
            // length
            long len = in.readUnsignedInt();
            try
            {
                int classID = dump.mapAddressToId(classAddr);
                IObject io = dump.getObject(classID);
                if (io instanceof IClass)
                    oclazz = (IClass)io;
                else
                    throw new IOException();
            }
            catch (SnapshotException e)
            {
                // move to end of object
                if (checkSkipBytes(len) != len)
                    throw new IOException();
                // Invalid object, but might be good enough for skipping over
                return new InstanceImpl(objectId, address, null, null);
            }
        }

        // check if we need to defer reading the class
        List<IClass> hierarchy = resolveClassHierarchy(dump, oclazz);
        if (hierarchy == null)
        {
            throw new IOException(Messages.HprofRandomAccessParser_Error_DumpIncomplete);
        }
        else
        {
            List<Field> instanceFields = new ArrayList<Field>();
            for (IClass clazz : hierarchy)
            {
                List<FieldDescriptor> fields = clazz.getFieldDescriptors();
                for (int ii = 0; ii < fields.size(); ii++)
                {
                    FieldDescriptor field = fields.get(ii);
                    int type = field.getType();
                    Object value = readValue(in, dump, type);
                    instanceFields.add(new Field(field.getName(), field.getType(), value));
                }
            }

            ClassImpl classImpl = (ClassImpl) hierarchy.get(0);

            if (dump.isClassLoader(objectId))
                return new ClassLoaderImpl(objectId, address, classImpl, instanceFields);
            else
                return new InstanceImpl(objectId, address, classImpl, instanceFields);
        }
    }

    private IArray readObjectArrayDump(int objectId, ISnapshot dump) throws IOException, SnapshotException
    {
        long id = in.readID(idSize);

        checkSkipBytes(4);
        int size = in.readInt();
        long len = (long)size * idSize;

        long arrayClassObjectID = in.readID(idSize);

        int typeId;
        if (objectId == -1)
        {
            try
            {
                typeId = dump.mapAddressToId(arrayClassObjectID);
            }
            catch (SnapshotException e)
            {
                ObjectArrayImpl array = new ObjectArrayImpl(objectId, id, null, size);
                // Move to end of object
                if (checkSkipBytes(len) != len)
                    throw new IOException();
                return array;
            }
        }
        else
        {
            typeId = dump.mapAddressToId(arrayClassObjectID);
        }
        IClass arrayType = (IClass) dump.getObject(typeId);
        if (arrayType == null)
            throw new RuntimeException(Messages.HprofRandomAccessParser_Error_MissingFakeClass);

        Object content = null;
        if (len < LAZY_LOADING_LIMIT)
        {
            long[] data = new long[size];
            for (int ii = 0; ii < data.length; ii++)
                data[ii] = in.readID(idSize);
            content = data;
        }
        else
        {
            content = new ArrayDescription.Offline(false, in.position(), 0, size);
            if (objectId == -1)
            {
                // Move to end of object
                if (checkSkipBytes(len) != len)
                    throw new IOException();
            }
        }

        ObjectArrayImpl array = new ObjectArrayImpl(objectId, id, (ClassImpl) arrayType, size);
        array.setInfo(content);
        return array;
    }

    private IArray readPrimitiveArrayDump(int objectId, ISnapshot dump) throws IOException, SnapshotException
    {
        long id = in.readID(idSize);

        checkSkipBytes(4);
        int arraySize = in.readInt();

        long elementType = in.readByte();
        if ((elementType < IPrimitiveArray.Type.BOOLEAN) || (elementType > IPrimitiveArray.Type.LONG))
            throw new IOException(Messages.Pass1Parser_Error_IllegalType);

        int elementSize = IPrimitiveArray.ELEMENT_SIZE[(int) elementType];
        long len = elementSize * (long)arraySize;

        Object content = null;
        if (len < LAZY_LOADING_LIMIT)
        {
            byte[] data = new byte[(int)len];
            in.readFully(data);
            content = elementType == IObject.Type.BYTE ? data : new ArrayDescription.Raw(data);
        }
        else
        {
            content = new ArrayDescription.Offline(true, in.position(), elementSize, arraySize);
            if (objectId == -1)
            {
                // Move to end of object
                if (checkSkipBytes(len) != len)
                    throw new IOException();
            }
        }

        // lookup class by name
        IClass clazz = null;
        String name = IPrimitiveArray.TYPE[(int) elementType];
        Collection<IClass> classes = dump.getClassesByName(name, false);
        if (classes == null || classes.isEmpty())
        {
            if (objectId == -1)
            {
                // Return a dummy array which can be ignored
                PrimitiveArrayImpl array = new PrimitiveArrayImpl(objectId, id, (ClassImpl) clazz, arraySize, (int) elementType);
                return array;
            }
            throw new IOException(MessageUtil.format(Messages.HprofRandomAccessParser_Error_MissingClass, name));
        }
        else if (classes.size() > 1)
            throw new IOException(MessageUtil.format(Messages.HprofRandomAccessParser_Error_DuplicateClass, name));
        else
            clazz = classes.iterator().next();

        PrimitiveArrayImpl array = new PrimitiveArrayImpl(objectId, id, (ClassImpl) clazz, arraySize, (int) elementType);
        array.setInfo(content);

        return array;
    }

    public synchronized long[] readObjectArray(ArrayDescription.Offline descriptor, int offset, int length)
                    throws IOException
    {
        int elementSize = this.idSize;

        in.seek(descriptor.getPosition() + ((long)offset * elementSize));
        long[] data = new long[length];
        for (int ii = 0; ii < data.length; ii++)
            data[ii] = in.readID(idSize);
        return data;
    }

    public synchronized byte[] readPrimitiveArray(ArrayDescription.Offline descriptor, int offset, int length)
                    throws IOException
    {
        int elementSize = descriptor.getElementSize();

        in.seek(descriptor.getPosition() + ((long)offset * elementSize));

        byte[] data = new byte[length * elementSize];
        in.readFully(data);
        return data;
    }

    private int skipRecords(int segmentType) throws IOException
    {
        boolean again = true;
        do
        {
            int skip = -1;
            switch (segmentType)
            {
                case Record.HEAP_DUMP_SEGMENT:
                    skip = 8;
                    break;
                case Constants.DumpSegment.ROOT_UNKNOWN:
                case Constants.DumpSegment.ROOT_STICKY_CLASS:
                case Constants.DumpSegment.ROOT_MONITOR_USED:
                    skip = idSize;
                    break;
                case Constants.DumpSegment.ROOT_JNI_GLOBAL:
                    skip = idSize * 2;
                    break;
                case Constants.DumpSegment.ROOT_JNI_LOCAL:
                case Constants.DumpSegment.ROOT_JAVA_FRAME:
                case Constants.DumpSegment.ROOT_THREAD_OBJECT:
                    skip = idSize + 8;
                    break;
                case Constants.DumpSegment.ROOT_NATIVE_STACK:
                case Constants.DumpSegment.ROOT_THREAD_BLOCK:
                    skip = idSize + 4;
                    break;
                case Constants.DumpSegment.CLASS_DUMP:
                    skipClassDump();
                    // Already skipped enough, so just reread
                    skip = 0;
                    break;
                default:
                    again = false;
                    break;
            }
            if (skip >= 0)
            {
                // Skip over new segment header etc.
                checkSkipBytes(skip);
                segmentType = in.readUnsignedByte();
            }
        }
        while (again);
        return segmentType;
    }

    private void skipClassDump() throws IOException
    {
        checkSkipBytes(7 * idSize + 8);

        int constantPoolSize = in.readUnsignedShort();
        for (int ii = 0; ii < constantPoolSize; ii++)
        {
            checkSkipBytes(2);
            skipValue(in);
        }

        int numStaticFields = in.readUnsignedShort();
        for (int i = 0; i < numStaticFields; i++)
        {
            checkSkipBytes(idSize);
            skipValue(in);
        }

        int numInstanceFields = in.readUnsignedShort();
        checkSkipBytes((idSize + 1) * numInstanceFields);
    }

    private int checkSkipBytes(int skip) throws IOException
    {
        int left = skip;
        while (left > 0)
        {
            int skipped = in.skipBytes(left);
            if (skipped == 0)
            {
                in.readByte();
                skipped = 1;
            }
            left -= skipped;
        }
        return skip - left;
    }

    private long checkSkipBytes(long skip) throws IOException
    {
        long left = skip;
        while (left > 0)
        {
            int skipped = in.skipBytes(Math.min(left, Integer.MAX_VALUE));
            if (skipped == 0)
            {
                in.readByte();
                skipped = 1;
            }
            left -= skipped;
        }
        return skip - left;
    }
}

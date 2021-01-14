/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and others.
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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.describer.Version;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.parser.io.BufferedRandomAccessInputStream;
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
import org.eclipse.mat.util.MessageUtil;

public class HprofRandomAccessParser extends AbstractParser
{
    public static final int LAZY_LOADING_LIMIT = 256;
    private final IPositionInputStream in;

    public HprofRandomAccessParser(File file, Version version, int identifierSize, long len,
                    HprofPreferences.HprofStrictness strictnessPreference) throws IOException
    {
        super(strictnessPreference);
        RandomAccessFile raf = new RandomAccessFile(file, "r"); //$NON-NLS-1$
        boolean gzip = CompressedRandomAccessFile.isGZIP(raf);
        if (gzip)
        {
            raf.close();
            long requested = len / 10;
            long maxFree = CompressedRandomAccessFile.checkMemSpace(requested);
            // If we are very memory constrained use a file cache
            if (requested > maxFree && FileCacheCompressedRandomAccessFile.isDiskSpace(file, len))
                raf = new FileCacheCompressedRandomAccessFile(file);
            else
                raf = new CompressedRandomAccessFile(file, true, len);
        }
        this.in = new DefaultPositionInputStream(new BufferedRandomAccessInputStream(raf, 512));
        this.version = version;
        this.idSize = identifierSize;
    }

    public synchronized void close() throws IOException
    {
        in.close();
    }

    public synchronized IObject read(int objectId, long position, ISnapshot dump) throws IOException, SnapshotException
    {
        in.seek(position);
        int segmentType = in.readUnsignedByte();
        switch (segmentType)
        {
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

    private IObject readInstanceDump(int objectId, ISnapshot dump) throws IOException, SnapshotException
    {
        long address = in.readID(idSize);
        if (in.skipBytes(8 + idSize) != 8 + idSize)
            throw new IOException();

        // check if we need to defer reading the class
        List<IClass> hierarchy = resolveClassHierarchy(dump, dump.getClassOf(objectId));
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

        in.skipBytes(4);
        int size = in.readInt();

        long arrayClassObjectID = in.readID(idSize);

        IClass arrayType = (IClass) dump.getObject(dump.mapAddressToId(arrayClassObjectID));
        if (arrayType == null)
            throw new RuntimeException(Messages.HprofRandomAccessParser_Error_MissingFakeClass);

        Object content = null;
        if ((long)size * idSize < LAZY_LOADING_LIMIT)
        {
            long[] data = new long[size];
            for (int ii = 0; ii < data.length; ii++)
                data[ii] = in.readID(idSize);
            content = data;
        }
        else
        {
            content = new ArrayDescription.Offline(false, in.position(), 0, size);
        }

        ObjectArrayImpl array = new ObjectArrayImpl(objectId, id, (ClassImpl) arrayType, size);
        array.setInfo(content);
        return array;
    }

    private IArray readPrimitiveArrayDump(int objectId, ISnapshot dump) throws IOException, SnapshotException
    {
        long id = in.readID(idSize);

        in.skipBytes(4);
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
        }

        // lookup class by name
        IClass clazz = null;
        String name = IPrimitiveArray.TYPE[(int) elementType];
        Collection<IClass> classes = dump.getClassesByName(name, false);
        if (classes == null || classes.isEmpty())
            throw new IOException(MessageUtil.format(Messages.HprofRandomAccessParser_Error_MissingClass, name));
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
}

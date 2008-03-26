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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.parser.io.BufferedRandomAccessInputStream;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.AbstractArrayImpl;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.ClassLoaderImpl;
import org.eclipse.mat.parser.model.InstanceImpl;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.parser.model.AbstractArrayImpl.ArrayContentDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;


public class HprofRandomAccessParser extends HprofBasics
{
    public static final int LAZY_LOADING_LIMIT = 256;

    public HprofRandomAccessParser(File file, int version, int identifierSize) throws IOException
    {
        in = new PositionInputStream(new BufferedRandomAccessInputStream(new RandomAccessFile(file, "r"), 512));
        this.version = version;
        this.identifierSize = identifierSize;
    }

    public synchronized void close() throws IOException
    {
        in.close();
    }

    public synchronized IObject read(int objectId, long position, ISnapshot dump) throws IOException,
                    SnapshotException
    {
        in.seek(position);
        int type = in.readUnsignedByte();
        switch (type)
        {
            case HPROF_GC_INSTANCE_DUMP:
            {
                return readInstance(objectId, position, dump);
            }
            case HPROF_GC_OBJ_ARRAY_DUMP:
            {
                return readArray(objectId, false, dump);
            }
            case HPROF_GC_PRIM_ARRAY_DUMP:
            {
                return readArray(objectId, true, dump);
            }
            case HPROF_GC_ROOT_UNKNOWN:
            case HPROF_GC_ROOT_THREAD_OBJ:
            case HPROF_GC_ROOT_JNI_GLOBAL:
            case HPROF_GC_ROOT_JNI_LOCAL:
            case HPROF_GC_ROOT_JAVA_FRAME:
            case HPROF_GC_ROOT_NATIVE_STACK:
            case HPROF_GC_ROOT_STICKY_CLASS:
            case HPROF_GC_ROOT_THREAD_BLOCK:
            case HPROF_GC_ROOT_MONITOR_USED:
            case HPROF_GC_CLASS_DUMP:
            default:
            {
                throw new IOException("Unrecognized heap dump sub-record type:  " + type);
            }
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

    private IObject readInstance(int objectId, long position, ISnapshot dump) throws IOException, SnapshotException
    {
        long address = readID();
        // in.readInt(); // StackTrace stackTrace =
        // getStackTraceFromSerial(in.readInt());
        // /*long classID = */readID();
        // /* int bytesFollowing = */in.readInt();
        if (in.skipBytes(8 + identifierSize) != 8 + identifierSize)
            throw new IOException();

        // check if we need to defer reading the class
        List<IClass> hierarchy = resolveClassHierarchy(dump, dump.getClassOf(objectId));
        if (hierarchy == null)
        {
            throw new IOException("need to create dummy class. dump incomplete");
        }
        else
        {
            int readBytes = 0;
            List<Field> instanceFields = new ArrayList<Field>();
            for (IClass clazz : hierarchy)
            {
                List<FieldDescriptor> fields = clazz.getFieldDescriptors();
                for (int ii = 0; ii < fields.size(); ii++)
                {
                    FieldDescriptor field = fields.get(ii);
                    byte type = (byte) field.getSignature().charAt(0);
                    Object[] value = new Object[1];
                    readBytes += readValueForTypeSignature(dump, in, type, value);
                    instanceFields.add(new Field(field.getName(), field.getSignature(), value[0]));
                }
            }

            ClassImpl classImpl = (ClassImpl) hierarchy.get(0);

            if (dump.isClassLoader(objectId))
                return new ClassLoaderImpl(objectId, address, classImpl, instanceFields);
            else
                return new InstanceImpl(objectId, address, classImpl, instanceFields);
        }
    }

    private IArray readArray(int objectId, boolean isPrimitive, ISnapshot dump) throws IOException,
                    SnapshotException
    {
        long id = readID();
        in.readInt(); // stackTrace
        int arraySize = in.readInt();

        long elementClassID;
        if (isPrimitive)
        {
            elementClassID = in.readByte();
        }
        else
        {
            elementClassID = readID();
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
            Object content = null;
            int size = elSize * arraySize;

            if (size < LAZY_LOADING_LIMIT)
            {
                byte[] data = new byte[size];
                in.readFully(data);
                content = data;
            }
            else
            {
                content = new AbstractArrayImpl.ArrayContentDescriptor(true, in.position(), elSize, arraySize);
            }

            IClass clazz = null;
            Collection<IClass> classes = dump.getClassesByName(IPrimitiveArray.TYPE[(int) elementClassID], false);
            if ((classes == null) || classes.isEmpty())
                throw new RuntimeException("missing fake class " + IPrimitiveArray.TYPE[(int) elementClassID]);
            else if (classes.size() > 1)
                throw new IOException("Duplicate class: " + IPrimitiveArray.TYPE[(int) elementClassID]);
            else
                clazz = classes.iterator().next();

            IPrimitiveArray answer = new PrimitiveArrayImpl(objectId, id, (ClassImpl) clazz, arraySize,
                            (int) elementClassID, content);
            return answer;
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

            IClass arrayType = null;
            if (arrayClassID != 0)
            {
                arrayType = (IClass) dump.getObject(dump.mapAddressToId(arrayClassID));
                if (arrayType == null) { throw new RuntimeException("missing fake class"); }
            }
            else if (elementClassID != 0)
            {
                arrayType = (IClass) dump.getObject(dump.mapAddressToId(elementClassID));
                if (arrayType != null)
                {
                    String name = arrayType.getName() + "[]";
                    Collection<IClass> classes = dump.getClassesByName(name, false);
                    if ((classes == null) || (classes.size() == 0))
                        throw new RuntimeException("missing fake class for array " + id);
                    else if (classes.size() > 1)
                        throw new IOException("Duplicate class: " + name);
                    else
                        arrayType = classes.iterator().next();
                }
            }

            Object content = null;
            if (arraySize * identifierSize < LAZY_LOADING_LIMIT)
            {
                long[] data = new long[arraySize];
                for (int ii = 0; ii < data.length; ii++)
                    data[ii] = readID();
                content = data;
            }
            else
            {
                content = new AbstractArrayImpl.ArrayContentDescriptor(false, in.position(), 0, arraySize);
            }

            IObjectArray answer = new ObjectArrayImpl(objectId, id, (ClassImpl) arrayType, arraySize, elementClassID,
                            arrayType.getObjectAddress(), content);
            return answer;
        }
    }

    public Object read(ArrayContentDescriptor descriptor) throws IOException
    {
        return read(descriptor, 0, descriptor.isPrimitive() ? descriptor.getElementSize() * descriptor.getArraySize()
                        : descriptor.getArraySize() * this.identifierSize);
    }

    public synchronized Object read(ArrayContentDescriptor descriptor, int offset, int length) throws IOException
    {
        in.seek(descriptor.getPosition() + offset);

        if (descriptor.isPrimitive())
        {
            byte[] data = new byte[length];
            in.readFully(data);
            return data;
        }
        else
        {
            long[] data = new long[length / this.identifierSize];
            for (int ii = 0; ii < data.length; ii++)
            {
                data[ii] = readID();
            }
            return data;
        }
    }
}

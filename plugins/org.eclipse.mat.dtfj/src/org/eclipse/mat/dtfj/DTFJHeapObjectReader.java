/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.IObjectReader;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.ClassLoaderImpl;
import org.eclipse.mat.parser.model.InstanceImpl;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;

import com.ibm.dtfj.image.CorruptData;
import com.ibm.dtfj.image.CorruptDataException;
import com.ibm.dtfj.image.DataUnavailable;
import com.ibm.dtfj.image.Image;
import com.ibm.dtfj.image.ImageAddressSpace;
import com.ibm.dtfj.image.ImagePointer;
import com.ibm.dtfj.image.ImageProcess;
import com.ibm.dtfj.image.MemoryAccessException;
import com.ibm.dtfj.java.JavaClass;
import com.ibm.dtfj.java.JavaClassLoader;
import com.ibm.dtfj.java.JavaField;
import com.ibm.dtfj.java.JavaHeap;
import com.ibm.dtfj.java.JavaLocation;
import com.ibm.dtfj.java.JavaMonitor;
import com.ibm.dtfj.java.JavaObject;
import com.ibm.dtfj.java.JavaRuntime;
import com.ibm.dtfj.java.JavaStackFrame;
import com.ibm.dtfj.java.JavaThread;

/**
 * @author ajohnson
 */
public class DTFJHeapObjectReader implements IObjectReader
{
    /**
     * Size above which we only return an array stub - the data is returned
     * piecemeal later
     */
    private static final int LARGE_ARRAY_SIZE = 1000;
    /** Whether to use stack frames and methods as objects and classes */
    private static final boolean getExtraInfo = false;
    /** the actual DTFJ image */
    private Image image;
    /** the JVM */
    private JavaRuntime jvm;
    /** the address space the Java runtime is in */
    private ImageAddressSpace addrSpace;
    /** the process the Java runtime is in */
    private ImageProcess process;
    /**
     * whether to give up and throw an exception if reading object data fails,
     * or whether to carry on getting more data
     */
    private static final boolean throwExceptions = true;

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.parser.IObjectReader#close()
     */
    public void close() throws IOException
    {
        // No way to close the dump, so just remove refs
        image = null;
        jvm = null;
        addrSpace = null;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.parser.IObjectReader#getAddon(java.lang.Class)
     */
    public <A> A getAddon(Class<A> addon) throws SnapshotException
    {
        if (image != null && addon.isAssignableFrom(image.getClass()))
        {
            return (A) image;
        }
        else if (jvm != null && addon.isAssignableFrom(jvm.getClass()))
        {
            return (A) jvm;
        }
        else if (addrSpace != null && addon.isAssignableFrom(addrSpace.getClass()))
        {
            return (A) addrSpace;
        }
        else if (process != null && addon.isAssignableFrom(process.getClass()))
        {
            return (A) process;
        }
        else
        {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.mat.parser.IObjectReader#open(org.eclipse.mat.snapshot.ISnapshot
     * )
     */
    public void open(ISnapshot snapshot) throws IOException
    {
        image = DTFJIndexBuilder.getDump(new File(snapshot.getSnapshotInfo().getPath()), snapshot.getSnapshotInfo()
                        .getProperty("$heapFormat")); //$NON-NLS-1$
        // Find the JVM
        jvm = DTFJIndexBuilder.getRuntime(image, null);
        try
        {
            addrSpace = DTFJIndexBuilder.getAddressSpace(jvm);
            // Find the process
            for (Iterator i2 = addrSpace.getProcesses(); i2.hasNext() && process == null;)
            {
                Object next2 = i2.next();
                if (next2 instanceof CorruptData)
                    continue;
                ImageProcess proc = (ImageProcess) next2;
                for (Iterator i3 = proc.getRuntimes(); i3.hasNext();)
                {
                    Object next3 = i3.next();
                    if (next3 instanceof CorruptData)
                        continue;
                    if (jvm.equals(next3))
                    {
                        process = proc;
                        break;
                    }
                }
            }
        }
        catch (CorruptDataException e)
        {
            IOException e2 = new IOException(Messages.DTFJHeapObjectReader_UnableToFindAddressSpace);
            e2.initCause(e);
            throw e2;
        }
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.parser.IObjectReader#read(int,
     * org.eclipse.mat.snapshot.ISnapshot)
     */
    public IObject read(int objectId, ISnapshot snapshot) throws SnapshotException, IOException
    {
        long addr = snapshot.mapIdToAddress(objectId);
        try
        {
            // DTFJ is not thread safe, but MAT is multi-threaded
            synchronized (image)
            {
                if (getExtraInfo)
                {
                    // See if the class looks like a method
                    ClassImpl cls = (ClassImpl) snapshot.getClassOf(objectId);
                    if (cls.getName().contains(DTFJIndexBuilder.METHOD_NAME))
                    {
                        // Get the special method/stack frame data
                        IObject ret = createObject2(snapshot, objectId, addr, cls);
                        if (ret != null)
                            return ret;
                    }
                }
                JavaObject jo = getJavaObjectByAddress0(addr);
                IObject inst;
                if (snapshot.isArray(objectId))
                {
                    inst = createArray(snapshot, objectId, addr, jo);
                }
                else
                {
                    inst = createObject(snapshot, objectId, addr, jo);
                }
                return inst;
            }
        }
        catch (MemoryAccessException e)
        {
            throw new SnapshotException(MessageFormat.format(Messages.DTFJHeapObjectReader_ErrorReadingObjectAtIndex,
                            objectId, format(addr)), e);
        }
        catch (CorruptDataException e)
        {
            throw new SnapshotException(MessageFormat.format(Messages.DTFJHeapObjectReader_ErrorReadingObjectAtIndex,
                            objectId, format(addr)), e);
        }
        catch (IllegalArgumentException e)
        {
            throw new SnapshotException(MessageFormat.format(Messages.DTFJHeapObjectReader_ErrorReadingObjectAtIndex,
                            objectId, format(addr)), e);
        }
    }

    /**
     * Create an MAT object for a stack frame special object
     * 
     * @param snapshot
     * @param objectId
     * @param addr
     * @param cls
     * @return
     */
    private IObject createObject2(ISnapshot snapshot, int objectId, long addr, ClassImpl cls)
    {
        // Use a search over every frame to find the right one
        if (getExtraInfo)
        {
            long prevFrameAddress = 0;
            for (Iterator i = jvm.getThreads(); i.hasNext();)
            {
                Object next = i.next();
                if (next instanceof CorruptData)
                {
                    continue;
                }
                JavaThread jt = (JavaThread) next;
                int pointerSize = snapshot.getSnapshotInfo().getIdentifierSize();
                // Count all the frames
                int totalDepth = 0;
                for (Iterator j = jt.getStackFrames(); j.hasNext(); ++totalDepth)
                {
                    j.next();
                }
                int depth = 0;
                for (Iterator j = jt.getStackFrames(); j.hasNext(); ++depth)
                {
                    Object next2 = j.next();
                    if (next2 instanceof CorruptData)
                    {
                        continue;
                    }
                    JavaStackFrame jsf = (JavaStackFrame) next2;
                    long currAddr = DTFJIndexBuilder.getFrameAddress(jsf, prevFrameAddress, pointerSize);
                    prevFrameAddress = currAddr;
                    if (currAddr == addr)
                    {
                        // These fields should match those in the method
                        // class
                        List<Field> fields = new ArrayList<Field>();
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            Field f = new Field("lineNumber", IObject.Type.INT, Integer.valueOf(jl.getLineNumber())); //$NON-NLS-1$
                            fields.add(f);
                        }
                        catch (DataUnavailable e)
                        {}
                        catch (CorruptDataException e)
                        {}
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            Field f = new Field("compilationLevel", IObject.Type.INT, Integer.valueOf(jl //$NON-NLS-1$
                                            .getCompilationLevel()));
                            fields.add(f);
                        }
                        catch (ClassCastException e)
                        {
                            // Sov problem
                        }
                        catch (CorruptDataException e)
                        {}
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            Field f = new Field("locationAddress", IObject.Type.LONG, Long.valueOf(jl //$NON-NLS-1$
                                            .getAddress().getAddress()));
                            fields.add(f);
                        }
                        catch (ClassCastException e)
                        {
                            // Sov problem
                        }
                        catch (CorruptDataException e)
                        {}
                        Field f = new Field("frameNumber", IObject.Type.INT, Integer.valueOf(depth)); //$NON-NLS-1$
                        fields.add(f);
                        f = new Field("stackDepth", IObject.Type.INT, Integer.valueOf(totalDepth - depth)); //$NON-NLS-1$
                        fields.add(f);
                        InstanceImpl inst = new InstanceImpl(objectId, addr, cls, fields);
                        return inst;
                    }
                }
            }
        }
        // Not found
        return null;
    }

    /**
     * Read a JavaObject using the general DTFJ method, if available
     * 
     * @param addr
     *            the address of the Java object in the address space
     * @return The Java Object at the given address
     * @throws CorruptDataException
     */
    private JavaObject getJavaObjectByAddress0(long addr) throws CorruptDataException, MemoryAccessException
    {
        ImagePointer ip = addrSpace.getPointer(addr);
        try
        {
            // Previous versions of DTFJ might not have this method, so handle
            // that possibility
            JavaObject jo = jvm.getObjectAtAddress(ip);
            return jo;
        }
        catch (DataUnavailable e)
        {
            return getJavaObjectByAddress(addr);
        }
        catch (LinkageError e)
        {
            return getJavaObjectByAddress(addr);
        }
    }

    /**
     * Slow, but universal object finder. Scans over every object it can find.
     * 
     * @param addr
     *            the address of the Java object in the address space
     * @return The Java Object at the given address
     * @throws CorruptDataException
     */
    private JavaObject getJavaObjectByAddress(long addr)
    {
        for (Iterator i = jvm.getHeaps(); i.hasNext();)
        {
            Object next = i.next();
            if (next instanceof CorruptData)
            {
                continue;
            }
            JavaHeap jh = (JavaHeap) next;
            for (Iterator j = jh.getObjects(); j.hasNext();)
            {
                Object next2 = j.next();
                if (next2 instanceof CorruptData)
                {
                    continue;
                }
                JavaObject jo = (JavaObject) next2;
                if (jo.getID().getAddress() == addr)
                {
                    // System.out.println("At "+format(addr)+" found JavaObject
                    // "+jo+" by scanning entire heap");
                    return jo;
                }
            }
        }

        // Now look for thread objects
        for (Iterator i = jvm.getThreads(); i.hasNext();)
        {
            Object next = i.next();
            if (next instanceof CorruptData)
            {
                continue;
            }
            JavaThread thrd = (JavaThread) next;
            try
            {
                JavaObject jo = thrd.getObject();
                if (jo != null && jo.getID().getAddress() == addr) { return jo; }
            }
            catch (CorruptDataException e)
            {}
        }

        // Now look for monitor objects
        for (Iterator i = jvm.getMonitors(); i.hasNext();)
        {
            Object next = i.next();
            if (next instanceof CorruptData)
            {
                continue;
            }
            JavaMonitor mon = (JavaMonitor) next;
            JavaObject jo = mon.getObject();
            if (jo != null && jo.getID().getAddress() == addr) { return jo; }
        }

        // Now look for class loader objects
        for (Iterator i = jvm.getJavaClassLoaders(); i.hasNext();)
        {
            Object next = i.next();
            if (next instanceof CorruptData)
            {
                continue;
            }
            JavaClassLoader ldr = (JavaClassLoader) next;
            try
            {
                JavaObject jo = ldr.getObject();
                if (jo != null && jo.getID().getAddress() == addr) { return jo; }
            }
            catch (CorruptDataException e)
            {}
        }

        throw new IllegalArgumentException(MessageFormat.format(
                        Messages.DTFJHeapObjectReader_JavaObjectAtAddressNotFound, format(addr)));
    }

    /**
     * @param snapshot
     * @param objectId
     * @param addr
     * @param jo
     * @return
     * @throws CorruptDataException
     * @throws SnapshotException
     */
    private InstanceImpl createObject(ISnapshot snapshot, int objectId, long addr, JavaObject jo)
                    throws CorruptDataException, MemoryAccessException, SnapshotException
    {
        ClassImpl cls = (ClassImpl) snapshot.getClassOf(objectId);
        /*
         * Optimization - don't bother going to dump if there are no fields to
         * be found, and find the number of fields in advance if we do.
         */
        int nFields = 0;
        for (ClassImpl cls2 = cls; cls2 != null; cls2 = cls2.getSuperClass())
        {
            nFields += cls2.getFieldDescriptors().size();
        }
        List<Field> fields = new ArrayList<Field>(nFields);
        if (nFields > 0)
            for (JavaClass jc = jo.getJavaClass(); jc != null; jc = jc.getSuperclass())
            {
                for (Iterator ii = jc.getDeclaredFields(); ii.hasNext();)
                {
                    Object next = ii.next();
                    if (next instanceof CorruptData)
                        continue;
                    JavaField jf = (JavaField) next;
                    if (!Modifier.isStatic(jf.getModifiers()))
                    {
                        Object val = null;
                        try
                        {
                            Object o = jf.get(jo);
                            if (o instanceof JavaObject)
                            {
                                JavaObject jo2 = (JavaObject) o;
                                long addr2 = jo2.getID().getAddress();
                                val = new ObjectReference(snapshot, addr2);
                            }
                            else
                            {
                                if (o instanceof Number || o instanceof Character || o instanceof Boolean || o == null)
                                {
                                    val = o;
                                }
                                else
                                {
                                    // Unexpected type
                                }
                            }
                        }
                        catch (CorruptDataException e)
                        {
                            logOrThrow(e);
                        }
                        catch (MemoryAccessException e)
                        {
                            logOrThrow(e);
                        }
                        Field f = new Field(jf.getName(), DTFJIndexBuilder.signatureToType(jf.getSignature()), val);
                        if (false)
                            System.out.println(jc.getName() + " New field " + f.getName() + " " //$NON-NLS-1$ //$NON-NLS-2$
                                            + f.getVerboseSignature() + " " + val); //$NON-NLS-1$
                        fields.add(f);
                    }
                }
            }
        InstanceImpl inst;
        if (snapshot.isClassLoader(objectId))
        {
            inst = new ClassLoaderImpl(objectId, addr, cls, fields);
        }
        else
        {
            inst = new InstanceImpl(objectId, addr, cls, fields);
        }
        // System.out.println("inst = "+inst);
        return inst;
    }

    static class DeferredReadArray
    {
        // The MAT object ID
        private int objectId;
        // The MAT and DTFJ address
        private long addr;
        // The DTFJ object
        private JavaObject jo;

        public DeferredReadArray(int objectId, long addr, JavaObject jo)
        {
            setObjectId(objectId);
            setAddr(addr);
            setJo(jo);
        }

        private void setObjectId(int objectId)
        {
            this.objectId = objectId;
        }

        int getObjectId()
        {
            return objectId;
        }

        private void setAddr(long addr)
        {
            this.addr = addr;
        }

        long getAddr()
        {
            return addr;
        }

        private void setJo(JavaObject jo)
        {
            this.jo = jo;
        }

        JavaObject getJo()
        {
            return jo;
        }
    }

    /**
     * @param snapshot
     * @param objectId
     * @param addr
     * @param jo
     * @return
     * @throws CorruptDataException
     * @throws SnapshotException
     */
    private IObject createArray(ISnapshot snapshot, int objectId, long addr, JavaObject jo)
                    throws CorruptDataException, MemoryAccessException, SnapshotException
    {
        ClassImpl cls = (ClassImpl) snapshot.getClassOf(objectId);
        int offset = 0;
        int length = jo.getArraySize();
        // System.out.println("Array length "+length);
        JavaClass arrayClass = jo.getJavaClass();
        if (DTFJIndexBuilder.isPrimitiveArray(arrayClass))
        {
            String typeName = arrayClass.getName();
            int type = getArrayType(typeName);

            Object res;
            if (length < LARGE_ARRAY_SIZE)
            {
                res = getPrimitiveData(jo, type, offset, length);
            }
            else
            {
                res = new DeferredReadArray(objectId, addr, jo);
            }

            // System.out.println("new "+type+" array["+length+"]");
            // System.out.println("Byte Data "+Arrays.toString(res));
            PrimitiveArrayImpl ret = new PrimitiveArrayImpl(objectId, addr, cls, length, type);
            ret.setInfo(res);
            return ret;
        }
        else
        {
            Object res;
            if (length < LARGE_ARRAY_SIZE)
            {
                res = getObjectData(jo, offset, length);
            }
            else
            {
                res = new DeferredReadArray(objectId, addr, jo);
            }
            // System.out.println("new object array["+length+"]");
            // System.out.println("long Data "+Arrays.toString(res));
            ObjectArrayImpl ret = new ObjectArrayImpl(objectId, addr, cls, length);
            ret.setInfo(res);
            return ret;
        }
    }

    /**
     * Extracts object data from a Java array
     * 
     * @param jo
     * @param offset
     * @param length
     * @return an array of addresses of the objects
     * @throws CorruptDataException
     */
    private long[] getObjectData(JavaObject jo, int offset, int length) throws CorruptDataException,
                    MemoryAccessException
    {
        JavaObject temp[] = new JavaObject[length];
        try
        {
            jo.arraycopy(offset, temp, 0, length);
            // System.out.println("Data "+Arrays.toString(temp));
        }
        catch (MemoryAccessException e)
        {
            logOrThrow(e);
        }
        long res[] = new long[length];
        for (int i = 0; i < length; ++i)
        {
            long addr2 = temp[i] == null ? 0 : temp[i].getID().getAddress();
            res[i] = addr2;
        }
        return res;
    }

    /**
     * Convert DTFJ array types to MAT types
     * 
     * @param typeName
     * @return
     */
    private int getArrayType(String typeName)
    {
        int type;
        if (typeName.equals("[Z") || typeName.equals("[boolean")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.BOOLEAN;
        }
        else if (typeName.equals("[B") || typeName.equals("[byte")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.BYTE;
        }
        else if (typeName.equals("[C") || typeName.equals("[char")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.CHAR;
        }
        else if (typeName.equals("[S") || typeName.equals("[short")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.SHORT;
        }
        else if (typeName.equals("[I") || typeName.equals("[int")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.INT;
        }
        else if (typeName.equals("[F") || typeName.equals("[float")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.FLOAT;
        }
        else if (typeName.equals("[J") || typeName.equals("[long")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.LONG;
        }
        else if (typeName.equals("[D") || typeName.equals("[double")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            type = IObject.Type.DOUBLE;
        }
        else
        {
            // Should never occur
            type = IObject.Type.BYTE;
            throw new IllegalArgumentException(MessageFormat.format(Messages.DTFJHeapObjectReader_UnexpectedTypeName,
                            typeName));
        }
        return type;
    }

    /**
     * Extract non-object data from a non-object array
     * 
     * @param jo
     * @param typeName
     * @param offset
     *            in data items
     * @param length
     * @return
     * @throws CorruptDataException
     */
    private Object getPrimitiveData(JavaObject jo, int type, int offset, int length) throws CorruptDataException,
                    MemoryAccessException
    {
        Object res;
        switch (type)
        {
            case IObject.Type.BOOLEAN:
                res = new boolean[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(res));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            case IObject.Type.BYTE:
                res = new byte[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(res));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            case IObject.Type.CHAR:
                res = new char[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(res));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            case IObject.Type.SHORT:
                res = new short[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(temp));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            case IObject.Type.INT:
                res = new int[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(res));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            case IObject.Type.FLOAT:
                res = new float[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(res));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            case IObject.Type.LONG:
                res = new long[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(res));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            case IObject.Type.DOUBLE:
                res = new double[length];
                try
                {
                    jo.arraycopy(offset, res, 0, length);
                    // System.out.println("Data "+Arrays.toString(temp));
                }
                catch (MemoryAccessException e)
                {
                    logOrThrow(e);
                }
                break;
            default:
                // Should never occur
                // type = IObject.Type.BYTE;
                // res = new byte[length * 8];
                throw new IllegalArgumentException(MessageFormat.format(Messages.DTFJHeapObjectReader_UnexpectedType,
                                type));
        }
        return res;
    }

    /**
     * Read some of the contents of an array
     * 
     * @param array
     *            The MAT array to be read
     * @param offset
     *            the offset into the array
     * @param length
     *            the number of items to be read
     * @return A primitive array holding the items
     */
    public Object readPrimitiveArrayContent(PrimitiveArrayImpl array, int offset, int length) throws IOException,
                    SnapshotException
    {
        Object info = array.getInfo();
        Object res;
        if (info instanceof DeferredReadArray)
        {
            DeferredReadArray da = (DeferredReadArray) info;
            try
            {
                // DTFJ is not thread safe, but MAT is multi-threaded
                synchronized (image)
                {
                    res = getPrimitiveData(da.getJo(), array.getType(), offset, length);
                }
            }
            catch (CorruptDataException e)
            {
                IOException e1 = new IOException(MessageFormat.format(
                                Messages.DTFJHeapObjectReader_ErrorReadingPrimitiveArray, da.getObjectId(), format(da
                                                .getAddr()), offset, length));
                e1.initCause(e);
                throw e1;
            }
            catch (MemoryAccessException e)
            {
                IOException e1 = new IOException(MessageFormat.format(
                                Messages.DTFJHeapObjectReader_ErrorReadingPrimitiveArray, da.getObjectId(), format(da
                                                .getAddr()), offset, length));
                e1.initCause(e);
                throw e1;
            }
        }
        else
        {
            res = Array.newInstance(info.getClass().getComponentType(), length);
            System.arraycopy(info, offset, res, 0, length);
        }
        return res;
    }

    /**
     * Read some of the contents of an array
     * 
     * @param array
     *            The MAT array to be read
     * @param offset
     *            the offset into the array
     * @param length
     *            the number of items to be read
     * @return A array of longs holding the addresses of the objects in the
     *         array
     */
    public long[] readObjectArrayContent(ObjectArrayImpl array, int offset, int length) throws IOException,
                    SnapshotException
    {
        Object info = array.getInfo();
        long res[];
        if (info instanceof DeferredReadArray)
        {
            DeferredReadArray da = (DeferredReadArray) info;
            try
            {
                // DTFJ is not thread safe, but MAT is multi-threaded
                synchronized (image)
                {
                    res = getObjectData(da.getJo(), offset, length);
                }
            }
            catch (CorruptDataException e)
            {
                IOException e1 = new IOException(MessageFormat.format(
                                Messages.DTFJHeapObjectReader_ErrorReadingObjectArray, da.getObjectId(), format(da
                                                .getAddr()), offset, length));
                e1.initCause(e);
                throw e1;
            }
            catch (MemoryAccessException e)
            {
                IOException e1 = new IOException(MessageFormat.format(
                                Messages.DTFJHeapObjectReader_ErrorReadingObjectArray, da.getObjectId(), format(da
                                                .getAddr()), offset, length));
                e1.initCause(e);
                throw e1;
            }
        }
        else
        {
            res = new long[length];
            System.arraycopy(info, offset, res, 0, length);
        }
        return res;
    }

    /**
     * Convert an address to a 0x hex number
     * 
     * @param address
     * @return A string representing the address
     */
    private static String format(long address)
    {
        return "0x" + Long.toHexString(address); //$NON-NLS-1$
    }

    private void logOrThrow(MemoryAccessException e) throws MemoryAccessException
    {
        if (throwExceptions)
            throw e;
        e.printStackTrace();
    }

    private void logOrThrow(CorruptDataException e) throws CorruptDataException
    {
        if (throwExceptions)
            throw e;
        e.printStackTrace();
    }
}

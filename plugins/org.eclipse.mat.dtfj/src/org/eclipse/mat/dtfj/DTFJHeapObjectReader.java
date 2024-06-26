/*******************************************************************************
 * Copyright (c) 2009,2023 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.dtfj.DTFJIndexBuilder.RuntimeInfo;
import org.eclipse.mat.parser.IObjectReader;
import org.eclipse.mat.parser.model.AbstractObjectImpl;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.ClassLoaderImpl;
import org.eclipse.mat.parser.model.InstanceImpl;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.VoidProgressListener;

import com.ibm.dtfj.image.CorruptData;
import com.ibm.dtfj.image.CorruptDataException;
import com.ibm.dtfj.image.DataUnavailable;
import com.ibm.dtfj.image.Image;
import com.ibm.dtfj.image.ImageAddressSpace;
import com.ibm.dtfj.image.ImageFactory;
import com.ibm.dtfj.image.ImagePointer;
import com.ibm.dtfj.image.ImageProcess;
import com.ibm.dtfj.image.MemoryAccessException;
import com.ibm.dtfj.java.JavaClass;
import com.ibm.dtfj.java.JavaClassLoader;
import com.ibm.dtfj.java.JavaField;
import com.ibm.dtfj.java.JavaHeap;
import com.ibm.dtfj.java.JavaLocation;
import com.ibm.dtfj.java.JavaMethod;
import com.ibm.dtfj.java.JavaMonitor;
import com.ibm.dtfj.java.JavaObject;
import com.ibm.dtfj.java.JavaRuntime;
import com.ibm.dtfj.java.JavaStackFrame;
import com.ibm.dtfj.java.JavaThread;

/**
 * Reads details of an object from a DTFJ dump.
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
    private static final boolean getExtraInfo = true;
    /** the file */
    private File file;
    /** All the key DTFJ data */
    private RuntimeInfo dtfjInfo;
    /** the snapshot */
    private ISnapshot snapshot;
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
        // Close the dump
        DTFJIndexBuilder.DumpCache.releaseDump(file, dtfjInfo, true);
        snapshot = null;
    }

    /**
     * Used as a proxy for org.eclipse.mat.parser to read objects
     * via address.
     */
    class ObjectAddressReference extends ObjectReference
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructor.
         * The address will be filled in by {@link ObjectReference}.
         */
        public ObjectAddressReference()
        {
            super(snapshot, 0);
        }

        /**
         * Create an IObject from the address.
         */
        public IObject getObject() throws SnapshotException
        {
            try
            {
                IObject o = DTFJHeapObjectReader.this.read(getObjectAddress(), -1, snapshot);
                ((AbstractObjectImpl)o).setSnapshot(snapshot);
                return o;
            }
            catch (IOException e)
            {
                throw new SnapshotException(e);
            }
        }
    }

    /**
     * Returns extra data to be provided by {@link ISnapshot#getSnapshotAddons(Class addon)}.
     * Also can be returned via {@link org.eclipse.mat.query.annotations.Argument}.
     * @see IObjectReader#getAddon(Class)
     * @param addon the type of the extra data required from the dump
     *  Types supported by DTFJHeapObjectReader include
     *  <ul>
     *  <li>{@link com.ibm.dtfj.image.Image}</li>
     *  <li>{@link com.ibm.dtfj.image.ImageAddressSpace}</li>
     *  <li>{@link com.ibm.dtfj.image.ImageProcess}</li>
     *  <li>{@link com.ibm.dtfj.java.JavaRuntime}</li>
     *  <li>{@link com.ibm.dtfj.image.ImageFactory} since 1.1</li>
     *  </ul>
     * @return the extra data
     */
    public <A> A getAddon(Class<A> addon) throws SnapshotException
    {
        ImageFactory factory = dtfjInfo.getImageFactory();
        Image image = dtfjInfo.getImage();
        ImageAddressSpace addrSpace = dtfjInfo.getImageAddressSpace();
        ImageProcess process = dtfjInfo.getImageProcess();
        JavaRuntime jvm = dtfjInfo.getJavaRuntime();
        if (image != null && addon.isAssignableFrom(image.getClass()))
        {
            return addon.cast(image);
        }
        else if (jvm != null && addon.isAssignableFrom(jvm.getClass()))
        {
            return addon.cast(jvm);
        }
        else if (addrSpace != null && addon.isAssignableFrom(addrSpace.getClass()))
        {
            return addon.cast(addrSpace);
        }
        else if (process != null && addon.isAssignableFrom(process.getClass()))
        {
            return addon.cast(process);
        }
        else if (factory != null && addon.isAssignableFrom(factory.getClass()))
        {
            return addon.cast(factory);
        }
        else if (addon.isAssignableFrom(ObjectAddressReference.class))
        {
            return addon.cast(new ObjectAddressReference());
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
    public void open(ISnapshot snapshot) throws IOException, SnapshotException
    {
        file = new File(snapshot.getSnapshotInfo().getPath());
        this.snapshot = snapshot;
        SnapshotInfo snapinfo = snapshot.getSnapshotInfo();
        RuntimeInfo info = DTFJIndexBuilder.DumpCache.getDump(file, snapinfo.getProperty("$heapFormat")); //$NON-NLS-1$
        Serializable runtimeId = snapinfo.getProperty(DTFJIndexBuilder.RUNTIME_ID_KEY);
        // Find the JVM
        dtfjInfo = DTFJIndexBuilder.getRuntime(info.getImageFactory(), info.getImage(), runtimeId, null);
     }

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.parser.IObjectReader#read(int,
     * org.eclipse.mat.snapshot.ISnapshot)
     */
    public IObject read(int objectId, ISnapshot snapshot) throws SnapshotException, IOException
    {
        long addr = snapshot.mapIdToAddress(objectId);
        return read(addr, objectId, snapshot);
    }

    IObject read(long addr, int objectId, ISnapshot snapshot) throws SnapshotException, IOException
    {
        try
        {
            // DTFJ is not thread safe, but MAT is multi-threaded
            synchronized (dtfjInfo.getImage())
            {
                if (getExtraInfo && objectId != -1)
                {
                    // See if the class looks like a method
                    ClassImpl cls = (ClassImpl) snapshot.getClassOf(objectId);
                    if (cls.getName().contains(DTFJIndexBuilder.METHOD_NAME_SIG) || cls.getName().equals(DTFJIndexBuilder.STACK_FRAME))
                    {
                        // Get the special method/stack frame data
                        IObject ret = createObject2(snapshot, objectId, addr, cls);
                        if (ret != null)
                            return ret;
                    }
                }
                JavaObject jo = getJavaObjectByAddress0(addr);
                IObject inst;
                if (objectId == -1 ? jo.isArray() : snapshot.isArray(objectId))
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
            for (Iterator<?> i = dtfjInfo.getJavaRuntime().getThreads(); i.hasNext();)
            {
                Object next = i.next();
                if (next instanceof CorruptData)
                {
                    continue;
                }
                JavaThread jt = (JavaThread) next;
                int pointerSize = snapshot.getSnapshotInfo().getIdentifierSize() * 8;
                // Count all the frames
                int totalDepth = 0;
                for (Iterator<?> j = jt.getStackFrames(); j.hasNext(); ++totalDepth)
                {
                    j.next();
                }
                int depth = 0;
                for (Iterator<?> j = jt.getStackFrames(); j.hasNext(); ++depth)
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
                        boolean useMethodName = false;
                        for (IClass cls1  = cls; cls1 != null && !useMethodName; cls1 = cls1.getSuperClass())
                        {
                            for (FieldDescriptor fd : cls1.getFieldDescriptors())
                            {
                                if (fd.getName().equals(DTFJIndexBuilder.METHOD_NAME))
                                {
                                    useMethodName = true;
                                    break;
                                }
                            }
                        }
                        if (useMethodName)
                        {
                            String methodName;
                            try
                            {
                                JavaLocation jl = jsf.getLocation();
                                JavaMethod jm = jl.getMethod();
                                JavaClass jc = jm.getDeclaringClass();
                                String name = jc.getName().replace('/', '.');
                                methodName = name
                                                + DTFJIndexBuilder.METHOD_NAME_PREFIX
                                                + DTFJIndexBuilder.getMethodName(jl.getMethod(),
                                                                new VoidProgressListener());
                            }
                            catch (DataUnavailable e)
                            {
                                methodName = null;
                            }
                            catch (CorruptDataException e)
                            {
                                methodName = null;
                            }
                            Field f = new Field(DTFJIndexBuilder.METHOD_NAME, IObject.Type.OBJECT, methodName);
                            fields.add(f);
                        }
                        String fileName;
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            fileName = jl.getFilename();
                        }
                        catch (DataUnavailable e)
                        {
                            fileName = null;
                        }
                        catch (CorruptDataException e)
                        {
                            fileName = null;
                        }
                        Field f = new Field(DTFJIndexBuilder.FILE_NAME, IObject.Type.OBJECT, fileName);
                        fields.add(f);
                        int lineNumber;
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            lineNumber = jl.getLineNumber(); 
                        }
                        catch (DataUnavailable e)
                        {
                            lineNumber = -1;
                        }
                        catch (CorruptDataException e)
                        {
                            lineNumber = -1;
                        }
                        f = new Field(DTFJIndexBuilder.LINE_NUMBER, IObject.Type.INT, Integer.valueOf(lineNumber));
                        fields.add(f);
                        int compLevel;
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            compLevel = jl.getCompilationLevel();
                        }
                        catch (ClassCastException e)
                        {
                            // Sov problem
                            compLevel = 0;
                        }
                        catch (CorruptDataException e)
                        {
                            compLevel = 0;
                        }
                        f = new Field(DTFJIndexBuilder.COMPILATION_LEVEL, IObject.Type.INT, Integer.valueOf(compLevel));
                        fields.add(f);
                        boolean nativ;
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            nativ = Modifier.isNative(jl.getMethod().getModifiers());
                        }
                        catch (ClassCastException e)
                        {
                            // Sov problem
                            nativ = false;
                        }
                        catch (CorruptDataException e)
                        {
                            nativ = false;
                        }
                        Field f2 = new Field(DTFJIndexBuilder.NATIVE, IObject.Type.BOOLEAN, Boolean.valueOf(nativ));
                        fields.add(f2);
                        long locaddr;
                        try
                        {
                            JavaLocation jl = jsf.getLocation();
                            locaddr = jl.getAddress().getAddress();
                        }
                        catch (ClassCastException e)
                        {
                            // Sov problem
                            locaddr = 0;
                        }
                        catch (CorruptDataException e)
                        {
                            locaddr = 0;
                        }
                        f = new Field(DTFJIndexBuilder.LOCATION_ADDRESS, IObject.Type.LONG, Long.valueOf(locaddr));
                        fields.add(f);
                        f = new Field(DTFJIndexBuilder.FRAME_NUMBER, IObject.Type.INT, Integer.valueOf(depth));
                        fields.add(f);
                        f = new Field(DTFJIndexBuilder.STACK_DEPTH, IObject.Type.INT, Integer.valueOf(totalDepth - depth));
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
        ImagePointer ip = dtfjInfo.getImageAddressSpace().getPointer(addr);
        try
        {
            // Previous versions of DTFJ might not have this method, so handle
            // that possibility
            JavaObject jo = dtfjInfo.getJavaRuntime().getObjectAtAddress(ip);
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
        // Now look for thread objects
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getThreads(); i.hasNext();)
        {
            Object next = i.next();
            if (next instanceof CorruptData)
            {
                continue;
            }
            JavaThread thrd = (JavaThread) next;
            long thAddr = DTFJIndexBuilder.getThreadAddress(thrd, null);
            if (addr == thAddr)
            {
                JavaObject jo;
                try
                {
                    jo = thrd.getObject();
                }
                catch (CorruptDataException e)
                {
                    jo = null;
                }
                return jo;
            }
        }

        // Now look for monitor objects
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getMonitors(); i.hasNext();)
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
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getJavaClassLoaders(); i.hasNext();)
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

        // Now look for ordinary objects on the heap
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getHeaps(); i.hasNext();)
        {
            Object next = i.next();
            if (next instanceof CorruptData)
            {
                continue;
            }
            JavaHeap jh = (JavaHeap) next;
            for (Iterator<?> j = jh.getObjects(); j.hasNext();)
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
        ClassImpl cls = findClass(snapshot, objectId, jo, addr);
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
            for (JavaClass jc = jo.getJavaClass(); jc != null; jc = getSuperclass(jc))
            {
                for (Iterator<?> ii = jc.getDeclaredFields(); ii.hasNext();)
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

    /**
     * Find the {@link ClassImpl} which is the type of the object.
     * @param snapshot the snapshot
     * @param objectId for normal reads this is the object ID, -1 if unknown
     * @param jo The DTFJ {@link JavaObject}
     * @param addr the address of the object, for error messages
     * @return the ClassImpl for the object, to allow the IOject to be constructed.
     * @throws CorruptDataException
     * @throws SnapshotException
     */
    private ClassImpl findClass(ISnapshot snapshot, int objectId, JavaObject jo, long addr)
                    throws CorruptDataException, SnapshotException
    {
        ClassImpl cls;
        if (objectId == -1)
        {
            JavaClass jc = jo.getJavaClass();
            if (jc != null)
            {
                JavaObject jco = jc.getObject();
                long caddr;
                if (jco != null)
                    caddr = jco.getID().getAddress();
                else
                    caddr = jc.getID().getAddress();
                int clsId = snapshot.mapAddressToId(caddr);
                IObject cls1 = snapshot.getObject(clsId);
                if (cls1 instanceof ClassImpl)
                    cls = (ClassImpl) cls1;
                else
                    throw new SnapshotException(MessageFormat.format(
                                    Messages.DTFJHeapObjectReader_ErrorReadingObjectAtIndex, objectId, format(addr)));
            }
            else
            {
                throw new SnapshotException(MessageFormat.format(
                                Messages.DTFJHeapObjectReader_ErrorReadingObjectAtIndex, objectId, format(addr)));
            }
        }
        else
        {
            cls = (ClassImpl) snapshot.getClassOf(objectId);
        }
        return cls;
    }

    /**
     * Avoid problems with corrupt dumps
     * @param jc
     * @return
     * @throws CorruptDataException
     */
    private JavaClass getSuperclass(JavaClass jc) throws CorruptDataException
    {
        try
        {
            return jc.getSuperclass();
        }
        catch (CorruptDataException e)
        {
            // We have already logged this error on creating the indexes
            if (false)
                logOrThrow(e);
            return null;
        }
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
        ClassImpl cls = findClass(snapshot, objectId, jo, addr);
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
            // Performance optimization - for PHD files delay reading the refs until really needed.
            // Often, we only need the array length.
            Object format = snapshot.getSnapshotInfo().getProperty("$heapFormat"); //$NON-NLS-1$
            if (!"DTFJ-PHD".equals(format) && length < LARGE_ARRAY_SIZE) //$NON-NLS-1$
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
                synchronized (dtfjInfo.getImage())
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
                synchronized (dtfjInfo.getImage())
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

/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG, IBM Corporation and others..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - bug fix for missing classes
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.HashMapLongObject.Entry;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class HprofParserHandlerImpl implements IHprofParserHandler
{
    // private String prefix;
    private AbstractParser.Version version;

    private XSnapshotInfo info = new XSnapshotInfo();

    /** constant pool cache */
    private HashMapLongObject<String> constantPool = new HashMapLongObject<String>(10000);
    private Map<String, List<ClassImpl>> classesByName = new HashMap<String, List<ClassImpl>>();
    private HashMapLongObject<ClassImpl> classesByAddress = new HashMapLongObject<ClassImpl>();

    private HashMapLongObject<List<XGCRootInfo>> gcRoots = new HashMapLongObject<List<XGCRootInfo>>(200);

    private IndexWriter.Identifier identifiers = null;
    private IndexWriter.IntArray1NWriter outbound = null;
    private IndexWriter.IntIndexCollector object2classId = null;
    private IndexWriter.LongIndexCollector object2position = null;
    private IndexWriter.SizeIndexCollectorUncompressed array2size = null;

    private Set<Long> requiredArrayClassIDs = new HashSet<Long>();
    private Set<Integer> requiredPrimitiveArrays = new HashSet<Integer>();
    private HashMapLongObject<Integer> requiredClassIDs = new HashMapLongObject<Integer>();

    private HashMapLongObject<HashMapLongObject<List<XGCRootInfo>>> threadAddressToLocals = new HashMapLongObject<HashMapLongObject<List<XGCRootInfo>>>();

    // The size of (possibly compressed) references in the heap
    private int refSize;
    // The size of uncompressed pointers in the object headers in the heap
    private int pointerSize;
    // The alignment between successive objects
    private int objectAlign;
    // New size of classes including per-instance fields
    private final boolean NEWCLASSSIZE = HprofPreferences.useAdditionalClassReferences();

    // //////////////////////////////////////////////////////////////
    // lifecycle
    // //////////////////////////////////////////////////////////////

    public void beforePass1(XSnapshotInfo snapshotInfo) throws IOException
    {
        this.info = snapshotInfo;
        this.identifiers = new IndexWriter.Identifier();
    }

    public void beforePass2(IProgressListener monitor) throws IOException, SnapshotException
    {
        // add dummy address for system class loader object
        identifiers.add(0);

        // sort and assign preliminary object ids
        identifiers.sort();

        // See what the actual object alignment is
        calculateAlignment();
        
        // Set property to show if compressed oops are used on x64 bit dumps
        if (pointerSize == 8) // if x64 bit dump
        {
            info.setProperty("$useCompressedOops", refSize == 4); //$NON-NLS-1$
        }

        // if necessary, create required classes not contained in the heap
        createRequiredFakeClasses();

        // informational messages to the user
        monitor.sendUserMessage(IProgressListener.Severity.INFO, MessageUtil.format(
                        Messages.HprofParserHandlerImpl_HeapContainsObjects, info.getPath(), identifiers.size()), null);

        // if instance dumps for classes are present, then fix up the classes
        addTypesAndDummyStatics();

        int maxClassId = 0;

        // calculate instance size for all classes
        for (Iterator<?> e = classesByAddress.values(); e.hasNext();)
        {
            ClassImpl clazz = (ClassImpl) e.next();
            int index = identifiers.reverse(clazz.getObjectAddress());
            clazz.setObjectId(index);

            maxClassId = Math.max(maxClassId, index);

            clazz.setHeapSizePerInstance(calculateInstanceSize(clazz));
            clazz.setUsedHeapSize(calculateClassSize(clazz));
        }

        // create index writers
        outbound = new IndexWriter.IntArray1NWriter(this.identifiers.size(), Index.OUTBOUND.getFile(info.getPrefix()
                        + "temp."));//$NON-NLS-1$
        object2classId = new IndexWriter.IntIndexCollector(this.identifiers.size(), IndexWriter
                        .mostSignificantBit(maxClassId));
        object2position = new IndexWriter.LongIndexCollector(this.identifiers.size(), IndexWriter
                        .mostSignificantBit(new File(this.info.getPath()).length()));
        array2size = new IndexWriter.SizeIndexCollectorUncompressed(this.identifiers.size());

        // java.lang.Class needs some special treatment so that object2classId
        // is written correctly
        List<ClassImpl> javaLangClasses = classesByName.get(ClassImpl.JAVA_LANG_CLASS);
        ClassImpl javaLangClass = javaLangClasses.get(0);
        javaLangClass.setObjectId(identifiers.reverse(javaLangClass.getObjectAddress()));

        // log references for classes
        for (Iterator<?> e = classesByAddress.values(); e.hasNext();)
        {
            ClassImpl clazz = (ClassImpl) e.next();
            clazz.setSuperClassIndex(identifiers.reverse(clazz.getSuperClassAddress()));
            clazz.setClassLoaderIndex(identifiers.reverse(clazz.getClassLoaderAddress()));

            // [INFO] in newer jdk hprof files, the boot class loader
            // has an address other than 0. The class loader instances
            // is still not contained in the hprof file
            if (clazz.getClassLoaderId() < 0)
            {
                clazz.setClassLoaderAddress(0);
                clazz.setClassLoaderIndex(identifiers.reverse(0));
            }

            boolean skipLogRefs = false;
            // add class instance - if not set by pass1 from an instance_dump for the class
            if (clazz.getClazz() == null)
            {
                clazz.setClassInstance(javaLangClass);
                if (NEWCLASSSIZE)
                {
                    // Recalculate the clazz heap size based on also java.lang.Class fields
                    // No need to do this for classes of other types as
                    // these have object instance records with fields converted to statics
                    clazz.setUsedHeapSize(clazz.getUsedHeapSize() + clazz.getClazz().getHeapSizePerInstance());
                }
                clazz.getClazz().addInstance(clazz.getUsedHeapSize());
            }
            else
            {
                // References for classes with instance dump records will be generated in pass2
                skipLogRefs = true;
            }

            // resolve super class
            ClassImpl superclass = lookupClass(clazz.getSuperClassAddress());
            if (superclass != null)
                superclass.addSubClass(clazz);

            object2classId.set(clazz.getObjectId(), clazz.getClazz().getObjectId());

            if (!skipLogRefs)
                outbound.log(identifiers, clazz.getObjectId(), clazz.getReferences());
        }

        // report dependencies for system class loader
        // (if no classes use this class loader, cleanup garbage will remove it
        // again)
        ClassImpl classLoaderClass = this.classesByName.get(IClass.JAVA_LANG_CLASSLOADER).get(0);
        HeapObject heapObject = new HeapObject(this.identifiers.reverse(0), 0, classLoaderClass, classLoaderClass
                        .getHeapSizePerInstance());
        heapObject.references.add(classLoaderClass.getObjectAddress());
        this.addObject(heapObject, 0);

        constantPool = null;
    }

    /**
     * Possible HPROF extension:
     * classes also with instance dump records.
     * The classes could be of a type other than java.lang.Class
     * There could also be per-instance fields defined by their type.
     * Those values can be made accessible to MAT bby creating pseudo-static fields.
     */
    private void addTypesAndDummyStatics()
    {
        // Set type (and size?) for classes with object instances
        // These will have had the type set by Pass1Parser
        for (Iterator<Entry<ClassImpl>> it = classesByAddress.entries(); it.hasNext();)
        {
            Entry<ClassImpl> e = it.next();
            ClassImpl cl = e.getValue();
            ClassImpl type = cl.getClazz();
            if (type != null)
            {
                List<FieldDescriptor> newStatics = new ArrayList<FieldDescriptor>(cl.getStaticFields());
                List<IClass> icls = resolveClassHierarchy(type.getClassAddress());
                for (IClass tcl : icls)
                {
                    for (FieldDescriptor fd : tcl.getFieldDescriptors())
                    {
                        // Create pseudo-static field
                        Field st = new Field("<" + fd.getName() + ">", fd.getType(), null); //$NON-NLS-1$ //$NON-NLS-2$
                        newStatics.add(st);
                    }
                }
                if (newStatics.size() != cl.getStaticFields().size())
                {
                    ClassImpl newcl = new ClassImpl(cl.getObjectAddress(), cl.getName(), cl.getSuperClassAddress(),
                                    cl.getClassLoaderAddress(), newStatics.toArray(new Field[newStatics.size()]),
                                    cl.getFieldDescriptors().toArray(new FieldDescriptor[0]));
                    newcl.setClassInstance(type);
                    // Fix up the existing lookups
                    classesByAddress.put(e.getKey(), newcl);
                    List<ClassImpl>nms = classesByName.get(cl.getName());
                    for (int i = 0; i < nms.size(); ++i)
                    {
                        if (nms.get(i) == cl)
                            nms.set(i, newcl);
                    }
                }
            }
        }

        // Set type to new type with the dummy static fields
        for (Iterator<Entry<ClassImpl>> it = classesByAddress.entries(); it.hasNext();)
        {
            Entry<ClassImpl> e = it.next();
            ClassImpl cl = e.getValue();
            ClassImpl type = cl.getClazz();
            if (type != null)
            {
                ClassImpl type2 = classesByAddress.get(type.getObjectAddress());
                // Actual object test, not equality as we need to maintain linkage to the new class
                if (type != type2)
                {
                    cl.setClassInstance(type2);
                }
            }
        }
    }

    /**
     * Calculate possible restrictions on object alignment by finding the GCD of differences
     * between object addresses (ignoring address 0).
     */
    private void calculateAlignment()
    {
        // Minimum alignment of 8 bytes
        final int minAlign = 8;
        // Maximum alignment of 256 bytes
        final int maxAlign = 256;
        long prev = 0;
        long align = 0;
        for (IteratorLong it = identifiers.iterator(); it.hasNext(); )
        {
            long next = it.next();
            if (next == 0)
                continue;
            long diff = next - prev;
            prev = next;
            if (next == diff)
                continue;
            if (align == 0)
            {
                align = diff;
            }
            else
            {
                long mx = Math.max(align, diff);
                long mn = Math.min(align, diff);
                long d = mx % mn;
                while (d != 0) {
                    mx = mn;
                    mn = d;
                    d = mx % mn;
                }
                align = mn;
                // Minimum alignment
                if (align <= minAlign)
                    break;
            }
        }
        // Sanitise the alignment
        objectAlign = Math.max((int)Math.min(align, maxAlign), minAlign);
    }

    private void createRequiredFakeClasses() throws IOException, SnapshotException
    {
        // we know: system class loader has object address 0
        long nextObjectAddress = 0;
        // For generating the fake class names
        int clsid = 0;
        // java.lang.Object for the superclass
        List<ClassImpl>jlos = classesByName.get("java.lang.Object"); //$NON-NLS-1$
        long jlo = jlos == null || jlos.isEmpty() ? 0 : jlos.get(0).getObjectAddress();
        // Fake java.lang.Class, java.lang.ClassLoader for later
        String clss[] = {ClassImpl.JAVA_LANG_CLASS, ClassImpl.JAVA_LANG_CLASSLOADER};
        for (String cls : clss)
        {
            List<ClassImpl>jlcs = classesByName.get(cls);
            if (jlcs == null || jlcs.isEmpty())
            {
                while (identifiers.reverse(++nextObjectAddress) >= 0)
                {}
                IClass type = new ClassImpl(nextObjectAddress, cls, jlo, 0, new Field[0], new FieldDescriptor[0]);
                addClass((ClassImpl) type, -1);
            }
        }
        
        // create required (fake) classes for arrays
        if (!requiredArrayClassIDs.isEmpty())
        {
            for (long arrayClassID : requiredArrayClassIDs)
            {
                IClass arrayType = lookupClass(arrayClassID);
                if (arrayType == null)
                {
                    int objectId = identifiers.reverse(arrayClassID);
                    if (objectId >= 0)
                    {
                        String msg = MessageUtil.format(Messages.HprofParserHandlerImpl_Error_ExpectedClassSegment,
                                        Long.toHexString(arrayClassID));
                        throw new SnapshotException(msg);
                    }

                    arrayType = new ClassImpl(arrayClassID, "unknown-class-"+clsid+"[]", jlo, 0, new Field[0], //$NON-NLS-1$ //$NON-NLS-2$
                                    new FieldDescriptor[0]);
                    ++clsid;
                    addClass((ClassImpl) arrayType, -1);
                }
            }
        }
        requiredArrayClassIDs = null;

        if (!requiredPrimitiveArrays.isEmpty())
        {
            for (Integer arrayType : requiredPrimitiveArrays)
            {
                String name = IPrimitiveArray.TYPE[arrayType];
                IClass clazz = lookupClassByName(name, true);
                if (clazz == null)
                {
                    while (identifiers.reverse(++nextObjectAddress) >= 0)
                    {}

                    clazz = new ClassImpl(nextObjectAddress, name, jlo, 0, new Field[0], new FieldDescriptor[0]);
                    addClass((ClassImpl) clazz, -1);
                }

            }
        }

        // create required (fake) classes for objects
        if (!requiredClassIDs.isEmpty())
        {
            for (Iterator<HashMapLongObject.Entry<Integer>> it = requiredClassIDs.entries(); it.hasNext(); )
            {
                HashMapLongObject.Entry<Integer> e = it.next();
                long classID = e.getKey();
                IClass type = lookupClass(classID);
                if (type == null)
                {
                    int objectId = identifiers.reverse(classID);
                    if (objectId >= 0)
                    {
                        String msg = MessageUtil.format(Messages.HprofParserHandlerImpl_Error_ExpectedClassSegment,
                                        Long.toHexString(classID));
                        throw new SnapshotException(msg);
                    }
                    // Create some dummy fields
                    int size = e.getValue();
                    // Special value for missing superclass
                    if (size >= Integer.MAX_VALUE) 
                        size = 0;
                    int nfields = size / 4 + Integer.bitCount(size % 4);
                    FieldDescriptor fds[] = new FieldDescriptor[nfields];
                    int i;
                    for (i = 0; i < size / 4; ++i)
                    {
                        fds[i] = new FieldDescriptor("unknown-field-"+i, IObject.Type.INT); //$NON-NLS-1$
                    }
                    if ((size & 2) != 0)
                    {
                        fds[i] = new FieldDescriptor("unknown-field-"+i, IObject.Type.SHORT); //$NON-NLS-1$
                        ++i;
                    }
                    if ((size & 1) != 0)
                    {
                        fds[i] = new FieldDescriptor("unknown-field-"+i, IObject.Type.BYTE); //$NON-NLS-1$
                        ++i;
                    }
                    type = new ClassImpl(classID, "unknown-class-"+clsid, jlo, 0, new Field[0], fds); //$NON-NLS-1$
                    ++clsid;
                    addClass((ClassImpl) type, -1);
                }
            }
        }
        requiredClassIDs = null;
        
        identifiers.sort();
    }

    private int calculateInstanceSize(ClassImpl clazz)
    {
        if (!clazz.isArrayType())
        {
            return alignUpToX(calculateSizeRecursive(clazz), objectAlign);
        }
        else
        {
            // use the referenceSize only to pass the proper ID size
            // arrays calculate the rest themselves.
            return refSize;
        }
    }
    
	private int calculateSizeRecursive(ClassImpl clazz)
	{
		if (clazz.getSuperClassAddress() == 0)
		{
			return pointerSize + refSize;
		}
		ClassImpl superClass = classesByAddress.get(clazz.getSuperClassAddress());
		int ownFieldsSize = 0;
		for (FieldDescriptor field : clazz.getFieldDescriptors())
			ownFieldsSize += sizeOf(field);

		return alignUpToX(ownFieldsSize + calculateSizeRecursive(superClass), refSize);
	}

    private int calculateClassSize(ClassImpl clazz)
    {
        int staticFieldsSize = 0;
        for (Field field : clazz.getStaticFields())
            staticFieldsSize += sizeOf(field);
        return alignUpToX(staticFieldsSize, objectAlign);
    }

    private int sizeOf(FieldDescriptor field)
    {
        int type = field.getType();
        if (type == IObject.Type.OBJECT)
            return refSize;

        return IPrimitiveArray.ELEMENT_SIZE[type];
    }

    private int alignUpToX(int n, int x)
    {
        int r = n % x;
        return r == 0 ? n : n + x - r;
    }

    private long alignUpToX(long n, int x)
    {
        long r = n % x;
        return r == 0 ? n : n + x - r;
    }

    public IOne2LongIndex fillIn(IPreliminaryIndex index) throws IOException
    {
        // ensure all classes loaded by the system class loaders are marked as
        // GCRoots
        //
        // For some dumps produced with jmap 1.5_xx this is not the case, and
        // it may happen that the super classes of some classes are missing
        // Array classes, e.g. java.lang.String[][] are not explicitly
        // marked. They are also not marked as "system class" in the non-jmap
        // heap dumps
        ClassImpl[] allClasses = classesByAddress.getAllValues(new ClassImpl[0]);
        for (ClassImpl clazz : allClasses)
        {
            if (clazz.getClassLoaderAddress() == 0 && !clazz.isArrayType()
                            && !gcRoots.containsKey(clazz.getObjectAddress()))
            {
                addGCRoot(clazz.getObjectAddress(), 0, GCRootInfo.Type.SYSTEM_CLASS);
            }
        }

        // classes model
        HashMapIntObject<ClassImpl> classesById = new HashMapIntObject<ClassImpl>(classesByAddress.size());
        for (Iterator<ClassImpl> iter = classesByAddress.values(); iter.hasNext();)
        {
            ClassImpl clazz = iter.next();
            classesById.put(clazz.getObjectId(), clazz);
        }
        index.setClassesById(classesById);

        index.setGcRoots(map2ids(gcRoots));

        HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thread2objects2roots = new HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>>();
        for (Iterator<HashMapLongObject.Entry<HashMapLongObject<List<XGCRootInfo>>>> iter = threadAddressToLocals
                        .entries(); iter.hasNext();)
        {
            HashMapLongObject.Entry<HashMapLongObject<List<XGCRootInfo>>> entry = iter.next();
            int threadId = identifiers.reverse(entry.getKey());
            if (threadId >= 0)
            {
                HashMapIntObject<List<XGCRootInfo>> objects2roots = map2ids(entry.getValue());
                if (!objects2roots.isEmpty())
                    thread2objects2roots.put(threadId, objects2roots);
            }
        }
        index.setThread2objects2roots(thread2objects2roots);

        index.setIdentifiers(identifiers);

        index.setArray2size(array2size.writeTo(Index.A2SIZE.getFile(info.getPrefix() + "temp."))); //$NON-NLS-1$

        index.setObject2classId(object2classId);

        index.setOutbound(outbound.flush());

        return object2position.writeTo(new File(info.getPrefix() + "temp.o2hprof.index")); //$NON-NLS-1$
    }

    private HashMapIntObject<List<XGCRootInfo>> map2ids(HashMapLongObject<List<XGCRootInfo>> source)
    {
        HashMapIntObject<List<XGCRootInfo>> sink = new HashMapIntObject<List<XGCRootInfo>>();
        for (Iterator<HashMapLongObject.Entry<List<XGCRootInfo>>> iter = source.entries(); iter.hasNext();)
        {
            HashMapLongObject.Entry<List<XGCRootInfo>> entry = iter.next();
            int idx = identifiers.reverse(entry.getKey());
            if (idx >= 0)
            {
                // sometimes it happens that there is no object for an
                // address reported as a GC root. It's not clear why
                for (Iterator<XGCRootInfo> roots = entry.getValue().iterator(); roots.hasNext();)
                {
                    XGCRootInfo root = roots.next();
                    root.setObjectId(idx);
                    if (root.getContextAddress() != 0)
                    {
                        int contextId = identifiers.reverse(root.getContextAddress());
                        if (contextId < 0)
                            roots.remove();
                        else
                            root.setContextId(contextId);
                    }
                }
                sink.put(idx, entry.getValue());
            }
        }
        return sink;
    }

    public void cancel()
    {
        if (constantPool != null)
            constantPool.clear();

        if (outbound != null)
            outbound.cancel();

    }

    // //////////////////////////////////////////////////////////////
    // report parsed entities
    // //////////////////////////////////////////////////////////////

    public void addProperty(String name, String value) throws IOException
    {
        if (IHprofParserHandler.VERSION.equals(name))
        {
            version = AbstractParser.Version.valueOf(value);
            info.setProperty(HprofHeapObjectReader.VERSION_PROPERTY, version.name());
        }
        else if (IHprofParserHandler.IDENTIFIER_SIZE.equals(name))
        {
            int idSize = Integer.parseInt(value);
            info.setIdentifierSize(idSize);
            pointerSize = idSize;
            refSize = idSize;
        }
        else if (IHprofParserHandler.CREATION_DATE.equals(name))
        {
            info.setCreationDate(new Date(Long.parseLong(value)));
        }
        else if (IHprofParserHandler.REFERENCE_SIZE.equals(name))
        {
            refSize = Integer.parseInt(value);
        }
    }

    public void addGCRoot(long id, long referrer, int rootType)
    {
        if (referrer != 0)
        {
            HashMapLongObject<List<XGCRootInfo>> localAddressToRootInfo = threadAddressToLocals.get(referrer);
            if (localAddressToRootInfo == null)
            {
                localAddressToRootInfo = new HashMapLongObject<List<XGCRootInfo>>();
                threadAddressToLocals.put(referrer, localAddressToRootInfo);
            }
            List<XGCRootInfo> gcRootInfo = localAddressToRootInfo.get(id);
            if (gcRootInfo == null)
            {
                gcRootInfo = new ArrayList<XGCRootInfo>(1);
                localAddressToRootInfo.put(id, gcRootInfo);
            }
            gcRootInfo.add(new XGCRootInfo(id, referrer, rootType));
            return; // do not add the object as GC root
        }

        List<XGCRootInfo> r = gcRoots.get(id);
        if (r == null)
            gcRoots.put(id, r = new ArrayList<XGCRootInfo>(3));
        r.add(new XGCRootInfo(id, referrer, rootType));
    }

    public void addClass(ClassImpl clazz, long filePosition) throws IOException
    {
        this.identifiers.add(clazz.getObjectAddress());
        this.classesByAddress.put(clazz.getObjectAddress(), clazz);

        List<ClassImpl> list = classesByName.get(clazz.getName());
        if (list == null)
            classesByName.put(clazz.getName(), list = new ArrayList<ClassImpl>());
        list.add(clazz);
    }

    public void addObject(HeapObject object, long filePosition) throws IOException
    {
        int index = object.objectId;

        // check if some thread to local variables references have to be added
        HashMapLongObject<List<XGCRootInfo>> localVars = threadAddressToLocals.get(object.objectAddress);
        if (localVars != null)
        {
            IteratorLong e = localVars.keys();
            while (e.hasNext())
            {
                object.references.add(e.next());
            }
        }

        // log references
        outbound.log(identifiers, index, object.references);

        int classIndex = object.clazz.getObjectId();
        object.clazz.addInstance(object.usedHeapSize);

        // log address
        object2classId.set(index, classIndex);
        object2position.set(index, filePosition);

        // log array size
        if (object.isArray)
            array2size.set(index, object.usedHeapSize);
    }

    public void reportInstance(long id, long filePosition)
    {
        // Check for INSTANCE_DUMP for an existing class object
        if (!classesByAddress.containsKey(id))
        {
            this.identifiers.add(id);
        }
    }

    public void reportRequiredObjectArray(long arrayClassID)
    {
        requiredArrayClassIDs.add(arrayClassID);
    }

    public void reportRequiredPrimitiveArray(int arrayType)
    {
        requiredPrimitiveArrays.add(arrayType);
    }

    public void reportRequiredClass(long classID, int size)
    {
        if (requiredClassIDs.containsKey(classID))
        {
            if (requiredClassIDs.get(classID) > size)
            {
                // Make the size the minimum
                requiredClassIDs.put(classID, size);
            }
        }
        else
        {
            requiredClassIDs.put(classID, size);
        }
    }
    
    // //////////////////////////////////////////////////////////////
    // lookup heap infos
    // //////////////////////////////////////////////////////////////

    public int getIdentifierSize()
    {
        return info.getIdentifierSize();
    }

    public HashMapLongObject<String> getConstantPool()
    {
        return constantPool;
    }

    public ClassImpl lookupClass(long classId)
    {
        return classesByAddress.get(classId);
    }

    public IClass lookupClassByName(String name, boolean failOnMultipleInstances)
    {
        List<ClassImpl> list = classesByName.get(name);
        if (list == null)
            return null;
        if (failOnMultipleInstances && list.size() != 1)
            throw new RuntimeException(MessageUtil.format(
                            Messages.HprofParserHandlerImpl_Error_MultipleClassInstancesExist, name));
        return list.get(0);
    }

    public IClass lookupClassByIndex(int objIndex)
    {
        return lookupClass(this.identifiers.get(objIndex));
    }

    public List<IClass> resolveClassHierarchy(long classId)
    {
        List<IClass> answer = new ArrayList<IClass>();

        ClassImpl clazz = classesByAddress.get(classId);
        answer.add(clazz);

        while (clazz.hasSuperClass())
        {
            clazz = classesByAddress.get(clazz.getSuperClassAddress());
            answer.add(clazz);
        }

        return answer;
    }

    public int mapAddressToId(long address)
    {
        return this.identifiers.reverse(address);
    }

    public XSnapshotInfo getSnapshotInfo()
    {
        return info;
    }

    public long getObjectArrayHeapSize(ClassImpl arrayType, int size)
    {
        long usedHeapSize = alignUpToX(pointerSize + refSize + 4 + size * arrayType.getHeapSizePerInstance(), objectAlign);
        return usedHeapSize;
    }

    public long getPrimitiveArrayHeapSize(byte elementType, int size)
    {
        long usedHeapSize = alignUpToX(alignUpToX(pointerSize + refSize + 4, refSize) + size * (long)PrimitiveArrayImpl.ELEMENT_SIZE[(int) elementType], objectAlign);
        return usedHeapSize;
    }

}

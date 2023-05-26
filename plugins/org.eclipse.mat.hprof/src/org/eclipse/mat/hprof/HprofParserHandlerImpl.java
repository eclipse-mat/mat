/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - bug fix for missing classes
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.HashMapLongObject.Entry;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.collect.SetLong;
import org.eclipse.mat.hprof.describer.Version;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.index.IndexReader.SizeIndexReader;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IndexWriter.IntArray1NWriter;
import org.eclipse.mat.parser.index.IndexWriter.IntIndexCollector;
import org.eclipse.mat.parser.index.IndexWriter.IntIndexStreamer;
import org.eclipse.mat.parser.index.IndexWriter.LongIndexCollector;
import org.eclipse.mat.parser.index.IndexWriter.LongIndexStreamer;
import org.eclipse.mat.parser.index.IndexWriter.SizeIndexCollectorUncompressed;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class HprofParserHandlerImpl implements IHprofParserHandler
{
    // private String prefix;
    private Version version;

    private XSnapshotInfo info = new XSnapshotInfo();

    private Map<String, List<ClassImpl>> classesByName = new HashMap<String, List<ClassImpl>>();
    private HashMapLongObject<ClassImpl> classesByAddress = new HashMapLongObject<ClassImpl>();

    private HashMapLongObject<List<XGCRootInfo>> gcRoots = new HashMapLongObject<List<XGCRootInfo>>(200);

    private IndexWriter.Identifier identifiers0 = null;
    private IIndexReader.IOne2LongIndex identifiers = null;
    private IntArray1NWriter outbound = null;
    private IntIndexCollector object2classId = null;
    private LongIndexCollector object2position = null;
    private ObjectToSize array2size = null;

    private HashMap<Long, Boolean> requiredArrayClassIDs = new HashMap<Long, Boolean>();
    private HashMap<Long, Integer> requiredClassIDs = new HashMap<Long, Integer>();
    private IClass[] primitiveArrays = new IClass[IPrimitiveArray.TYPE.length];
    private boolean[] requiredPrimitiveArrays = new boolean[IPrimitiveArray.COMPONENT_TYPE.length];

    private SetLong classLoaders = new SetLong();

    private HashMapLongObject<HashMapLongObject<List<XGCRootInfo>>> threadAddressToLocals = new HashMapLongObject<HashMapLongObject<List<XGCRootInfo>>>();

    /** Keep track of numbers and size of discarded objects */
    private ConcurrentHashMap<Integer, ClassImpl> discardedObjectsByClass = new ConcurrentHashMap<Integer, ClassImpl>();

    // The size of (possibly compressed) references in the heap
    private int refSize;
    // The size of uncompressed pointers in the object headers in the heap
    private int pointerSize;
    // The alignment between successive objects
    private int objectAlign;
    // New size of classes including per-instance fields
    private final boolean NEWCLASSSIZE = HprofPreferences.useAdditionalClassReferences();
    // Largest offset into HPROF file
    private long maxFilePosition = 0;

    /** Which class instances to possibly discard */ 
    private Pattern discardPattern = Pattern.compile("char\\[\\]|java\\.lang\\.String"); //$NON-NLS-1$
    /** How often to discard */
    private double discardRatio = 0.0;
    /** Select which group of objects are discarded */
    private double discardOffset = 0.0;
    /** Random number seed for choosing discards */
    private long discardSeed = 1;
    /** Random number generator to choose what to discard */
    private Random rand = new Random(discardSeed);

    /**
     * Use to see how much memory is used by arrays.
     * Smaller than {@link IndexWriter.SizeIndexCollectorUncompressed}.
     * Compare with {@link org.eclipse.mat.dtfj.DTFJIndexBuilder.ObjectToSize}
     * which is not thread-safe.
     */
    private static class ObjectToSize
    {
        /** For small objects */
        private byte objectToSize[];
        /** For large objects */
        private ConcurrentHashMap<Integer,Long> bigObjs = new ConcurrentHashMap<Integer,Long>();
        private static final int SHIFT = 3;
        private static final int MASK = 0xff;

        ObjectToSize(int size)
        {
            objectToSize = new byte[size];
        }

        long get(int index)
        {
            if (bigObjs.containsKey(index))
            {
                long size = bigObjs.get(index);
                return size;
            }
            else
            {
                return (objectToSize[index] & MASK) << SHIFT;
            }
        }

        long getSize(int index)
        {
            return get(index);
        }

        /**
         * Rely on most object sizes being small, and having the lower 3 bits
         * zero. Some classes will have sizes with lower 3 bits not zero, but
         * there are a limited number of these. It is better to use expand the
         * range for ordinary objects.
         *
         * @param index object ID
         * @param size in bytes
         */
        void set(int index, long size)
        {
            if ((size & ~(MASK << SHIFT)) == 0)
            {
                objectToSize[index] = (byte) (size >>> SHIFT);
            }
            else
            {
                bigObjs.put(index, size);
            }
        }

        public IIndexReader.IOne2SizeIndex writeTo(File indexFile) throws IOException
        {
            final int size = objectToSize.length;
            return new SizeIndexReader(new IntIndexStreamer().writeTo(indexFile, new IteratorInt()
            {
                int i;

                public boolean hasNext()
                {
                    return i < size;
                }

                public int next()
                {
                    if (!hasNext())
                        throw new NoSuchElementException();
                    return SizeIndexCollectorUncompressed.compress(getSize(i++));
                }

            }));
        }
    }

    // //////////////////////////////////////////////////////////////
    // lifecycle
    // //////////////////////////////////////////////////////////////

    public void beforePass1(XSnapshotInfo snapshotInfo) throws IOException
    {
        this.info = snapshotInfo;
        this.identifiers0 = new IndexWriter.Identifier();
        if (info.getProperty("discard_ratio") instanceof Integer) //$NON-NLS-1$
        {
            discardRatio = (Integer)info.getProperty("discard_ratio") / 100.0; //$NON-NLS-1$
            if (info.getProperty("discard_offset") instanceof Integer) //$NON-NLS-1$
            {
                discardOffset = (Integer)info.getProperty("discard_offset") / 100.0; //$NON-NLS-1$
            }
            else
            {
                info.setProperty("discard_offset", (int)Math.round(discardOffset * 100)); //$NON-NLS-1$
            }
            if (info.getProperty("discard_seed") instanceof Integer) //$NON-NLS-1$
            {
                discardSeed = (Integer)info.getProperty("discard_seed"); //$NON-NLS-1$
            }
            else
            {
                info.setProperty("discard_seed", discardSeed); //$NON-NLS-1$
            }
            rand = new Random(discardSeed);
            if (info.getProperty("discard_pattern") instanceof String) //$NON-NLS-1$
            {
                discardPattern = Pattern.compile((String)info.getProperty("discard_pattern")); //$NON-NLS-1$
            }
            else
            {
                info.setProperty("discard_pattern", discardPattern.toString()); //$NON-NLS-1$
            }
        }
    }

    public void beforePass2(IProgressListener monitor) throws IOException, SnapshotException
    {
        // add dummy address for system class loader object
        identifiers0.add(0);

        // sort and assign preliminary object ids
        identifiers0.sort();

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
                        Messages.HprofParserHandlerImpl_HeapContainsObjects, info.getPath(), identifiers0.size()), null);

        // if instance dumps for classes are present, then fix up the classes
        addTypesAndDummyStatics();

        int maxClassId = 0;

        // calculate instance size for all classes
        for (Iterator<ClassImpl> e = classesByAddress.values(); e.hasNext();)
        {
            ClassImpl clazz = e.next();
            int index = identifiers0.reverse(clazz.getObjectAddress());
            clazz.setObjectId(index);
            if (index < 0)
            {
                monitor.sendUserMessage(IProgressListener.Severity.ERROR,
                                MessageUtil.format(Messages.HprofParserHandlerImpl_ClassNotFoundInAddressIndex,
                                                clazz.getTechnicalName()),
                                null);
            }

            maxClassId = Math.max(maxClassId, index);

            clazz.setHeapSizePerInstance(calculateInstanceSize(clazz));
            clazz.setUsedHeapSize(calculateClassSize(clazz));

            // For fixing up HPROF files created from exporting DTFJ snapshots
            classLoaders.add(clazz.getClassLoaderAddress());
        }

        // Compress the identifiers index to disk
        identifiers = (new LongIndexStreamer()).writeTo(Index.IDENTIFIER.getFile(info.getPrefix() + "temp."), identifiers0.iterator()); //$NON-NLS-1$);
        identifiers0.delete();
        identifiers0 = null;

        // create index writers
        outbound = new IntArray1NWriter(this.identifiers.size(), Index.OUTBOUND.getFile(info.getPrefix()
                        + "temp."));//$NON-NLS-1$
        object2classId = new IntIndexCollector(this.identifiers.size(), IndexWriter
                        .mostSignificantBit(maxClassId));
        object2position = new LongIndexCollector(this.identifiers.size(), IndexWriter
                        .mostSignificantBit(maxFilePosition));
        array2size = new ObjectToSize(this.identifiers.size());

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
            if (clazz.getClazz() == null || clazz.getClazz().getName().startsWith("<")) //$NON-NLS-1$
            {
                if (clazz.getClazz() == null)
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
        HeapObject heapObject = new HeapObject(0, classLoaderClass, classLoaderClass
                        .getHeapSizePerInstance());
        heapObject.references.add(classLoaderClass.getObjectAddress());
        this.addObject(heapObject, true);

    }

    /**
     * Possible HPROF extension:
     * classes also with instance dump records.
     * The classes could be of a type other than java.lang.Class
     * There could also be per-instance fields defined by their type.
     * Those values can be made accessible to MAT by creating pseudo-static fields.
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
                    // Relies on replacement of a value being safe inside iterator
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
        for (IteratorLong it = identifiers0.iterator(); it.hasNext(); )
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
                while (identifiers0.reverse(nextObjectAddress += objectAlign) >= 0)
                {}
                IClass type = new ClassImpl(nextObjectAddress, cls, jlo, 0, new Field[0], new FieldDescriptor[0]);
                addFakeClass((ClassImpl) type, -1);
            }
        }

        // create required (fake) classes for arrays
        if (!requiredArrayClassIDs.isEmpty())
        {
            for (long arrayClassID : requiredArrayClassIDs.keySet())
            {
                IClass arrayType = lookupClass(arrayClassID);
                if (arrayType == null)
                {
                    int objectId = identifiers0.reverse(arrayClassID);
                    if (objectId >= 0)
                    {
                        String msg = MessageUtil.format(Messages.HprofParserHandlerImpl_Error_ExpectedClassSegment,
                                        Long.toHexString(arrayClassID));
                        throw new SnapshotException(msg);
                    }

                    arrayType = new ClassImpl(arrayClassID, "unknown-class-"+clsid+"[]", jlo, 0, new Field[0], //$NON-NLS-1$ //$NON-NLS-2$
                                    new FieldDescriptor[0]);
                    ++clsid;
                    addFakeClass((ClassImpl) arrayType, -1);
                }
            }
        }
        requiredArrayClassIDs = null;

        for(int arrayType = 0; arrayType < requiredPrimitiveArrays.length; arrayType++)
        {
            if (requiredPrimitiveArrays[arrayType])
            {
                String name = IPrimitiveArray.TYPE[arrayType];
                IClass clazz = lookupClassByName(name, true);
                if (clazz == null)
                {
                    while (identifiers0.reverse(nextObjectAddress += objectAlign) >= 0)
                    {}

                    clazz = new ClassImpl(nextObjectAddress, name, jlo, 0, new Field[0], new FieldDescriptor[0]);
                    addFakeClass((ClassImpl) clazz, -1);
                }
                primitiveArrays[arrayType] = clazz;
            }
        }

        // create required (fake) classes for objects
        if (!requiredClassIDs.isEmpty())
        {
            for (Map.Entry<Long, Integer> e : requiredClassIDs.entrySet())
            {
                long classID = e.getKey();
                IClass type = lookupClass(classID);
                if (type == null)
                {
                    int objectId = identifiers0.reverse(classID);
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
                    addFakeClass((ClassImpl) type, -1);
                }
            }
        }
        requiredClassIDs = null;

        identifiers0.sort();
    }

    private int calculateInstanceSize(ClassImpl clazz)
    {
        if (!clazz.isArrayType())
        {
            if (clazz.getSuperClassAddress() != 0)
            {
                ClassImpl superClass = classesByAddress.get(clazz.getSuperClassAddress());
                // Base the size of a stack frame on the number of locals, set in pass1.
                if (superClass.getName().equals("<method>")) //$NON-NLS-1$
                    return (int) clazz.getHeapSizePerInstance();
            }
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

    public IOne2LongIndex fillIn(IPreliminaryIndex index, IProgressListener listener) throws IOException
    {
        /*
         * System classes should be marked appropriately in the HPROF file.
         * lambda classes should not be marked as system GC roots.
         */
        boolean foundSystemClasses = false;
        for (Iterator<List<XGCRootInfo>>it = gcRoots.values(); it.hasNext() && !foundSystemClasses; )
        {
            for (XGCRootInfo x : it.next())
            {
                if (x.getType() == GCRootInfo.Type.SYSTEM_CLASS)
                {
                    foundSystemClasses = true;
                    break;
                }
            }
        }
        if (!foundSystemClasses)
        {
            // Probably not needed anymore.
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
        }

        // classes model
        HashMapIntObject<ClassImpl> classesById = new HashMapIntObject<ClassImpl>(classesByAddress.size());
        for (Iterator<ClassImpl> iter = classesByAddress.values(); iter.hasNext();)
        {
            ClassImpl clazz = iter.next();
            classesById.put(clazz.getObjectId(), clazz);
        }
        index.setClassesById(classesById);

        // Create Histogram of discarded objects
        long discardedObjects = 0;
        long discardedSize = 0;
        List<UnreachableObjectsHistogram.Record> records = new ArrayList<UnreachableObjectsHistogram.Record>();
        for(ClassImpl clazz : discardedObjectsByClass.values()) {
            records.add(new UnreachableObjectsHistogram.Record(
                            clazz.getName(),
                            clazz.getObjectAddress(),
                            clazz.getNumberOfObjects(),
                            clazz.getTotalSize()));
            discardedObjects += clazz.getNumberOfObjects();
            discardedSize += clazz.getTotalSize();
        }
        if (discardedObjects > 0)
        {
            UnreachableObjectsHistogram deadObjectHistogram = new UnreachableObjectsHistogram(records);
            info.setProperty(UnreachableObjectsHistogram.class.getName(), deadObjectHistogram);
            listener.sendUserMessage(IProgressListener.Severity.WARNING, MessageUtil.format(
                            Messages.HprofParserHandlerImpl_DiscardedObjects, 
                            discardedObjects, discardedSize, discardRatio, discardPattern), null);
        }

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

        index.setObject2classId(object2classId.writeTo(Index.O2CLASS.getFile(info.getPrefix() + "temp."))); //$NON-NLS-1$

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
            version = Version.valueOf(value);
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
        else if (IHprofParserHandler.STREAM_LENGTH.equals(name))
        {
            long length = Long.parseLong(value);
            info.setProperty(HprofHeapObjectReader.HPROF_LENGTH_PROPERTY, length);
        }
        else if (IHprofParserHandler.HEAP_POSITION.equals(name))
        {
            long pos = Long.parseLong(value);
            info.setProperty(HprofHeapObjectReader.HPROF_HEAP_START, pos);
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

    private void addFakeClass(ClassImpl clazz, long filePosition) throws IOException
    {
        this.identifiers0.add(clazz.getObjectAddress());
        this.classesByAddress.put(clazz.getObjectAddress(), clazz);

        List<ClassImpl> list = classesByName.get(clazz.getName());
        if (list == null)
            classesByName.put(clazz.getName(), list = new ArrayList<ClassImpl>());
        list.add(clazz);
    }

    public void addClass(ClassImpl clazz, long filePosition, int idSize, int instsize) throws IOException
    {
        this.identifiers0.add(clazz.getObjectAddress());
        this.classesByAddress.put(clazz.getObjectAddress(), clazz);

        List<ClassImpl> list = classesByName.get(clazz.getName());
        if (list == null)
            classesByName.put(clazz.getName(), list = new ArrayList<ClassImpl>());
        list.add(clazz);

        if (clazz.getSuperClassAddress() != 0) {
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
            reportRequiredClass(clazz.getSuperClassAddress(), supersize, false);
        }
    }

    private void prepareHeapObject(HeapObject object) throws IOException
    {
        if (object.isPrimitiveArray)
        {
            byte elementType = (byte) object.classIdOrElementType;
            ClassImpl clazz = (ClassImpl) lookupPrimitiveArrayClassByType(elementType);
            object.usedHeapSize = getPrimitiveArrayHeapSize(elementType, object.arraySize);
            object.references.add(clazz.getObjectAddress());
            object.clazz = clazz;
        }

        if (object.isObjectArray)
        {
            long arrayClassObjectID = object.classIdOrElementType;
            ClassImpl arrayType = (ClassImpl) lookupClass(arrayClassObjectID);
            if (arrayType == null)
                throw new RuntimeException(MessageUtil.format(
                                Messages.Pass2Parser_Error_HandlerMustCreateFakeClassForAddress,
                                Long.toHexString(arrayClassObjectID)));

            object.usedHeapSize = getObjectArrayHeapSize(arrayType, object.arraySize);
            object.references.add(arrayType.getObjectAddress());
            long[] ids = object.ids;
            for (int ii = 0; ii < object.arraySize; ii++)
            {
                if (ids[ii] != 0)
                    object.references.add(ids[ii]);
            }
            object.clazz = arrayType;
            // References now transfered, so free some space
            ids = null;
            object.ids = null;
        }

        if (!object.isObjectArray && !object.isPrimitiveArray)
        {
            long classID = object.classIdOrElementType;
            List<IClass> hierarchy = resolveClassHierarchy(classID);
            ByteArrayPositionInputStream in = new ByteArrayPositionInputStream(object.instanceData, object.idSize);

            ClassImpl thisClazz = (ClassImpl) hierarchy.get(0);

            ClassImpl objcl = lookupClass(object.objectAddress);
            Field statics[] = new Field[0];
            if (objcl != null)
            {
                // An INSTANCE_DUMP record for a class type
                // This clazz is perhaps of different actual type, not java.lang.Class
                // The true type has already been set in PassParser1 and beforePass2()
                ClassImpl objcls = (ClassImpl) objcl;
                statics = objcls.getStaticFields().toArray(statics);
                // Heap size of each class type object is individual as have statics
                object.clazz = thisClazz;
                object.usedHeapSize = objcls.getUsedHeapSize();
                // and extract the class references
                object.references.addAll(objcls.getReferences());
            }
            else
            {
                object.clazz = thisClazz;
                object.usedHeapSize = thisClazz.getHeapSizePerInstance();
                object.references.add(thisClazz.getObjectAddress());
            }

            // extract outgoing references
            int pos = 0;
            for (IClass clazz : hierarchy)
            {
                for (FieldDescriptor field : clazz.getFieldDescriptors())
                {
                    int type = field.getType();
                    // Find match for pseudo-statics
                    Field stField = null;
                    for (int stidx = 0; stidx < statics.length; ++stidx)
                    {
                        if (statics[stidx] != null && statics[stidx].getType() == type && statics[stidx].getName().equals("<"+field.getName()+">")) { //$NON-NLS-1$ //$NON-NLS-2$
                            // Found a field
                            stField = statics[stidx];
                            // Don't use this twice.
                            statics[stidx] = null;
                            break;
                        }
                    }
                    if (type == IObject.Type.OBJECT)
                    {
                        long refId = in.readID(object.idSize);
                        pos += object.idSize;
                        if (refId != 0)
                        {
                            object.references.add(refId);
                            if (stField != null)
                            {
                                stField.setValue(new ObjectReference(null, refId));
                            }
                        }
                    }
                    else
                    {
                        Object value = AbstractParser.readValue(in, null, type, object.idSize);
                        if (stField != null)
                            stField.setValue(value);
                    }
                }
            }

            // For fixing up HPROF files created from exporting DTFJ snapshots
            if (!classLoaders.contains(0) && classLoaders.contains(object.objectAddress))
            {
                for (ClassImpl clazz : classesByAddress.getAllValues(new ClassImpl[0]))
                {
                    if (clazz.getClassLoaderAddress() == object.objectAddress)
                    {
                        object.references.add(clazz.getObjectAddress());
                    }
                }
            }

            if (pos != object.instanceData.length)
            {
                boolean unknown = false;
                for (IClass clazz : hierarchy)
                {
                    if (clazz.getName().startsWith("unknown-class")) //$NON-NLS-1$
                    {
                        unknown = true;
                    }
                }

                // TODO get the strictness settings across from eclipse
//                if (unknown && (strictnessPreference == HprofStrictness.STRICTNESS_WARNING || strictnessPreference == HprofStrictness.STRICTNESS_PERMISSIVE))
//                {
//                    //monitor.sendUserMessage(Severity.WARNING, MessageUtil.format(Messages.Pass2Parser_Error_InsufficientBytesRead, thisClazz.getName(), Long.toHexString(id), Long.toHexString(segmentStartPos), Long.toHexString(endPos), Long.toHexString(in.position())), null);
//                }
//                else
//                {
//                    throw new IOException(MessageUtil.format(Messages.Pass2Parser_Error_InsufficientBytesRead, thisClazz.getName(), Long.toHexString(id), Long.toHexString(segmentStartPos), Long.toHexString(endPos), Long.toHexString(in.position())));
//                }
            }
        }

    }
    public void addObject(HeapObject object) throws IOException
    {
        addObject(object, false);
    }

    private void addObject(HeapObject object, boolean prepared) throws IOException
    {
        if (!prepared)
            prepareHeapObject(object);

        // this may be called from multiple threads
        // so, each function called inside here needs to be threadsafe
        // it will not do to simply synchronize here as we need
        // better concurrency than that

        int index = mapAddressToId(object.objectAddress);
        if (index < 0)
        {
            // Discarded object
            ClassImpl cls = discardedObjectsByClass.get(object.clazz.getObjectId());
            if (cls == null)
            {
                cls = new ClassImpl(object.clazz.getObjectAddress(), 
                                object.clazz.getName(),
                                object.clazz.getSuperClassAddress(),
                                object.clazz.getClassLoaderAddress(),
                                new Field[0],
                                new FieldDescriptor[0]);
                cls.setHeapSizePerInstance(object.clazz.getHeapSizePerInstance());
                ClassImpl clsOld = discardedObjectsByClass.putIfAbsent(object.clazz.getObjectId(), cls);
                if (clsOld != null)
                    cls = clsOld;
            }
            /*
             * Keep count of discards
             * @TODO consider overflow as we count discard >Integer.MAX_VALUE
             */
            cls.addInstance(object.usedHeapSize);
            return;
        }

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
        object2position.set(index, object.filePosition);

        // log array size
        if (object.isPrimitiveArray || object.isObjectArray)
            array2size.set(index, object.usedHeapSize);
    }

    /**
     * Randomly choose whether to discard
     * @return
     */
    private boolean discard()
    {
        if (discardRatio <= 0.0)
            return false;
        // Always accept the first object to give a start to the heap
        if (identifiers0.size() == 0)
            return false;
        double d = rand.nextDouble();
        double top = discardRatio + discardOffset;
        /*
         * Wrap around the range.
         * [dddddddd..] 0.8,0.0
         * [..dddddddd] 0.8,0.2
         * [dd..dddddd] 0.8,0.4
         */
        return (d < top && d >= discardOffset) || d < top - 1.0;
    }

    /** 
     * Choose to discard also based on object type
     * 
     * @param classId the HPROF class Id
     * @return
     */
    private boolean discard(long classId)
    {
        if (!discard())
            return false;
        ClassImpl cls = lookupClass(classId);
        if (cls != null && discardPattern.matcher(cls.getName()).matches())
        {
            return true;
        }
        return false;
    }

    /**
     * Adjust the last seen file position.
     * Used to see how many bits are needed for the index.
     * @param filePosition
     */
    private void reportFilePosition(long filePosition)
    {
        if (filePosition > maxFilePosition)
            maxFilePosition = filePosition;
    }

    private void reportInstance(long id, long filePosition)
    {
        this.identifiers0.add(id);
        reportFilePosition(filePosition);
    }

    public void reportInstanceWithClass(long id, long filePosition, long classID, int size)
    {
        if (discard(classID))
        {
            // Skip
            reportFilePosition(filePosition);
            return;
        }
        reportInstance(id, filePosition);
        reportRequiredClass(classID, size, true);
    }

    public void reportInstanceOfObjectArray(long id, long filePosition, long arrayClassID)
    {
        if (discard(arrayClassID))
        {
            // Skip
            reportFilePosition(filePosition);
            return;
        }
        reportInstance(id, filePosition);
        reportRequiredObjectArray(arrayClassID);
    }

    public void reportInstanceOfPrimitiveArray(long id, long filePosition, int arrayType)
    {
        if (discard() && discardPattern.matcher(IPrimitiveArray.TYPE[arrayType]).matches())
        {
            // Skip
            reportFilePosition(filePosition);
            reportRequiredPrimitiveArray(arrayType);
            return;
        }
        reportInstance(id, filePosition);
        reportRequiredPrimitiveArray(arrayType);
    }

    private void reportRequiredObjectArray(long arrayClassID)
    {
        requiredArrayClassIDs.putIfAbsent(arrayClassID, true);
    }

    private void reportRequiredPrimitiveArray(int arrayType)
    {
        requiredPrimitiveArrays[arrayType] = true;
    }

    private void reportRequiredClass(long classID, int size, boolean sizeKnown)
    {
        if (sizeKnown)
        {
            requiredClassIDs.put(classID, size);
        }
        else
        {
            requiredClassIDs.putIfAbsent(classID, size);
        }
    }

    // //////////////////////////////////////////////////////////////
    // lookup heap infos
    // //////////////////////////////////////////////////////////////

    public int getIdentifierSize()
    {
        return info.getIdentifierSize();
    }

    public ClassImpl lookupClass(long classId)
    {
        return classesByAddress.get(classId);
    }

    public IClass lookupPrimitiveArrayClassByType(byte elementType)
    {
        return primitiveArrays[elementType];
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

    ConcurrentHashMap<Long, List<IClass>> classHierarchyCache = new ConcurrentHashMap<Long, List<IClass>>();
    public List<IClass> resolveClassHierarchy(long classId)
    {
        List<IClass> cached = classHierarchyCache.get(classId);
        if (cached != null)
        {
            return cached;
        }

        List<IClass> answer = new ArrayList<IClass>();

        ClassImpl clazz = classesByAddress.get(classId);
        answer.add(clazz);

        while (clazz.hasSuperClass())
        {
            clazz = classesByAddress.get(clazz.getSuperClassAddress());
            answer.add(clazz);
        }

        classHierarchyCache.put(classId, answer);
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

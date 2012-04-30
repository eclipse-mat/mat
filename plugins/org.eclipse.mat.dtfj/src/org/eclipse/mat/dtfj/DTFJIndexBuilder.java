/*******************************************************************************
 * Copyright (c) 2009,2012 IBM Corporation.
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.collect.SetLong;
import org.eclipse.mat.parser.IIndexBuilder;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.index.IIndexReader.IOne2ManyIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2SizeIndex;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.index.IndexReader.SizeIndexReader;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IndexWriter.IntIndexStreamer;
import org.eclipse.mat.parser.index.IndexWriter.LongIndexStreamer;
import org.eclipse.mat.parser.index.IndexWriter.SizeIndexCollectorUncompressed;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;

import com.ibm.dtfj.image.CorruptData;
import com.ibm.dtfj.image.CorruptDataException;
import com.ibm.dtfj.image.DTFJException;
import com.ibm.dtfj.image.DataUnavailable;
import com.ibm.dtfj.image.Image;
import com.ibm.dtfj.image.ImageAddressSpace;
import com.ibm.dtfj.image.ImageFactory;
import com.ibm.dtfj.image.ImagePointer;
import com.ibm.dtfj.image.ImageProcess;
import com.ibm.dtfj.image.ImageSection;
import com.ibm.dtfj.image.ImageStackFrame;
import com.ibm.dtfj.image.ImageThread;
import com.ibm.dtfj.image.MemoryAccessException;
import com.ibm.dtfj.java.JavaClass;
import com.ibm.dtfj.java.JavaClassLoader;
import com.ibm.dtfj.java.JavaField;
import com.ibm.dtfj.java.JavaHeap;
import com.ibm.dtfj.java.JavaLocation;
import com.ibm.dtfj.java.JavaMethod;
import com.ibm.dtfj.java.JavaMonitor;
import com.ibm.dtfj.java.JavaObject;
import com.ibm.dtfj.java.JavaReference;
import com.ibm.dtfj.java.JavaRuntime;
import com.ibm.dtfj.java.JavaStackFrame;
import com.ibm.dtfj.java.JavaThread;
import com.ibm.dtfj.runtime.ManagedRuntime;

/**
 * Reads and parses a DTFJ dump, building indexes which are then used by MAT to create a snapshot.
 * @author ajohnson
 */
public class DTFJIndexBuilder implements IIndexBuilder
{
    /*
     * Names used as pseudo-class names
     * Not translatable 
     */
    static final String METHOD = "<method>"; //$NON-NLS-1$
    private static final String METHOD_TYPE = "<method type>"; //$NON-NLS-1$
    static final String STACK_FRAME = "<stack frame>"; //$NON-NLS-1$
    private static final String NATIVE_MEMORY = "<native memory>"; //$NON-NLS-1$
    private static final String NATIVE_MEMORY_TYPE = "<native memory type>"; //$NON-NLS-1$
    /*
     * Field names for pseudo classes.
     * Not translatable 
     */
    static final String STACK_DEPTH = "stackDepth"; //$NON-NLS-1$
    static final String FRAME_NUMBER = "frameNumber"; //$NON-NLS-1$
    static final String LOCATION_ADDRESS = "locationAddress"; //$NON-NLS-1$
    static final String COMPILATION_LEVEL = "compilationLevel"; //$NON-NLS-1$
    static final String LINE_NUMBER = "lineNumber"; //$NON-NLS-1$
    private static final String DECLARING_CLASS = "declaringClass"; //$NON-NLS-1$
    static final String METHOD_NAME = "methodName"; //$NON-NLS-1$
    static final String FILE_NAME = "fileName"; //$NON-NLS-1$

    /** Separator between the package/class name and the method name */
    static final String METHOD_NAME_PREFIX = "."; //$NON-NLS-1$
    /** Unique string only found in method names */
    static final String METHOD_NAME_SIG = "("; //$NON-NLS-1$

    private static final String PLUGIN_ID = InitDTFJ.getDefault().getBundle().getSymbolicName();
    /** The key to store the runtime id out of the dump */
    static final String RUNTIME_ID_KEY = "$runtimeId"; //$NON-NLS-1$
    /** How many elements in an object array to examine at once */
    private static final int ARRAY_PIECE_SIZE = 100000;
    /** How many bytes to scan in a native stack frame when looking for GC roots */
    private static final int NATIVE_STACK_FRAME_SIZE = 2048;
    /** How many bytes to scan in a Java stack frame when looking for GC roots */
    private static final int JAVA_STACK_FRAME_SIZE = 256;
    /**
     * How many bytes to scan in a huge Java stack section when looking for GC
     * roots
     */
    private static final long JAVA_STACK_SECTION_MAX_SIZE = 1024 * 1024L;
    /**
     * How many bytes to scan in a huge native stack section when looking for GC
     * roots
     */
    private static final long NATIVE_STACK_SECTION_MAX_SIZE = 1024 * 1024L;
    /** Whether DTFJ has root support */
    private static final boolean haveDTFJRoots = true;
    /** Whether to use DTFJ Root support */
    private static final boolean useDTFJRoots = true;
    /**
     * Whether DTFJ has references support - per instance as can switch on for
     * dumps without field info
     */
    private boolean haveDTFJRefs = false;
    /**
     * Whether to use DTFJ references support - per instance as can switch on
     * for dumps without field info
     */
    private boolean useDTFJRefs = false;
    /**
     * Whether to include thread roots (e.g. Java_Locals) as full roots, or just
     * thread roots + refs from the thread
     */
    private static final boolean useThreadRefsNotRoots = true;
    /**
     * Find roots - mark all unreferenced objects as roots so that not many
     * objects are lost by the initial GC by MAT
     */
    private static final boolean presumeRoots = false;
    /** Whether to mark all class loaders (if presume roots is true) */
    private static final boolean markAllLoaders = false;
    /** Whether to mark system classes as heap roots */
    private static final boolean useSystemClassRoots = true;
    /** Whether to skip heap roots marked marked as weak/soft reference etc. */
    private static final boolean skipWeakRoots = true;
    private final String methodsAsClassesPref = Platform.getPreferencesService().getString(PLUGIN_ID,
                    PreferenceConstants.P_METHODS, "", null); //$NON-NLS-1$
    /** Whether to represent all methods as pseudo-classes */
    private final boolean getExtraInfo2 = PreferenceConstants.ALL_METHODS_AS_CLASSES.equals(methodsAsClassesPref);
    /** Whether to represent only the stack frames as pseudo-objects */
    private final boolean getExtraInfo3 = PreferenceConstants.FRAMES_ONLY.equals(methodsAsClassesPref);
    /** Whether to represent stack frames and methods as objects and classes */
    private final boolean getExtraInfo = getExtraInfo2 || getExtraInfo3
                    || PreferenceConstants.RUNNING_METHODS_AS_CLASSES.equals(methodsAsClassesPref);
    /** name of java.lang.Class */
    private static final String JAVA_LANG_CLASS ="java/lang/Class"; //$NON-NLS-1$
    /** name of java.lang.ClassLoader */
    private static final String JAVA_LANG_CLASSLOADER ="java/lang/ClassLoader"; //$NON-NLS-1$
    /**
     * Whether to guess finalizable objects as those unreferenced objects with
     * finalizers
     */
    private static final boolean guessFinalizables = true;
    /** whether to print out debug information and make errors more severe */
    private static final boolean debugInfo = InitDTFJ.getDefault().isDebugging();
    /** severity flag for internal - info means error worked around safely */
    private static final IProgressListener.Severity Severity_INFO = debugInfo ? Severity.ERROR : Severity.INFO;
    /** severity flag for internal - warning means possible problem */
    private static final IProgressListener.Severity Severity_WARNING = debugInfo ? Severity.ERROR : Severity.WARNING;
    /** How many times to print out a repeating error */
    private static final int errorCount = debugInfo ? getDebugOption("org.eclipse.mat.dtfj/errorCount", 20) : 20; //$NON-NLS-1$
    /** print out extra information to the console */
    private static final boolean verbose = InitDTFJ.getDefault().isDebugging()
                    && getDebugOption("org.eclipse.mat.dtfj/debug/verbose", false); //$NON-NLS-1$
    /** How many objects before it is worth saving the index to disk */
    private static final int INDEX_COUNT_FOR_TEMPFILE = 5000000;

    /** The actual dump file */
    private File dump;
    /** The string prefix used to build the index files */
    private String pfx;
    /**
     * The requested runtime id, or null. In the rare case of more than one Java
     * runtime in a dump then this can be used to select another JVM.
     */
    private String runtimeId = Platform.getPreferencesService().getString(PLUGIN_ID, PreferenceConstants.P_RUNTIMEID,
                    "", null); //$NON-NLS-1$
    /** All the key DTFJ data */
    private RuntimeInfo dtfjInfo;
    /** Used to cache DTFJ images */
    private static final Map<File, ImageSoftReference> imageMap = new HashMap<File, ImageSoftReference>();
    /** Used to store the factory */
    private static final Map<Image, ImageFactory> factoryMap = Collections.synchronizedMap(new WeakHashMap<Image, ImageFactory>()); 
    /** Used to keep count of the active images */
    private static int imageCount;
    /** Used to clear the cache of images */
    private static Timer clearTimer;

    /** The outbound references index */
    private IndexWriter.IntArray1NWriter outRefs;
    /** The temporary object id to size index, for arrays and variable sized objects - used to build arrayToSize.
     * Could instead be a {@link SizeIndexCollectorUncompressed} but that is bigger. */
    private ObjectToSize indexToSize;
    /** The array size in bytes index */
    private IOne2SizeIndex arrayToSize;
    /** The object/class id number to address index for accumulation of ids */
    private IndexWriter.Identifier indexToAddress0;
    /** The object/class id number to address index once all ids are found */
    private IIndexReader.IOne2LongIndex indexToAddress;
    /** The object id to class id index for accumulation and lookup */
    private IndexWriter.IntIndexCollector objectToClass;
    /** The object id to class id index once mapping is done */
    private IIndexReader.IOne2OneIndex objectToClass1;
    /** The class id to MAT class information index */
    private HashMapIntObject<ClassImpl> idToClass;
    /** The map of object ids to lists of associated GC roots for that object id */
    private HashMapIntObject<List<XGCRootInfo>> gcRoot;
    /** The map of thread object ids to GC roots owned by that thread */
    private HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> threadRoots;

    /** Flag used to not guess if GC roots finds finalizables */
    private boolean foundFinalizableGCRoots = false;
    /** number of times getModifiers succeeded */
    private int modifiersFound;
    /** address space pointer size */
    private int addressSpacePointerSize;

    /** Used to remember dummy addresses for classes without addresses */
    private HashMap<JavaClass, Long> dummyClassAddress = new HashMap<JavaClass, Long>();
    /** Used to remember dummy addresses for methods without addresses */
    private HashMap<JavaMethod, Long> dummyMethodAddress = new HashMap<JavaMethod, Long>();
    /** Used to remember dummy addresses for methods without addresses which have a faulty equals() method */
    private IdentityHashMap<JavaMethod, Long> dummyMethodAddress2 = new IdentityHashMap<JavaMethod, Long>();        
    /**
     * Used to remember method addresses in case two different methods attempt
     * to use the same address
     */
    private HashMapLongObject<JavaMethod> methodAddresses = new HashMapLongObject<JavaMethod>();
    /** The next address to use for a class without an address */
    private long nextClassAddress = 0x1000000080000000L;

    /** Used to store the addresses of all the classes loaded by each class loader */
    HashMapIntObject<ArrayLong> loaderClassCache;
    /**
     * Just used to check efficiency of pseudo roots - holds alternative set of
     * roots.
     */
    private HashMapIntObject<String> missedRoots;
    /**
     * Used to see how much memory has been freed by the initial garbage
     * collection
     */
    private HashMapIntObject<ClassImpl> idToClass2;
    /**
     * Used to see how much memory has been freed by the initial garbage
     * collection
     */
    private IndexWriter.IntIndexCollector objectToClass2;
    
    /**
     * Used for very corrupt dumps without a java/lang/Class
     */
    private static final class DummyJavaClass implements JavaClass, CorruptData
    {
        private final String name; 
        public DummyJavaClass(String name)
        {
            this.name = name;
        }

        public JavaClassLoader getClassLoader() throws CorruptDataException
        {
            throw new CorruptDataException(this);
        }

        public JavaClass getComponentType() throws CorruptDataException
        {
            throw new CorruptDataException(this);
        }

        public Iterator<?> getConstantPoolReferences()
        {
            return Collections.EMPTY_LIST.iterator();
        }

        public Iterator<?> getDeclaredFields()
        {
            return Collections.EMPTY_LIST.iterator();
        }

        public Iterator<?> getDeclaredMethods()
        {
            return Collections.EMPTY_LIST.iterator();
        }

        public ImagePointer getID()
        {
            return null;
        }

        public Iterator<?> getInterfaces()
        {
            return Collections.EMPTY_LIST.iterator();
        }

        public int getModifiers() throws CorruptDataException
        {
            throw new CorruptDataException(this);
        }

        public String getName() throws CorruptDataException
        {
            return name;
        }

        public JavaObject getObject() throws CorruptDataException
        {
            return null;
        }

        public Iterator<?> getReferences()
        {
            return Collections.EMPTY_LIST.iterator();
        }

        public JavaClass getSuperclass() throws CorruptDataException
        {
            throw new CorruptDataException(this);
        }

        public boolean isArray() throws CorruptDataException
        {
            return false;
        }

        public ImagePointer getAddress()
        {
            return null;
        }

        public long getInstanceSize() throws DataUnavailable, CorruptDataException
        {
            throw new DataUnavailable();
        }

        public JavaObject getProtectionDomain() throws DataUnavailable, CorruptDataException
        {
            throw new DataUnavailable();
        }
    }

    /**
     * Use to see how much memory has been freed by the initial garbage
     * collection
     */
    private static class ObjectToSize
    {
        /** For small objects */
        private byte objectToSize[];
        /** For large objects */
        private HashMapIntLong bigObjs = new HashMapIntLong();
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
         * @param index
         * @param size
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

    private ObjectToSize objectToSize2;

    /**
     * Same as HashMapLongObject except that the first
     * item put will be the first returned.
     * Relies on key 0 being returned first from {@link HashMapLongObject} and {@link SetLong}.
     */
    private static abstract class RefStore<E>
    {
        long first;
        public abstract E put(long key, E value);
        public abstract int size();
        public abstract Iterator<HashMapLongObject.Entry<E>> entries();
        public abstract long[] getAllKeys();
        public abstract boolean containsKey(long k);
        public abstract IteratorLong keys();
    }

    private static class RefMap<E> extends RefStore<E>
    {
        final HashMapLongObject<E> map;
        /**
         * Create a map
         */
        public RefMap()
        {
            map = new HashMapLongObject<E>();
        }

        public E put(long key, E value)
        {
            if (size() == 0)
                first = key;
            return map.put(key ^ first, value);
        }

        public int size()
        {
            return map.size();
        }

        public Iterator<HashMapLongObject.Entry<E>> entries()
        {
            final Iterator<HashMapLongObject.Entry<E>> it = map.entries();
            return new Iterator<HashMapLongObject.Entry<E>>() {

                public boolean hasNext()
                {
                    return it.hasNext();
                }

                public HashMapLongObject.Entry<E> next()
                {
                    final HashMapLongObject.Entry<E> e = it.next();
                    return new HashMapLongObject.Entry<E>()
                    {

                        public long getKey()
                        {
                            return e.getKey() ^ first;
                        }

                        public E getValue()
                        {
                            return e.getValue();
                        }

                    };
                }

                public void remove()
                {
                    it.remove();
                }

            };
        }

        public long[] getAllKeys()
        {
            long ret[] = map.getAllKeys();
            for (int i = 0; i < ret.length; ++i)
            {
                ret[i] ^= first;
            }
            return ret;
        }

        public boolean containsKey(long k)
        {
            return map.containsKey(k ^ first);
        }

        public IteratorLong keys()
        {
            final IteratorLong it = map.keys();
            return new IteratorLong()
            {

                public boolean hasNext()
                {
                    return it.hasNext();
                }

                public long next()
                {
                    return it.next() ^ first;
                }

            };
        }
    }

    private static class RefSet<E> extends RefStore<E>
    {
        final SetLong set;
        /**
         * Create a map which just stores keys, not values
         */
        public RefSet()
        {
            set = new SetLong();
        }

        /**
         * Note: returns null if no value already set, or
         * the supplied value if an existing value is set,
         * but not the previous value.
         */
        public E put(long key, E value)
        {
            if (size() == 0)
                first = key;
            return set.add(key ^ first) ? null : value;
        }

        public int size()
        {
            return set.size();
        }

        public Iterator<HashMapLongObject.Entry<E>> entries()
        {
            final IteratorLong it = set.iterator();
            return new Iterator<HashMapLongObject.Entry<E>>()
            {

                public boolean hasNext()
                {
                    return it.hasNext();
                }

                public HashMapLongObject.Entry<E> next()
                {
                    final long e = it.next();
                    return new HashMapLongObject.Entry<E>()
                    {

                        public long getKey()
                        {
                            return e ^ first;
                        }

                        public E getValue()
                        {
                            throw new UnsupportedOperationException();
                        }

                    };
                }

                public void remove()
                {
                    throw new UnsupportedOperationException();
                }

            };
        }

        public long[] getAllKeys()
        {
            long ret[] = set.toArray();
            for (int i = 0; i < ret.length; ++i)
            {
                ret[i] ^= first;
            }
            return ret;
        }

        public boolean containsKey(long k)
        {
            return set.contains(k ^ first);
        }

        public IteratorLong keys()
        {
            final IteratorLong it = set.iterator();
            return new IteratorLong()
            {

                public boolean hasNext()
                {
                    return it.hasNext();
                }

                public long next()
                {
                    return it.next() ^ first;
                }

            };
        }
    }

    /* Message counts to reduce duplicated messages */
    private int msgNgetRefsMissing = errorCount;
    private int msgNgetRefsExtra = errorCount;
    private int msgNarrayRefsNPE = errorCount;
    private int msgNgetRefsUnavailable = errorCount;
    private int msgNgetRefsCorrupt = errorCount;
    private int msgNbigSegs = errorCount;
    private int msgNinvalidArray = errorCount;
    private int msgNinvalidObj = errorCount;
    private int msgNbrokenEquals = errorCount;
    private int msgNbrokenInterfaceSuper = errorCount;
    private int msgNmissingLoaderMsg = errorCount;
    private int msgNcorruptCount = errorCount;
    private int msgNrootsWarning = errorCount;
    private int msgNguessFinalizable = errorCount;
    private int msgNgetRefsAllMissing = errorCount;
    private int msgNgetSuperclass = errorCount;
    private int msgNnullThreadObject = errorCount;
    private int msgNbadThreadInfo = errorCount;
    private int msgNunexpectedModifiers = errorCount;
    private int msgNcorruptSection = errorCount;
    private int msgNclassForObject = errorCount;
    private int msgNcomponentClass = errorCount;
    private int msgNtypeForClassObject = errorCount;
    private int msgNobjectSize = errorCount;
    private int msgNoutboundReferences = errorCount;
    private int msgNnoSuperClassForArray = errorCount;
    private int msgNproblemReadingJavaStackFrame = errorCount;

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.parser.IIndexBuilder#cancel() Appears to be called
     * first before anything happens
     */
    public void cancel()
    {
        // Close DTFJ Image if possible
        if (dtfjInfo != null)
            releaseDump(dump, dtfjInfo, true);

        if (outRefs != null)
        {
            outRefs.cancel();
            outRefs = null;
        }
        if (arrayToSize != null)
        {
            try
            {
                arrayToSize.close();
            }
            catch (IOException e)
            {}
            arrayToSize.delete();
            arrayToSize = null;
        }
        indexToAddress0 = null;
        if (indexToAddress != null)
        {
            try
            {
                indexToAddress.close();
            }
            catch (IOException e)
            {}
            indexToAddress.delete();
            indexToAddress = null;
        }
        objectToClass = null;
        if (objectToClass1 != null)
        {
            try
            {
                objectToClass1.close();
            }
            catch (IOException e)
            {}
            objectToClass1.delete();
            objectToClass1 = null;
        }
        idToClass = null;
        if (objectToClass2 != null)
        {
            try
            {
                objectToClass2.close();
                objectToClass2.delete();
            }
            catch (IOException e)
            {}
            objectToClass2 = null;
        }
        idToClass2 = null;
        objectToSize2 = null;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.parser.IIndexBuilder#clean(int[],
     * org.eclipse.mat.util.IProgressListener) Called after initial garbage
     * collection to show new indices for objects and -1 for object which are
     * garbage and have been deleted.
     */
    public void clean(int[] purgedMapping, IProgressListener listener) throws IOException
    {
        if (purgedMapping == null)
        {
            listener.sendUserMessage(Severity.ERROR, Messages.DTFJIndexBuilder_NullPurgedMapping, null);
            return;
        }
        listener.beginTask(Messages.DTFJIndexBuilder_PurgingDeadObjectsFromImage, purgedMapping.length / 10000);
        int count = 0;
        long memFree = 0;
        for (int i = 0; i < purgedMapping.length; ++i)
        {
            if (i % 10000 == 0)
            {
                listener.worked(1);
                if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
            }
            if (purgedMapping[i] == -1)
            {
                ++count;
                if (objectToSize2 != null)
                {
                    long objSize = objectToSize2.getSize(i);
                    memFree += objSize;
                    if (verbose)
                    {
                        int type = objectToClass2.get(i);
                        debugPrint("Purging " + i + " size " + objSize + " type " + type + " " + idToClass2.get(type)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    }
                }
                if (missedRoots != null && missedRoots.containsKey(i))
                {
                    debugPrint("Alternative roots would have found root " + i + " " + missedRoots.get(i)); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            else
            {
                // debugPrint("Remap "+i+"->"+purgedMapping[i]);
            }
        }
        if (debugInfo)
        {
            listener.sendUserMessage(Severity.INFO, MessageFormat.format(Messages.DTFJIndexBuilder_PurgedIdentifiers,
                            count, memFree), null);
        }
        // Free memory
        objectToSize2 = null;
        missedRoots = null;
        releaseDump(dump, dtfjInfo, false);
        // Debug
        listener.done();
    }

    /*
     * (non-Javadoc)
     * @seeorg.eclipse.mat.parser.IIndexBuilder#fill(org.eclipse.mat.parser.
     * IPreliminaryIndex, org.eclipse.mat.util.IProgressListener) Fill the
     * initial index with: mapping object index to address
     */
    public void fill(IPreliminaryIndex index, IProgressListener listener) throws SnapshotException, IOException
    {

        long then1 = System.currentTimeMillis();

        // This is 100% on the progress bar
        final int workCount = 10000;
        // How many objects to process before indicating to the progress bar
        final int workObjectsStep = 10000;
        listener.beginTask(MessageFormat.format(Messages.DTFJIndexBuilder_ProcessingImageFromFile, dump), workCount);
        int workCountSoFar = 0;

        XSnapshotInfo ifo = index.getSnapshotInfo();

        // The dump may have changed, so reread it
        clearCachedDump(dump);
        Serializable dumpType = ifo.getProperty("$heapFormat"); //$NON-NLS-1$
        dtfjInfo = getDump(dump, dumpType);

        long now1 = System.currentTimeMillis();
        listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                        Messages.DTFJIndexBuilder_TookmsToGetImageFromFile, (now1 - then1), dump, dumpType), null);

        // Basic information
        try
        {
            ifo.setCreationDate(new Date(dtfjInfo.getImage().getCreationTime()));
        }
        catch (DataUnavailable e)
        {
            listener.sendUserMessage(Severity.WARNING, Messages.DTFJIndexBuilder_NoDateInImage, e);
        }

        // Find the JVM
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingJVM);
        dtfjInfo = getRuntime(dtfjInfo.getImage(), runtimeId, listener);
        final String actualRuntimeId = dtfjInfo.getRuntimeId();
        if (actualRuntimeId != null)
        {
            index.getSnapshotInfo().setProperty(RUNTIME_ID_KEY, actualRuntimeId);
            listener.sendUserMessage(Severity.INFO,
                            MessageFormat.format(Messages.DTFJIndexBuilder_DTFJJavaRuntime, actualRuntimeId), null);
        }

        try
        {
            ifo.setJvmInfo(dtfjInfo.getJavaRuntime().getVersion());
            listener.sendUserMessage(Severity.INFO, MessageFormat.format(Messages.DTFJIndexBuilder_JVMVersion, ifo
                            .getJvmInfo()), null);
        }
        catch (CorruptDataException e)
        {
            listener.sendUserMessage(Severity.WARNING, Messages.DTFJIndexBuilder_NoRuntimeVersionFound, e);
            try
            {
                ifo.setJvmInfo(dtfjInfo.getJavaRuntime().getFullVersion());
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(Messages.DTFJIndexBuilder_JVMFullVersion,
                                ifo.getJvmInfo()), null);
            }
            catch (CorruptDataException e2)
            {
                listener.sendUserMessage(Severity.WARNING, Messages.DTFJIndexBuilder_NoRuntimeFullVersionFound, e2);
            }
        }

        int pointerSize = getPointerSize(dtfjInfo, listener);
        ifo.setIdentifierSize(getPointerBytes(pointerSize));

        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_Pass1);

        indexToAddress0 = new IndexWriter.Identifier();

        // Pass 1

        // Find last address of heap - use for dummy class addresses
        long lastAddress = 0x0;
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getHeaps(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingHeaps, dtfjInfo.getJavaRuntime()))
                continue;
            JavaHeap jh = (JavaHeap) next;
            for (Iterator<?> i2 = jh.getSections(); i2.hasNext();)
            {
                Object next2 = i2.next();
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingHeapSections, dtfjInfo.getJavaRuntime()))
                {
                    // Even a corrupt section might have an address and size
                    if (!(next2 instanceof ImageSection))
                        continue;
                }
                ImageSection is = (ImageSection) next2;
                long endAddr = is.getBaseAddress().add(is.getSize()).getAddress();
                lastAddress = Math.max(lastAddress, endAddr);
            }
        }
        if (lastAddress != 0)
            nextClassAddress = (lastAddress + 7L) & ~7L;

        // Find the bootstrap loader using the idea that it it the only loader
        // to have loaded itself
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingClassLoaders);
        JavaObject bootLoaderObject = null;
        JavaClassLoader bootLoader = null;
        long bootLoaderAddress = 0, fixedBootLoaderAddress = 0;
        ClassImpl bootLoaderType = null;
        boolean foundBootLoader = false;
        HashMap<JavaObject, JavaClassLoader> loaders = new HashMap<JavaObject, JavaClassLoader>();
        HashSet<JavaClass> loaderTypes = new HashSet<JavaClass>();
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getJavaClassLoaders(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingClassLoaders1, dtfjInfo.getJavaRuntime()))
                continue;
            JavaClassLoader jcl = (JavaClassLoader) next;
            long loaderAddress = 0;
            try
            {
                JavaObject loaderObject = jcl.getObject();
                // Remember the class loader
                loaders.put(loaderObject, jcl);
                if (loaderObject == null)
                {
                    // Potential boot loader
                    debugPrint("Found class loader with null Java object " + jcl); //$NON-NLS-1$
                    if (!foundBootLoader)
                    {
                        bootLoader = jcl;
                        bootLoaderObject = loaderObject;
                        bootLoaderAddress = 0;
                        fixedBootLoaderAddress = fixBootLoaderAddress(bootLoaderAddress, bootLoaderAddress);
                        debugPrint("adding boot loader object at " + format(bootLoaderAddress)); //$NON-NLS-1$
                        foundBootLoader = true;
                    }
                }
                else
                {
                    // Get address first, in case getting class fails
                    loaderAddress = loaderObject.getID().getAddress();
                    if (bootLoader == null)
                    {
                        // Make sure there is some kind of boot loader, this
                        // should be replaced later.
                        bootLoader = jcl;
                        bootLoaderObject = loaderObject;
                        bootLoaderAddress = loaderAddress;
                        fixedBootLoaderAddress = fixBootLoaderAddress(bootLoaderAddress, bootLoaderAddress);
                        debugPrint("adding potential boot loader object at " + format(bootLoaderAddress)); //$NON-NLS-1$
                    }
                    JavaClass loaderObjectClass = loaderObject.getJavaClass();
                    loaderTypes.add(loaderObjectClass);
                    String loaderClassName = getClassName(loaderObjectClass, listener);
                    if (loaderClassName.equals("*System*")) //$NON-NLS-1$
                    {
                        // Potential boot loader - Javacore
                        debugPrint("Found class loader of type *System* " + jcl); //$NON-NLS-1$
                        if (!foundBootLoader)
                        {
                            bootLoader = jcl;
                            bootLoaderObject = loaderObject;
                            bootLoaderAddress = loaderAddress;
                            fixedBootLoaderAddress = fixBootLoaderAddress(bootLoaderAddress, bootLoaderAddress);
                            // No need for dummy Java object
                            debugPrint("adding boot loader object at " + format(bootLoaderAddress)); //$NON-NLS-1$
                            foundBootLoader = true;
                        }
                    }
                    else
                    {
                        debugPrint("Found class loader " + loaderClassName + " at " + format(loaderAddress)); //$NON-NLS-1$ //$NON-NLS-2$

                        JavaClassLoader jcl2 = getClassLoader(loaderObjectClass, listener);
                        if (jcl.equals(jcl2))
                        {
                            debugPrint("Found class loader which loaded itself " + loaderClassName + " at " + format(loaderAddress)); //$NON-NLS-1$ //$NON-NLS-2$
                            if (!foundBootLoader)
                            {
                                bootLoader = jcl;
                                bootLoaderObject = loaderObject;
                                bootLoaderAddress = loaderAddress;
                                fixedBootLoaderAddress = fixBootLoaderAddress(bootLoaderAddress, bootLoaderAddress);
                                // No need for dummy Java object
                                debugPrint("adding boot loader object at " + format(bootLoaderAddress)); //$NON-NLS-1$
                                foundBootLoader = true;
                            }
                        }
                        else
                        {
                            debugPrint("Found other class loader " + loaderClassName + " at " + format(loaderAddress)); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                }
            }
            catch (CorruptDataException e)
            {
                // 1.4.2 AIX 64-bit CorruptDataExceptions
                // from Class loader objects: 32/64-bit problem
                listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemFindingClassLoaderInformation, format(loaderAddress)),
                                e);
            }
        }
        if (bootLoader == null)
        {
            // Very corrupt dump with no useful loader information
            fixedBootLoaderAddress = fixBootLoaderAddress(bootLoaderAddress, bootLoaderAddress);
            // Boot loader with 0 address needs a dummy entry as no Java object
            // for it will be found
            indexToAddress0.add(fixedBootLoaderAddress);
            debugPrint("No boot class loader found so adding dummy boot class loader object at " //$NON-NLS-1$
                            + format(fixedBootLoaderAddress));
        }
        else if (bootLoaderObject == null)
        {
            // Boot loader with null object implying 0 address needs a dummy entry as no Java
            // object for it will be found
            indexToAddress0.add(fixedBootLoaderAddress);
        }

        // Holds all of the classes as DTFJ JavaClass - just used in this
        // method.
        // Use a hash set to collect the classes and sort them later
        Set<JavaClass> allClasses = new LinkedHashSet<JavaClass>();

        // Find all the classes (via the class loaders), and remember them
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingClasses);
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getJavaClassLoaders(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingClassLoaders, dtfjInfo.getJavaRuntime()))
                continue;
            JavaClassLoader jcl = (JavaClassLoader) next;
            for (Iterator<?> j = jcl.getDefinedClasses(); j.hasNext();)
            {
                Object next2 = j.next();
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingClasses, jcl))
                    continue;
                JavaClass j2 = (JavaClass) next2;
                rememberClass(j2, allClasses, listener);
            }
        }

        // Find all the objects - don't store them as too many
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingObjects);
        int objProgress = 0;
        final int s2 = indexToAddress0.size();
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getHeaps(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingHeaps, dtfjInfo.getJavaRuntime()))
                continue;
            JavaHeap jh = (JavaHeap) next;
            for (Iterator<?> j = jh.getObjects(); j.hasNext();)
            {
                Object next2 = j.next();
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingObjects, dtfjInfo.getJavaRuntime()))
                    continue;
                JavaObject jo = (JavaObject) next2;

                if (++objProgress % workObjectsStep == 0)
                {
                    listener.worked(1);
                    workCountSoFar += 1;
                    if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
                }

                long objAddress = jo.getID().getAddress();
                objAddress = fixBootLoaderAddress(bootLoaderAddress, objAddress);

                rememberObject(jo, objAddress, allClasses, listener);
            }
        }
        final int nobj = indexToAddress0.size() - s2;
        debugPrint("Objects on heap " + nobj); //$NON-NLS-1$

        // Find any more classes (via the class loaders), and remember them
        // This might not be needed - all the classes cached should be found in
        // the defined list of some loader
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingClassesCachedByClassLoaders);
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getJavaClassLoaders(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingClassLoaders, dtfjInfo.getJavaRuntime()))
                continue;
            if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
            JavaClassLoader jcl = (JavaClassLoader) next;
            for (Iterator<?> j = jcl.getCachedClasses(); j.hasNext();)
            {
                Object next2 = j.next();
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingCachedClasses, jcl))
                    continue;
                JavaClass j2 = (JavaClass) next2;
                if (!allClasses.contains(j2))
                {
                    try
                    {
                        String className = j2.getName();
                        listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_AddingExtraClassViaCachedList, className), null);
                    }
                    catch (CorruptDataException e)
                    {
                        listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_AddingExtraClassOfUnknownNameViaCachedList, j2), e);
                    }
                    rememberClass(j2, allClasses, listener);
                }
            }
        }

        // Make the ID to address array ready for reverse lookups
        if (indexToAddress0.size() > 0)
        {
            indexToAddress0.sort();
        }

        // Holds all of the methods as DTFJ JavaMethod - just used in this
        // method. Initialise always to avoid compilation errors.
        LinkedHashSet<JavaMethod> allMethods = new LinkedHashSet<JavaMethod>();
        // Holds a mapping from stack frame to method addresses
        LinkedHashMap<Long, Long> allFrames = new LinkedHashMap<Long, Long>();

        // See if thread, monitor and class loader objects are present in heap
        // core-sample-dmgr.dmp.zip
        HashMapLongObject<JavaObject> missingObjects = new HashMapLongObject<JavaObject>();
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingThreadObjectsMissingFromHeap);
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getThreads(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingThreads, dtfjInfo.getJavaRuntime()))
                continue;
            JavaThread th = (JavaThread) next;
            JavaObject threadObject;
            try
            {
                threadObject = th.getObject();
            }
            catch (CorruptDataException e)
            {
                threadObject = null;
            }
            // Thread object could be null if the thread is being attached
            long threadAddress = getThreadAddress(th, listener);
            if (threadAddress != 0)
            {
                if (indexToAddress0.reverse(threadAddress) < 0)
                {
                    missingObjects.put(threadAddress, threadObject);
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ThreadObjectNotFound, format(threadAddress)), null);
                }
            }
            if (getExtraInfo)
            {
                // Scan stack frames for pseudo-classes
                int frameId = 0;
                long prevFrameAddress = 0;
                for (Iterator<?> ii = th.getStackFrames(); ii.hasNext(); ++frameId)
                {
                    Object next2 = ii.next();
                    if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingJavaStackFrames,
                                    th))
                        continue;
                    JavaStackFrame jf = (JavaStackFrame) next2;
                    if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
                    JavaLocation jl = null;
                    try
                    {
                        jl = jf.getLocation();
                        JavaMethod jm = jl.getMethod();
                        long frameAddress = getFrameAddress(jf, prevFrameAddress, pointerSize);
                        prevFrameAddress = frameAddress;
                        if (frameAddress != 0)
                        {
                            if (indexToAddress0.reverse(frameAddress) < 0)
                            {
                                long methodAddress = getMethodAddress(jm, listener);
                                if (!allFrames.containsKey(frameAddress))
                                {
                                    if (!getExtraInfo3)
                                        allMethods.add(jm);
                                    allFrames.put(frameAddress, methodAddress);
                                }
                                else
                                {
                                    String newMethodName;
                                    try
                                    {
                                        newMethodName = getMethodName(jm, listener);
                                    }
                                    catch (CorruptDataException e)
                                    {
                                        newMethodName = format(methodAddress);
                                    }
                                    String oldMethodName;
                                    long oldMethodAddress = allFrames.get(frameAddress);
                                    try
                                    {
                                        JavaMethod oldJm = methodAddresses.get(allFrames.get(frameAddress));
                                        if (oldJm != null)
                                        {
                                            oldMethodName = getMethodName(oldJm, listener);
                                        }
                                        else
                                        {
                                            oldMethodName = format(oldMethodAddress);
                                        }
                                    }
                                    catch (CorruptDataException e)
                                    {
                                        oldMethodName = format(allFrames.get(oldMethodAddress));
                                    }
                                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                    Messages.DTFJIndexBuilder_DuplicateJavaStackFrame, frameId,
                                                    format(frameAddress), newMethodName, oldMethodName,
                                                    format(threadAddress)), null);
                                }
                            }
                            else
                            {
                                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_IgnoringJavaStackFrame, frameId,
                                                format(frameAddress), format(threadAddress)), null);
                            }
                        }
                    }
                    catch (CorruptDataException e)
                    {
                        if (jl != null)
                        {
                            if (msgNproblemReadingJavaStackFrame-- > 0)
                                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_ProblemReadingJavaStackFrameLocation, frameId,
                                                jl, format(threadAddress)), e);
                        }
                        else
                        {
                            if (msgNproblemReadingJavaStackFrame-- > 0)
                                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_ProblemReadingJavaStackFrame, frameId,
                                                format(threadAddress)), e);
                        }
                    }
                }
            }
        }
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingMonitorObjects);
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getMonitors(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingMonitors, dtfjInfo.getJavaRuntime()))
                continue;
            JavaMonitor jm = (JavaMonitor) next;
            JavaObject obj = jm.getObject();
            if (obj != null)
            {
                long monitorAddress = obj.getID().getAddress();
                if (indexToAddress0.reverse(monitorAddress) < 0)
                {
                    missingObjects.put(monitorAddress, obj);
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_MonitorObjectNotFound, format(monitorAddress)), null);
                }
            }
        }
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingClassLoaderObjects);
        for (Iterator<JavaObject> i = loaders.keySet().iterator(); i.hasNext();)
        {
            JavaObject obj = i.next();
            if (obj != null)
            {
                long loaderAddress = obj.getID().getAddress();
                loaderAddress = fixBootLoaderAddress(bootLoaderAddress, loaderAddress);
                if (indexToAddress0.reverse(loaderAddress) < 0)
                {
                    missingObjects.put(loaderAddress, obj);
                    try
                    {
                        String type = getClassName(obj.getJavaClass(), listener);
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ClassLoaderObjectNotFoundType, format(loaderAddress),
                                        type), null);
                    }
                    catch (CorruptDataException e)
                    {
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ClassLoaderObjectNotFound, format(loaderAddress)), e);
                    }

                }
            }
        }
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_AddingMissingObjects);
        for (Iterator<HashMapLongObject.Entry<JavaObject>> it = missingObjects.entries(); it.hasNext(); )
        {
            HashMapLongObject.Entry<JavaObject> entry = it.next();
            JavaObject obj = entry.getValue();
            long address = entry.getKey();
            if (obj != null)
            {
                rememberObject(obj, address, allClasses, listener);
            }
            else
            {
                indexToAddress0.add(address);
            }
        }

        // check for superclasses in case the classloader list is incomplete
        Set<JavaClass>extraSuperclasses = new LinkedHashSet<JavaClass>();
        for (JavaClass cls : allClasses)
        {
            for (JavaClass sup = getSuperclass(cls, listener); sup != null; sup = getSuperclass(sup, listener))
            {
                if (!allClasses.contains(sup))
                {
                    if (!extraSuperclasses.add(sup))
                        break;
                }
                else
                {
                    break;
                }
            }
        }
        for (JavaClass sup : extraSuperclasses)
        {
            try
            {
                String className = sup.getName();
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                Messages.DTFJIndexBuilder_AddingExtraClassViaSuperclassList, className), null);
            }
            catch (CorruptDataException e)
            {
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                Messages.DTFJIndexBuilder_AddingExtraClassOfUnknownNameViaSuperclassList, sup), e);
            }
            rememberClass(sup, allClasses, listener);
        }
        extraSuperclasses.clear();

        // Make a tree set so that going over all the classes is
        // predictable and cache friendly.
        final IProgressListener listen = listener;
        TreeSet<JavaClass> sortedClasses = new TreeSet<JavaClass>(new Comparator<JavaClass>()
        {
            public int compare(JavaClass o1, JavaClass o2)
            {
                long clsaddr1 = getClassAddress(o1, listen);
                long clsaddr2 = getClassAddress(o2, listen);
                return clsaddr1 < clsaddr2 ? -1 : clsaddr1 > clsaddr2 ? 1 : 0;
            }
        });
        sortedClasses.addAll(allClasses);
        allClasses = sortedClasses;
        
        if (getExtraInfo && getExtraInfo2)
        {
            listener.worked(1);
            workCountSoFar += 1;
            listener.subTask(Messages.DTFJIndexBuilder_FindingAllMethods);
            for (JavaClass jc : allClasses)
            {
                for (Iterator<?> i = jc.getDeclaredMethods(); i.hasNext();)
                {
                    Object next = i.next();
                    if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredMethods, jc))
                        continue;
                    JavaMethod jm = (JavaMethod) next;
                    allMethods.add(jm);
                }
            }
        }

        long nativeAddr = 0;
        long nativeTypeAddr = 0;
        long methodTypeAddr = 0;
        long methodAddr = 0;
        long stackFrameAddr = 0;
        if (getExtraInfo)
        {
            // Dummy address for the native memory and method pseudo-type
            nativeAddr = nextClassAddress;
            indexToAddress0.add(nativeAddr);
            nextClassAddress += 8;
            nativeTypeAddr = nextClassAddress;
            indexToAddress0.add(nativeTypeAddr);
            nextClassAddress += 8;
            if (getExtraInfo3)
            {
                stackFrameAddr = nextClassAddress;
                indexToAddress0.add(stackFrameAddr);
                nextClassAddress += 8;
            }
            else
            {
                methodTypeAddr = nextClassAddress;
                indexToAddress0.add(methodTypeAddr);
                nextClassAddress += 8;
                methodAddr = nextClassAddress;
                indexToAddress0.add(methodAddr);
                nextClassAddress += 8;

                // Extra objects when dealing with stack frames as objects
                // Add the methods
                for (JavaMethod jm : allMethods)
                {
                    indexToAddress0.add(getMethodAddress(jm, listener));
                }
            }
            // Add the frames
            for (long frame : allFrames.keySet())
            {
                indexToAddress0.add(frame);
            }
            debugPrint("added methods " + allMethods.size() + " frames " + allFrames.size()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Make the ID to address array ready for reverse lookups
        if (indexToAddress0.size() > 0)
        {
            indexToAddress0.sort();
        }

        // Temporary list for classes
        IndexWriter.Identifier indexToAddressCls = new IndexWriter.Identifier();

        //
        JavaClass clsJavaLangClassLoader = null;
        JavaClass clsJavaLangClass = null;
        for (JavaClass j2 : allClasses)
        {
            // First find the class obj for java.lang.Class
            // This is needed for every other class
            try
            {
                JavaObject clsObject = j2.getObject();
                if (clsObject != null)
                {
                    clsJavaLangClass = clsObject.getJavaClass();
                    // Found class, so done
                    break;
                }
            }
            catch (IllegalArgumentException e)
            {
                // IllegalArgumentException from
                // JavaClass.getObject() due to bad class pointer in object
                listener.sendUserMessage(Severity.ERROR, Messages.DTFJIndexBuilder_ProblemFindingJavaLangClass, e);
            }
            catch (CorruptDataException e)
            {
                if (msgNcorruptCount-- > 0)
                    listener
                                    .sendUserMessage(Severity.WARNING,
                                                    Messages.DTFJIndexBuilder_ProblemFindingJavaLangClass, e);
            }
        }
        if (clsJavaLangClass != null)
        {
            // Just in case it isn't there already
            allClasses.add(clsJavaLangClass);
        }
        // Total all the classes and remember the addresses for mapping to IDs
        for (JavaClass cls : allClasses)
        {
            String clsName = null;
            // Get the name - if we cannot get the name then the class can
            // not be built.
            clsName = getClassName(cls, listen);
            // Find java.lang.ClassLoader. There should not be duplicates.
            if (clsJavaLangClassLoader == null && clsName.equals(JAVA_LANG_CLASSLOADER))
                clsJavaLangClassLoader = cls;
            // Find java.lang.Class. There should not be duplicates.
            if (clsJavaLangClass == null && clsName.equals(JAVA_LANG_CLASS))
                clsJavaLangClass = cls;
            long clsaddr = getClassAddress(cls, listener);
            /*
             * IBM Java 5.0 seems to have JavaClass at the same address as the
             * associated object, and these are outside the heap, so need to be
             * counted. IBM Java 6 seems to have JavaClass at a different
             * address to the associated object and the associated object is
             * already in the heap, so will have been found already. IBM Java
             * 1.4.2 can have classes without associated objects. These won't be
             * on the heap so should be added now. The other class objects have
             * the same address as the real objects and are listed in the heap.
             * If the id is null then the object will be too. Double counting is
             * bad.
             */
            if (indexToAddress0.reverse(clsaddr) < 0)
            {
                // JavaClass == JavaObject, so add the class (which isn't on
                // the heap) to the list
                indexToAddressCls.add(clsaddr);
                debugPrint("adding class " + clsName + " at " + format(clsaddr) + " to the identifier list"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            else
            {
                debugPrint("skipping class " + clsName + " at " + format(clsaddr) //$NON-NLS-1$ //$NON-NLS-2$
                                + " as the associated object is already on the identifier list"); //$NON-NLS-1$
            }
        }
        // Check for very corrupt dumps
        if (clsJavaLangClass == null)
        {
            listener.sendUserMessage(Severity.WARNING,
                            Messages.DTFJIndexBuilder_ProblemFindingJavaLangClassViaName, null);
            // Create a dummy java/lang/Class
            clsJavaLangClass = new DummyJavaClass(JAVA_LANG_CLASS);
            allClasses.add(clsJavaLangClass);
            long clsaddr = getClassAddress(clsJavaLangClass, listener);
            indexToAddressCls.add(clsaddr);
        }
        if (clsJavaLangClassLoader == null)
        {
            // Create a dummy java/lang/ClassLoader
            clsJavaLangClassLoader = new DummyJavaClass(JAVA_LANG_CLASSLOADER);
            allClasses.add(clsJavaLangClassLoader);
            long clsaddr = getClassAddress(clsJavaLangClassLoader, listener);
            indexToAddressCls.add(clsaddr);
        }

        // Add class ids to object list
        for (int i = 0; i < indexToAddressCls.size(); ++i)
        {
            indexToAddress0.add(indexToAddressCls.get(i));
        }
        // Free the class address list for GC
        indexToAddressCls = null;

        int nClasses = allClasses.size();

        int pseudoClasses;
        if (getExtraInfo)
        {
            if (getExtraInfo3)
                pseudoClasses = 3;
            else
                pseudoClasses = 4;
            nClasses += allMethods.size() + pseudoClasses; // For method pseudo-types
        }
        else
        {
            pseudoClasses = 0;
        }

        // Make the ID to address array ready for reverse lookups
        if (indexToAddress0.size() > 0)
        {
            indexToAddress0.sort();
        }
        Runtime runtime = Runtime.getRuntime();
        long maxFree = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
        // If we do not have lots of free space then use a temporary file.
        // The constant 16 is an experimentally determined value.
        long indexCountForTempFile = Math.max(INDEX_COUNT_FOR_TEMPFILE, maxFree / 16);
        if (indexToAddress0.size() >= indexCountForTempFile)
        {
            // Write the index to disk and then use the compressed disk version
            indexToAddress = (new LongIndexStreamer()).writeTo(Index.IDENTIFIER.getFile(pfx + "temp."), indexToAddress0.iterator());
        }
        else
        {
            // The flat version is bigger but a little quicker
            indexToAddress = indexToAddress0;
        }
        indexToAddress0 = null;
        // Notify the builder about all the identifiers.
        index.setIdentifiers(indexToAddress);
        if (getExtraInfo)
        {
            listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                            Messages.DTFJIndexBuilder_FoundIdentifiersObjectsClassesMethods, indexToAddress.size(),
                            indexToAddress.size() - nClasses - allFrames.size(), allFrames.size(), nClasses - allMethods.size() - pseudoClasses, allMethods.size()), null);
        }
        else
        {
            listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                            Messages.DTFJIndexBuilder_FoundIdentifiersObjectsClasses, indexToAddress.size(),
                            indexToAddress.size() - nClasses, nClasses), null);
        }
        debugPrint("Total identifiers " + indexToAddress.size()); //$NON-NLS-1$

        if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }

        // Pass 2 - build the classes and object data
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_Pass2);
        debugPrint("Classes " + nClasses); //$NON-NLS-1$
        idToClass = new HashMapIntObject<ClassImpl>(nClasses);

        if (debugInfo)
        {
            // For calculating purge sizes
            objectToSize2 = new ObjectToSize(indexToAddress.size());
        }

        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_BuildingClasses);

        ClassImpl jlc = genClass(clsJavaLangClass, idToClass, bootLoaderAddress, 0, listener);
        genClass2(clsJavaLangClass, jlc, jlc, pointerSize, listener);

        // Now do java.lang.ClassLoader
        ClassImpl jlcl = clsJavaLangClassLoader != null ? genClass(clsJavaLangClassLoader, idToClass, bootLoaderAddress, 0, listener) : null;
        if (jlcl != null)
        {
            genClass2(clsJavaLangClassLoader, jlcl, jlc, pointerSize, listener);
        }

        boolean foundFields = false;
        for (JavaClass j2 : allClasses)
        {
            // Don't do java.lang.Class twice
            if (j2.equals(clsJavaLangClass))
                continue;
            // Don't do java.lang.ClassLoader twice
            if (j2.equals(clsJavaLangClassLoader))
                continue;

            // Fix for PHD etc without superclasses
            // so make class loader types extend java.lang.ClassLoader
            long newSuper = 0;
            if (jlcl != null && loaderTypes.contains(j2))
            {
                JavaClass sup = getSuperclass(j2, listener);
                if (sup == null || getSuperclass(sup, listener) == null)
                {
                    newSuper = jlcl.getObjectAddress();
                }
            }

            ClassImpl ci = genClass(j2, idToClass, bootLoaderAddress, newSuper, listener);
            if (ci != null)
            {
                genClass2(j2, ci, jlc, pointerSize, listener);
                // See if any fields have been found, or whether we need to use
                // getReferences instead
                if (!foundFields)
                {
                    List<FieldDescriptor> fd = ci.getFieldDescriptors();
                    if (!fd.isEmpty())
                        foundFields = true;
                }
            }
        }

        if (bootLoaderObject == null)
        {
            // If there is no boot loader type,
            bootLoaderType = jlcl;
            // The bootLoaderType should always have been found by now, so
            // invent something now to avoid NullPointerExceptions.
            if (bootLoaderType == null)
                bootLoaderType = idToClass.values().next();
        }

        // If none of the classes have any fields then we have to try using
        // references instead.
        if (!foundFields)
        {
            // E.g. PHD dumps
            haveDTFJRefs = true;
            useDTFJRefs = true;
        }

        if (getExtraInfo)
        {
            ClassImpl nativeType = genDummyType(NATIVE_MEMORY_TYPE, nativeTypeAddr, nativeAddr, null, new FieldDescriptor[0], idToClass, bootLoaderAddress, listener);
            ClassImpl nativeMemory = genDummyType(NATIVE_MEMORY, nativeAddr, 0L, nativeType, new FieldDescriptor[0], idToClass, bootLoaderAddress, listener);
            if (getExtraInfo3)
            {
                FieldDescriptor[] fld = new FieldDescriptor[] { new FieldDescriptor(LINE_NUMBER, IObject.Type.INT),
                                new FieldDescriptor(COMPILATION_LEVEL, IObject.Type.INT),
                                new FieldDescriptor(LOCATION_ADDRESS, IObject.Type.LONG),
                                new FieldDescriptor(FILE_NAME, IObject.Type.OBJECT),
                                new FieldDescriptor(METHOD_NAME, IObject.Type.OBJECT),
                                new FieldDescriptor(FRAME_NUMBER, IObject.Type.INT),
                                new FieldDescriptor(STACK_DEPTH, IObject.Type.INT) };
                ClassImpl stackFrame = genDummyType(STACK_FRAME, stackFrameAddr, nativeAddr, nativeType, fld, idToClass, bootLoaderAddress, listener);
            }
            else
            {
                FieldDescriptor[] fld = new FieldDescriptor[] { new FieldDescriptor(LINE_NUMBER, IObject.Type.INT),
                                new FieldDescriptor(COMPILATION_LEVEL, IObject.Type.INT),
                                new FieldDescriptor(LOCATION_ADDRESS, IObject.Type.LONG),
                                new FieldDescriptor(FILE_NAME, IObject.Type.OBJECT),
                                new FieldDescriptor(FRAME_NUMBER, IObject.Type.INT),
                                new FieldDescriptor(STACK_DEPTH, IObject.Type.INT) };
                ClassImpl methodType = genDummyType(METHOD_TYPE, methodTypeAddr, nativeTypeAddr, nativeType, new FieldDescriptor[0], idToClass, bootLoaderAddress, listener);
                ClassImpl method = genDummyType(METHOD, methodAddr, nativeAddr, methodType, fld, idToClass, bootLoaderAddress, listener);

                for (JavaMethod jm : allMethods)
                {
                    try
                    {
                        ClassImpl ci = genClass(jm, methodAddr, methodType, idToClass, bootLoaderAddress, listener);
                    }
                    catch (CorruptDataException e)
                    {
                        listener.sendUserMessage(Severity.ERROR,
                                        Messages.DTFJIndexBuilder_ProblemBuildingClassObjectForMethod, e);
                    }
                }
            }
        }

        // fix up the subclasses for MAT
        int maxClsId = 0;
        for (Iterator<ClassImpl> i = idToClass.values(); i.hasNext();)
        {
            ClassImpl ci = i.next();
            int supid = ci.getSuperClassId();
            if (supid >= 0)
            {
                ClassImpl sup = idToClass.get(supid);
                if (sup != null)
                {
                    sup.addSubClass(ci);
                }
            }
            maxClsId = Math.max(maxClsId, ci.getObjectId());
        }
        // Notify the builder about the classes. The builder seems to destroy
        // entries which are unreachable.
        index.setClassesById(idToClass);

        // See which classes would have finalizable objects
        SetLong finalizableClass;
        if (guessFinalizables)
        {
            finalizableClass = new SetLong();
            for (JavaClass cls : allClasses)
            {
                long addr = isFinalizable(cls, listener);
                if (addr != 0)
                    finalizableClass.add(addr);
            }
        }

        // Object id to class id
        objectToClass = new IndexWriter.IntIndexCollector(indexToAddress.size(), IndexWriter
                        .mostSignificantBit(maxClsId));

        // Do the object refs to other refs
        IOne2ManyIndex out2b;
        outRefs = new IndexWriter.IntArray1NWriter(indexToAddress.size(), Index.OUTBOUND.getFile(pfx
                        + "temp.")); //$NON-NLS-1$

        // Keep track of all objects which are referred to. Remaining objects
        // are candidate roots
        BitField refd = new BitField(indexToAddress.size());

        // fix up type of class objects
        for (Iterator<ClassImpl> i = idToClass.values(); i.hasNext();)
        {
            ClassImpl ci = i.next();
            int clsId = ci.getClassId();
            int objId = ci.getObjectId();
            objectToClass.set(objId, clsId);
        }

        // check outbound refs
        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingOutboundReferencesForClasses);
        // 10% of remaining bar for the classes processing
        final int classFrac = 3;
        final int workObjectsStep2 = Math.max(1, classFrac * allClasses.size() / (workCount - workCountSoFar));
        final int work2 = (workCount - workCountSoFar) * workObjectsStep2 / (classFrac * allClasses.size() + 1);
        // Classes processed in address order (via TreeSet) so PHD reading is
        // cache friendly.
        for (JavaClass j2 : allClasses)
        {
            if (++objProgress % workObjectsStep2 == 0)
            {
                listener.worked(work2);
                workCountSoFar += work2;
                if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
            }
            long claddr = getClassAddress(j2, listener);
            int objId = indexToAddress.reverse(claddr);
            // Accumulate the outbound refs
            ArrayLong ref = exploreClass(indexToAddress, fixedBootLoaderAddress, idToClass, j2, listener);
            if (ref == null)
                continue;
            try
            {
                checkRefs(j2, Messages.DTFJIndexBuilder_CheckRefsClass, ref, jlc.getObjectAddress(), bootLoaderAddress,
                                listener);
            }
            catch (CorruptDataException e)
            {
                String clsName = null;
                ClassImpl ci = idToClass.get(objId);
                if (ci != null)
                    clsName = ci.getName();
                if (clsName == null)
                    clsName = j2.toString();
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemCheckingOutboundReferencesForClass, clsName), e);
            }

            // fix up outbound refs for ordinary classes
            addRefs(refd, objId, ref);
            outRefs.log(indexToAddress, objId, ref);
        }

        if (getExtraInfo)
        {
            addDummyTypeRefs(nativeAddr, refd);
            addDummyTypeRefs(nativeTypeAddr, refd);
            if (getExtraInfo3)
            {
                addDummyTypeRefs(stackFrameAddr, refd);
            }
            else
            {
                // fix up outbound refs for methods
                for (JavaMethod m2 : allMethods)
                {
                    long claddr = getMethodAddress(m2, listener);
                    addDummyTypeRefs(claddr, refd);
                }
                addDummyTypeRefs(methodTypeAddr, refd);
                addDummyTypeRefs(methodAddr, refd);
            }
        }

        if (getExtraInfo)
        {
            // fix the types of all the frames
            for (long addr : allFrames.keySet())
            {
                int objId = indexToAddress.reverse(addr);
                long frameTypeAddr = getExtraInfo3 ? stackFrameAddr : allFrames.get(addr);
                int clsId = indexToAddress.reverse(frameTypeAddr);
                objectToClass.set(objId, clsId);
            }
        }

        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingRoots);

        indexToSize = new ObjectToSize(indexToAddress.size());

        // Java 1.4.2 has bootLoader as null and the address of the Java stack
        // frame at the lower memory address
        boolean scanUp = bootLoaderAddress == 0;

        boolean goodDTFJRoots = processDTFJRoots(pointerSize, scanUp, listener);

        // Used to keep track of what extra stuff DTFJ gives
        SetInt prevRoots = rootSet();
        SetInt newRoots;

        if (!goodDTFJRoots)
        {
            workCountSoFar = processConservativeRoots(pointerSize, fixedBootLoaderAddress, scanUp, workCountSoFar,
                            listener);
            missedRoots = addMissedRoots(missedRoots);
            newRoots = rootSet();
        }
        else if (verbose)
        {
            // Just for debugging. We are going to use DTFJ roots, but want to
            // see what conservative GC would give.
            HashMapIntObject<List<XGCRootInfo>> gcRoot2 = gcRoot;
            HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> threadRoots2 = threadRoots;
            File threadsFile = new File(pfx + "threads");
            File threadsFileSave = new File(pfx + ".save.threads");
            threadsFile.renameTo(threadsFileSave);
            workCountSoFar = processConservativeRoots(pointerSize, fixedBootLoaderAddress, scanUp, workCountSoFar,
                            listener);
            missedRoots = addMissedRoots(missedRoots);
            newRoots = rootSet();
            // Restore DTFJ Roots
            gcRoot = gcRoot2;
            threadRoots = threadRoots2;
            // Restore the threads file
            threadsFile.delete();
            threadsFileSave.renameTo(threadsFile);
        }
        else
        {
            newRoots = prevRoots;
        }

        // Debug - find the roots which DTFJ missed
        for (IteratorInt it = newRoots.iterator(); it.hasNext(); )
        {
            int i = it.next();
            if (!prevRoots.contains(i))
            {
                debugPrint("DTFJ Roots missed object id " + i + " " + format(indexToAddress.get(i)) + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + missedRoots.get(i));
            }
        }

        // Debug - find the roots which only DTFJ found
        for (IteratorInt it = prevRoots.iterator(); it.hasNext(); )
        {
            int i = it.next();
            if (!newRoots.contains(i))
            {
                debugPrint("DTFJ Roots has extra object id " + i + " " + format(indexToAddress.get(i)) + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                + missedRoots.get(i));
            }
        }

        // Mark everything - for debug
        if (false)
            for (int i = 0; i < indexToAddress.size(); ++i)
            {
                long addr = indexToAddress.get(i);
                addRoot(gcRoot, addr, addr, GCRootInfo.Type.UNKNOWN);
            }

        listener.worked(1);
        workCountSoFar += 1;
        listener.subTask(Messages.DTFJIndexBuilder_FindingOutboundReferencesForObjects);

        loaderClassCache = initLoaderClassesCache();

        int objProgress2 = 0;
        // Find all the objects
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getHeaps(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingHeaps, dtfjInfo.getJavaRuntime()))
                continue;
            JavaHeap jh = (JavaHeap) next;
            for (Iterator<?> j = jh.getObjects(); j.hasNext();)
            {
                Object next2 = j.next();
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingObjects, dtfjInfo.getJavaRuntime()))
                    continue;
                JavaObject jo = (JavaObject) next2;

                if (++objProgress2 % workObjectsStep == 0)
                {
                    // Progress monitoring
                    int workDone = workObjectsStep * (workCount - workCountSoFar)
                                    / (workObjectsStep + nobj - objProgress2);
                    debugPrint("workCount=" + workCountSoFar + "/" + workCount + " objects=" + objProgress2 + "/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                    + nobj + " " + workDone); //$NON-NLS-1$
                    listener.worked(workDone);
                    workCountSoFar += workDone;
                    if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
                }

                processHeapObject(jo, jo.getID().getAddress(), pointerSize, bootLoaderAddress, loaders, jlc, refd, listener);
            }
        }
        // Objects not on the heap
        for (Iterator<HashMapLongObject.Entry<JavaObject>> it = missingObjects.entries(); it.hasNext(); )
        {
            HashMapLongObject.Entry<JavaObject> entry = it.next();
            long objAddr = entry.getKey();
            JavaObject jo = entry.getValue();
            processHeapObject(jo, objAddr, pointerSize, bootLoaderAddress, loaders, jlc, refd, listener);
        }

        // Boot Class Loader
        if (bootLoaderObject == null)
        {
            // To accumulate the outbound refs
            ArrayLong aa = new ArrayLong();
            // Add a reference to the class
            aa.add(bootLoaderType.getObjectAddress());
            int objId = indexToAddress.reverse(fixedBootLoaderAddress);
            addLoaderClasses(objId, aa);
            try
            {
                checkRefs(bootLoaderObject, Messages.DTFJIndexBuilder_CheckRefsBootLoader, aa, jlc.getObjectAddress(),
                                bootLoaderAddress, listener);
            }
            catch (CorruptDataException e)
            {
                listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_ProblemCheckingBootLoaderReferences,
                                e);
            }
            addRefs(refd, objId, aa);
            outRefs.log(indexToAddress, objId, aa);
            // If there are no instances of ClassLoader then the size is
            // unknown, so set it to zero?
            if (bootLoaderType.getHeapSizePerInstance() == -1)
                bootLoaderType.setHeapSizePerInstance(0);
            bootLoaderType.addInstance(bootLoaderType.getHeapSizePerInstance());
            objectToClass.set(objId, bootLoaderType.getObjectId());
            if (debugInfo)
            {
                // For calculating purge sizes
                objectToSize2.set(objId, bootLoaderType.getHeapSizePerInstance());
            }
        }

        if (getExtraInfo)
        {
            // Generate outbound refs for Java Stack Frames from thread roots
            // Could be slow process
            int count = 0;
            for (long addr : allFrames.keySet())
            {
                // To accumulate the outbound refs
                ArrayLong aa = new ArrayLong();
                int objId = indexToAddress.reverse(addr);
                // Object to class mapping set earlier
                int clsId = objectToClass.get(objId);
                ClassImpl cls = idToClass.get(clsId);
                long frameTypeAddr = cls.getObjectAddress();

                // set instance size of each stack frame
                long size = indexToSize.getSize(objId);
                // if not in the variable sized objects table then use the fixed size
                if (size == 0)
                    size = cls.getHeapSizePerInstance();
                cls.addInstance(size);
                if (debugInfo)
                {
                    // For calculating purge sizes
                    objectToSize2.set(objId, size);
                }

                aa.add(frameTypeAddr);
                // Look at each threads root
                for (Iterator<HashMapIntObject<List<XGCRootInfo>>> it = threadRoots.values(); it.hasNext();)
                {
                    HashMapIntObject<List<XGCRootInfo>> hm = it.next();
                    // Look at each object marked by a thread
                    for (Iterator<List<XGCRootInfo>> i2 = hm.values(); i2.hasNext();)
                    {
                        // Look at the roots that mark that object
                        List<XGCRootInfo> l2 = i2.next();
                        for (Iterator<XGCRootInfo> i3 = l2.iterator(); i3.hasNext();)
                        {
                            XGCRootInfo xf = i3.next();
                            ++count;
                            // Does the root come from this frame?
                            if (xf.getContextAddress() == addr)
                            {
                                aa.add(xf.getObjectAddress());
                            }
                        }
                    }
                }
                addRefs(refd, objId, aa);
                outRefs.log(indexToAddress, objId, aa);
            }
        }

        arrayToSize = indexToSize.writeTo(Index.A2SIZE.getFile(pfx + "temp.")); //$NON-NLS-1$
        indexToSize = null;
        index.setArray2size(arrayToSize);

        out2b = outRefs.flush();
        // flush doesn't clear an internal array
        outRefs = null;
        index.setOutbound(out2b);

        // Missing finalizables from XML and GC roots
        // All objects with a finalize method which are not reached from
        // another object are guessed as being 'finalizable'.
        if (guessFinalizables && !(goodDTFJRoots && foundFinalizableGCRoots))
        {
            int finalizables = 0;
            listener.subTask(Messages.DTFJIndexBuilder_GeneratingExtraRootsFromFinalizables);
            for (int i = 0; i < indexToAddress.size(); ++i)
            {
                int clsId = objectToClass.get(i);
                long clsAddr = indexToAddress.get(clsId);
                if (finalizableClass.contains(clsAddr))
                {
                    long addr = indexToAddress.get(i);
                    if (!refd.get(i))
                    {
                        if (!gcRoot.containsKey(i))
                        {
                            ClassImpl classInfo = idToClass.get(clsId);
                            String clsInfo;
                            // If objectToClass has not yet been filled in for objects
                            // then this could be null
                            if (classInfo != null)
                            {
                                clsInfo = classInfo.getName();
                            }
                            else
                            {
                                clsInfo = format(clsAddr);
                            }
                            // Make a root as this object is not referred to nor
                            // a normal root, but has a finalize method
                            // This ensures that all finalizable objects are
                            // retained (except isolated cycles),
                            ++finalizables;
                            addRoot(gcRoot, addr, addr, GCRootInfo.Type.FINALIZABLE);
                            refd.set(i);
                            if (msgNguessFinalizable-- > 0)
                                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_ObjectIsFinalizable, clsInfo, format(addr)),
                                                null);
                            debugPrint("extra finalizable root " + i + " " + format(addr)); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                    else
                    {
                        /*
                         * The object is reachable another way, but we should indicate it needs to
                         * be finalized later.
                         * Unfinalized objects are just weakly held - strong references break paths to GC roots.
                         */
                        if (!skipWeakRoots)
                            addRoot(gcRoot, addr, addr, GCRootInfo.Type.UNFINALIZED);
                    }
                }
            }
            listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                            Messages.DTFJIndexBuilder_FinalizableObjectsMarkedAsRoots, finalizables), null);
        }

        // Remaining roots
        if (gcRoot.isEmpty() || threadRoots.isEmpty() || threadRootObjects() == 0 || presumeRoots)
        {
            listener.subTask(Messages.DTFJIndexBuilder_GeneratingExtraRootsMarkingAllUnreferenced);
            /*
             * Get the GarbageCleaner to mark anything unreachable as reachable
             * by adding pseudo-roots. This will also mark isolated cycles.
             */
            index.getSnapshotInfo().setProperty("keep_unreachable_objects", GCRootInfo.Type.UNKNOWN); //$NON-NLS-1$
            int extras = 0;
            for (int i = 0; i < indexToAddress.size(); ++i)
            {
                if (!refd.get(i))
                {
                    long addr = indexToAddress.get(i);
                    if (!gcRoot.containsKey(i))
                    {
                        // Make a root as this object is not referred to nor a
                        // normal root
                        // This ensures that all objects are retained (except
                        // isolated cycles),
                        // just in case the approximate roots miss something
                        // important
                        ++extras;
                        addRoot(gcRoot, addr, addr, GCRootInfo.Type.UNKNOWN);
                        refd.set(i);
                        debugPrint("extra root " + i + " " + format(addr)); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            }
            if (markAllLoaders)
            {
                for (JavaObject lo : loaders.keySet())
                {
                    long addr = lo.getID().getAddress();
                    int i = indexToAddress.reverse(addr);
                    if (i >= 0)
                    {
                        if (!gcRoot.containsKey(i))
                        {
                            // Make a root as this class loader might not be
                            // marked.
                            // The loader will be in a cycle with its classes.
                            ++extras;
                            addRoot(gcRoot, addr, addr, GCRootInfo.Type.UNKNOWN);
                            refd.set(i);
                            debugPrint("extra root " + i + " " + format(addr)); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                }
            }
            // If there are millions of roots then the lists can take a lot of room, so trim them.
            for (Iterator<HashMapIntObject.Entry<List<XGCRootInfo>>> it = gcRoot.entries(); it.hasNext(); )
            {
                HashMapIntObject.Entry<List<XGCRootInfo>> e = it.next();
                List<XGCRootInfo> l = e.getValue();
                switch (l.size())
                {
                    // The list should always have something in it
                    case 1:
                        l = Collections.<XGCRootInfo>singletonList(l.get(0));
                        break;
                    default:
                        if (l instanceof ArrayList)
                        {
                            ((ArrayList<XGCRootInfo>)l).trimToSize();
                        }
                        else
                        {
                            l = new ArrayList<XGCRootInfo>(l);
                        }
                        break;
                }
                gcRoot.put(e.getKey(), l);
            }
            listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                            Messages.DTFJIndexBuilder_UnreferenceObjectsMarkedAsRoots, extras), null);
        }

        index.setGcRoots(gcRoot);
        index.setThread2objects2roots(threadRoots);
        if (goodDTFJRoots)
        {
            String msg = MessageFormat.format(Messages.DTFJIndexBuilder_UsingDTFJRoots, gcRoot.size());
            listener.sendUserMessage(Severity.INFO, msg, null);
            debugPrint(msg);
        }
        else
        {
            String msg = MessageFormat.format(Messages.DTFJIndexBuilder_UsingConservativeGarbageCollectionRoots, gcRoot
                            .size());
            listener.sendUserMessage(Severity.WARNING, msg, null);
            debugPrint(msg);
        }

        if (objectToClass.size() >= indexCountForTempFile)
        {
            objectToClass1 = objectToClass.writeTo(Index.O2CLASS.getFile(pfx + "temp."));
        }
        else
        {
            objectToClass1 = objectToClass;
        }
        objectToClass = null;
        index.setObject2classId(objectToClass1);

        if (verbose)
        {
            // For identifying purged objects
            idToClass2 = copy(idToClass);
            objectToClass2 = copy(objectToClass1, IndexWriter.mostSignificantBit(maxClsId));
        }

        // If a message count goes below zero then a message has not been
        // printed
        int skippedMessages = 0;
        skippedMessages += Math.max(0, 0);
        skippedMessages += Math.max(0, -msgNgetRefsMissing);
        skippedMessages += Math.max(0, -msgNgetRefsExtra);
        skippedMessages += Math.max(0, -msgNarrayRefsNPE);
        skippedMessages += Math.max(0, -msgNgetRefsUnavailable);
        skippedMessages += Math.max(0, -msgNgetRefsCorrupt);
        skippedMessages += Math.max(0, -msgNbigSegs);
        skippedMessages += Math.max(0, -msgNinvalidArray);
        skippedMessages += Math.max(0, -msgNinvalidObj);
        skippedMessages += Math.max(0, -msgNbrokenEquals);
        skippedMessages += Math.max(0, -msgNbrokenInterfaceSuper);
        skippedMessages += Math.max(0, -msgNmissingLoaderMsg);
        skippedMessages += Math.max(0, -msgNcorruptCount);
        skippedMessages += Math.max(0, -msgNrootsWarning);
        skippedMessages += Math.max(0, -msgNguessFinalizable);
        skippedMessages += Math.max(0, -msgNgetRefsAllMissing);
        skippedMessages += Math.max(0, -msgNgetSuperclass);
        skippedMessages += Math.max(0, -msgNnullThreadObject);
        skippedMessages += Math.max(0, -msgNbadThreadInfo);
        skippedMessages += Math.max(0, -msgNunexpectedModifiers);
        skippedMessages += Math.max(0, -msgNcorruptSection);
        skippedMessages += Math.max(0, -msgNclassForObject);
        skippedMessages += Math.max(0, -msgNcomponentClass);
        skippedMessages += Math.max(0, -msgNtypeForClassObject);
        skippedMessages += Math.max(0, -msgNobjectSize);
        skippedMessages += Math.max(0, -msgNoutboundReferences);
        skippedMessages += Math.max(0, -msgNnoSuperClassForArray);
        skippedMessages += Math.max(0, -msgNproblemReadingJavaStackFrame);
        if (skippedMessages > 0)
        {
            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_RepeatedMessagesSuppressed, skippedMessages), null);
        }

        long now2 = System.currentTimeMillis();
        listener.sendUserMessage(Severity.INFO, MessageFormat.format(Messages.DTFJIndexBuilder_TookmsToParseFile,
                        (now2 - now1), dump), null);
        // Free some memory
        gcRoot = null;
        threadRoots = null;
        // leave the DTFJ image around as we need to reopen the image later
        dummyClassAddress = null;
        dummyMethodAddress = null;
        dummyMethodAddress2 = null;
        methodAddresses = null;
        loaderClassCache = null;
        listener.done();
    }

    /**
     * Add outbound references for method types and pseudo types
     * @param methodTypeAddr
     * @param refd record of inbound refs
     * @throws IOException
     */
    private void addDummyTypeRefs(long methodTypeAddr, BitField refd) throws IOException
    {
        if (methodTypeAddr != 0)
        {
            // method pseudo-type
            int objId = indexToAddress.reverse(methodTypeAddr);
            ClassImpl ci = idToClass.get(objId);
            if (ci != null)
            {
                ArrayLong ref = ci.getReferences();
                addRefs(refd, objId, ref);
                outRefs.log(indexToAddress, objId, ref);
            }
        }
    }

    /**
     * Get the address of a stack frame
     * If the frame address isn't found then use the previous frame address + pointer size rounded up to 4 bytes
     * @param jf
     * @param prevFrameAddress
     * @param pointerSize in bits
     * @return
     */
    static long getFrameAddress(JavaStackFrame jf, long prevFrameAddress, int pointerSize)
    {
        long frameAddress;
        try
        {
            frameAddress = getAlignedAddress(jf.getBasePointer(), pointerSize).getAddress();
        }
        catch (CorruptDataException e)
        {
            frameAddress = 0;
        }
        if (frameAddress == 0 && prevFrameAddress != 0)
        {  
            frameAddress = prevFrameAddress + (pointerSize + 31) / 32 * 4;
        }
        return frameAddress;
    }

    /**
     * Create a file to store all of the thread stack information.
     * Close the PrintWriter when done.
     * @return a PrintWriter used to store information in the file
     */
    private PrintWriter createThreadInfoFile()
    {
        PrintWriter pw = null;
        try
        {
            // Used to store thread stack information
            pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(pfx + "threads"), "UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (IOException e)
        {}
        return pw;
    }

    /**
     * Write out information about this thread and its thread stack so that MAT
     * can retrieve the information later
     * @param out where to write out the data
     * @param th the thread in question
     */
    private void printThreadStack(PrintWriter out, JavaThread th)
    {
        out.print("Thread "); //$NON-NLS-1$
        long threadAddress = getThreadAddress(th, null);
        out.println(threadAddress != 0  ? "0x"+Long.toHexString(threadAddress) : "<unknown>"); //$NON-NLS-1$ //$NON-NLS-2$
        for (Iterator<?> it = th.getStackFrames(); it.hasNext(); out.println())
        {
            Object next = it.next();
            out.print(" at "); //$NON-NLS-1$
            if (next instanceof CorruptData)
            {
                continue;
            }
            JavaStackFrame jsf = (JavaStackFrame) next;
            try
            {
                JavaLocation jl = jsf.getLocation();
                try
                {
                    JavaMethod jm = jl.getMethod();
                    try
                    {
                        JavaClass jc = jm.getDeclaringClass();
                        out.print(jc.getName().replace("/", ".")); //$NON-NLS-1$ //$NON-NLS-2$
                        out.print("."); //$NON-NLS-1$
                    }
                    catch (CorruptDataException e)
                    {}
                    catch (DataUnavailable e)
                    {}
                    try
                    {
                        out.print(jm.getName());
                    }
                    catch (CorruptDataException e)
                    {}
                    try
                    {
                        out.print(jm.getSignature());
                    }
                    catch (CorruptDataException e)
                    {}
                    out.print(" "); //$NON-NLS-1$
                    try
                    {
                        if (Modifier.isNative(jm.getModifiers()))
                        {
                            out.print("(Native Method)"); //$NON-NLS-1$
                            continue;
                        }
                    }
                    catch (CorruptDataException e)
                    {}
                }
                catch (CorruptDataException e)
                {}
                try
                {
                    out.print("("); //$NON-NLS-1$
                    out.print(jl.getFilename());
                    try
                    {
                        out.print(":" + jl.getLineNumber()); //$NON-NLS-1$
                    }
                    catch (CorruptDataException e)
                    {}
                    catch (DataUnavailable e)
                    {}
                    int cl = 0;
                    try
                    {
                        cl = jl.getCompilationLevel();
                    }
                    catch (CorruptDataException e2)
                    {}
                    if (cl > 0)
                        out.print("(Compiled Code)"); //$NON-NLS-1$
                }
                catch (DataUnavailable e)
                {
                    int cl = 0;
                    try
                    {
                        cl = jl.getCompilationLevel();
                    }
                    catch (CorruptDataException e2)
                    {}
                    if (cl > 0)
                        out.print("Compiled Code"); //$NON-NLS-1$
                    else
                        out.print("Unknown Source"); //$NON-NLS-1$
                }
                catch (CorruptDataException e)
                {
                    int cl = 0;
                    try
                    {
                        cl = jl.getCompilationLevel();
                    }
                    catch (CorruptDataException e2)
                    {}
                    if (cl > 0)
                        out.print("Compiled Code"); //$NON-NLS-1$
                    else
                        out.print("Unknown Source"); //$NON-NLS-1$
                }
                finally
                {
                    out.print(")"); //$NON-NLS-1$
                }
            }
            catch (CorruptDataException e)
            {}
        }
        out.println();
        out.println(" locals:"); //$NON-NLS-1$
    }
    
    /**
     * Print out a single local variable for thread stack data
     * @param pw where to store the information
     * @param target the address of the object
     * @param frameNum the Java stack frame, starting from 0 at top of stack
     */
    private void printLocal(PrintWriter pw, long target, int frameNum)
    {
        if (indexToAddress.reverse(target) >= 0)
            pw.println("  objectId=0x" + Long.toHexString(target) + ", line=" //$NON-NLS-1$ //$NON-NLS-2$
                            + frameNum);
    }
    
    private int processConservativeRoots(int pointerSize, long fixedBootLoaderAddress, boolean scanUp,
                    int workCountSoFar, IProgressListener listener)
    {
        gcRoot = new HashMapIntObject<List<XGCRootInfo>>();

        listener.subTask(Messages.DTFJIndexBuilder_GeneratingSystemRoots);

        /*
         * There isn't actually a need to mark the system classes as a thread
         * marks java/lang/Thread, which marks the boot loader, which marks all
         * the classes. Other parsers do mark them, so this simulates that
         * behaviour.
         */
        if (useSystemClassRoots)
        {
            // Mark the boot class loader itself - is this required?
            addRoot(gcRoot, fixedBootLoaderAddress, fixedBootLoaderAddress, GCRootInfo.Type.SYSTEM_CLASS);
            for (Iterator<ClassImpl> i = idToClass.values(); i.hasNext();)
            {
                ClassImpl ci = i.next();

                // Should we mark all system classes?
                if (ci.getClassLoaderAddress() == fixedBootLoaderAddress)
                {
                    addRoot(gcRoot, ci.getObjectAddress(), ci.getClassLoaderAddress(), GCRootInfo.Type.SYSTEM_CLASS);
                }
            }
        }

        listener.subTask(Messages.DTFJIndexBuilder_GeneratingThreadRoots);

        PrintWriter pw = createThreadInfoFile();
        
        threadRoots = new HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>>();
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getThreads(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingThreads, dtfjInfo.getJavaRuntime()))
                continue;
            JavaThread th = (JavaThread) next;
            listener.worked(1);
            workCountSoFar += 1;
            if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
            try
            {
                long threadAddress = getThreadAddress(th, null);
                // Thread object could be null if the thread is being attached
                if (threadAddress != 0)
                {
                    // CorruptDataException from
                    // deadlock/xa64/j9/core.20071025.dmp.zip
                    try
                    {
                        int threadState = th.getState();
                        // debugPrint("Considering thread
                        // "+format(threadAddress)+"
                        // "+Integer.toBinaryString(threadState)+"
                        // "+th.getName());
                        if ((threadState & JavaThread.STATE_ALIVE) == 0)
                        {
                            // Ignore threads which are not alive
                            continue;
                        }
                    }
                    catch (CorruptDataException e)
                    {
                        listener.sendUserMessage(Severity_INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ThreadStateNotFound, format(threadAddress)), e);
                        // debugPrint("Considering thread
                        // "+format(threadAddress)+" as state unavailable");
                    }
                    catch (IllegalArgumentException e)
                    {
                        // IllegalArgumentException from
                        // Thread.getName()
                        listener.sendUserMessage(Severity_INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ThreadNameNotFound, format(threadAddress)), e);
                        // debugPrint("Considering thread
                        // "+format(threadAddress)+" as state unavailable");
                    }

                    // Make the thread a proper GC Root
                    addRoot(gcRoot, threadAddress, threadAddress, GCRootInfo.Type.THREAD_OBJ);

                    int threadID = indexToAddress.reverse(threadAddress);
                    HashMapIntObject<List<XGCRootInfo>> thr = new HashMapIntObject<List<XGCRootInfo>>();
                    threadRoots.put(threadID, thr);
                }
                else
                {
                    // Null thread object
                    Exception e1;
                    long jniEnvAddress;
                    String name = ""; //$NON-NLS-1$
                    try
                    {
                        name = th.getName();
                        jniEnvAddress = th.getJNIEnv().getAddress();
                        e1 = null;
                    }
                    catch (CorruptDataException e)
                    {
                        jniEnvAddress = 0;
                        e1 = e;
                    }
                    if (msgNnullThreadObject-- > 0)
                        listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ThreadObjectNotFoundSoIgnoring, name,
                                        format(jniEnvAddress)), e1);
                }

                scanJavaThread(th, threadAddress, pointerSize, threadRoots, listener, scanUp, pw);
                try
                {
                    ImageThread it = th.getImageThread();
                    scanImageThread(th, it, threadAddress, pointerSize, threadRoots, listener);
                }
                catch (DataUnavailable e)
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_NativeThreadNotFound, format(threadAddress)), e);
                }
            }
            catch (CorruptDataException e)
            {
                if (msgNbadThreadInfo-- > 0)
                    listener.sendUserMessage(Severity.WARNING,
                                    Messages.DTFJIndexBuilder_ProblemReadingThreadInformation, e);
            }
        }
        if (pw != null)
        {
            pw.close();
        }

        // Monitor GC roots
        listener.subTask(Messages.DTFJIndexBuilder_GeneratingMonitorRoots);

        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getMonitors(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingMonitors, dtfjInfo.getJavaRuntime()))
                continue;
            JavaMonitor jm = (JavaMonitor) next;
            JavaObject obj = jm.getObject();
            if (obj != null)
            {
                // Make the monitored object a root
                try
                {
                    JavaThread jt = jm.getOwner();
                    if (jt != null)
                    {
                        // Unowned monitors do not keep objects alive
                        addRootForThread(obj, jt, listener);
                    }
                }
                catch (CorruptDataException e)
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_UnableToFindThreadOwningMonitor, format(jm.getID()
                                                    .getAddress()), format(obj.getID().getAddress())), e);
                    // Play safe and add as a global root
                    addRootForThread(obj, null, listener);
                }
                // Is there any need to mark enter waiters or notify waiters
                // Surely the object is also a local variable, but perhaps local
                // variable information is incorrect.
                addRootForThreads(obj, jm.getEnterWaiters(), listener);
                addRootForThreads(obj, jm.getNotifyWaiters(), listener);
            }
        }
        return workCountSoFar;
    }

    private boolean processDTFJRoots(int pointerSize, boolean scanUp, IProgressListener listener)
    {
        boolean goodDTFJRoots = false;
        if (haveDTFJRoots)
        {
            listener.subTask(Messages.DTFJIndexBuilder_FindingRootsFromDTFJ);
            debugPrint("DTFJ roots"); //$NON-NLS-1$

            HashMapIntObject<List<XGCRootInfo>> gcRoot2;
            gcRoot2 = new HashMapIntObject<List<XGCRootInfo>>();
            HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> threadRoots2;
            threadRoots2 = new HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>>();

            goodDTFJRoots = true;

            // For debug
            missedRoots = new HashMapIntObject<String>();

            // See if the heap roots support is even in DTFJ
            Iterator<?> it;
            try
            {
                it = dtfjInfo.getJavaRuntime().getHeapRoots();
                // Javacore reader returns null
                if (it == null)
                {
                    it = Collections.EMPTY_LIST.iterator();
                    listener.sendUserMessage(Severity_WARNING, Messages.DTFJIndexBuilder_DTFJgetHeapRootsReturnsNull,
                                    null);
                }
            }
            catch (LinkageError e)
            {
                goodDTFJRoots = false;
                it = Collections.EMPTY_LIST.iterator();
                listener.sendUserMessage(Severity_WARNING, Messages.DTFJIndexBuilder_DTFJDoesNotSupportHeapRoots, e);
            }

            // True heap roots using DTFJ
            if (goodDTFJRoots)
            {
                listener.subTask(Messages.DTFJIndexBuilder_GeneratingGlobalRoots);
                debugPrint("Processing global roots"); //$NON-NLS-1$
                for (; it.hasNext();)
                {
                    Object next = it.next();
                    if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingRoots, dtfjInfo.getJavaRuntime()))
                        continue;
                    JavaReference r = (JavaReference) next;
                    processRoot(r, null, gcRoot2, threadRoots2, pointerSize, listener);
                }
                listener.subTask(Messages.DTFJIndexBuilder_GeneratingThreadRoots);
                debugPrint("Processing thread roots"); //$NON-NLS-1$
                PrintWriter pw = null;
                if (gcRoot2.size() > 0)
                {
                	pw = createThreadInfoFile();
                }
                for (Iterator<?> thit = dtfjInfo.getJavaRuntime().getThreads(); thit.hasNext();)
                {
                    Object next = thit.next();
                    if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingThreads, dtfjInfo.getJavaRuntime()))
                        continue;
                    JavaThread th = (JavaThread) next;

                    if (pw != null)
                    {
                        // For thread stack information
                        printThreadStack(pw, th);
                    }
                    // The base pointer appears to be the last address of the frame, not the
                    // first
                    long prevAddr = 0;
                    long prevFrameAddress = 0;
                    int frameNum = 0;
                    // We need to look ahead to get the frame size
                    Object nextFrame = null;
                    for (Iterator<?> ii = th.getStackFrames(); nextFrame != null || ii.hasNext(); ++frameNum)
                    {
                        // Use the lookahead frame if available
                        Object next2;
                        if (nextFrame != null)
                        {
                            next2 = nextFrame;
                            nextFrame = null;
                        }
                        else
                        {
                            next2 = ii.next();
                        }
                        if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingJavaStackFrames,
                                        th))
                            continue;
                        JavaStackFrame jf = (JavaStackFrame) next2;
                        // - getHeapRoots returns null
                        if (jf.getHeapRoots() == null)
                        {
                            if (msgNrootsWarning-- > 0)
                                listener.sendUserMessage(Severity_WARNING,
                                                Messages.DTFJIndexBuilder_DTFJgetHeapRootsFromStackFrameReturnsNull,
                                                null);
                            continue;
                        }
                        for (Iterator<?> i3 = jf.getHeapRoots(); i3.hasNext();)
                        {
                            Object next3 = i3.next();
                            if (isCorruptData(next3, listener, Messages.DTFJIndexBuilder_CorruptDataReadingRoots, dtfjInfo.getJavaRuntime()))
                                continue;
                            JavaReference r = (JavaReference) next3;
                            processRoot(r, th, gcRoot2, threadRoots2, pointerSize, listener);
                            if (pw != null)
                            {
                                // Details of the locals
                                try
                                {
                                    Object o = r.getTarget();
                                    if (o instanceof JavaObject)
                                    {
                                        JavaObject jo = (JavaObject) o;
                                        long target = jo.getID().getAddress();
                                        printLocal(pw, target, frameNum);
                                    }
                                    else if (o instanceof JavaClass)
                                    {
                                        JavaClass jc = (JavaClass) o;
                                        long target = getClassAddress(jc, listener);
                                        printLocal(pw, target, frameNum);
                                    }
                                }
                                catch (CorruptDataException e)
                                {}
                                catch (DataUnavailable e)
                                {}
                            }
                        }
                        if (getExtraInfo)
                        {
                            long frameAddress = getFrameAddress(jf, prevFrameAddress, pointerSize);
                            long searchSize = JAVA_STACK_FRAME_SIZE;
                            if (scanUp)
                            {
                                // Check the next frame to limit the current frame size
                                if (ii.hasNext())
                                {
                                    nextFrame = ii.next();
                                    if (!isCorruptData(nextFrame, listener,
                                                    Messages.DTFJIndexBuilder_CorruptDataReadingJavaStackFrames, th))
                                    {
                                        JavaStackFrame jf2 = (JavaStackFrame) nextFrame;
                                        try
                                        {
                                            ImagePointer ip2 = getAlignedAddress(jf2.getBasePointer(), pointerSize);
                                            long address2 = ip2.getAddress();
                                            long s2 = address2 - frameAddress;
                                            if (s2 > 0 && s2 < searchSize)
                                            {
                                                searchSize = s2;
                                            }
                                        }
                                        catch (CorruptDataException e)
                                        {
                                            // Ignore for the moment - we'll find it again
                                            // next time.
                                        }
                                    }
                                }
                            }
                            else
                            {
                                // Check the previous frame to limit the current frame size
                                if (prevAddr == 0)
                                {
                                    prevAddr = getJavaStackBase(th, frameAddress);
                                }
                                long s2 = frameAddress - prevAddr;
                                prevAddr = frameAddress;
                                if (s2 > 0 && s2 < searchSize)
                                {
                                    searchSize = s2;
                                }
                                // Go backwards from ip so that we search the known good
                                // addresses first
                                searchSize = -searchSize;
                            }
                            prevFrameAddress = frameAddress;
                            int frameId = indexToAddress.reverse(frameAddress);
                            if (frameAddress != 0 && frameId >= 0)
                            {
                                // Set the frame size
                                long size = Math.abs(searchSize);
                                setFrameSize(frameId, size);
                                // Mark the frame
                                // Thread object could be null if the thread
                                // is being attached
                                long threadAddress = getThreadAddress(th, null);
                                if (threadAddress != 0)
                                {
                                    int thrId = indexToAddress.reverse(threadAddress);

                                    // Add it to the thread roots
                                    HashMapIntObject<List<XGCRootInfo>> thr = threadRoots2.get(thrId);
                                    if (thr == null)
                                    {
                                        // Build new list for the thread
                                        thr = new HashMapIntObject<List<XGCRootInfo>>();
                                        threadRoots2.put(thrId, thr);
                                    }
                                    addRoot(thr, frameAddress, threadAddress, GCRootInfo.Type.JAVA_STACK_FRAME);
                                    // Add it to the global GC roots
                                    if (!useThreadRefsNotRoots)
                                        addRoot(gcRoot2, frameAddress, threadAddress, GCRootInfo.Type.JAVA_STACK_FRAME);
                                }
                                else
                                {
                                    // No thread information so make a
                                    // global root
                                    addRoot(gcRoot2, frameAddress, frameAddress, GCRootInfo.Type.JAVA_STACK_FRAME);
                                }
                            }
                        }
                    }
                    if (pw != null)
                        pw.println();
                }
                if (pw != null)
                {
                    pw.close();
                }

                // We need some roots, so disable DTFJ roots if none are found
                if (gcRoot2.isEmpty())
                {
                    goodDTFJRoots = false;
                    listener.sendUserMessage(Severity.WARNING, Messages.DTFJIndexBuilder_NoDTFJRootsFound, null);
                }

                if (!useDTFJRoots)
                {
                    goodDTFJRoots = false;
                    listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_DTFJRootsDisabled, null);
                }

                // The refd array is not affected by the GCroots

                // Still assign the gc roots even if the goodDTFJRoots is false
                // in case we want to see how good they were.
                gcRoot = gcRoot2;
                threadRoots = threadRoots2;
            }
        }
        return goodDTFJRoots;
    }

    /**
     * Set the size of the stack frame type.
     * We can only have one size as the frame type instance size, 
     * so choose the first sensible size.
     * We store other sizes using the indexToSize index. 
     * @param frameId
     * @param size in bytes
     */
    private long setFrameSize(int frameId, long size)
    {
        // If we are just interest in frames for local variables
        // then having sizes on the stack, not heap, confuses the overall picture
        if (getExtraInfo3)
            return size;
        int clsId = objectToClass.get(frameId);
        ClassImpl cls = idToClass.get(clsId);
        if (cls != null && cls.getName().contains(METHOD_NAME_SIG))
        {
            long prevSize = cls.getHeapSizePerInstance();

            if (prevSize <= 0 || prevSize == JAVA_STACK_FRAME_SIZE)
            {
                // The previous size wasn't valid, so set it now
                cls.setHeapSizePerInstance(size);
            }
            else if (size == JAVA_STACK_FRAME_SIZE)
            {
                // The current size isn't sensible, so use the previous
                size = prevSize;
            }
            else
            {
                // The size isn't the same as the previous one, so remember this one
                if (size != prevSize)
                    indexToSize.set(frameId, size);
            }
        }
        return size;
    }

    private HashMapIntObject<String> addMissedRoots(HashMapIntObject<String> roots)
    {
        // Create information about roots we are not using
        if (roots == null)
            roots = new HashMapIntObject<String>();
        for (IteratorInt ii = gcRoot.keys(); ii.hasNext();)
        {
            int i = ii.next();
            List<XGCRootInfo> lr = gcRoot.get(i);
            String info = XGCRootInfo.getTypeSetAsString(lr.toArray(new XGCRootInfo[lr.size()]));
            String prev = roots.get(i);
            roots.put(i, prev != null ? prev + "," + info : info); //$NON-NLS-1$
        }
        for (int key : threadRoots.getAllKeys())
        {
            int oldRoots1[] = threadRoots.get(key).getAllKeys();
            for (int i : oldRoots1)
            {
                List<XGCRootInfo> lr = threadRoots.get(key).get(i);
                String info = XGCRootInfo.getTypeSetAsString(lr.toArray(new XGCRootInfo[lr.size()]));
                String prev = roots.get(i);
                roots.put(i, prev != null ? prev + "," + info : info); //$NON-NLS-1$
            }
        }
        return roots;
    }

    private SetInt rootSet()
    {
        SetInt prevRoots = new SetInt();

        if (gcRoot != null)
        {
            int oldRoots[] = gcRoot.getAllKeys();
            for (int i : oldRoots)
            {
                prevRoots.add(i);
            }
            for (int key : threadRoots.getAllKeys())
            {
                oldRoots = threadRoots.get(key).getAllKeys();
                for (int i : oldRoots)
                {
                    prevRoots.add(i);
                }
            }
        }
        return prevRoots;
    }

    /**
     * Count the number of real objects which are thread roots from the stack.
     * If this is zero then we probably are missing GC roots.
     * @return Number of objects (not classes) marked as roots by threads.
     */
    private int threadRootObjects()
    {
        int objRoots = 0;
        // Look at each threads root
        for (Iterator<HashMapIntObject<List<XGCRootInfo>>> it = threadRoots.values(); it.hasNext();)
        {
            HashMapIntObject<List<XGCRootInfo>> hm = it.next();
            // Look at each object marked by a thread
            for (IteratorInt i2 = hm.keys(); i2.hasNext();)
            {
                int objId = i2.next();
                // If it is not a class then possibly count it
                if (!idToClass.containsKey(objId))
                {
                    for (Iterator<XGCRootInfo> i3 = hm.get(objId).iterator(); i3.hasNext(); )
                    {                        
                        int type = i3.next().getType();
                        // If it is a true local from a stack frame
                        if (type == GCRootInfo.Type.JAVA_LOCAL 
                            || type == GCRootInfo.Type.NATIVE_STACK
                            || type == GCRootInfo.Type.NATIVE_LOCAL)
                        {
                           ++objRoots;
                           break;
                        }
                    }
                }
            }
        }
        return objRoots;
    }

    /**
     * Record the object address in the list of identifiers and the associated
     * classes in the class list
     * 
     * @param jo
     * @param objAddress
     * @param allClasses
     * @param listener
     */
    private void rememberObject(JavaObject jo, long objAddress, Set<JavaClass> allClasses, IProgressListener listener)
    {

        // Always add object; the type can be guessed later and the object might
        // be something important like a thread or class loader
        indexToAddress0.add(objAddress);
        // debugPrint("adding object at "+format(objAddress));

        try
        {
            JavaClass cls = jo.getJavaClass();
            rememberClass(cls, allClasses, listener);
        }
        catch (CorruptDataException e)
        {
            if (msgNclassForObject-- > 0) listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ProblemFindingClassesForObject, format(objAddress)), e);
        }
    }

    /**
     * Record the class and all its superclasses
     * @param cls
     * @param allClasses
     * @param listener
     */
    private void rememberClass(JavaClass cls, Set<JavaClass> allClasses, IProgressListener listener)
    {
        while (allClasses.add(cls))
        {
            if (debugInfo)
                debugPrint("Adding extra class " + getClassName(cls, listener)); //$NON-NLS-1$
            try
            {
                // Check if component classes are not in class loader list.
                // Some array classes are this way and the primitive types.
                while (cls.isArray())
                {
                    cls = cls.getComponentType();
                    if (allClasses.add(cls))
                    {
                        if (debugInfo)
                            debugPrint("Adding extra array component class " + getClassName(cls, listener)); //$NON-NLS-1$
                    }
                    else
                    {
                        // We have already added this type
                        break;
                    }
                }

            }
            catch (CorruptDataException e)
            {
                if (msgNcomponentClass-- > 0)
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemFindingComponentClass,
                                    format(getClassAddress(cls, listener))), e);
            }
        }
    }

    /**
     * Deal with a Java object. Build outbound references Set. Build class of
     * object Map. Build array size.
     * 
     * @param jo
     * @param objAddr object address (in case there is no JavaObject)
     * @param pointerSize
     * @param bootLoaderAddress
     * @param loaders
     * @param jlc
     * @param refd
     * @param listener
     * @throws IOException
     */
    private void processHeapObject(JavaObject jo, long objAddr, int pointerSize,
                    long bootLoaderAddress, HashMap<JavaObject, JavaClassLoader> loaders, ClassImpl jlc,
                    BitField refd, IProgressListener listener)
                    throws IOException
    {
        objAddr = fixBootLoaderAddress(bootLoaderAddress, objAddr);
        int objId = indexToAddress.reverse(objAddr);

        if (objId < 0)
        {
            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(Messages.DTFJIndexBuilder_SkippingObject,
                            format(objAddr)), null);
            return;
        }

        if (idToClass.get(objId) != null)
        {
            // Class objects are dealt with elsewhere
            // debugPrint("Skipping class "+idToClass.get(objId).getName());
            return;
        }

        int clsId = -1;
        JavaClass type = null;
        long clsAddr = 0;
        try
        {
            if (jo != null)
            {
                type = jo.getJavaClass();
                clsAddr = getClassAddress(type, listener);

                clsId = indexToAddress.reverse(clsAddr);
            }

            if (clsId >= 0)
            {
                if (verbose)
                    debugPrint("found object " + objId + " " + getClassName(type, listener) + " at " + format(objAddr) + " clsId " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                    + clsId);
            }
            else
            {
                if (type != null)
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemGettingClassIDType, format(objAddr), getClassName(type, listener),
                                    format(clsAddr)), null);
                }
                else
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemGettingClassID, format(objAddr)), null);
                }
            }
        }
        catch (CorruptDataException e)
        {
            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ProblemGettingClassID, format(objAddr)), e);
        }

        if (clsId < 0)
        {
            // Make sure the object has a class e.g. java.lang.Object, even if
            // it is wrong!
            clsId = jlc.getSuperClassId();
            clsAddr = jlc.getSuperClassAddress();
            if (clsAddr == 0)
            {
                // Even more corrupt - no Object!
                ClassImpl cls = findClassFromName("java.lang.Object", listener); //$NON-NLS-1$
                if (cls == null)
                {
                    cls = jlc;
                }
                clsId = cls.getObjectId();
                clsAddr = cls.getObjectAddress();
            }
            if (loaders.get(jo) != null)
            {
                // It is a loader, so try to make it the type of a class loader
                ClassImpl cls = findJavaLangClassloader(listener);
                if (cls != null)
                {
                    {
                        clsId = cls.getObjectId();
                        clsAddr = cls.getObjectAddress();
                    }
                }
            }
            // Leave type as null so we skip processing object fields/array
            // elements
        }

        // debugPrint("set objectID "+objId+" at address "+format(objAddr)+"
        // classID "+clsId+" "+format(clsAddr));
        objectToClass.set(objId, clsId);

        // Add object count/size to the class
        ClassImpl cls = idToClass.get(clsId);
        try
        {
            if (cls != null)
            {
                if (cls == jlc)
                {
                    debugPrint("Found class as object at " + format(objAddr)); //$NON-NLS-1$
                }
                long size;
                if (jo != null)
                {
                    size = getObjectSize(jo, pointerSize);
                }
                else
                {
                    // Use the existing size as no size is available
                    size = cls.getHeapSizePerInstance();
                    if (size < 0) size = 0;
                }
                cls.addInstance(size);
                if (cls.isArrayType())
                {
                    int arrayLen = jo.getArraySize();
                    // Bytes, not elements
                    indexToSize.set(objId, size);
                    if (debugInfo)
                    {
                        // For calculating purge sizes
                        objectToSize2.set(objId, size);
                    }
                    // debugPrint("array size "+size+" arrayLen "+arrayLen);
                    int headerPointer = getPointerBytes(pointerSize);
                    if (headerPointer == 8)
                    {
                        long bigSize = calculateArraySize(cls, arrayLen, headerPointer);
                        if (bigSize > size)
                        {
                            // Probably compressed pointers where on a 64-bit
                            // system references are stored as a 32-bit
                            // quantity.
                            if (false) debugPrint("Array size with "+headerPointer+" pointers calculated "+bigSize+" actual "+size+" arrayLen "+arrayLen); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                            headerPointer = 4;
                            bigSize = calculateArraySize(cls, arrayLen, headerPointer);
                            if (false) debugPrint("Array size with "+headerPointer+" pointers calculated "+bigSize+" actual "+size+" arrayLen "+arrayLen); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        }
                    }
                    cls.setHeapSizePerInstance(headerPointer);
                }
                else
                {
                    // Allow for objects of the same type with different sizes
                    long oldSize = cls.getHeapSizePerInstance();
                    if (oldSize < 0)
                    {
                        // First time, so set the size
                        cls.setHeapSizePerInstance(size);
                        // Check what we stored
                        oldSize = cls.getHeapSizePerInstance();
                    }
                    if (oldSize != size)
                    {
                        // Different size to before, so use the array size table
                        indexToSize.set(objId, size);
                    }

                    if (debugInfo)
                    {
                        // For calculating purge sizes
                        objectToSize2.set(objId, size);
                    }
                }
            }
            else
            {
                listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemGettingObjectClass, format(objAddr)), null);
            }
        }
        catch (CorruptDataException e)
        {
            if (msgNobjectSize-- > 0) listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ProblemGettingObjectSize, format(objAddr)), e);
            // Try to cope with bad sizes - at least register an instance of
            // this class
            cls.addInstance(0);
            if (cls.getHeapSizePerInstance() == -1)
                cls.setHeapSizePerInstance(0);
        }

        // To accumulate the outbound refs
        ArrayLong aa = new ArrayLong();

        // Add a reference to the class
        aa.add(clsAddr);

        // Is the object a class loader?
        if (loaders.containsKey(jo))
        {
            addLoaderClasses(objId, aa);
        }

        if (type != null)
        {
            try
            {
                // Array size
                if (jo.isArray())
                {
                    // get the size
                    int arrayLen = jo.getArraySize();
                    exploreArray(indexToAddress, bootLoaderAddress, idToClass, jo, type, aa, arrayLen, listener);
                }
                else
                {
                    exploreObject(indexToAddress, bootLoaderAddress, idToClass, jo, type, aa, false, listener);
                }
            }
            catch (CorruptDataException e)
            {
                if (msgNoutboundReferences-- > 0) listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemGettingOutboundReferences, format(objAddr)), e);
            }
        }
        else
        {
            debugPrint("Null type"); //$NON-NLS-1$
        }
        try
        {
            checkRefs(jo, Messages.DTFJIndexBuilder_CheckRefsObject, aa, jlc.getObjectAddress(), bootLoaderAddress, listener);
        }
        catch (CorruptDataException e)
        {
            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ProblemCheckingOutboundReferences, format(objAddr)), e);
        }

        // The GC roots associated with a thread are outbound references for the
        // thread, not global roots
        addThreadRefs(objId, aa);
        addRefs(refd, objId, aa);
        outRefs.log(indexToAddress, objId, aa);
    }

    /**
     * Estimate the size of an array using the same calculation as
     * ObjectArrayImpl.java and PrimitiveArrayImpl.java.
     * 
     * @param cls
     * @param arrayLen
     *            in elements
     * @param pointerBytes
     *            in bytes
     * @return size in bytes
     */
    private long calculateArraySize(ClassImpl cls, int arrayLen, int pointerBytes)
    {
        int elem;
        if (cls.getName().equals("byte[]")) //$NON-NLS-1$
        {
            elem = 1;
        }
        else if (cls.getName().equals("short[]")) //$NON-NLS-1$
        {
            elem = 2;
        }
        else if (cls.getName().equals("int[]")) //$NON-NLS-1$
        {
            elem = 4;
        }
        else if (cls.getName().equals("long[]")) //$NON-NLS-1$
        {
            elem = 8;
        }
        else if (cls.getName().equals("boolean[]")) //$NON-NLS-1$
        {
            elem = 1;
        }
        else if (cls.getName().equals("char[]")) //$NON-NLS-1$
        {
            elem = 2;
        }
        else if (cls.getName().equals("float[]")) //$NON-NLS-1$
        {
            elem = 4;
        }
        else if (cls.getName().equals("double[]")) //$NON-NLS-1$
        {
            elem = 8;
        }
        else
        {
            elem = pointerBytes;
        }
        long bigSize = 2L * pointerBytes + 4 + (long) elem * arrayLen;
        return bigSize;
    }

    private ClassImpl findJavaLangClassloader(IProgressListener listener)
    {
        return findClassFromName(ClassImpl.JAVA_LANG_CLASSLOADER, listener);
    }
    
    private ClassImpl findClassFromName(String name, IProgressListener listener)
    {
        for (Iterator<ClassImpl> i = idToClass.values(); i.hasNext();)
        {
            ClassImpl cls = i.next();
            if (cls != null)
            {
                if (cls.getName().equals(name)) { return cls; }
            }
            else
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_NullClassImpl, i), null);
            }
        }
        return null;
    }

    private void scanJavaThread(JavaThread th, long threadAddress, int pointerSize,
                    HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thr, IProgressListener listener,
                    boolean scanUp, PrintWriter pw) throws CorruptDataException
    {
        if (pw != null) 
        {
            printThreadStack(pw, th);
        }
        int frameId = 0;
        Set<ImagePointer> searched = new HashSet<ImagePointer>();
        // Find the first address
        long veryFirstAddr = 0;
        // The base pointer appears to be the last address of the frame, not the
        // first
        long prevAddr = 0;
        long prevFrameAddress = 0;
        // We need to look ahead to get the frame size
        Object nextFrame = null;
        for (Iterator<?> ii = th.getStackFrames(); nextFrame != null || ii.hasNext(); ++frameId)
        {
            // Use the lookahead frame if available
            Object next2;
            if (nextFrame != null)
            {
                next2 = nextFrame;
                nextFrame = null;
            }
            else
            {
                next2 = ii.next();
            }
            if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingJavaStackFrames, th))
                continue;
            JavaStackFrame jf = (JavaStackFrame) next2;
            if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
            Set<ImagePointer> searchedInFrame = new LinkedHashSet<ImagePointer>();
            long address = 0;
            long searchSize = JAVA_STACK_FRAME_SIZE;
            try
            {
                ImagePointer ip = getAlignedAddress(jf.getBasePointer(), pointerSize);
                address = ip.getAddress();
                if (scanUp)
                {
                    // Check the next frame to limit the current frame size
                    if (ii.hasNext())
                    {
                        nextFrame = ii.next();
                        if (!isCorruptData(nextFrame, listener,
                                        Messages.DTFJIndexBuilder_CorruptDataReadingJavaStackFrames, th))
                        {
                            JavaStackFrame jf2 = (JavaStackFrame) nextFrame;
                            try
                            {
                                ImagePointer ip2 = getAlignedAddress(jf2.getBasePointer(), pointerSize);
                                long address2 = ip2.getAddress();
                                long s2 = address2 - address;
                                if (s2 > 0 && s2 < searchSize)
                                {
                                    searchSize = s2;
                                }
                            }
                            catch (CorruptDataException e)
                            {
                                // Ignore for the moment - we'll find it again
                                // next time.
                            }
                        }
                    }
                }
                else
                {
                    // Check the previous frame to limit the current frame size
                    if (prevAddr == 0)
                    {
                        prevAddr = getJavaStackBase(th, address);
                    }
                    long s2 = address - prevAddr;
                    prevAddr = address;
                    if (s2 > 0 && s2 < searchSize)
                    {
                        searchSize = s2;
                    }
                    // Go backwards from ip so that we search the known good
                    // addresses first
                    searchSize = -searchSize;
                }
                if (veryFirstAddr == 0)
                {
                    veryFirstAddr = Math.min(address, address + searchSize);
                }
                if (debugInfo) debugPrint("Frame " + jf.getLocation().getMethod().getName()); //$NON-NLS-1$
                searchFrame(pointerSize, threadAddress, thr, ip, searchSize, GCRootInfo.Type.JAVA_LOCAL, gcRoot,
                                searchedInFrame, null);
            }
            catch (MemoryAccessException e)
            {
                // We don't know the size of the frame, so could go beyond the
                // end and get an error
                JavaLocation jl = null;
                try
                {
                    jl = jf.getLocation();
                    JavaMethod jm = jl.getMethod();
                    String className = jm.getDeclaringClass().getName();
                    String methodName = jm.getName();
                    String modifiers = getModifiers(jm, listener);
                    String sig = jm.getSignature();
                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_PossibleProblemReadingJavaStackFramesMethod, frameId,
                                    format(address), searchSize, modifiers, className, methodName, sig,
                                    format(threadAddress)), e);
                }
                catch (DataUnavailable e2)
                {
                    // Location will have been set up
                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_PossibleProblemReadingJavaStackFramesLocation, frameId,
                                    format(address), searchSize, jl, format(threadAddress)), e);
                }
                catch (CorruptDataException e2)
                {
                    if (jl != null)
                    {
                        listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_PossibleProblemReadingJavaStackFramesLocation, frameId,
                                        format(address), searchSize, jl, format(threadAddress)), e);
                    }
                    else
                    {
                        listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_PossibleProblemReadingJavaStackFrames, frameId,
                                        format(address), searchSize, format(threadAddress)), e);
                    }
                }
            }
            catch (CorruptDataException e)
            {
                JavaLocation jl = null;
                try
                {
                    jl = jf.getLocation();
                    JavaMethod jm = jl.getMethod();
                    String className = jm.getDeclaringClass().getName();
                    String methodName = jm.getName();
                    String modifiers = getModifiers(jm, listener);
                    String sig;
                    try
                    {
                        sig = jm.getSignature();
                    }
                    catch (CorruptDataException e2)
                    {
                        sig = "()"; //$NON-NLS-1$
                    }
                    if (msgNproblemReadingJavaStackFrame-- > 0)
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ProblemReadingJavaStackFramesMethod, frameId,
                                        format(address), searchSize, modifiers, className, methodName, sig,
                                        format(threadAddress)), e);
                }
                catch (DataUnavailable e2)
                {
                    // Location will have been set up
                    if (msgNproblemReadingJavaStackFrame-- > 0)
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ProblemReadingJavaStackFramesLocation, frameId,
                                        format(address), searchSize, jl, format(threadAddress)), e);
                }
                catch (CorruptDataException e2)
                {
                    if (jl != null)
                    {
                        if (msgNproblemReadingJavaStackFrame-- > 0)
                            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                            Messages.DTFJIndexBuilder_ProblemReadingJavaStackFramesLocation, frameId,
                                            format(address), searchSize, jl, format(threadAddress)), e);
                    }
                    else
                    {
                        if (msgNproblemReadingJavaStackFrame-- > 0)
                            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                            Messages.DTFJIndexBuilder_ProblemReadingJavaStackFrames, frameId, format(address),
                                            searchSize, format(threadAddress)), e);
                    }
                }
            }
            // Add all the searched locations in this frame to the master list
            searched.addAll(searchedInFrame);            
            if (pw != null)
            {
            	// Indicate the local variables associated with this frame
                for (ImagePointer addr : searchedInFrame)
                {
                    try
                    {
                        // Construct new pointer based on frame address
                        long target = getPointerAddressAt(addr, 0, pointerSize);
                        printLocal(pw, target, frameId);
                     }
                    catch (MemoryAccessException e)
                    {}
                    catch (CorruptDataException e)
                    {}
                }
            }

            if (getExtraInfo)
            {
                prevFrameAddress = getFrameAddress(jf, prevFrameAddress, pointerSize);
            }
            if (!getExtraInfo || address == 0)
            {
                // Mark the classes of methods as referenced by the thread
                try
                {
                    long clsAddress;
                    if (getExtraInfo && prevFrameAddress != 0)
                    {
                        clsAddress = prevFrameAddress;
                    }
                    else
                    {
                        JavaMethod jm = jf.getLocation().getMethod();
                        JavaClass cls = jm.getDeclaringClass();
                        clsAddress = getClassAddress(cls, listener);
                    }
                    int clsId = indexToAddress.reverse(clsAddress);
                    if (clsId >= 0)
                    {
                        // Mark the class
                        HashMapIntObject<List<XGCRootInfo>> thr1 = thr.get(indexToAddress.reverse(threadAddress));
                        if (thr1 != null)
                        {
                            // Add it to the thread roots
                            addRoot(thr1, clsAddress, threadAddress, GCRootInfo.Type.JAVA_LOCAL);
                            // Add it to the global GC roots
                            if (!useThreadRefsNotRoots)
                                addRoot(gcRoot, clsAddress, threadAddress, GCRootInfo.Type.JAVA_LOCAL);
                        }
                        else
                        {
                            // No thread information so make a global root
                            addRoot(gcRoot, clsAddress, threadAddress, GCRootInfo.Type.JAVA_LOCAL);
                        }
                    }
                }
                catch (DataUnavailable e2)
                {}
                catch (CorruptDataException e2)
                {}
            }

        }
        if (pw != null)
        {
            pw.println();
        }
        for (Iterator<?> ii = th.getStackSections(); ii.hasNext();)
        {
            Object next2 = ii.next();
            if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingJavaStackSections, th))
                continue;
            ImageSection is = (ImageSection) next2;
            if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
            ImagePointer ip = is.getBaseAddress();
            long size = is.getSize();
            try
            {
                debugPrint("Java stack section"); //$NON-NLS-1$
                if (size <= JAVA_STACK_SECTION_MAX_SIZE)
                {
                    searchFrame(pointerSize, threadAddress, thr, ip, size, GCRootInfo.Type.JAVA_LOCAL, gcRoot, null,
                                    searched);
                }
                else
                {
                    // Giant frame, so just search the top and the bottom rather
                    // than 500MB!
                    long size2 = size;
                    size = JAVA_STACK_SECTION_MAX_SIZE / 2;
                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_HugeJavaStackSection, format(ip.getAddress()), size2,
                                    format(threadAddress), size), null);
                    searchFrame(pointerSize, threadAddress, thr, ip, size, GCRootInfo.Type.JAVA_LOCAL, gcRoot, null,
                                    searched);
                    ip = ip.add(size2 - size);
                    searchFrame(pointerSize, threadAddress, thr, ip, size, GCRootInfo.Type.JAVA_LOCAL, gcRoot, null,
                                    searched);
                }
            }
            catch (MemoryAccessException e)
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemReadingJavaStackSection, format(ip.getAddress()),
                                size, format(threadAddress)), e);
            }
            catch (CorruptDataException e)
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemReadingJavaStackSection, format(ip.getAddress()),
                                size, format(threadAddress)), e);
            }
        }
    }

    /**
     * Find the lowest address of the stack section which contains the given
     * address
     * 
     * @param th
     *            The thread.
     * @param addr
     *            The address in question.
     * @return The lowest address in the section containing the address.
     */
    private long getJavaStackBase(JavaThread th, long addr)
    {
        for (Iterator<?> ii = th.getStackSections(); ii.hasNext();)
        {
            Object next2 = ii.next();
            if (next2 instanceof CorruptData)
                continue;
            ImageSection is = (ImageSection) next2;
            long base = is.getBaseAddress().getAddress();
            if (base <= addr && addr < base + is.getSize()) { return base; }
        }
        return 0;
    }

    private void scanImageThread(JavaThread th, ImageThread it, long threadAddress, int pointerSize,
                    HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thr, IProgressListener listener)
                    throws CorruptDataException
    {
        try
        {
            int frameId = 0;
            // We need to look ahead to get the frame size
            Object nextFrame = null;
            for (Iterator<?> ii = it.getStackFrames(); nextFrame != null || ii.hasNext(); ++frameId)
            {
                // Use the lookahead frame if available
                Object next2;
                if (nextFrame != null)
                {
                    next2 = nextFrame;
                    nextFrame = null;
                }
                else
                {
                    next2 = ii.next();
                }
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingNativeStackFrames, th))
                    continue;
                ImageStackFrame jf = (ImageStackFrame) next2;
                if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
                ImagePointer ip = jf.getBasePointer();
                long searchSize = NATIVE_STACK_FRAME_SIZE;
                // Check the next frame to limit the current frame size
                if (ii.hasNext())
                {
                    nextFrame = ii.next();
                    if (!isCorruptData(nextFrame, listener,
                                    Messages.DTFJIndexBuilder_CorruptDataReadingNativeStackFrames, th))
                    {
                        ImageStackFrame jf2 = (ImageStackFrame) nextFrame;
                        try
                        {
                            ImagePointer ip2 = jf2.getBasePointer();
                            long s2 = ip2.getAddress() - ip.getAddress();
                            if (s2 > 0 && s2 < searchSize)
                            {
                                searchSize = s2;
                            }
                        }
                        catch (CorruptDataException e)
                        {
                            // Ignore for the moment - we'll find it again next
                            // time
                        }
                    }
                }
                try
                {
                    debugPrint("native stack frame"); //$NON-NLS-1$
                    searchFrame(pointerSize, threadAddress, thr, ip, searchSize, GCRootInfo.Type.NATIVE_STACK, gcRoot,
                                    null, null);
                }
                catch (MemoryAccessException e)
                {
                    // We don't know the size of the frame, so could go beyond
                    // the end and get an error
                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_PossibleProblemReadingNativeStackFrame, frameId,
                                    format(ip.getAddress()), searchSize, format(threadAddress)), e);
                }
                catch (CorruptDataException e)
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemReadingNativeStackFrame, frameId, format(ip
                                                    .getAddress()), searchSize, format(threadAddress)), e);
                }
            }
        }
        catch (DataUnavailable e)
        {
            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_NativeStackFrameNotFound, format(threadAddress)), e);
        }
        for (Iterator<?> ii = it.getStackSections(); ii.hasNext();)
        {
            Object next2 = ii.next();
            if (isCorruptData(next2, listener,
                            Messages.DTFJIndexBuilder_DTFJIndexBuilder_CorruptDataReadingNativeStackSection, th))
                continue;
            ImageSection is = (ImageSection) next2;
            ImagePointer ip = is.getBaseAddress();
            long size = is.getSize();
            try
            {
                debugPrint("native stack section"); //$NON-NLS-1$
                if (size <= NATIVE_STACK_SECTION_MAX_SIZE)
                {
                    searchFrame(pointerSize, threadAddress, thr, ip, size, GCRootInfo.Type.NATIVE_STACK, gcRoot, null,
                                    null);
                }
                else
                {
                    // Giant frame, so just search the top and the bottom rather
                    // than 500MB!
                    long size2 = size;
                    size = NATIVE_STACK_SECTION_MAX_SIZE / 2;
                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_HugeNativeStackSection, format(ip.getAddress()), size2,
                                    format(threadAddress), size), null);
                    searchFrame(pointerSize, threadAddress, thr, ip, size, GCRootInfo.Type.NATIVE_STACK, gcRoot, null,
                                    null);
                    ip = ip.add(size2 - size);
                    searchFrame(pointerSize, threadAddress, thr, ip, size, GCRootInfo.Type.NATIVE_STACK, gcRoot, null,
                                    null);
                }
            }
            catch (MemoryAccessException e)
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemReadingNativeStackSection, format(ip.getAddress()),
                                size, format(threadAddress)), e);
            }
            catch (CorruptDataException e)
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemReadingNativeStackSection, format(ip.getAddress()),
                                size, format(threadAddress)), e);
            }
        }
    }

    /**
     * The runtime version of MAT might not know this root type
     * 
     * @param rootType
     * @return rootType or UNKNOWN
     */
    private int newRootType(int rootType)
    {
        return GCRootInfo.getTypeAsString(rootType) != null ? rootType : GCRootInfo.Type.UNKNOWN;
    }

    /**
     * Add a root to the list of roots
     * 
     * @param r
     *            The reference to the object
     * @param thread
     *            Thread thread that referred to this object, or null
     * @param gcRoot2
     *            Where to store the global roots
     * @param threadRoots2
     *            Where to store the thread roots
     * @param pointerSize
     *            size of pointers in bits
     * @param listener
     */
    private void processRoot(JavaReference r, JavaThread thread, HashMapIntObject<List<XGCRootInfo>> gcRoot2,
                    HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> threadRoots2, int pointerSize,
                    IProgressListener listener)
    {
        debugPrint("Process root " + r); //$NON-NLS-1$
        int type = 0;
        boolean threadRoot = false;
        int rootType = JavaReference.HEAP_ROOT_UNKNOWN;
        try
        {
            rootType = r.getRootType();
            switch (rootType)
            {
                case JavaReference.HEAP_ROOT_JNI_GLOBAL:
                    type = GCRootInfo.Type.NATIVE_STATIC;
                    break;
                case JavaReference.HEAP_ROOT_JNI_LOCAL:
                    type = GCRootInfo.Type.NATIVE_STACK;
                    type = GCRootInfo.Type.NATIVE_LOCAL;
                    threadRoot = true;
                    break;
                case JavaReference.HEAP_ROOT_MONITOR:
                    type = GCRootInfo.Type.BUSY_MONITOR;
                    threadRoot = true;
                    break;
                case JavaReference.HEAP_ROOT_STACK_LOCAL:
                    type = GCRootInfo.Type.JAVA_LOCAL;
                    threadRoot = true;
                    break;
                case JavaReference.HEAP_ROOT_SYSTEM_CLASS:
                    type = GCRootInfo.Type.SYSTEM_CLASS;
                    if (!useSystemClassRoots)
                    {
                        return; // Ignore system classes
                    // for moment as should
                    // be found via
                    // bootclassloader
                    }
                    break;
                case JavaReference.HEAP_ROOT_THREAD:
                    type = GCRootInfo.Type.THREAD_BLOCK;
                    type = GCRootInfo.Type.THREAD_OBJ;
                    break;
                case JavaReference.HEAP_ROOT_OTHER:
                    debugPrint("Root type HEAP_ROOT_OTHER"); //$NON-NLS-1$
                    type = GCRootInfo.Type.UNKNOWN;
                    break;
                case JavaReference.HEAP_ROOT_UNKNOWN:
                    debugPrint("Root type HEAP_ROOT_UNKNOWN"); //$NON-NLS-1$
                    type = GCRootInfo.Type.UNKNOWN;
                    break;
                case JavaReference.HEAP_ROOT_FINALIZABLE_OBJ:
                    // The object is in the finalizer queue
                    type = GCRootInfo.Type.FINALIZABLE;
                    // No need to guess
                    foundFinalizableGCRoots = true;
                    break;
                case JavaReference.HEAP_ROOT_UNFINALIZED_OBJ:
                    // The object will in the end need to be finalized, but is
                    // currently in use
                    type = GCRootInfo.Type.UNFINALIZED;
                    break;
                case JavaReference.HEAP_ROOT_CLASSLOADER:
                    type = GCRootInfo.Type.SYSTEM_CLASS;
                    if (!useSystemClassRoots)
                    {
                        // Ignore class loaders as will be found via instances
                        // of a class.
                        // E.g. Thread -> class java.lang.Thread -> bootstrap
                        // loader
                        return;
                    }
                    break;
                case JavaReference.HEAP_ROOT_STRINGTABLE:
                    type = GCRootInfo.Type.UNKNOWN;
                    break;
                default:
                    debugPrint("Unknown root type " + rootType); //$NON-NLS-1$
                    type = GCRootInfo.Type.UNKNOWN;
                    break;
            }
        }
        catch (CorruptDataException e)
        {
            type = GCRootInfo.Type.UNKNOWN;
            listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_UnableToFindTypeOfRoot, e);
        }
        int reach = JavaReference.REACHABILITY_UNKNOWN;
        try
        {
            reach = r.getReachability();
            switch (reach)
            {
                default:
                case JavaReference.REACHABILITY_UNKNOWN:
                case JavaReference.REACHABILITY_STRONG:
                    break;
                case JavaReference.REACHABILITY_WEAK:
                case JavaReference.REACHABILITY_SOFT:
                case JavaReference.REACHABILITY_PHANTOM:
                    if (skipWeakRoots)
                        return;
                    break;
            }
        }
        catch (CorruptDataException e)
        {
            listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_UnableToFindReachabilityOfRoot, e);
        }
        int refType = JavaReference.REFERENCE_UNKNOWN;
        try
        {
            refType = r.getReferenceType();
        }
        catch (CorruptDataException e)
        {
            listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_UnableToFindReferenceTypeOfRoot, e);
        }
        try
        {
            long target = 0;

            Object o = r.getTarget();
            if (o instanceof JavaObject)
            {
                JavaObject jo = (JavaObject) o;
                target = jo.getID().getAddress();
            }
            else if (o instanceof JavaClass)
            {
                JavaClass jc = (JavaClass) o;
                target = getClassAddress(jc, listener);
            }
            else
            {
                // Unknown root target, so ignore
                if (o != null)
                {
                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_UnableToFindTargetOfRoot, o, o.getClass()), null);
                    debugPrint("Unexpected root type " + o.getClass()); //$NON-NLS-1$
                }
                else
                {
                    listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_NullTargetOfRoot, null);
                    debugPrint("Unexpected null root target"); //$NON-NLS-1$
                }
                return;
            }

            long source = target;
            try
            {
                Object so = r.getSource();
                if (so instanceof JavaObject)
                {
                    JavaObject jo = (JavaObject) so;
                    source = jo.getID().getAddress();
                }
                else if (so instanceof JavaClass)
                {
                    JavaClass jc = (JavaClass) so;
                    source = getClassAddress(jc, listener);
                }
                else if (so instanceof JavaStackFrame)
                {
                    JavaStackFrame js = (JavaStackFrame) so;
                    if (getExtraInfo)
                    {
                        source = getAlignedAddress(js.getBasePointer(), pointerSize).getAddress();
                    }
                    // Thread is supplied, and stack frame is not an object
                    if (thread != null && (!getExtraInfo || source == 0 || indexToAddress.reverse(source) < 0))
                    {
                        source = getThreadAddress(thread, listener);
                    }
                }
                else if (so instanceof JavaThread)
                {
                    // Not expected, but sov DTFJ returns this
                    JavaThread jt = (JavaThread)so;
                    source = getThreadAddress(jt ,listener);
                    // Thread is supplied, and jt is not found as an object
                    if (thread != null && (source == 0 || indexToAddress.reverse(source) < 0))
                    {
                        source = getThreadAddress(thread, listener);
                    }
                    // Sov DTFJ has curious types
                    String desc = r.getDescription();
                    if (desc.startsWith("stack") || desc.startsWith("Register")) //$NON-NLS-1$ //$NON-NLS-2$
                    {
                        // These roots are not thread
                        type = GCRootInfo.Type.NATIVE_STACK;
                        threadRoot = true;
                    }
                }
                else if (so instanceof JavaRuntime)
                {
                    // Not expected, but J9 DTFJ returns this
                    debugPrint("Unexpected source " + so); //$NON-NLS-1$
                }
                else if (so == null)
                {
                    // Unknown
                }
                else
                {
                    debugPrint("Unexpected source " + so); //$NON-NLS-1$
                }
            }
            catch (CorruptDataException e)
            {
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                Messages.DTFJIndexBuilder_UnableToFindSourceOfRoot, format(target)), e);
            }
            catch (DataUnavailable e)
            {
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                Messages.DTFJIndexBuilder_UnableToFindSourceOfRoot, format(target)), e);
            }
            int targetId = indexToAddress.reverse(target);
            // Only used for missedRoots
            String desc = targetId + " " + format(target) + " " + format(source) + " " + rootType + " " + refType + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                            + reach + " " + r.getDescription(); //$NON-NLS-1$
            if (targetId < 0)
            {
                String desc2 = ""; //$NON-NLS-1$
                Exception e1 = null;
                try
                {
                    if (o instanceof JavaObject)
                    {
                        JavaObject jo = (JavaObject) o;
                        desc2 = getClassName(jo.getJavaClass(), listener);
                    }
                    else if (o instanceof JavaClass)
                    {
                        JavaClass jc = (JavaClass) o;
                        desc2 = getClassName(jc, listener);
                    }
                    else
                    {
                        // Should never occur
                        desc2 = desc2 + o;
                    }
                }
                catch (CorruptDataException e)
                {
                    // Ignore exception as just for logging
                    e1 = e;
                }
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_UnableToFindRoot, format(target), desc2, format(source),
                                rootType, r.getDescription()), e1);
                return;
            }
            if (newRootType(type) == GCRootInfo.Type.UNKNOWN)
            {
                String desc2 = ""; //$NON-NLS-1$
                Exception e1 = null;
                try
                {
                    if (o instanceof JavaObject)
                    {
                        JavaObject jo = (JavaObject) o;
                        desc2 = getClassName(jo.getJavaClass(), listener);
                    }
                    else if (o instanceof JavaClass)
                    {
                        JavaClass jc = (JavaClass) o;
                        desc2 = getClassName(jc, listener);
                    }
                    else
                    {
                        // Should never occur
                        desc2 = o.toString();
                    }
                }
                catch (CorruptDataException e)
                {
                    // Ignore exception as just for logging
                    e1 = e;
                }
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                Messages.DTFJIndexBuilder_MATRootTypeUnknown, type, format(target), desc2,
                                format(source), rootType, r.getDescription()), e1);
            }
            if (threadRoot)
            {
                int thrId = indexToAddress.reverse(source);
                if (thrId >= 0)
                {
                    HashMapIntObject<List<XGCRootInfo>> thr = threadRoots2.get(thrId);
                    if (thr == null)
                    {
                        // Build new list for the thread
                        thr = new HashMapIntObject<List<XGCRootInfo>>();
                        threadRoots2.put(thrId, thr);
                    }
                    addRoot(thr, target, source, type);
                    if (!useThreadRefsNotRoots)
                        addRoot(gcRoot2, target, source, type);
                }
                else
                {
                    addRoot(gcRoot2, target, source, type);
                }
            }
            else
            {
                addRoot(gcRoot2, target, source, type);
            }
            int tgt = targetId;
            int src = indexToAddress.reverse(source);
            if (src < 0)
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_UnableToFindSourceID, format(target), format(source), r
                                                .getDescription()), null);
            }
            missedRoots.put(Integer.valueOf(tgt), desc);
        }
        catch (DataUnavailable e)
        {
            listener.sendUserMessage(Severity.WARNING, Messages.DTFJIndexBuilder_ProblemGettingRoots, e);
        }
        catch (CorruptDataException e)
        {
            listener.sendUserMessage(Severity.WARNING, Messages.DTFJIndexBuilder_ProblemGettingRoots, e);
        }
    }

    /**
     * Round the object size to allow for alignment
     * @param jo
     * @param pointerSize in bits
     * @return the object size in bytes
     * @throws CorruptDataException
     */
    private long getObjectSize(JavaObject jo, int pointerSize) throws CorruptDataException
    {
        // DTFJ size includes any link field, so just round to 8 bytes
        long s = (jo.getSize() + 7) & ~7L;
        return s;
    }

    /**
     * Get an aligned version of a pointer.
     * 
     * @param p
     *            The original pointer.
     * @param pointerSize
     *            The size to align to in bits.
     * @return The aligned pointer.
     */
    private static ImagePointer getAlignedAddress(ImagePointer p, int pointerSize)
    {
        if (p == null)
            return p;
        long addr = p.getAddress();
        if (pointerSize == 64)
        {
            addr &= 7L;
        }
        else
        {
            addr &= 3L;
        }
        return p.add(-addr);
    }

    /**
     * @param idToClass2
     */
    private HashMapIntObject<ClassImpl> copy(HashMapIntObject<ClassImpl> idToClass1)
    {
        HashMapIntObject<ClassImpl> idToClass2 = new HashMapIntObject<ClassImpl>(idToClass1.size());
        for (IteratorInt ii = idToClass1.keys(); ii.hasNext();)
        {
            int i = ii.next();
            idToClass2.put(i, idToClass1.get(i));
        }
        return idToClass2;
    }

    /**
     * @param objectToClass2
     */
    private IndexWriter.IntIndexCollector copy(IIndexReader.IOne2OneIndex objectToClass1, int bits)
    {
        IndexWriter.IntIndexCollector objectToClass2 = new IndexWriter.IntIndexCollector(objectToClass1.size(), bits);
        for (int i = 0; i < objectToClass1.size(); ++i)
        {
            int j = objectToClass1.get(i);
            objectToClass2.set(i, j);
        }
        return objectToClass2;
    }

    /**
     * Build a cache of classes loaded by each loader
     * @return
     */
    private HashMapIntObject<ArrayLong> initLoaderClassesCache()
    {
        HashMapIntObject<ArrayLong> cache = new HashMapIntObject<ArrayLong>();
        for (Iterator<ClassImpl> i = idToClass.values(); i.hasNext();)
        {
            ClassImpl ci = i.next();
            int load = ci.getClassLoaderId();
            if (!cache.containsKey(load))
                cache.put(load, new ArrayLong());
            ArrayLong classes = cache.get(load);
            if (!(getExtraInfo2 && ci.getName().contains(METHOD_NAME_SIG)))
            {
                // Skip method classes - they are also found via the declaring
                // class
                debugPrint("Adding ref to class " + ci.getObjectId() + " at address " //$NON-NLS-1$ //$NON-NLS-2$
                                + format(ci.getObjectAddress()) + " for loader " + load); //$NON-NLS-1$
                classes.add(ci.getObjectAddress());
            }
        }
        return cache;
    }

    /**
     * @param objId
     * @param aa
     */
    private void addLoaderClasses(int objId, ArrayLong aa)
    {
        debugPrint("Found loader " + objId + " at address " + format(indexToAddress.get(objId)) + " size=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + idToClass.size());
        // Add all the classes loaded by it as references
        ArrayLong classes = loaderClassCache.get(objId);
        if (classes != null)
        {
            aa.addAll(classes);
        }
    }

    /**
     * Helper method to test whether object is corrupt and to log the corrupt
     * data
     * 
     * @param next
     * @param listener
     * @param msg
     * @return
     */
    private static boolean isCorruptData(Object next, IProgressListener listener, String msg)
    {
        if (next instanceof CorruptData)
        {
            CorruptData d = (CorruptData) next;
            if (listener != null)
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(msg, formattedCorruptDataAddress(d), d
                                .toString()), new CorruptDataException(d));
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Helper method to test whether object is corrupt and to log the corrupt
     * data
     * 
     * @param next
     * @param listener
     * @param msg
     * @param detail
     *            - some more information about the source for the iterator
     * @param addr
     *            - the address of the source for the iterator
     * @return
     */
    private boolean isCorruptData(Object next, IProgressListener listener, String msg, String detail, long addr)
    {
        if (next instanceof CorruptData)
        {
            CorruptData d = (CorruptData) next;
            if (listener != null)
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(msg, formattedCorruptDataAddress(d), d
                                .toString(), detail, format(addr)), new CorruptDataException(d));
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Format the data address of the corruption. Avoid problems if no address
     * is available.
     * 
     * @param d
     * @return
     */
    private static String formattedCorruptDataAddress(CorruptData d)
    {
        ImagePointer ip = d.getAddress();
        if (ip != null)
        {
            return format(d.getAddress().getAddress());
        }
        else
        {
            // No address in the corrupt data Translate?
            return "null"; //$NON-NLS-1$
        }
    }

    /**
     * Helper method to test whether object is corrupt and to log the corrupt
     * data
     * 
     * @param next
     * @param listener
     * @param msg
     * @return
     */
    private static boolean isCorruptData(Object next, IProgressListener listener, String msg, JavaRuntime detail)
    {
        if (next instanceof CorruptData)
        {
            CorruptData d = (CorruptData) next;
            long addr;
            try
            {
                addr = detail.getJavaVM().getAddress();
            }
            catch (CorruptDataException e)
            {
                addr = 0;
            }
            logCorruptData(listener, msg, d, addr);
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Helper method to test whether object is corrupt and to log the corrupt
     * data
     * 
     * @param next
     * @param listener
     * @param msg
     * @return
     */
    private static boolean isCorruptData(Object next, IProgressListener listener, String msg, JavaClassLoader detail)
    {
        if (next instanceof CorruptData)
        {
            CorruptData d = (CorruptData) next;
            long addr;
            try
            {
                JavaObject ldr = detail.getObject();
                if (ldr != null)
                {
                    addr = ldr.getID().getAddress();
                }
                else
                {
                    addr = 0;
                }
            }
            catch (CorruptDataException e)
            {
                addr = 0;
            }
            logCorruptData(listener, msg, d, addr);
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Helper method to test whether object is corrupt and to log the corrupt
     * data
     * 
     * @param next
     * @param listener
     * @param msg
     *            With corrupt data address {0} corrupt data {1} class name {2}
     *            class address {3}
     * @return
     */
    private boolean isCorruptData(Object next, IProgressListener listener, String msg, JavaClass detail)
    {
        if (next instanceof CorruptData)
        {
            CorruptData d = (CorruptData) next;
            long addr = getClassAddress(detail, listener);
            String name;
            try
            {
                name = detail.getName();
            }
            catch (CorruptDataException e)
            {
                name = e.toString();
            }
            if (listener != null)
                listener.sendUserMessage(Severity.ERROR, MessageFormat.format(msg, formattedCorruptDataAddress(d), d
                                .toString(), name, format(addr)), new CorruptDataException(d));
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Helper method to test whether object is corrupt and to log the corrupt
     * data
     * 
     * @param next
     * @param listener
     * @param msg
     *            With corrupt data address {0} corrupt data {1} class name {2}
     *            class address {3} modifiers {4} class name {5} method name {6}
     *            method signature {7}
     * @return
     */
    private boolean isCorruptData(Object next, IProgressListener listener, String msg, JavaClass jc, JavaMethod detail)
    {
        if (next instanceof CorruptData)
        {
            CorruptData d = (CorruptData) next;
            String clsName;
            String methName;
            String methSig;
            try
            {
                clsName = jc != null ? jc.getName() : ""; //$NON-NLS-1$
            }
            catch (CorruptDataException e)
            {
                clsName = e.toString();
            }
            try
            {
                methName = detail.getName();
            }
            catch (CorruptDataException e)
            {
                methName = e.toString();
            }
            try
            {
                methSig = detail.getSignature();
            }
            catch (CorruptDataException e)
            {
                methSig = e.toString();
            }
            long addr = jc != null ? getClassAddress(jc, listener) : 0;
            String modifiers = getModifiers(detail, listener);
            if (listener != null)
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(msg, formattedCorruptDataAddress(d), d
                                .toString(), clsName, format(addr), modifiers, clsName, methName, methSig),
                                new CorruptDataException(d));
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Get the modifiers for a method (public/private etc.)
     * 
     * @param detail
     * @param listener
     *            for logging error messages
     * @return A string representation of the modifiers.
     */
    private String getModifiers(JavaMethod detail, IProgressListener listener)
    {
        String modifiers;
        int mods;
        Exception e1 = null;
        try
        {
            // Remove unexpected modifiers - DTFJ defect?
            mods = detail.getModifiers();
        }
        catch (CorruptDataException e)
        {
            mods = 0;
            e1 = e;
        }

        final int expectedMods = (Modifier.ABSTRACT | Modifier.FINAL | Modifier.NATIVE | Modifier.PRIVATE
                        | Modifier.PROTECTED | Modifier.PUBLIC | Modifier.STATIC | Modifier.STRICT | Modifier.SYNCHRONIZED);
        int unexpectedMods = mods & ~expectedMods;
        if ((e1 != null || unexpectedMods != 0) && msgNunexpectedModifiers-- >= 0)
        {
            String m1 = Modifier.toString(unexpectedMods);
            String methName = ""; //$NON-NLS-1$
            String methClass = ""; //$NON-NLS-1$
            String sig = ""; //$NON-NLS-1$
            try
            {
                methName = detail.getName();
                methClass = detail.getDeclaringClass().getName();
            }
            catch (CorruptDataException e)
            {
                if (e1 == null)
                    e1 = e;
            }
            catch (DataUnavailable e)
            {
                if (e1 == null)
                    e1 = e;
            }
            try
            {
                sig = detail.getSignature();
            }
            catch (CorruptDataException e)
            {
                sig = "()"; //$NON-NLS-1$
                if (e1 == null)
                    e1 = e;
            }
            String mod = Modifier.toString(mods);
            listener.sendUserMessage(Severity_INFO, MessageFormat.format(Messages.DTFJIndexBuilder_UnexpectedModifiers,
                            format(unexpectedMods), m1, mod, methClass, methName, sig), e1);
        }
        modifiers = Modifier.toString(mods & expectedMods);
        return modifiers;
    }

    /**
     * Helper method to test whether object is corrupt and to log the corrupt
     * data
     * 
     * @param next
     * @param listener
     * @param msg
     *            address {0} corrupt data {1} thread name {2} thread address
     *            {3}
     * @return
     */
    private static boolean isCorruptData(Object next, IProgressListener listener, String msg, JavaThread detail)
    {
        if (next instanceof CorruptData)
        {
            CorruptData d = (CorruptData) next;
            long addr;
            String name;
            if (detail == null)
            {
                // Could be scanning a image thread without an Java thread
                addr = 0;
                name = ""; //$NON-NLS-1$
            }
            else
            {
                addr = getThreadAddress(detail, null);
                try
                {
                    name = detail.getName();
                }
                catch (CorruptDataException e)
                {
                    name = e.toString();
                }
            }
            if (listener != null)
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(msg, formattedCorruptDataAddress(d), d
                                .toString(), name, format(addr)), new CorruptDataException(d));
            return true;
        }
        else
        {
            return false;
        }
    }

    private static void logCorruptData(IProgressListener listener, String msg, CorruptData d, long addr)
    {
        if (listener != null)
            listener.sendUserMessage(Severity.ERROR, MessageFormat.format(msg, formattedCorruptDataAddress(d), d
                            .toString(), format(addr)), new CorruptDataException(d));
    }

    /**
     * Holds key DTFJ information.
     * @author ajohnson
     */
    static class RuntimeInfo {
        private ImageFactory factory;
        private Image image;
        private ImageAddressSpace imageAddressSpace;
        private ImageProcess imageProcess;
        private JavaRuntime javaRuntime;
        private String runtimeId;
        public RuntimeInfo(Image img, ImageAddressSpace space, ImageProcess process, JavaRuntime runtime, String id)
        {
            factory = factoryMap.get(img);
            image = img;
            imageAddressSpace = space;
            imageProcess = process;
            javaRuntime = runtime;
            runtimeId = id;
        }
        public ImageFactory getImageFactory()
        {
            if (factory == null)
                throw new IllegalStateException();
            return factory;
        }
        public Image getImage()
        {
            if (image == null)
                throw new IllegalStateException();
            return image;
        }
        public ImageAddressSpace getImageAddressSpace()
        {
            if (imageAddressSpace == null)
                throw new IllegalStateException();
            return imageAddressSpace;
        }
        public ImageProcess getImageProcess()
        {
            if (imageProcess == null)
                throw new IllegalStateException();
            return imageProcess;
        }
        public JavaRuntime getJavaRuntime()
        {
            if (javaRuntime == null)
                throw new IllegalStateException();
            return javaRuntime;
        }
        public String getRuntimeId()
        {
            return runtimeId;
        }
        /**
         * Allows objects to be garbage collected.
         */
        public void clear() {
            factory = null;
            image = null;
            imageAddressSpace = null;
            imageProcess = null;
            javaRuntime = null;
        }
    }
    /**
     * Find a Java runtime from the image
     * 
     * @param image the image to find the JavaRuntime from
     * @param requestedId the runtime id such as 0.0.0 or 1.2.3, or null for the only/first one.
     * @param listener
     * @return A filled in RuntimeInfo object.
     */
    static RuntimeInfo getRuntime(Image image, Serializable requestedId, IProgressListener listener) throws IOException
    {
        ImageAddressSpace ias = null;
        ImageProcess proc = null;
        JavaRuntime run = null;
        String fullRuntimeId = null;
        int nAddr = 0;
        int nProc = 0;
        int nJavaRuntimes = 0;
        String lastAddr = ""; //$NON-NLS-1$
        String lastProc = ""; //$NON-NLS-1$
        String lastJavaRuntime = ""; //$NON-NLS-1$
        // Cope with an empty String from preferences
        if ("".equals(requestedId)) //$NON-NLS-1$
            requestedId = null;
        // Split out the address space, process, runtime
        String sp[] = ((requestedId instanceof String ? (String)requestedId:"")).split("\\.", 3); //$NON-NLS-1$ //$NON-NLS-2$
        String id0 = sp[0];
        String id1 = sp.length > 1 ? sp[1] : ""; //$NON-NLS-1$
        String id2 = sp.length > 2 ? sp[2] : ""; //$NON-NLS-1$
        int addrId = 0;
        for (Iterator<?> i1 = image.getAddressSpaces(); i1.hasNext(); ++addrId)
        {
            Object next1 = i1.next();
            if (isCorruptData(next1, listener, Messages.DTFJIndexBuilder_CorruptDataReadingAddressSpaces))
                continue;
            ias = (ImageAddressSpace) next1;
            ++nAddr;
            lastAddr = ias.toString();
            // If the toString name is just the default Object.toString with a default hashcode then it isn't any good as a comparison
            String defaultToString = ias.getClass().getName()+'@'+Integer.toHexString(System.identityHashCode(ias));
            if (lastAddr.equals(defaultToString))
                lastAddr = Integer.toString(addrId);
            // Extract out any hex id
            if (lastAddr.matches(".*0x[0-9A-Fa-f]+")) //$NON-NLS-1$
                lastAddr = lastAddr.substring(lastAddr.lastIndexOf("0x")); //$NON-NLS-1$
            int procId = 0;
            for (Iterator<?> i2 = ias.getProcesses(); i2.hasNext(); ++procId)
            {
                Object next2 = i2.next();
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingProcesses))
                    continue;
                proc = (ImageProcess) next2;
                ++nProc;
                try
                {
                    lastProc = proc.getID();
                    // Avoid confusion of small process ids with nProc index
                    if (lastProc.matches("[0123456789]")) //$NON-NLS-1$
                        lastProc = "0x"+lastProc; //$NON-NLS-1$
                }
                catch (DataUnavailable e)
                {
                    if (listener != null)
                        listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_ErrorReadingProcessID, e);
                    lastProc = Integer.toString(procId);
                }
                catch (CorruptDataException e)
                {
                    if (listener != null)
                        listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_ErrorReadingProcessID, e);
                    lastProc = Integer.toString(procId);
                }
                int runtimeId = 0;
                for (Iterator<?> i3 = proc.getRuntimes(); i3.hasNext(); ++runtimeId)
                {
                    Object next3 = i3.next();
                    if (isCorruptData(next3, listener, Messages.DTFJIndexBuilder_CorruptDataReadingRuntimes))
                        continue;
                    String currentId = addrId+"."+procId+"."+runtimeId;  //$NON-NLS-1$//$NON-NLS-2$
                    if (next3 instanceof JavaRuntime)
                    {
                        JavaRuntime runtime = (JavaRuntime)next3;
                        try
                        {
                            lastJavaRuntime = format(runtime.getJavaVM().getAddress());
                        }
                        catch (CorruptDataException e)
                        {
                            lastJavaRuntime = Integer.toString(runtimeId);
                        }
                        ++nJavaRuntimes;
                        
                        Exception e1 = null;
                        String version = null;
                        try
                        {
                            version = runtime.getVersion();
                        }
                        catch (CorruptDataException e)
                        {
                            e1 = e;
                        }
                        if (run == null && (requestedId == null 
                            || currentId.equals(requestedId) 
                            || (id0.length() == 0 || id0.equals(lastAddr) || id0.equals(Integer.toString(addrId)))
                            && (id1.length() == 0 || id1.equals(lastProc) || id1.equals(Integer.toString(procId)))
                            && (id2.length() == 0 || id2.equals(lastJavaRuntime) ||id2.equals(Integer.toString(runtimeId)))
                          ))
                        {
                            run = runtime;
                            fullRuntimeId = currentId;
                            if (requestedId != null && listener != null)
                            {
                                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_FoundJavaRuntime, currentId, lastAddr, lastProc, lastJavaRuntime, version), e1);
                            }
                        }
                        else
                        {
                            if (listener != null)
                            {
                                if (requestedId == null)
                                {
                                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                    Messages.DTFJIndexBuilder_IgnoringExtraJavaRuntimeWithoutRuntimeId, currentId, lastAddr, lastProc, lastJavaRuntime, version), e1);
                                }
                                else
                                {
                                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_IgnoringExtraJavaRuntime, currentId, lastAddr, lastProc, lastJavaRuntime, version), e1);
                                }
                            }
                        }
                    }
                    else
                    {
                        ManagedRuntime mr = (ManagedRuntime) next3;
                        Exception e1 = null;
                        String version = null;
                        try
                        {
                            version = mr.getVersion();
                        }
                        catch (CorruptDataException e)
                        {
                            e1 = e;
                        }
                        if (listener != null)
                            listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                            Messages.DTFJIndexBuilder_IgnoringManagedRuntime, currentId, lastAddr, lastProc, lastJavaRuntime, version), e1);
                    }
                }
            }
        }
        if (run == null)
        {
            if (requestedId != null)
            {
                throw new IOException(MessageFormat.format(Messages.DTFJIndexBuilder_UnableToFindJavaRuntimeId,
                                requestedId, nAddr, lastAddr, nProc, lastProc, nJavaRuntimes, lastJavaRuntime));
            }
            else
            {
                throw new IOException(MessageFormat.format(Messages.DTFJIndexBuilder_UnableToFindJavaRuntime, nAddr,
                                lastAddr, nProc, lastProc, nJavaRuntimes, lastJavaRuntime));
            }
        }
        // Don't force a runtimeId if there is only one
        if (requestedId == null && nJavaRuntimes <= 1)
            fullRuntimeId = null;
        return new RuntimeInfo(image, ias, proc, run, fullRuntimeId);
    }

    /**
     * Find the pointer size for the runtime
     * 
     * @param run1
     *            The Java runtime
     * @param listener
     *            To indicate progress/errors
     * @return the pointer size in bits
     */
    private int getPointerSize(RuntimeInfo info, IProgressListener listener)
    {
        int pointerSize = 0;
        long maxAddress = 0;
        ImageAddressSpace ias = info.getImageAddressSpace();
        for (Iterator<?> it = ias.getImageSections(); it.hasNext();)
        {
            Object next1 = it.next();
            if (next1 instanceof CorruptData)
                continue;
            ImageSection sect = (ImageSection) next1;
            maxAddress = Math.max(maxAddress, sect.getBaseAddress().getAddress());
            maxAddress = Math.max(maxAddress, sect.getBaseAddress().getAddress() + sect.getSize() - 1);
        }
        ImageProcess proc = info.getImageProcess();
        JavaRuntime run = info.getJavaRuntime();
        // 31,32,64 bits - conversion done later
        pointerSize = proc.getPointerSize();

        /* Experimentally see what size pointers end up */
        long ptrBits = 0;
        long longBits = 0;
        try
        {
            ImagePointer ip = run.getJavaVM();
            for (int i = 0; i < 200; ++i)
            {
                ImagePointer pointer = ip.getPointerAt(i);
                // PHD can mistakenly return null
                if (pointer != null)
                    ptrBits |= pointer.getAddress();
                longBits |= ip.getLongAt(i);
            }
        }
        catch (CorruptDataException e)
        {}
        catch (MemoryAccessException e)
        {}
        if (longBits == ~0L)
        {
            // All bits set, so pointer could be any value
            addressSpacePointerSize = 0;
            while (ptrBits != 0)
            {
                ++addressSpacePointerSize;
                ptrBits >>>= 1;
            }
            if (addressSpacePointerSize != pointerSize)
            {
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                Messages.DTFJIndexBuilder_UsingProcessPointerSizeNotAddressSpacePointerSize,
                                pointerSize, ias.toString(), addressSpacePointerSize), null);
            }
        }
        if ((maxAddress & ~(~0L >>> (64 - pointerSize))) != 0)
        {
            listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                            Messages.DTFJIndexBuilder_HighestMemoryAddressFromAddressSpaceIsUnaccessibleFromPointers,
                            format(maxAddress), ias.toString(), pointerSize), null);
        }
        return pointerSize;
    }
    
    /**
     * Calculate a pointer size in bytes
     * @param pointerSize in bits (64, 32, 31)
     * @return
     */
    private int getPointerBytes(int pointerSize)
    {
        return (pointerSize + 7) / 8;
    }

    /**
     * @param obj
     * @param j
     * @param listener
     *            To indicate progress/errors
     */
    private void addRootForThreads(JavaObject obj, Iterator<?> j, IProgressListener listener)
    {
        for (; j.hasNext();)
        {
            Object next2 = j.next();
            if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingThreadsFromMonitors))
                continue;
            JavaThread jt2 = (JavaThread) next2;
            addRootForThread(obj, jt2, listener);
        }
    }

    private void addRootForThread(JavaObject obj, JavaThread jt2, IProgressListener listener)
    {
        long objAddress = obj.getID().getAddress();
        if (jt2 != null)
        {
            long thrd2 = getThreadAddress(jt2, null);
            if (thrd2 != 0)
            {
                int thrId = indexToAddress.reverse(thrd2);
                if (thrId >= 0)
                {
                    HashMapIntObject<List<XGCRootInfo>> thr = threadRoots.get(thrId);
                    if (thr != null)
                    {
                        addRoot(thr, objAddress, thrd2, GCRootInfo.Type.BUSY_MONITOR);
                        if (useThreadRefsNotRoots)
                            return;
                    }
                    else
                    {
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ProblemFindingRootInformation, format(thrd2),
                                        format(objAddress)), null);
                    }
                }
                else
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemFindingThread, format(thrd2),
                                    format(objAddress)), null);
                }
            }
            else
            {
                // Null thread object
            }
        }
        else
        {
            debugPrint("Null thread, so no thread specific root"); //$NON-NLS-1$
        }
        addRoot(gcRoot, objAddress, objAddress, GCRootInfo.Type.BUSY_MONITOR);
    }

    /**
     * Gets all the outbound references from an object via DTFJ, compares them to
     * the supplied refs,  optionally replaces them.
     * 
     * @param type
     * @param desc
     * @param aa
     * @param addrJavaLangClass
     *            Address of java.lang.Class
     * @param bootLoaderAddress
     *            The MAT view of the address of the boot loader
     * @param listener
     *            To indicate progress/errors
     * @throws CorruptDataException
     */
    private void checkRefs(Object type, String desc, ArrayLong aa, long addrJavaLangClass, long bootLoaderAddress,
                    IProgressListener listener) throws CorruptDataException
    {

        if (!haveDTFJRefs)
            return;

        // Performance optimization - don't find references two ways
        if (!useDTFJRefs && !debugInfo)
            return;

        RefStore<String> objset = debugInfo ? new RefMap<String>() : new RefSet<String>();

        Iterator<?> i2;
        boolean hasDTFJRefs = false;

        String name = ""; //$NON-NLS-1$
        long objAddr;
        if (type instanceof JavaClass)
        {
            JavaClass jc = (JavaClass) type;

            JavaClass clsOfCls;
            JavaObject clsObj;
            try
            {
                clsObj = jc.getObject();
            }
            catch (CorruptDataException e)
            {
                // This error will have already been logged
                clsObj = null;
            }
            if (clsObj != null)
            {
                clsOfCls = clsObj.getJavaClass();
            }
            else
            {
                // Sometime there is not an associated Java object
                // We'll use addrJavaLangClass later
            }

            name = getClassName(jc, listener);
            objAddr = getClassAddress(jc, listener);
            try
            {
                // Collect references as well from JavaObject representing the
                // class
                if (clsObj != null)
                {
                    // Must have a reference to java.lang.Class first in the
                    // list, normally obtained from the java.lang.Class Object
                    // objset.put(getClassAddress(clsOfCls, listener), "added
                    // java.lang.Class address"); // Doesn't
                    // reference class
                    i2 = clsObj.getReferences();
                    hasDTFJRefs |= i2.hasNext();
                    collectRefs(i2, objset, desc, name, objAddr, listener);
                }
                else
                {
                    // Must have a reference to java.lang.Class first in the
                    // list, so add one now
                    objset.put(addrJavaLangClass, "added java.lang.Class address"); //$NON-NLS-1$
                }
                i2 = jc.getReferences();
            }
            catch (LinkageError e)
            {
                // If not implemented, then ignore
                return;
            }
            catch (NullPointerException e)
            {
                // Null Pointer exception from array classes because
                // of null class loader
                if (msgNarrayRefsNPE-- > 0)
                    listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ExceptionGettingOutboundReferences, desc, name,
                                    format(objAddr)), e);
                return;
            }

            if (clsObj != null)
            {
                // Sov used to miss this
                if (false)
                    objset.put(clsObj.getID().getAddress(), "added JavaObject for JavaClass"); //$NON-NLS-1$
            }

            JavaClassLoader classLoader = getClassLoader(jc, listener);
            long loaderAddr = getLoaderAddress(classLoader, bootLoaderAddress);
            if (classLoader == null || classLoader.getObject() == null)
            {
                if (debugInfo) debugPrint("Null loader obj " + getClassName(jc, listener)); //$NON-NLS-1$
                // getReferences won't find this otherwise
                objset.put(loaderAddr, "added boot loader"); //$NON-NLS-1$
            }
            else
            {
                // Doesn't reference classloader
                if (false)
                    objset.put(loaderAddr, "added class loader"); //$NON-NLS-1$
            }

            JavaClass sup = getSuperclass(jc, listener);
            if (sup != null)
            {
                // Doesn't reference superclass
                if (false)
                    objset.put(getClassAddress(sup, listener), "added super class"); //$NON-NLS-1$
            }
            if (getExtraInfo && getExtraInfo2)
            {
                // Add method pseudo-classes as DTFJ won't find them
                for (Iterator<?> i = jc.getDeclaredMethods(); i.hasNext();)
                {
                    Object next = i.next();
                    if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredMethods, jc))
                        continue;
                    JavaMethod jm = (JavaMethod) next;
                    objset.put(getMethodAddress(jm, listener), "method"); //$NON-NLS-1$
                }
            }
        }
        else if (type instanceof JavaObject)
        {
            JavaObject jo = (JavaObject) type;
            objAddr = jo.getID().getAddress();
            JavaClass clsObj;
            try
            {
                clsObj = jo.getJavaClass();
                name = getClassName(clsObj, listener);
                if (clsObj.isArray())
                {
                    // Sov doesn't ref array class, instead it gives the element
                    // type.
                    objset.put(getClassAddress(clsObj, listener), "added array class address"); //$NON-NLS-1$
                }
                // Doesn't reference class
                if (false)
                    objset.put(getClassAddress(clsObj, listener), "added class address"); //$NON-NLS-1$
            }
            catch (CorruptDataException e)
            {
                // Class isn't available (e.g. corrupt heap), so add some
                // information based on what processHeap guessed
                int objId = indexToAddress.reverse(objAddr);
                int clsId = objectToClass.get(objId);
                ClassImpl cls = idToClass.get(clsId);
                if (cls != null)
                {
                    long classAddr = cls.getObjectAddress();
                    name = cls.getName();
                    objset.put(classAddr, "added dummy class address"); //$NON-NLS-1$
                }
            }
            // Classloader doesn't ref classes
            // superclass fields are missed
            // array objects return null refs
            try
            {
                i2 = jo.getReferences();
            }
            catch (LinkageError e)
            {
                // If not implemented, then ignore
                return;
            }
            catch (OutOfMemoryError e)
            {
                // OutOfMemoryError with large object array
                listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ErrorGettingOutboundReferences, desc, name, format(objAddr)),
                                e);
                return;
            }
        }
        else
        {
            // Null boot loader object
            return;
        }
        int objId = indexToAddress.reverse(objAddr);

		// If there are no DTFJ refs (e.g. javacore) then don't use DTFJ refs
        hasDTFJRefs |= i2.hasNext();
        collectRefs(i2, objset, desc, name, objAddr, listener);

        if (debugInfo)
        {
            // Test getReferences versus old way of getting references

            // debugPrint("Obj "+type+" "+aaset.size()+" "+objset.size());
            // for (IteratorLong il = aa.iterator(); il.hasNext(); ) {
            // debugPrint("A "+format(il.next()));
            // }

            // Test for missing references from getReferences()
            SetLong inBoth = new SetLong();
            boolean missingRefs = false;
            for (IteratorLong il = aa.iterator(); il.hasNext();)
            {
                long l = il.next();
                if (!objset.containsKey(l))
                {
                    missingRefs = true;
                    int newObjId = indexToAddress.reverse(l);
                    String clsInfo = objDesc(newObjId);
                    if (msgNgetRefsMissing-- > 0)
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_DTFJGetReferencesMissingID, newObjId, format(l),
                                        clsInfo, desc, name, objId, format(objAddr)), null);
                }
                else
                {
                    inBoth.add(l);
                }
            }
            if (false && missingRefs)
            {
                debugPrint("All DTFJ references from " + desc + " " + name); //$NON-NLS-1$ //$NON-NLS-2$
                for (Iterator<HashMapLongObject.Entry<String>> it = objset.entries(); it.hasNext(); )
                {
                    HashMapLongObject.Entry<String> ee = it.next();
                    debugPrint(format(ee.getKey()) + " " + ee.getValue()); //$NON-NLS-1$
                }
            }

            // Test for extra references from getReferences()
            for (Iterator<HashMapLongObject.Entry<String>> it = objset.entries(); it.hasNext(); )
            {
                HashMapLongObject.Entry<String> ee = it.next();
                Long l = ee.getKey();
                if (!inBoth.contains(l))
                {
                    int newObjId = indexToAddress.reverse(l);
                    String clsInfo = objDesc(newObjId);
                    // extra superclass references for objects
                    if (msgNgetRefsExtra-- > 0)
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_DTFJGetReferencesExtraID, newObjId, format(l), 
                                        ee.getValue(), clsInfo, desc, name, objId, format(objAddr)), null);
                }
            }
        }

        if (false && aa.size() > 200)
        {
            debugPrint("aa1 " + aa.size()); //$NON-NLS-1$
            for (IteratorLong il = aa.iterator(); il.hasNext();)
            {
                debugPrint("A " + format(il.next())); //$NON-NLS-1$
            }
        }
        if (useDTFJRefs)
        {
            if (objset.size() == 0 || !hasDTFJRefs)
            {
                // Sov has problems with objects of type [B, [C etc.
                if (!aa.isEmpty() && msgNgetRefsAllMissing-- > 0)
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_DTFJGetReferencesMissingAllReferences, name, objId,
                                    format(objAddr)), null);
            }
            else
            {
                aa.clear();
                for (IteratorLong it = objset.keys(); it.hasNext(); )
                {
                    // Don't bother removing the addresses which can't be converted to an index
                    aa.add(it.next());
                }
            }
        }
        if (false && aa.size() > 200)
        {
            debugPrint("aa1 " + aa.size()); //$NON-NLS-1$
            for (IteratorLong il = aa.iterator(); il.hasNext();)
            {
                debugPrint("A " + format(il.next())); //$NON-NLS-1$
            }
        }
    }

    /**
     * Describe the object at the given index
     * 
     * @param newObjId
     * @return
     */
    private String objDesc(int newObjId)
    {
        String clsInfo;
        if (newObjId >= 0)
        {
            ClassImpl classInfo = idToClass.get(newObjId);
            if (classInfo != null)
            {
                clsInfo = MessageFormat.format(Messages.DTFJIndexBuilder_ObjDescClass, classInfo.getName());
            }
            else
            {
                int clsId = objectToClass.get(newObjId);
                if (clsId >= 0 && clsId < indexToAddress.size())
                {
                    long clsAddr = indexToAddress.get(clsId);
                    classInfo = idToClass.get(clsId);
                    // If objectToClass has not yet been filled in for objects
                    // then this could be null
                    if (classInfo != null)
                    {
                        clsInfo = MessageFormat.format(Messages.DTFJIndexBuilder_ObjDescObjType, classInfo.getName(),
                                        format(clsAddr));
                    }
                    else
                    {
                        clsInfo = MessageFormat
                                        .format(Messages.DTFJIndexBuilder_ObjDescObjTypeAddress, format(clsAddr));
                    }
                }
                else
                {
                    clsInfo = ""; //$NON-NLS-1$
                }
            }
        }
        else
        {
            clsInfo = ""; //$NON-NLS-1$
        }
        return clsInfo;
    }

    /**
     * Collect all the outbound references from a JavaClass/JavaObject
     * 
     * @param i2
     *            Iterator to walk over the references
     * @param objset
     *            Where to put the references
     * @param desc
     *            Type of base object (Class, Object, Class loader etc.)
     * @param name
     *            Name of object
     * @param objAddr
     *            Its address
     * @param listener
     *            For displaying messages
     * @throws CorruptDataException
     */
    private void collectRefs(Iterator<?> i2, RefStore<String> objset, String desc, String name, long objAddr,
                    IProgressListener listener)
    {
        // Check the refs
        // Javacore reader gives null rather than an empty iterator
        if (i2 == null) { return; }
        for (; i2.hasNext();)
        {
            Object next3 = i2.next();
            if (isCorruptData(next3, listener, Messages.DTFJIndexBuilder_CorruptDataReadingReferences, name, objAddr))
                continue;
            JavaReference jr = (JavaReference) next3;
            long addr;
            try
            {
                Object target = jr.getTarget();
                if (jr.isClassReference())
                {
                    addr = getClassAddress((JavaClass) target, listener);
                }
                else if (jr.isObjectReference())
                {
                    addr = ((JavaObject) target).getID().getAddress();
                }
                else
                {
                    // neither of isClassReference and
                    // isObjectReference return true
                    listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_UnexpectedReferenceType, jr.getDescription(), desc, name,
                                    format(objAddr)), null);
                    if (target == null)
                    {
                        // array objects return null refs
                        // null reference for classes without super
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_UnexpectedNullReferenceTarget, jr.getDescription(),
                                        desc, name, format(objAddr)), null);
                        continue;
                    }
                    else if (target instanceof JavaClass)
                    {
                        addr = getClassAddress((JavaClass) target, listener);
                    }
                    else if (target instanceof JavaObject)
                    {
                        addr = ((JavaObject) target).getID().getAddress();
                    }
                    else
                    {
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_UnexpectedReferenceTargetType, target, jr
                                                        .getDescription(), desc, name, format(objAddr)), null);
                        continue;
                    }
                }
                // Skip class references to itself via the class object (as
                // these are considered all part of the one class)
                if (!(addr == objAddr && (jr.getReferenceType() == JavaReference.REFERENCE_CLASS_OBJECT || jr
                                .getReferenceType() == JavaReference.REFERENCE_ASSOCIATED_CLASS)))
                {
                    objset.put(addr, jr.getDescription());
                }
            }
            catch (DataUnavailable e)
            {
                if (msgNgetRefsUnavailable-- > 0)
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_UnableToGetOutboundReference, jr.getDescription(), desc,
                                    name, format(objAddr)), e);
            }
            catch (CorruptDataException e)
            {
                if (msgNgetRefsCorrupt-- > 0)
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_UnableToGetOutboundReference, jr.getDescription(), desc,
                                    name, format(objAddr)), e);
            }
        }
    }

    /**
     * Add all the Java locals etc for the thread as outbound references
     * 
     * @param obj
     * @param aa
     */
    private void addThreadRefs(int obj, ArrayLong aa)
    {
        if (useThreadRefsNotRoots)
        {
            // The thread roots must be set up by this point
            HashMapIntObject<List<XGCRootInfo>> hm = threadRoots.get(obj);
            if (hm != null)
            {
                for (IteratorInt i = hm.keys(); i.hasNext();)
                {
                    int objId = i.next();
                    aa.add(indexToAddress.get(objId));
                }
            }
        }
    }

    /**
     * Remember objects which have been referred to Use to make sure every
     * object will be reachable
     * 
     * @param refd referenced objects
     * @param objId source object ID
     * @param ref list of outbound refs
     */
    private void addRefs(BitField refd, int objId, ArrayLong ref)
    {
        for (IteratorLong il = ref.iterator(); il.hasNext();)
        {
            long ad = il.next();
            int id = indexToAddress.reverse(ad);
            // debugPrint("object id "+objId+" ref to "+id+" 0x"+format(ad));
            if (id >= 0 && objId != id)
            {
                refd.set(id);
            }
        }
    }

    /**
     * @param type
     *            The JavaClass
     * @param listener
     *            For logging
     * @return the address of the Java Object representing this class
     */
    private long getClassAddress(final JavaClass type, IProgressListener listener)
    {
        JavaObject clsObject;
        Exception e1 = null;
        try
        {
            clsObject = type.getObject();
        }
        catch (CorruptDataException e)
        {
            // Ignore the error and proceed as though it was not available e.g.
            // javacore
            clsObject = null;
            e1 = e;
        }
        catch (IllegalArgumentException e)
        {
            // IllegalArgumentException if object address not found
            clsObject = null;
            e1 = e;
        }
        if (clsObject == null)
        {
            // use the class address if the object address is not available
            ImagePointer ip = type.getID();
            if (ip != null)
            {
                return ip.getAddress();
            }
            else
            {
                // This may be is a class which DTFJ built
                Long addr = dummyClassAddress.get(type);
                if (addr != null)
                {
                    // Return the address we have already used
                    return addr;
                }
                else
                {
                    // Build a unique dummy address
                    long clsAddr = nextClassAddress;
                    dummyClassAddress.put(type, clsAddr);
                    nextClassAddress += 8;
                    String clsName = getClassName(type, listener);
                    listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ClassHasNoAddress, clsName, format(clsAddr)), e1);
                    return clsAddr;
                }
            }
        }
        else
        {
            return clsObject.getID().getAddress();
        }
    }

    /**
     * If the object is the boot loader, modify its address to something
     * suitable for MAT Originally used when it was thought that MAT had to have
     * the boot loader at location 0 Could be used to change a zero boot loader
     * address to a made-up value.
     * 
     * @param bootLoaderAddress
     * @param objAddress
     * @return
     */
    private long fixBootLoaderAddress(long bootLoaderAddress, long objAddress)
    {
        // Fix-up for MAT which presumes the boot loader is at address 0
        // if (objAddress == bootLoaderAddress) objAddress = 0x0L;
        // This doesn't seem to be critical
        return objAddress;
    }

    /**
     * Search a frame for any pointers to heap objects Throws MemoryAccessError
     * so the caller can decide if that is possible or bad
     * 
     * @param pointerSize
     *            in bits
     * @param threadAddress
     * @param thr
     * @param ip
     * @param searchSize
     *            How many bytes to search
     * @param rootType
     *            type of GC root e.g. native root, Java frame root etc.
     * @param gc
     *            The map of roots - from object id to description of list of
     *            roots of that object
     * @param searchedAddresses
     *            Add any locations containing valid objects to this set of
     *            searched locations
     * @param excludedAddresses
     *            Don't use any locations which have already been used
     * @throws CorruptDataException
     *             , MemoryAccessException
     */
    private void searchFrame(int pointerSize, long threadAddress,
                    HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thrs, ImagePointer ip, long searchSize,
                    int rootType, HashMapIntObject<List<XGCRootInfo>> gc, Set<ImagePointer> searchedAddresses,
                    Set<ImagePointer> excludedAddresses) throws CorruptDataException, MemoryAccessException
    {
        debugPrint("searching thread " + format(threadAddress) + " " + format(ip.getAddress()) + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + format(searchSize) + " " + rootType); //$NON-NLS-1$
        long frameAddress;
        HashMapIntObject<List<XGCRootInfo>> thr = thrs.get(indexToAddress.reverse(threadAddress));
        if (getExtraInfo)
        {
            // See if the frame is an object
            frameAddress = ip.getAddress();
            int frameId = indexToAddress.reverse(frameAddress);
            if (frameAddress != 0 && frameId >= 0)
            {
                // Mark the frame
                if (thr != null)
                {
                    // Add it to the thread roots
                    addRoot(thr, frameAddress, threadAddress, rootType);
                    // Create a new locals list for the frame
                    thr = new HashMapIntObject<List<XGCRootInfo>>();
                    thrs.put(frameId, thr);
                    // Add it to the global GC roots
                    if (!useThreadRefsNotRoots)
                        addRoot(gc, frameAddress, threadAddress, rootType);
                }
                else
                {
                    // No thread information so make a global root
                    addRoot(gc, frameAddress, threadAddress, rootType);
                }
                long size = Math.abs(searchSize);
                setFrameSize(frameId, size);
            }
            else
            {
                frameAddress = threadAddress;
            }
        }
        else
        {
            frameAddress = threadAddress;
        }
        // Read items off the frame
        final int pointerAdjust = searchSize >= 0 ? getPointerBytes(pointerSize) : -getPointerBytes(pointerSize);
        for (long j = 0; Math.abs(j) < Math.abs(searchSize); j += pointerAdjust)
        {
            ImagePointer location = ip.add(j);
            long addr = getPointerAddressAt(location, 0, pointerSize);
            int id = indexToAddress.reverse(addr);
            if (addr != 0 && id >= 0)
            {
                // Found object
                if (excludedAddresses == null || !excludedAddresses.contains(location))
                {
                    if (searchedAddresses != null)
                        searchedAddresses.add(location);
                    if (thr != null)
                    {
                        // Add it to the thread roots
                        addRoot(thr, addr, frameAddress, rootType);
                        // Add it to the global GC roots
                        if (!useThreadRefsNotRoots)
                            addRoot(gc, addr, frameAddress, rootType);
                    }
                    else
                    {
                        // No thread information so make a global root
                        addRoot(gc, addr, frameAddress, rootType);
                    }
                }
            }
        }
    }

    /**
     * Reads a pointer value of the given size
     * @param ip the base address
     * @param j the offset from the base address in bytes
     * @param pointerSize the size in bits (64,32,31)
     * @return The address as a long
     * @throws MemoryAccessException
     * @throws CorruptDataException
     */
    private long getPointerAddressAt(ImagePointer ip, long j, int pointerSize) throws MemoryAccessException, CorruptDataException
    {
        long addr;
        if (addressSpacePointerSize == pointerSize)
        {
            // Rely on address space to do the right thing
            // getPointerAt indexes by bytes, not pointers size
            ImagePointer i2 = ip.getPointerAt(j);
            // PHD can mistakenly return null
            addr = i2 != null ? i2.getAddress() : 0;
        }
        else
        {
            switch (pointerSize)
            {
                case 64:
                    addr = ip.getLongAt(j);
                    break;
                case 32:
                case 31:
                    addr = ip.getIntAt(j) & (1L << pointerSize) - 1;
                    break;
                default:
                    ImagePointer i2 = ip.getPointerAt(j);
                    addr = i2.getAddress();
                    break;
            }
        }
        return addr;
    }

    /**
     * @param j2
     * @param ci
     * @param jlc
     * @param pointerSize
     *            size of pointer in bits - used for correcting object sizes
     * @param listener
     *            for error reporting
     */
    private void genClass2(JavaClass j2, ClassImpl ci, ClassImpl jlc, int pointerSize, IProgressListener listener)
    {
        ci.setClassInstance(jlc);
        long size = 0;
        try
        {
            JavaObject object = j2.getObject();
            if (object != null)
            {
                size = getObjectSize(object, pointerSize);
                if (jlc.getHeapSizePerInstance() < 0)
                    jlc.setHeapSizePerInstance(size);
            }
        }
        catch (IllegalArgumentException e)
        {
            // problems with getObject when the class is corrupt?
            listener.sendUserMessage(Severity.WARNING, Messages.DTFJIndexBuilder_ProblemGettingSizeOfJavaLangClass, e);
        }
        catch (CorruptDataException e)
        {
            // Javacore causes too many of these errors
            // listener.sendUserMessage(Severity.WARNING, "Problem setting size
            // of instance of java.lang.Class", e);
        }
        // TODO should we use segments to get the RAM/ROM class size?
        size += classSize(j2, listener);
        ci.setUsedHeapSize(size);
        if (debugInfo)
        {
            // For calculating purge sizes
            objectToSize2.set(ci.getObjectId(), size);
        }
        jlc.addInstance(size);
        debugPrint("build class " + ci.getName() + " at " + ci.getObjectId() + " address " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + format(ci.getObjectAddress()) + " loader " + ci.getClassLoaderId() + " super " //$NON-NLS-1$ //$NON-NLS-2$
                        + ci.getSuperClassId() + " size " + ci.getUsedHeapSize()); //$NON-NLS-1$
    }

    private long classSize(JavaClass jc, IProgressListener listener)
    {
        long size = 0;
        try
        {
            // Try to accumulate the size of the actual class object
            JavaObject jo = jc.getObject();
            if (jo != null)
            {
                size += jo.getSize();
            }
        }
        catch (CorruptDataException e)
        {}
        for (Iterator<?> i = jc.getDeclaredMethods(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredMethods, jc))
                continue;
            JavaMethod jm = (JavaMethod) next;
            if (!(getExtraInfo && getExtraInfo2))
            {
                size += getMethodSize(jc, jm, listener);
            }
        }
        return size;
    }

    private long getMethodSize(JavaClass jc, JavaMethod jm, IProgressListener listener)
    {
        long size = 0;
        for (Iterator<?> j = jm.getBytecodeSections(); j.hasNext();)
        {
            Object next2 = j.next();
            if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingBytecodeSections, jc, jm))
                continue;
            ImageSection is = (ImageSection) next2;
            final int bigSegment = 0x10000;
            long sizeSeg = checkSegmentSize(jc, jm, is, bigSegment,
                            Messages.DTFJIndexBuilder_UnexpectedBytecodeSectionSize, listener);
            size += sizeSeg;
            // debugPrint("Adding bytecode code section at
            // "+format(is.getBaseAddress().getAddress())+" size "+size);
        }
        for (Iterator<?> j = jm.getCompiledSections(); j.hasNext();)
        {
            Object next2 = j.next();
            // 1.4.2 CorruptData
            if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingCompiledSections, jc, jm))
                continue;
            ImageSection is = (ImageSection) next2;
            final int bigSegment = 0x60000;
            long sizeSeg = checkSegmentSize(jc, jm, is, bigSegment,
                            Messages.DTFJIndexBuilder_UnexpectedCompiledCodeSectionSize, listener);
            size += sizeSeg;
        }
        return size;
    }

    /**
     * Avoid problems with bad compiled code segment sizes. Also Sov has some
     * negative sizes for bytecode sections.
     * 
     * @param jc
     * @param jm
     * @param is
     * @param bigSegment
     * @param message
     *            segment base {0} size {1} size limit {2} modifiers {3} class
     *            name {4} method name {5} sig {6}
     * @param listener
     * @return
     */
    private long checkSegmentSize(JavaClass jc, JavaMethod jm, ImageSection is, final int bigSegment, String message,
                    IProgressListener listener)
    {
        long sizeSeg = is.getSize();
        if (sizeSeg < 0 || sizeSeg >= bigSegment)
        {
            String clsName;
            String methName;
            String methSig;
            try
            {
                clsName = jc != null ? jc.getName() : ""; //$NON-NLS-1$
            }
            catch (CorruptDataException e)
            {
                clsName = e.toString();
            }
            try
            {
                methName = jm.getName();
            }
            catch (CorruptDataException e)
            {
                methName = e.toString();
            }
            try
            {
                methSig = jm.getSignature();
            }
            catch (CorruptDataException e)
            {
                methSig = e.toString();
            }
            if (msgNbigSegs-- > 0)
            {
                String mods = getModifiers(jm, listener);
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(message, format(is.getBaseAddress()
                                .getAddress()), sizeSeg, bigSegment, mods, clsName, methName, methSig), null);
            }
            sizeSeg = 0;
        }
        return sizeSeg;
    }

    /**
     * Is a class finalizable? Is there a finalize method other than from
     * java.lang.Object?
     * 
     * @param c
     * @param listener
     * @return Class address if the objects of this class are finalizable
     */
    private long isFinalizable(JavaClass c, IProgressListener listener)
    {
        long ca = 0;
        String cn = getClassName(c, listener);
        ca = getClassAddress(c, listener);
        while (getSuperclass(c, listener) != null)
        {
            String cn1 = getClassName(c, listener);
            long ca1 = getClassAddress(c, listener);
            for (Iterator<?> it = c.getDeclaredMethods(); it.hasNext();)
            {
                Object next = it.next();
                if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredMethods, c))
                    continue;
                JavaMethod m = (JavaMethod) next;
                try
                {
                    if (m.getName().equals("finalize")) //$NON-NLS-1$
                    {
                        try
                        {
                            if (m.getSignature().equals("()V")) //$NON-NLS-1$
                            {
                                // Correct signature
                                return ca;
                            }
                        }
                        catch (CorruptDataException e)
                        {
                            // Unknown signature, so presume it is the
                            // finalize() method.
                            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                            Messages.DTFJIndexBuilder_ProblemDetirminingFinalizeMethodSig, cn1,
                                            format(ca1)), e);
                            return ca;
                        }
                    }
                }
                catch (CorruptDataException e)
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemDetirminingFinalizeMethod, cn1, format(ca1)), e);
                }
            }
            c = getSuperclass(c, listener);
        }
        return 0L;
    }

    private ArrayLong exploreClass(IIndexReader.IOne2LongIndex m2, long bootLoaderAddress, HashMapIntObject<ClassImpl> hm,
                    JavaClass j2, IProgressListener listener)
    {
        String clsName = null;
        long claddr = getClassAddress(j2, listener);
        int objId = m2.reverse(claddr);
        ClassImpl ci = hm.get(objId);
        if (debugInfo)
            debugPrint("Class " + getClassName(j2, listener) + " " + format(claddr) + " " + objId + " " + ci); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        if (ci == null)
        {
            // Perhaps the class was corrupt and never built
            return null;
        }
        int clsId = ci.getClassId();
        clsName = ci.getName();
        debugPrint("found class object " + objId + " type " + clsName + " at " + format(ci.getObjectAddress()) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + " clsId " + clsId); //$NON-NLS-1$
        ArrayLong ref = ci.getReferences();
        // Constant pool references have already been set up as pseudo
        // fields
        if (false)
            for (Iterator<?> i2 = j2.getConstantPoolReferences(); i2.hasNext();)
            {
                Object next = i2.next();
                if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingConstantPool, j2))
                    continue;
                if (next instanceof JavaObject)
                {
                    JavaObject jo = (JavaObject) next;
                    long address = jo.getID().getAddress();
                    ref.add(address);
                }
                else if (next instanceof JavaClass)
                {
                    JavaClass jc = (JavaClass) next;
                    long address = getClassAddress(jc, listener);
                    ref.add(address);
                }
            }
        // Superclass address are now added by getReferences()
        // long supAddr = ci.getSuperClassAddress();
        // if (supAddr != 0) ref.add(ci.getSuperClassAddress());
        if (getExtraInfo && getExtraInfo2)
        {
            // Add references to methods
            for (Iterator<?> i = j2.getDeclaredMethods(); i.hasNext();)
            {
                Object next = i.next();
                if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredMethods, j2))
                    continue;
                JavaMethod jm = (JavaMethod) next;
                ref.add(getMethodAddress(jm, listener));
            }
        }
        for (IteratorLong il = ref.iterator(); il.hasNext();)
        {
            long ad = il.next();
            if (false)
                debugPrint("ref to " + m2.reverse(ad) + " " + format(ad) + " for " + objId); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        return ref;
    }

    /**
     * @param m2
     * @param bootLoaderAddress
     * @param hm
     * @param jo
     * @param type
     * @param aa
     * @param arrayLen
     * @param listener
     *            To indicate progress/errors
     * @throws CorruptDataException
     */
    private void exploreArray(IIndexReader.IOne2LongIndex m2, long bootLoaderAddress, HashMapIntObject<ClassImpl> hm,
                    JavaObject jo, JavaClass type, ArrayLong aa, int arrayLen, IProgressListener listener)
                    throws CorruptDataException
    {
        // Performance optimization - don't find references two ways
        if (useDTFJRefs && !debugInfo)
            return;
        boolean primitive = isPrimitiveArray(type);
        if (!primitive)
        {
            // Do large arrays in pieces to try to avoid OutOfMemoryErrors
            int arrayStep = ARRAY_PIECE_SIZE;
            for (int arrayOffset = 0; arrayOffset < arrayLen; arrayOffset += arrayStep)
            {
                arrayStep = Math.min(arrayStep, arrayLen - arrayOffset);
                JavaObject refs[] = new JavaObject[arrayStep];
                if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }
                try
                {
                    // - arraycopy doesn't check indices
                    // IllegalArgumentException from
                    // JavaObject.arraycopy
                    try
                    {
                        debugPrint("Array copy " + arrayOffset + " " + arrayLen + " " + arrayStep); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        jo.arraycopy(arrayOffset, refs, 0, arrayStep);
                    }
                    catch (IllegalArgumentException e)
                    {
                        String typeName;
                        try
                        {
                            typeName = type.getName();
                        }
                        catch (CorruptDataException e1)
                        {
                            typeName = e1.toString();
                        }
                        listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ProblemReadingArray, typeName, arrayLen, arrayOffset,
                                        arrayStep, format(jo.getID().getAddress())), e);
                    }
                    int idx = arrayOffset;
                    for (JavaObject jao : refs)
                    {
                        if (jao != null)
                        {
                            // Add the non-null refs
                            long elementObjAddress = jao.getID().getAddress();
                            elementObjAddress = fixBootLoaderAddress(bootLoaderAddress, elementObjAddress);
                            int elementRef = m2.reverse(elementObjAddress);
                            if (elementRef < 0)
                            {
                                if (msgNinvalidArray-- > 0)
                                {
                                    String name;
                                    Exception e1 = null;
                                    if (debugInfo)
                                    {
                                        // Getting the class can be expensive for an unknown object,
                                        // so only do in debug mode to avoid rereading the dump
                                        try
                                        {
                                            JavaClass javaClass = jao.getJavaClass();
                                            name = javaClass != null ? javaClass.getName() : ""; //$NON-NLS-1$
                                        }
                                        catch (CorruptDataException e)
                                        {
                                            name = e.toString();
                                            e1 = e;
                                        }
                                    }
                                    else
                                    {
                                        name = "?"; //$NON-NLS-1$
                                    }
                                    String typeName;
                                    try
                                    {
                                        typeName = type.getName();
                                    }
                                    catch (CorruptDataException e)
                                    {
                                        typeName = e.toString();
                                        e1 = e;
                                    }
                                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                    Messages.DTFJIndexBuilder_InvalidArrayElement,
                                                    format(elementObjAddress), name, idx, typeName, arrayLen, format(jo
                                                                    .getID().getAddress())), e1);
                                }
                            }
                            else
                            {
                                if (hm.get(elementRef) != null)
                                {
                                    if (verbose)
                                        debugPrint("Found class ref field " + elementRef + " from array " //$NON-NLS-1$ //$NON-NLS-2$
                                                        + m2.reverse(jo.getID().getAddress()));
                                    aa.add(elementObjAddress);
                                }
                                else
                                {
                                    if (verbose)
                                        debugPrint("Found obj ref field " + elementRef + " from array " //$NON-NLS-1$ //$NON-NLS-2$
                                                        + m2.reverse(jo.getID().getAddress()));
                                    aa.add(elementObjAddress);
                                }
                            }
                        }
                        ++idx;
                    }
                }
                catch (CorruptDataException e)
                {
                    String typeName;
                    try
                    {
                        typeName = type.getName();
                    }
                    catch (CorruptDataException e1)
                    {
                        typeName = e1.toString();
                    }
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemReadingArray, typeName, arrayLen, arrayOffset,
                                    arrayStep, format(jo.getID().getAddress())), e);
                }
                catch (MemoryAccessException e)
                {
                    String typeName;
                    try
                    {
                        typeName = type.getName();
                    }
                    catch (CorruptDataException e1)
                    {
                        typeName = e1.toString();
                    }
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemReadingArray, typeName, arrayLen, arrayOffset,
                                    arrayStep, format(jo.getID().getAddress())), e);
                }
            }
        }
    }

    /**
     * Tests whether an array is a primitive array (i.e. the elements are not
     * objects) Allows for a bug in DTFJ
     * 
     * @param type
     * @return true if the array elements are primitives
     * @throws CorruptDataException
     */
    static boolean isPrimitiveArray(JavaClass type) throws CorruptDataException
    {
        // CMVC 136032 - getComponentType strips all of arrays instead of one
        try 
        {
            String name = type.getName();
            if (name.startsWith("[[")) //$NON-NLS-1$
                return false;
            return "[B".equals(name) || //$NON-NLS-1$
            "[S".equals(name) || //$NON-NLS-1$
            "[I".equals(name) || //$NON-NLS-1$
            "[J".equals(name) || //$NON-NLS-1$
            "[Z".equals(name) || //$NON-NLS-1$
            "[C".equals(name) || //$NON-NLS-1$
            "[F".equals(name) || //$NON-NLS-1$
            "[D".equals(name); //$NON-NLS-1$
        } 
        catch (CorruptDataException e)
        {
            // Ignore
        }
        try
        {
            JavaClass elemClass = type.getComponentType();
            if (elemClass.isArray()) return false;
            boolean primitive = isPrimitiveName(elemClass.getName());
            return primitive;
        }
        catch (CorruptDataException e)
        {
            return false;
        }
    }

    /**
     * @param elemClass
     * @return true if the class is a primitive class (int, byte, float etc.)
     * @throws CorruptDataException
     */
    private boolean isPrimitive(JavaClass elemClass) throws CorruptDataException
    {
        boolean primitive = getSuperclass(elemClass, null) == null && !elemClass.getName().equals("java/lang/Object") //$NON-NLS-1$
                        && !Modifier.isInterface(elemClass.getModifiers());
        return primitive;
    }

    /**
     * @param m2
     * @param bootLoaderAddress
     * @param hm
     * @param jo
     * @param type
     * @param aa
     * @param verbose
     *            print out extra information
     * @param listener
     *            To indicate progress/errors
     * @throws CorruptDataException
     */
    private void exploreObject(IIndexReader.IOne2LongIndex m2, long bootLoaderAddress, HashMapIntObject<ClassImpl> hm,
                    JavaObject jo, JavaClass type, ArrayLong aa, boolean verbose, IProgressListener listener)
    {
        // Performance optimization - don't find references two ways
        if (useDTFJRefs && !debugInfo)
            return;
        String typeName = getClassName(type, listener);
        if (verbose)
        {
            debugPrint("Exploring " + type + " at " + jo.getID().getAddress()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        for (JavaClass jc = type; jc != null; jc = getSuperclass(jc, listener))
        {
            String clsName = getClassName(jc, listener);
            for (Iterator<?> ii = jc.getDeclaredFields(); ii.hasNext();)
            {
                Object next3 = ii.next();
                if (isCorruptData(next3, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredFields, jc))
                    continue;
                JavaField jf = (JavaField) next3;
                String fieldName;
                try
                {
                    fieldName = jf.getName();
                }
                catch (CorruptDataException e)
                {
                    fieldName = "?"; //$NON-NLS-1$
                }
                String sig;
                try
                {
                    sig = jf.getSignature();
                }
                catch (CorruptDataException e)
                {
                    // Play safe & make field look like an object field
                    sig = "L?"; //$NON-NLS-1$
                }
                try
                {
                    if (!Modifier.isStatic(jf.getModifiers()))
                    {
                        if (sig.startsWith("[") || sig.startsWith("L")) //$NON-NLS-1$ //$NON-NLS-2$
                        {
                            try
                            {
                                Object obj;
                                try
                                {
                                    obj = jf.get(jo);
                                }
                                catch (IllegalArgumentException e)
                                {
                                    // - IllegalArgumentException
                                    // instead of CorruptDataException or a
                                    // partial JavaObject
                                    obj = null;
                                    fieldName = jf.getName();
                                    listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                                    Messages.DTFJIndexBuilder_ProblemReadingObjectFromField, clsName,
                                                    fieldName, sig, typeName, format(jo.getID().getAddress())), e);
                                }
                                if (obj instanceof JavaObject)
                                {
                                    JavaObject jo2 = (JavaObject) obj;
                                    long fieldObjAddress = jo2.getID().getAddress();
                                    fieldObjAddress = fixBootLoaderAddress(bootLoaderAddress, fieldObjAddress);
                                    int fieldRef = m2.reverse(fieldObjAddress);
                                    if (fieldRef < 0)
                                    {
                                        if (msgNinvalidObj-- > 0)
                                        {
                                            String name;
                                            Exception e1 = null;
                                            try
                                            {
                                                JavaClass javaClass = jo2.getJavaClass();
                                                name = javaClass != null ? javaClass.getName() : ""; //$NON-NLS-1$
                                            }
                                            catch (CorruptDataException e)
                                            {
                                                e1 = e;
                                                name = e.toString();
                                            }
                                            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                            Messages.DTFJIndexBuilder_InvalidObjectFieldReference,
                                                            format(fieldObjAddress), name, clsName, fieldName, sig,
                                                            typeName, format(jo.getID().getAddress())), e1);
                                        }
                                    }
                                    else
                                    {
                                        // Do unexpected duplicate fields
                                        // occur?
                                        // for (IteratorLong il =
                                        // aa.iterator();
                                        // il.hasNext(); ) {
                                        // if (il.next() == fieldObjAddress)
                                        // debugPrint("duplicate field value
                                        // "+format(fieldObjAddress)+" from
                                        // "+format(jo.getID().getAddress())+"
                                        // "+m2.reverse(jo.getID().getAddress())+"
                                        // "+jo.getJavaClass().getName()+"="+jc.getName()+"."+jf.getName()+":"+jf.getSignature());
                                        // }
                                        if (verbose)
                                        {
                                            if (hm.get(fieldRef) != null)
                                            {
                                                debugPrint("Found class ref field " + fieldRef + " from " //$NON-NLS-1$ //$NON-NLS-2$
                                                                + m2.reverse(jo.getID().getAddress()));
                                            }
                                            else
                                            {
                                                debugPrint("Found obj ref field " + fieldRef + " from " //$NON-NLS-1$ //$NON-NLS-2$
                                                                + m2.reverse(jo.getID().getAddress()));
                                            }
                                        }
                                        aa.add(fieldObjAddress);
                                    }
                                }
                            }
                            catch (CorruptDataException e)
                            {
                                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_ProblemReadingObjectFromField, clsName,
                                                fieldName, sig, typeName, format(jo.getID().getAddress())), e);
                            }
                            catch (MemoryAccessException e)
                            {
                                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_ProblemReadingObjectFromField, clsName,
                                                fieldName, sig, typeName, format(jo.getID().getAddress())), e);
                            }
                        }
                        else
                        {
                            // primitive field
                        }
                    }
                }
                catch (CorruptDataException e)
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_ProblemReadingObjectFromField, clsName, fieldName, sig,
                                    typeName, format(jo.getID().getAddress())), e);
                }
            }
        }
    }

    /**
     * @param gc
     * @param ci
     */
    private void addRoot(HashMapIntObject<List<XGCRootInfo>> gc, long obj, long ctx, int type)
    {
        XGCRootInfo rri = new XGCRootInfo(obj, ctx, newRootType(type));
        rri.setContextId(indexToAddress.reverse(rri.getContextAddress()));
        rri.setObjectId(indexToAddress.reverse(rri.getObjectAddress()));
        int objectId = rri.getObjectId();
        List<XGCRootInfo> rootsForID = gc.get(objectId);
        if (rootsForID == null)
        {
            rootsForID = new ArrayList<XGCRootInfo>(1);
            gc.put(objectId, rootsForID);
        }
        rootsForID.add(rri);
        // debugPrint("Root "+format(obj));
        int clsId = objectToClass.get(objectId);
        ClassImpl cls = idToClass.get(clsId);
        // debugPrint("objid "+objectId+" clsId "+clsId+" "+cls);
        String clsName = cls != null ? cls.getName() : ""; //$NON-NLS-1$
        String desc = "" + format(obj) + " " + objectId + " ctx " + format(ctx) + " " + rri.getContextId() + " type:" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                        + clsName;
        // 32 busy monitor
        // 64 java local
        // 256 thread obj
        debugPrint("Root " + type + " " + desc); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * First stage of building a class object Get the right class loader, the
     * superclass, the fields and the constant pool
     * 
     * @param j2
     * @param hm
     * @param bootLoaderAddress
     * @param superAddress
     *            If non-zero override superclass address with this.
     * @param listener
     *            To indicate progress/errors
     * @return the new class
     */
    private ClassImpl genClass(JavaClass j2, HashMapIntObject<ClassImpl> hm, long bootLoaderAddress, long sup,
                    IProgressListener listener)
    {
        long claddr = getClassAddress(j2, listener);
        String name = getClassName(j2, listener);

        long loader;
        try
        {
            // Set up class loaders
            JavaClassLoader load = getClassLoader(j2, listener);
            if (load == null)
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_UnableToFindClassLoader, name, format(claddr)), null);
            }
            loader = getLoaderAddress(load, bootLoaderAddress);
        }
        catch (CorruptDataException e)
        {
            // Unable to find class loader
            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_UnableToFindClassLoader, name, format(claddr)), e);
            loader = fixBootLoaderAddress(bootLoaderAddress, bootLoaderAddress);
        }

        if (sup == 0)
        {
            JavaClass superClass = getSuperclass(j2, listener);
            sup = superClass != null ? getClassAddress(superClass, listener) : 0L;
        }
        int superId;
        if (sup != 0)
        {
            superId = indexToAddress.reverse(sup);
            if (superId < 0)
            {
                // We have a problem at this point - the class has a real
                // superclass address, but a bad id.
                // If the address is non-zero ClassImpl will use the id,
                // which can cause exceptions inside of MAT
                // so clear the address.
                listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                Messages.DTFJIndexBuilder_SuperclassNotFound, format(sup), superId, format(claddr),
                                indexToAddress.reverse(claddr), name), null);
                sup = 0;
            }
        }
        else
        {
            superId = -1;
        }

        ArrayList<FieldDescriptor> al = new ArrayList<FieldDescriptor>();
        ArrayList<Field> al2 = new ArrayList<Field>();

        // Superclass is added by ClassImpl as a pseudo static field

        // We don't need to deal with superclass static fields as these are
        // maintained by the superclass
        for (Iterator<?> f1 = j2.getDeclaredFields(); f1.hasNext();)
        {
            Object next = f1.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredFields, j2))
                continue;
            JavaField jf = (JavaField) next;
            String fieldName = "?"; //$NON-NLS-1$
            String fieldSignature = "?"; //$NON-NLS-1$
            try
            {
                fieldName = jf.getName();
                try
                {
                    fieldSignature = jf.getSignature();
                }
                catch (CorruptDataException e)
                {}
                if (Modifier.isStatic(jf.getModifiers()))
                {
                    Object val = null;
                    try
                    {
                        // CorruptDataException when reading
                        // negative byte/shorts
                        Object o = jf.get(null);
                        if (o instanceof JavaObject)
                        {
                            JavaObject jo = (JavaObject) o;
                            long address = jo.getID().getAddress();
                            val = new ObjectReference(null, address);
                        }
                        else
                        {
                            if (o instanceof Number || o instanceof Character || o instanceof Boolean || o == null)
                            {
                                val = o;
                            }
                            else
                            {
                                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_UnexpectedValueForStaticField, o, fieldName,
                                                fieldSignature, j2.getName(), format(claddr)), null);
                            }
                        }
                    }
                    catch (IllegalArgumentException e)
                    {
                        // IllegalArgumentException from static
                        // JavaField.get()
                        listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_InvalidStaticField, fieldName, fieldSignature, name,
                                        format(claddr)), e);
                    }
                    catch (CorruptDataException e)
                    {
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_InvalidStaticField, fieldName, fieldSignature, name,
                                        format(claddr)), e);
                    }
                    catch (MemoryAccessException e)
                    {
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_InvalidStaticField, fieldName, fieldSignature, name,
                                        format(claddr)), e);
                    }
                    Field f = new Field(fieldName, signatureToType(fieldSignature, val), val);
                    debugPrint("Adding static field " + fieldName + " " + f.getType() + " " + val + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                    + f.getValue());
                    al2.add(f);
                }
                else
                {
                    FieldDescriptor fd = new FieldDescriptor(fieldName, signatureToType(fieldSignature));
                    al.add(fd);
                }
            }
            catch (CorruptDataException e)
            {
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_InvalidField, fieldName, fieldSignature, name,
                                format(claddr)), e);
            }
        }

        // Add java.lang.Class instance fields as pseudo static fields
        JavaObject joc;
        try
        {
            joc = j2.getObject();
        }
        catch (CorruptDataException e)
        {
            // Javacore - fails
            joc = null;
        }
        catch (IllegalArgumentException e)
        {
            // IllegalArgumentException if object address not
            // found
            listener.sendUserMessage(Severity.ERROR, Messages.DTFJIndexBuilder_ProblemBuildingClassObject, e);
            joc = null;
        }
        JavaClass j3;
        if (joc != null)
        {
            try
            {
                j3 = joc.getJavaClass();
            }
            catch (CorruptDataException e)
            {
                // Corrupt - fails for dump.xml
                long objAddr = joc.getID().getAddress();
                if (msgNtypeForClassObject-- > 0)
                    listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_UnableToFindTypeOfObject, format(objAddr),
                                    format(claddr), name), e);
                j3 = null;
            }
        }
        else
        {
            // No Java object for class, so skip looking for fields
            if (j2.getID() != null)
            {
                if (debugInfo)
                    debugPrint("No Java object for " + getClassName(j2, listener) + " at " + format(j2.getID().getAddress())); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
                if (debugInfo)
                    debugPrint("No Java object for " + getClassName(j2, listener)); //$NON-NLS-1$
            }
            j3 = null;
        }
        for (; j3 != null; j3 = getSuperclass(j3, listener))
        {
            for (Iterator<?> f1 = j3.getDeclaredFields(); f1.hasNext();)
            {
                Object next = f1.next();
                if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingDeclaredFields, j3))
                    continue;
                JavaField jf = (JavaField) next;
                String className2 = getClassName(j3, listener);
                String fieldName = "?"; //$NON-NLS-1$
                String fieldSignature = "?"; //$NON-NLS-1$;
                try
                {
                    fieldName = jf.getName();
                    try
                    {
                        fieldSignature = jf.getSignature();
                    }
                    catch (CorruptDataException e)
                    {}
                    if (!Modifier.isStatic(jf.getModifiers()))
                    {
                        Object val = null;
                        try
                        {
                            // CorruptDataException when reading
                            // negative byte/shorts
                            Object o = jf.get(joc);
                            if (o instanceof JavaObject)
                            {
                                JavaObject jo = (JavaObject) o;
                                long address = jo.getID().getAddress();
                                val = new ObjectReference(null, address);
                            }
                            else
                            {
                                if (o instanceof Number || o instanceof Character || o instanceof Boolean || o == null)
                                {
                                    val = o;
                                }
                            }
                        }
                        catch (CorruptDataException e)
                        {
                            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                            Messages.DTFJIndexBuilder_InvalidField, fieldName, fieldSignature,
                                            className2, format(claddr)), e);
                        }
                        catch (MemoryAccessException e)
                        {
                            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                            Messages.DTFJIndexBuilder_InvalidField, fieldName, fieldSignature,
                                            className2, format(claddr)), e);
                        }
                        // This is an instance field in the Java object
                        // representing the class, becoming a pseudo-static
                        // field in the MAT class
                        Field f = new Field("<" + fieldName + ">", signatureToType(fieldSignature, val), val); //$NON-NLS-1$ //$NON-NLS-2$
                        al2.add(f);
                    }
                }
                catch (CorruptDataException e)
                {
                    listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                    Messages.DTFJIndexBuilder_InvalidField, fieldName, fieldSignature, className2,
                                    format(claddr)), e);
                }
            }
        }

        // Add constant pool entries as pseudo fields
        int cpindex = 0;
        Iterator<?> f1;
        try
        {
            f1 = j2.getConstantPoolReferences();
        }
        catch (IllegalArgumentException e)
        {
            // IllegalArgumentException from
            // getConstantPoolReferences (bad ref?)
            listener.sendUserMessage(Severity.ERROR, Messages.DTFJIndexBuilder_ProblemBuildingClassObject, e);
            f1 = Collections.EMPTY_LIST.iterator();
        }
        for (; f1.hasNext();)
        {
            Object next = f1.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingConstantPoolReferences, j2))
                continue;
            Object val = null;
            JavaObject jo;
            long address;
            if (next instanceof JavaObject)
            {
                jo = (JavaObject) next;
                address = jo.getID().getAddress();
            }
            else if (next instanceof JavaClass)
            {
                JavaClass jc = (JavaClass) next;
                address = getClassAddress(jc, listener);
            }
            else
            {
                // Unexpected constant pool entry
                continue;
            }
            val = new ObjectReference(null, address);
            Field f = new Field("<constant pool[" + (cpindex++) + "]>", IObject.Type.OBJECT, val); //$NON-NLS-1$ //$NON-NLS-2$
            al2.add(f);
        }

        // Add the MAT descriptions of the fields
        Field[] statics = al2.toArray(new Field[al2.size()]);
        FieldDescriptor[] fld = al.toArray(new FieldDescriptor[al.size()]);
        String cname = getMATClassName(j2, listener);
        ClassImpl ci = new ClassImpl(claddr, cname, sup, loader, statics, fld);
        // Fix the indexes
        final long claddr2 = ci.getObjectAddress();
        final int clsId = indexToAddress.reverse(claddr2);
        // debugPrint("Setting class "+format(claddr)+" "+clsId+"
        // "+format(claddr2));
        if (clsId >= 0)
        {
            ci.setObjectId(clsId);
        }
        else
        {
            listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ClassAtAddressNotFound, format(claddr), clsId, name), null);
        }
        if (sup != 0)
        {
            // debugPrint("Super "+sup+" "+superId);
            // superId is valid
            ci.setSuperClassIndex(superId);
        }

        int loaderId = indexToAddress.reverse(loader);
        if (loaderId >= 0)
        {
            ci.setClassLoaderIndex(loaderId);
        }
        else
        {
            listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ClassLoaderAtAddressNotFound, format(loader), loaderId,
                            format(claddr), clsId, name), null);
        }

        // debugPrint("Build "+ci.getName()+" at "+format(claddr2));
        hm.put(indexToAddress.reverse(claddr), ci);
        return ci;
    }

    /**
     * Get a suitable address to use for a method
     * 
     * @param m
     *            The method
     * @param listener
     *            For logging
     * @return Either the first byte code section, or the first compiled code
     *         section, or a unique made-up address
     */
    private long getMethodAddress(JavaMethod m, IProgressListener listener)
    {
        // Disable if not needed
        if (!getExtraInfo)
            return 0;
        long ret = 0;
        JavaClass jc;
        try
        {
            jc = m.getDeclaringClass();
        }
        catch (DataUnavailable e)
        {
            jc = null;
        }
        catch (CorruptDataException e)
        {
            jc = null;
        }
        for (Iterator<?> it = m.getBytecodeSections(); it.hasNext();)
        {
            Object next = it.next();
            // Too many CorruptData items from AIX 1.4.2 dumps
            if (next instanceof CorruptData && msgNcorruptSection-- <= 0)
                continue;
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingBytecodeSections, jc, m))
                continue;
            ImageSection is = (ImageSection) next;
            ret = is.getBaseAddress().getAddress();
            if (ret != 0)
            {
                // Possible address, see if it has been used for another method
                // (e.g. with different class loader).
                JavaMethod other = methodAddresses.get(ret);
                if (other == null)
                {
                    methodAddresses.put(ret, m);
                    return ret;
                }
                else if (m.equals(other))
                {
                    return ret;
                }
                else
                {
                    // Already in use, so continue
                }
            }
        }
        for (Iterator<?> it = m.getCompiledSections(); it.hasNext();)
        {
            Object next = it.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingCompiledCodeSections, jc, m))
                continue;
            ImageSection is = (ImageSection) next;
            ret = is.getBaseAddress().getAddress();
            if (ret != 0)
            {
                // Possible address, see if it has been used for another method
                // (e.g. with different class loader).
                JavaMethod other = methodAddresses.get(ret);
                if (other == null)
                {
                    methodAddresses.put(ret, m);
                    return ret;
                }
                else if (m.equals(other))
                {
                    return ret;
                }
                else
                {
                    // Already in use, so continue
                }
            }
        }
        // e.g. native methods
        Long addr = m.equals(m) ? dummyMethodAddress.get(m) : dummyMethodAddress2.get(m);
        if (addr != null)
        {
            // Return the address we have already used
            return addr;
        }
        else
        {
            long clsAd = jc != null ? getClassAddress(jc, listener) : 0;
            String methName;
            try
            {
                methName = getMethodName(m, listener);
            }
            catch (CorruptDataException e)
            {
                methName = e.toString();
            }
            // Build a unique dummy address
            long clsAddr = nextClassAddress;
            if (m.equals(m))
            {    
                dummyMethodAddress.put(m, clsAddr);
            }
            else
            {
                // If an object doesn't equal itself we can't use a normal HashMap
                dummyMethodAddress2.put(m, clsAddr);                
            }
            nextClassAddress += 8;
            if (ret != 0)
            {
                listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                Messages.DTFJIndexBuilder_MethodHasNonUniqueAddress, methName, format(clsAd),
                                format(ret), format(clsAddr)), null);
            }
            else
            {
                listener.sendUserMessage(Severity.INFO,
                                MessageFormat.format(Messages.DTFJIndexBuilder_MethodHasNoAddress, methName,
                                                format(clsAd), format(clsAddr)), null);
            }
            return clsAddr;
        }
    }

    /**
     * Generate a pseudo class from a method
     * 
     * @param m
     *            The method
     * @param sup The superclass address
     * @param jlc
     *            MAT java.lang.Class for the type
     * @param hm
     *            object id to ClassImpl mapping
     * @param bootLoaderAddress
     * @param listener
     *            For error messages
     * @return the new class
     * @throws CorruptDataException
     */
    private ClassImpl genClass(JavaMethod m, long sup, ClassImpl jlc, HashMapIntObject<ClassImpl> hm,
                    long bootLoaderAddress, IProgressListener listener) throws CorruptDataException
    {
        // Disable if not needed
        if (!getExtraInfo)
            return null;
        String name = METHOD_NAME_PREFIX + getMethodName(m, listener);
        long claddr = getMethodAddress(m, listener);
        // Add the MAT descriptions of the fields
        Field[] statics;
        long loader;
        JavaClass jc;
        try
        {
            jc = m.getDeclaringClass();
            ObjectReference val = new ObjectReference(null, getClassAddress(jc, listener));
            statics = new Field[] { new Field(DECLARING_CLASS, IObject.Type.OBJECT, val) };
            // Set up class loaders
            JavaClassLoader load = getClassLoader(jc, listener);
            loader = getLoaderAddress(load, bootLoaderAddress);
            String className = getMATClassName(jc, listener);
            // Add the package and class name to the method name - useful for
            // accumulating package statistics
            name = className + name;
        }
        catch (DTFJException e)
        {
            jc = null;
            statics = new Field[0];
            loader = fixBootLoaderAddress(bootLoaderAddress, bootLoaderAddress);
            listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                            Messages.DTFJIndexBuilder_DeclaringClassNotFound, name, format(claddr)), e);
        }

        int superId;
        if (sup != 0)
        {
            superId = indexToAddress.reverse(sup);
            if (superId < 0)
            {
                // We have a problem at this point - the class has a real
                // superclass address, but a bad id.
                // If the address is non-zero ClassImpl will use the id,
                // which can cause exceptions inside of MAT
                // so clear the address.
                listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                Messages.DTFJIndexBuilder_SuperclassNotFound, format(sup), superId, format(claddr),
                                indexToAddress.reverse(claddr), name), null);
                sup = 0;
            }
        }
        else
        {
            superId = -1;
        }
        
        ClassImpl ci = new ClassImpl(claddr, name, sup, loader, statics, new FieldDescriptor[0]);
        debugPrint("building method class " + name + " " + format(claddr)); //$NON-NLS-1$ //$NON-NLS-2$
        // Fix the indexes
        final long claddr2 = ci.getObjectAddress();
        final int clsId = indexToAddress.reverse(claddr2);
        // debugPrint("Setting class "+format(claddr)+" "+clsId+"
        // "+format(claddr2));
        if (clsId >= 0)
        {
            ci.setObjectId(clsId);
        }
        else
        {
            listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ClassAtAddressNotFound, format(claddr), clsId, name), null);
        }

        if (sup != 0)
        {
            // debugPrint("Super "+sup+" "+superId);
            // superId is valid
            ci.setSuperClassIndex(superId);
        }

        int loaderId = indexToAddress.reverse(loader);
        if (loaderId >= 0)
        {
            ci.setClassLoaderIndex(loaderId);
        }
        else
        {
            listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ClassLoaderAtAddressNotFound, format(loader), loaderId,
                            format(claddr), clsId, name), null);
        }

        hm.put(ci.getObjectId(), ci);

        ci.setClassInstance(jlc);
        long size;
        if (getExtraInfo2)
        {
            size = getMethodSize(jc, m, listener);
        }
        else
        {
            size = 0;
        }
        ci.setUsedHeapSize(size);
        ci.setHeapSizePerInstance(0);
        if (debugInfo)
        {
            // For calculating purge sizes
            objectToSize2.set(ci.getObjectId(), size);
        }
        jlc.addInstance(size);
        return ci;
    }

    /**
     * Generate a pseudo-type for methods
     * @param cname name of the pseudo-class
     * @param claddr A dummy address
     * @param superType the super class address
     * @param type The type of this type (or null to use itself)
     * @param fields the field descriptors for this type
     * @param hm
     * @param bootLoaderAddress
     * @param listener
     * @return
     */
    private ClassImpl genDummyType(String cname, long claddr, long superType, ClassImpl type, FieldDescriptor[] fields, HashMapIntObject<ClassImpl> hm, long bootLoaderAddress, IProgressListener listener)
    {
        long loader = bootLoaderAddress;
        Field statics[] = new Field[0];
        FieldDescriptor[] fld = new FieldDescriptor[0];
        ClassImpl ci = new ClassImpl(claddr, cname, superType, loader, statics, fld);
        // Fix the indexes
        final long claddr2 = ci.getObjectAddress();
        final int clsId = indexToAddress.reverse(claddr2);
        if (clsId >= 0)
        {
            ci.setObjectId(clsId);
        }
        else
        {
            listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ClassAtAddressNotFound, format(claddr), clsId, cname), null);
        }

        if (superType != 0)
        {
            int superId = indexToAddress.reverse(superType);
            ci.setSuperClassIndex(superId);
        }

        int loaderId = indexToAddress.reverse(loader);
        if (loaderId >= 0)
        {
            ci.setClassLoaderIndex(loaderId);
        }
        else
        {
            listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                            Messages.DTFJIndexBuilder_ClassLoaderAtAddressNotFound, format(loader), loaderId,
                            format(claddr), clsId, cname), null);
        }

        hm.put(ci.getObjectId(), ci);

        if (type == null)
            type = ci;

        ci.setClassInstance(type);
        int size = 0;

        ci.setUsedHeapSize(size);
        ci.setHeapSizePerInstance(0);
        if (debugInfo)
        {
            // For calculating purge sizes
            objectToSize2.set(ci.getObjectId(), size);
        }
        type.addInstance(size);

        return ci;
    }

    static String getMethodName(JavaMethod meth, IProgressListener listener) throws CorruptDataException
    {
        String name = meth.getName();
        try
        {
            String sig = meth.getSignature();
            // 1.4.2 dumps have "Pseudo Frame" with no signature
            if (sig.equals("")) sig = "()"; //$NON-NLS-1$ //$NON-NLS-2$
            name += sig;
        }
        catch (CorruptDataException e)
        {
            name = name + "()"; //$NON-NLS-1$
        }
        return name;
    }

    /**
     * Converts the DTFJ field signature to the MAT type
     * 
     * @param sig
     * @return
     */
    static int signatureToType(String sig, Object value)
    {
        int ret = signatureToType(sig);
        if (ret == -1) ret = signatureToType(value);
        return ret;
    }
    
    /**
     * Converts the DTFJ field signature to the MAT type
     * 
     * @param sig
     * @return
     */
    static int signatureToType(String sig)
    {
        int ret;
        if (sig.length() == 0) return -1;
        switch (sig.charAt(0))
        {
            case 'L':
                ret = IObject.Type.OBJECT;
                break;
            case '[':
                ret = IObject.Type.OBJECT;
                break;
            case 'Z':
                ret = IObject.Type.BOOLEAN;
                break;
            case 'B':
                ret = IObject.Type.BYTE;
                break;
            case 'C':
                ret = IObject.Type.CHAR;
                break;
            case 'S':
                ret = IObject.Type.SHORT;
                break;
            case 'I':
                ret = IObject.Type.INT;
                break;
            case 'J':
                ret = IObject.Type.LONG;
                break;
            case 'F':
                ret = IObject.Type.FLOAT;
                break;
            case 'D':
                ret = IObject.Type.DOUBLE;
                break;
            default:
                ret = -1;
        }
        return ret;
    }
    
    /**
     * Converts the DTFJ object type to the MAT type
     * 
     * @param sig
     * @return
     */
    static int signatureToType(Object o)
    {
        int ret;
        if (o instanceof JavaObject || o == null)
        {
            ret = IObject.Type.OBJECT; 
        }
        else if (o instanceof Byte)
        {
            ret = IObject.Type.BYTE; 
        }
        else if (o instanceof Short)
        {
            ret = IObject.Type.SHORT; 
        }
        else if (o instanceof Integer)
        {
            ret = IObject.Type.INT; 
        }
        else if (o instanceof Long)
        {
            ret = IObject.Type.LONG; 
        }
        else if (o instanceof Float)
        {
            ret = IObject.Type.FLOAT; 
        }
        else if (o instanceof Double)
        {
            ret = IObject.Type.DOUBLE; 
        }
        else if (o instanceof Boolean)
        {
            ret = IObject.Type.BOOLEAN; 
        }
        else if (o instanceof Character)
        {
            ret = IObject.Type.CHAR; 
        }
        else
        {
            ret = -1;
        }
        return ret;
    }

    /**
     * Get an address associated with a thread
     * 1.Thread object address
     * 2. JNIEnv address
     * 3. corrupt data from thread object
     * @param th
     * @param listener
     * @return
     */
    static long getThreadAddress(JavaThread th, IProgressListener listener)
    {
        long ret = 0;
        CorruptDataException e = null;
        try
        {
            JavaObject o = th.getObject();
            if (o != null)
                ret = o.getID().getAddress();
        }
        catch (CorruptDataException e1)
        {
            e = e1;
        }
        if (ret == 0)
        {
            try
            {
                ret = th.getJNIEnv().getAddress();
            }
            catch (CorruptDataException e2)
            {
                ImagePointer ip = null;
                if (e != null) ip = e.getCorruptData().getAddress();
                if (ip == null) ip = e2.getCorruptData().getAddress();
                if (ip != null) ret = ip.getAddress();
            }
            if (listener != null)
            {
                if (ret == 0) {
                    try
                    {
                        String name = th.getName();
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_ProblemReadingJavaThreadInformationFor, name), e);
                    }
                    catch (CorruptDataException e2)
                    {
                        listener.sendUserMessage(Severity.WARNING,
                                        Messages.DTFJIndexBuilder_ProblemReadingJavaThreadInformation, e);
                        listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_ProblemReadingJavaThreadName, e2);
                    }
                }
                else 
                {
                    try
                    {
                        String name = th.getName();
                        listener.sendUserMessage(Severity.INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_UsingAddressForThreadName, format(ret), name), e);
                    }
                    catch (CorruptDataException e2)
                    {
                        listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_UsingAddressForThread, format(ret)), e);
                        listener.sendUserMessage(Severity.INFO, Messages.DTFJIndexBuilder_ProblemReadingJavaThreadName, e2);
                    }
                }
            }
        }
        return ret;
    }

    /**
     * @param load
     * @param bootLoaderAddress
     * @return
     * @throws CorruptDataException
     */
    private long getLoaderAddress(JavaClassLoader load, long bootLoaderAddress) throws CorruptDataException
    {
        long loader;
        if (load == null)
        {
            loader = bootLoaderAddress;
        }
        else
        {
            JavaObject loaderObject = load.getObject();
            if (loaderObject == null)
            {
                loader = bootLoaderAddress;
            }
            else
            {
                loader = loaderObject.getID().getAddress();
            }
        }
        loader = fixBootLoaderAddress(bootLoaderAddress, loader);
        return loader;
    }

    /**
     * Get the name for a class, but handle errors
     * @param javaClass
     * @param listen
     * @return
     */
    private String getClassName(JavaClass javaClass, IProgressListener listen)
    {
        String name;
        try
        {
            name = javaClass.getName();
        }
        catch (CorruptDataException e)
        {
            long id = getClassAddress(javaClass, listen);
            name = "corruptClassName@" + format(id); //$NON-NLS-1$
            try
            {
                if (javaClass.isArray())
                {
                    name = "[LcorruptArrayClassName@" + format(id) + ";"; //$NON-NLS-1$ //$NON-NLS-2$
                    JavaClass comp = javaClass.getComponentType();
                    String compName = getClassName(comp, listen);
                    if (compName.startsWith("[")) //$NON-NLS-1$
                        name = "[" + compName; //$NON-NLS-1$
                    else
                        name = "[L" + compName + ";"; //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            catch (CorruptDataException e2)
            {
            }
        }
        return name;
    }
    /**
     * @param javaClass
     * @return the type as a signature
     * @throws CorruptDataException
     *             Doesn't work for arrays - but should not find any in constant
     *             pool
     */
    private String getClassSignature(JavaClass javaClass) throws CorruptDataException
    {
        String sig;
        sig = "L" + javaClass.getName() + ";"; //$NON-NLS-1$ //$NON-NLS-2$
        return sig;
    }

    private static final String primitives[] = { Boolean.TYPE.getName(), Byte.TYPE.getName(), Short.TYPE.getName(),
                    Character.TYPE.getName(), Integer.TYPE.getName(), Long.TYPE.getName(), Float.TYPE.getName(),
                    Double.TYPE.getName(), Void.TYPE.getName() };
    private static final HashSet<String> primSet = new HashSet<String>(Arrays.asList(primitives));

    private static boolean isPrimitiveName(String name)
    {
        return primSet.contains(name);
    }

    /**
     * Get the address of the superclass object Avoid DTFJ bugs
     * 
     * @param j2
     * @param listener
     *            for logging
     * @return
     */
    private JavaClass getSuperclass(JavaClass j2, IProgressListener listener)
    {
        JavaClass sup = null;
        try
        {
            sup = j2.getSuperclass();

            // superclass for array can return java.lang.Object from
            // another dump!
            if (sup != null)
            {
                ImagePointer supAddr = sup.getID();
                ImagePointer clsAddr = sup.getID();
                supAddr = clsAddr;
                // Synthetic classes can have a null ID
                if (supAddr != null && clsAddr != null)
                {
                    ImageAddressSpace supAddressSpace = supAddr.getAddressSpace();
                    ImageAddressSpace clsAddressSpace = clsAddr.getAddressSpace();
                    if (!supAddressSpace.equals(clsAddressSpace))
                    {
                        if (supAddressSpace != clsAddressSpace)
                        {
                            if (listener != null)
                                listener.sendUserMessage(Severity.ERROR, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_SuperclassInWrongAddressSpace, j2.getName(),
                                                supAddressSpace, clsAddressSpace), null);
                            sup = null;
                        }
                        else
                        {
                            // ImageAddressSpace.equals broken -
                            // returns false
                            if (listener != null && msgNbrokenEquals-- > 0)
                                listener.sendUserMessage(Severity_INFO, MessageFormat.format(
                                                Messages.DTFJIndexBuilder_ImageAddressSpaceEqualsBroken,
                                                supAddressSpace, clsAddressSpace, System.identityHashCode(supAddr),
                                                System.identityHashCode(clsAddr)), null);
                        }
                    }
                }
            }

            if (sup == null)
            {
                // debugPrint("No superclass for "+j2.getName());
                if (j2.isArray() && j2.getObject() != null && j2.getObject().getJavaClass().getSuperclass() != null)
                {
                    // Fix DTFJ bug - find java.lang.Object to use as the
                    // superclass
                    for (sup = j2.getObject().getJavaClass(); sup.getSuperclass() != null; sup = sup.getSuperclass())
                    {}
                    if (listener != null && msgNnoSuperClassForArray-- > 0)
                        listener.sendUserMessage(Severity_INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_NoSuperclassForArray, j2.getName(), sup.getName()),
                                        null);
                }
            }
            else
            {
                // J9 DTFJ returns java.lang.Object for interfaces
                // Sov DTFJ returns java.lang.Object for primitives
                // or interfaces
                // PHD or javacore don't have modifiers, so don't try
                // getModifiers more than a few times if getModifiers never suceeds.
                if ((msgNgetSuperclass > 0 || modifiersFound > 0)
                                && Modifier.isInterface(j2.getModifiers()))
                {
                    ++modifiersFound;
                    if (listener != null && msgNbrokenInterfaceSuper-- > 0)
                        listener.sendUserMessage(Severity_INFO, MessageFormat.format(
                                        Messages.DTFJIndexBuilder_InterfaceShouldNotHaveASuperclass, j2.getName(), sup
                                                        .getName()), null);
                    sup = null;
                }
                else
                {
                    String name = j2.getName();
                    if (isPrimitiveName(name))
                    {
                        if (listener != null)
                            listener.sendUserMessage(Severity_INFO, MessageFormat.format(
                                            Messages.DTFJIndexBuilder_PrimitiveShouldNotHaveASuperclass, j2.getName(),
                                            sup.getName()), null);
                        sup = null;
                    }
                }
            }
            return sup;
        }
        catch (CorruptDataException e)
        {
            long addr = getClassAddress(j2, listener);
            String name = getClassName(j2, listener);
            if (listener != null && msgNgetSuperclass-- > 0)
                listener.sendUserMessage(Severity.WARNING, MessageFormat.format(
                                Messages.DTFJIndexBuilder_ProblemGettingSuperclass, name, format(addr)), e);
            return sup; // Just for Javacore
        }
    }

    /**
     * Basic class loader finder - copes with arrays not having a loader, use
     * component type loader instead
     * 
     * @param j2
     * @param listener
     *            for error messages
     * @return
     * @throws CorruptDataException
     */
    private JavaClassLoader getClassLoader1(JavaClass j2, IProgressListener listener) throws CorruptDataException
    {
        JavaClassLoader load;
        // Fix up possible problem with arrays not having a class loader
        // Use the loader of the component type instead
        for (JavaClass j3 = j2; (load = j3.getClassLoader()) == null && j3.isArray(); j3 = j3.getComponentType())
        {
            if (msgNmissingLoaderMsg-- > 0)
                listener.sendUserMessage(Severity_INFO, MessageFormat.format(Messages.DTFJIndexBuilder_NoClassLoader,
                                j3.getName(), j3.getComponentType().getName()), null);
        }
        return load;
    }

    /**
     * General class loader finder
     * 
     * @param j1
     * @param listener
     *            for error messages
     * @return
     * @throws CorruptDataException
     */
    private JavaClassLoader getClassLoader(JavaClass j1, IProgressListener listener) throws CorruptDataException
    {
        JavaClassLoader load;
        try
        {
            load = getClassLoader1(j1, listener);
        }
        catch (CorruptDataException e)
        {
            load = getClassLoader2(j1, listener);
            if (load != null)
                return load;
            throw e;
        }
        if (load == null)
        {
            load = getClassLoader2(j1, listener);
        }
        return load;
    }

    /**
     * @param j1
     * @return
     * @throws CorruptDataException
     */
    private JavaClassLoader getClassLoader2(JavaClass j1, IProgressListener listener) throws CorruptDataException
    {
        for (Iterator<?> i = dtfjInfo.getJavaRuntime().getJavaClassLoaders(); i.hasNext();)
        {
            Object next = i.next();
            if (isCorruptData(next, listener, Messages.DTFJIndexBuilder_CorruptDataReadingClassLoaders1, dtfjInfo.getJavaRuntime()))
                continue;
            JavaClassLoader jcl = (JavaClassLoader) next;
            for (Iterator<?> j = jcl.getDefinedClasses(); j.hasNext();)
            {
                Object next2 = j.next();
                if (isCorruptData(next2, listener, Messages.DTFJIndexBuilder_CorruptDataReadingClasses, jcl))
                    continue;
                JavaClass j2 = (JavaClass) next2;
                if (j2.equals(j1)) { return jcl; }
            }
        }
        return null;
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

    /**
     * Convert the DTFJ version of the class name to the MAT version The array
     * suffix is important for MAT operation - old J9 [[[java/lang/String ->
     * java.lang.String[][][] [char -> char[] 1.4.2, and new J9 after fix for
     * [[[Ljava/lang/String; -> java.lang.String[][][] [C -> char[]
     * 
     * @param j2
     * @param listener for messages
     * @return The MAT version of the class name
     */
    private String getMATClassName(JavaClass j2, IProgressListener listener)
    {
        // MAT expects the name with $, but with [][] for the dimensions
        String d = getClassName(j2, listener).replace('/', '.');
        // debugPrint("d = "+d);
        int dim = d.lastIndexOf("[") + 1; //$NON-NLS-1$
        int i = dim;
        int j = d.length();
        // Does the class name have L and semicolon around it
        if (j > 0 && d.charAt(j - 1) == ';')
        {
            // If so, remove them
            --j;
            if (d.charAt(i) == 'L')
                ++i;
            d = d.substring(i, j);
        }
        else if (i > 0)
        {
            d = d.substring(i);
            // Fix up primitive type names
            // DTFJ J9 array names are okay (char etc.)
            if (d.equals("Z")) //$NON-NLS-1$
                d = "boolean"; //$NON-NLS-1$
            else if (d.equals("B")) //$NON-NLS-1$
                d = "byte"; //$NON-NLS-1$
            else if (d.equals("S")) //$NON-NLS-1$
                d = "short"; //$NON-NLS-1$
            else if (d.equals("C")) //$NON-NLS-1$
                d = "char"; //$NON-NLS-1$
            else if (d.equals("I")) //$NON-NLS-1$
                d = "int"; //$NON-NLS-1$
            else if (d.equals("F")) //$NON-NLS-1$
                d = "float"; //$NON-NLS-1$
            else if (d.equals("J")) //$NON-NLS-1$
                d = "long"; //$NON-NLS-1$
            else if (d.equals("D")) //$NON-NLS-1$
                d = "double"; //$NON-NLS-1$
            else if (d.startsWith("L")) //$NON-NLS-1$
                // javacore reader bug - no semicolon
                d = d.substring(1);
        }
        if (dim > 0)
        {
            StringBuilder a = new StringBuilder(d);
            // Convert to MAT style array signature
            for (; dim > 0; --dim)
            {
                a.append("[]"); //$NON-NLS-1$
            }
            d = a.toString();
        }
        // debugPrint("d2 = "+d);
        return d;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.parser.IIndexBuilder#init(java.io.File,
     * java.lang.String)
     */
    public void init(File file, String prefix)
    {
        /*
         * Store the absolute file path as the absolute file will be used when
         * reopening the dump in DTFJHeapObjectReader.open()
         */
        dump = file.getAbsoluteFile();
        pfx = prefix;
    }

    /**
     * A counted cache for DTFJ Images.
     */
    static class ImageSoftReference extends SoftReference<Image>
    {
        // Use count
        private int count = 1;
        // This dump is out of date, so should be closed when no longer used.
        private boolean old;

        public ImageSoftReference(Image o)
        {
            super(o);
        }

        /**
         * Get the image to use again, increment the use count.
         * @return
         */
        public Image obtain()
        {
            // This dump is out of date, don't reuse.
            if (old)
                return null;
            Image x = get();
            if (x != null)
            {
                ++count;
            }
            return x;
        }

        /**
         * Stop using the image.
         * 
         * @param x1
         *            the image we want to stop using. It might not be the same
         *            if another thread opened a dump at the same time and put
         *            it into the cache.
         * @return true if the image matched the saved one in the soft
         *         reference so that we need to keep it around for the cache.
         */
        public boolean release(Image x1)
        {
            Image x = get();
            if (x == x1)
            {
                --count;
                return true;
            }
            return false;
        }

        /**
         * Release any resources associated with the image, provided it is not in use.
         */
        public void close()
        {
            boolean closeFailed = false;
            boolean softRefCleared = false;
            Image dumpImage = get();
            clear();
            if (dumpImage != null)
            {
                // Don't close the dump if it is still in use
                if (count == 0)
                {
                    factoryMap.remove(dumpImage);
                    try
                    {
                        // DTFJ 1.4
                        dumpImage.close();
                    }
                    catch (NoSuchMethodError e)
                    {
                        closeFailed = true;
                        dumpImage = null;
                    }
                }
            }
            else
            {
                softRefCleared = true;
            }
            cleanUp(closeFailed, softRefCleared);
        }
    }

    /**
     * Helper method to get a cached DTFJ image. Uses soft references to avoid
     * running out of memory.
     * 
     * @param dump the dump file
     * @param format
     *            The id of the DTFJ plugin
     * @return A RuntimeInfo with just the image filled in.
     * @throws Error
     *             , IOException
     */
    static RuntimeInfo getDump(File dump, Serializable format) throws Error, IOException
    {
        ImageSoftReference softReference;
        Image im;
        synchronized (imageMap)
        {
            // Cancel any pending clean-ups
            if (clearTimer != null)
            {
                clearTimer.cancel();
                clearTimer = null;
            }
            // Find an existing image for the dump file
            softReference = imageMap.get(dump);
            if (softReference != null)
            {
                im = softReference.obtain();
                if (im != null)
                {
                    ++imageCount;
                    return new RuntimeInfo(im, null, null, null, null);
                }
            }
        }
        // Failed, so get a new image for the dump
        im = getUncachedDump(dump, format);
        synchronized (imageMap)
        {
            // Check no new one obtained by another thread meanwhile.
            softReference = imageMap.get(dump);
            if (softReference == null || softReference.get() == null)
            {
                // Create a new soft reference
                imageMap.put(dump, new ImageSoftReference(im));
            }
            else
            {
            }
            // If we didn't create the soft reference then we will free the image on releasing the dump.
            ++imageCount;
        }
        return new RuntimeInfo(im, null, null, null, null);
    }

    /**
     * We want to cache DTFJ dump images, but want to free the memory when
     * nothing is going on. A compromise is to free the images 30 seconds
     * after the last image is released. A good initial parse will be followed
     * by an heap object reader open, so don't start a timer after a good parse.
     * @param dump The dump to be freed
     * @param dtfj The DTFJ objects. Contents cleared on return.
     * @param free If true and the total use count goes to zero, schedule cleanup
     */
    static void releaseDump(File dump, RuntimeInfo dtfj, boolean free)
    {
        // Already released?
        if (dtfj.image == null)
            return;
        ImageSoftReference closeDump = null;
        synchronized (imageMap)
        {
            ImageSoftReference sr = imageMap.get(dump);
            if (sr != null && sr.release(dtfj.image))
            {
                dtfj.clear();
                // Do not cache unused images if it is out of date or the plugin is not running
                if (sr.count == 0 && (sr.old || InitDTFJ.getDefault() == null))
                {
                    // The dump was out of date, and is now unused so free it.
                    imageMap.remove(dump);
                    closeDump = sr;
                }
            }
            else
            {
                // Is an image in the cache
                ImageSoftReference newsr = new ImageSoftReference(dtfj.image);
                // Do not cache the image if the plugin is not running
                if ((sr == null || sr.get() == null) && InitDTFJ.getDefault() != null)
                {
                    // There was no image in the cache, so save this one.
                    imageMap.put(dump, newsr);
                    dtfj.clear();
                }
                else
                {
                    // There is already an image, so discard this one.
                    // The use count was 1 on creation, so set it to 0.
                    newsr.release(dtfj.image);
                    dtfj.clear();
                    closeDump = newsr;
                }
            }
            if (--imageCount <= 0 && free)
            {
                if (clearTimer == null)
                    clearTimer = new Timer();
                // Wait for 30 seconds before cleaning up
                clearTimer.schedule(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        clearCachedDumps();
                    }
                }, 30 * 1000L);
            }
        }
        if (closeDump != null)
        {
            closeDump.close();
        }
    }

    /**
     * Forget about the cached version of the dump
     * 
     * @param dump
     */
    private static void clearCachedDump(File dump)
    {
        ImageSoftReference sr;
        synchronized (imageMap)
        {
            sr = imageMap.get(dump);
            if (sr == null)
            {
                // ignore
            }
            else if (sr.count >= 1)
            {
                /*
                 * Multiple users, so can't remove from the map
                 * If there was only one user then we can't remove it
                 * because if the cache were then empty on release it
                 * would be added back into the cache.
                 */
                sr.old = true;
                // we can't close it now
                sr = null;
            }
            else
            {
                /*
                 * found, but not in use, so close it
                 */
                sr = imageMap.remove(dump);
            }
        }
        if (sr != null)
        {
            sr.close();
        }
    }

    /**
     * Forget about all cached dumps which are not in use.
     */
    static void clearCachedDumps()
    {
        boolean closeFailed = false;
        boolean softRefCleared = false;
        List<ImageSoftReference>toClean;
        synchronized (imageMap)
        {
            toClean = new ArrayList<ImageSoftReference>();
            for (Iterator<Map.Entry<File, ImageSoftReference>> it = imageMap.entrySet().iterator(); it.hasNext(); )
            {
                Map.Entry<File, ImageSoftReference> e = it.next();
                if (e.getValue().count == 0)
                {
                    toClean.add(e.getValue());
                    it.remove();
                }
            }
        }
        for (ImageSoftReference sr : toClean)
        {
            Image dumpImage = sr.get();
            sr.clear();
            if (dumpImage != null)
            {
                factoryMap.remove(dumpImage);
                try
                {
                    // DTFJ 1.4
                    dumpImage.close();
                }
                catch (NoSuchMethodError e)
                {
                    closeFailed = true;
                    dumpImage = null;
                }
            }
            else
            {
                softRefCleared = true;
            }
        }
        cleanUp(closeFailed, softRefCleared);
    }

    /**
     * Helper method to get a DTFJ image
     * 
     * @param format
     *            The MAT description of the dump type e.g. DTFJ-J9)
     * @throws Error
     *             , IOException
     */
    private static Image getUncachedDump(File dump, Serializable format) throws Error, IOException
    {
        return getDynamicDTFJDump(dump, format);
    }

    /**
     * Get a DTFJ image dynamically using Eclipse extension points to find the
     * factory.
     * 
     * @param dump
     * @param format
     * @return
     * @throws IOException
     */
    private static Image getDynamicDTFJDump(File dump, Serializable format) throws IOException
    {
        Image image;
        IExtensionRegistry reg = Platform.getExtensionRegistry();
        IExtensionPoint point = reg.getExtensionPoint("com.ibm.dtfj.api", "imagefactory"); //$NON-NLS-1$ //$NON-NLS-2$

        if (point != null)
        {
            // Find all the DTFJ implementations
            for (IExtension ex : point.getExtensions())
            {
                // Find all the factories
                for (IConfigurationElement el : ex.getConfigurationElements())
                {
                    if (el.getName().equals("factory")) //$NON-NLS-1$
                    {
                        String id = el.getAttribute("id"); //$NON-NLS-1$
                        // Have we found the right factory?
                        if (id != null && id.equals(format))
                        {
                            File dumpFile = null, metaFile = null;
                            try
                            {
                                Set<List<File>> attemptedFiles = new LinkedHashSet<List<File>>();
                                // Get the ImageFactory
                                ImageFactory fact = (ImageFactory) el.createExecutableExtension("action"); //$NON-NLS-1$

                                String name = dump.getName();

                                // Find the content types of the dump
                                FileInputStream is = null;
                                IContentType ct0, cts[], cts2[];
                                // The default type
                                try
                                {
                                    is = new FileInputStream(dump);
                                    ct0 = Platform.getContentTypeManager().findContentTypeFor(is, name);
                                }
                                catch (IOException e)
                                {
                                    ct0 = null;
                                }
                                finally
                                {
                                    if (is != null)
                                        is.close();
                                }
                                // Types based on file name
                                try
                                {
                                    is = new FileInputStream(dump);
                                    cts = Platform.getContentTypeManager().findContentTypesFor(is, name);
                                }
                                catch (IOException e)
                                {
                                    cts = new IContentType[0];
                                }
                                finally
                                {
                                    if (is != null)
                                        is.close();
                                }
                                // Types not based on file name
                                try
                                {
                                    is = new FileInputStream(dump);
                                    cts2 = Platform.getContentTypeManager().findContentTypesFor(is, null);
                                }
                                catch (IOException e)
                                {
                                    cts2 = new IContentType[0];
                                }
                                finally
                                {
                                    if (is != null)
                                        is.close();
                                }

                                // See if the supplied dump matches any of the
                                // content types for the factory
                                for (IConfigurationElement el2 : el.getChildren())
                                {
                                    if (!el2.getName().equals("content-types")) //$NON-NLS-1$
                                        continue;

                                    String extId = el2.getAttribute("dump-type"); //$NON-NLS-1$
                                    String metaId = el2.getAttribute("meta-type"); //$NON-NLS-1$

                                    IContentType cext = Platform.getContentTypeManager().getContentType(extId);
                                    IContentType cmeta = Platform.getContentTypeManager().getContentType(metaId);

                                    if (cmeta != null)
                                    {
                                        // Found a metafile description
                                        boolean foundext[] = new boolean[1];
                                        boolean foundmeta[] = new boolean[1];
                                        String actualExts[] = getActualExts(cext, name, ct0, cts, cts2, foundext);
                                        String actualMetaExts[] = getActualExts(cmeta, name, ct0, cts, cts2, foundmeta);
                                        String possibleExts[] = getPossibleExts(cext);
                                        String possibleMetaExts[] = getPossibleExts(cmeta);

                                        // Is the supplied file a dump or a
                                        // meta. Decide which to try first.
                                        boolean extFirst = foundext[0];
                                        for (int i = 0; i < 2; ++i, extFirst = !extFirst)
                                        {
                                            if (extFirst)
                                            {
                                                for (String ext : actualExts)
                                                {
                                                    for (String metaExt : possibleMetaExts)
                                                    {
                                                        String metaExt1 = ext.equals("") && !metaExt.equals("") ? "." //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                                        + metaExt : metaExt;
                                                        String ext1 = metaExt.equals("") && !ext.equals("") ? "." + ext //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                        : ext;
                                                        debugPrint("ext " + ext + " " + ext1 + " " + metaExt + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                                                        + metaExt1);
                                                        if (endsWithIgnoreCase(name, ext1))
                                                        {
                                                            int p = name.length() - ext1.length();
                                                            dumpFile = dump;
                                                            metaFile = new File(dump.getParentFile(), name.substring(0,
                                                                            p)
                                                                            + metaExt1);
                                                            List<File>fs = new ArrayList<File>();
                                                            fs.add(dumpFile);
                                                            fs.add(metaFile);
                                                            attemptedFiles.add(fs);
                                                        }
                                                    }
                                                }
                                            }
                                            else
                                            {
                                                for (String metaExt : actualMetaExts)
                                                {
                                                    for (String ext : possibleExts)
                                                    {
                                                        String metaExt1 = ext.equals("") && !metaExt.equals("") ? "." //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                                        + metaExt : metaExt;
                                                        String ext1 = metaExt.equals("") && !ext.equals("") ? "." + ext //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                                        : ext;
                                                        debugPrint("meta " + ext + " " + ext1 + " " + metaExt + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                                                        + metaExt1);
                                                        if (endsWithIgnoreCase(name, metaExt1))
                                                        {
                                                            int p = name.length() - metaExt1.length();
                                                            dumpFile = new File(dump.getParentFile(), name.substring(0,
                                                                            p)
                                                                            + ext1);
                                                            metaFile = dump;
                                                            List<File>fs = new ArrayList<File>();
                                                            fs.add(dumpFile);
                                                            fs.add(metaFile);
                                                            attemptedFiles.add(fs);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else if (cext != null)
                                    {
                                        boolean foundext[] = new boolean[1];
                                        String actualExts[] = getActualExts(cext, name, ct0, cts, cts2, foundext);
                                        for (String ext : actualExts)
                                        {
                                            if (endsWithIgnoreCase(name, ext))
                                            {
                                                dumpFile = dump;
                                                metaFile = null;
                                                List<File>fs = new ArrayList<File>();
                                                fs.add(dumpFile);
                                                attemptedFiles.add(fs);
                                                break;
                                            }
                                        }
                                    }
                                }
                                
                                // Also try just the dump
                                dumpFile = dump;
                                metaFile = null;
                                List<File>fs = new ArrayList<File>();
                                fs.add(dump);
                                attemptedFiles.add(fs);
                                
                                // Try opening the dump
                                RuntimeException savedRuntimeException = null;
                                FileNotFoundException savedFileException = null;
                                try {
                                    for (List<File>fls : attemptedFiles)
                                    {
                                        dumpFile = fls.get(0);
                                        if (fls.size() >= 2)
                                        {
                                            metaFile = fls.get(1);
                                            try
                                            {
                                                image = fact.getImage(dumpFile, metaFile);
                                                // Save the factory too
                                                factoryMap.put(image, fact);
                                                return image;
                                            }
                                            catch (FileNotFoundException e)
                                            {
                                                checkIfDiskFull(dumpFile, metaFile, e, format);
                                            }
                                        }
                                        else
                                        {
                                            metaFile = null;
                                            try
                                            {
                                                image = fact.getImage(dumpFile);
                                                // Save the factory too
                                                factoryMap.put(image, fact);
                                                return image;
                                            }
                                            catch (RuntimeException e)
                                            {
                                                // Javacore currently throws
                                                // IndexOutOfBoundsException
                                                // for bad dumps
                                                savedRuntimeException = e;
                                                savedFileException = null;
                                            }
                                            catch (FileNotFoundException e)
                                            {
                                                savedRuntimeException = null;
                                                savedFileException = e;
                                                checkIfDiskFull(dumpFile, metaFile, e, format);
                                            }
                                        }
                                    }
                                }
                                catch (IOException e)
                                {
                                    // Could be out of disk space, so give up now
                                    // Clear the cache to attempt to free some disk
                                    // space
                                    clearCachedDumps();
                                    IOException e1 = new IOException(MessageFormat.format(
                                                    Messages.DTFJIndexBuilder_UnableToReadDumpMetaInDTFJFormat, dumpFile,
                                                    metaFile, format));
                                    e1.initCause(e);
                                    throw e1;
                                }
                                if (savedRuntimeException != null)
                                {
                                    // Javacore currently throws
                                    // IndexOutOfBoundsException for bad dumps
                                    IOException e1 = new IOException(MessageFormat.format(
                                                    Messages.DTFJIndexBuilder_UnableToReadDumpInDTFJFormat, dumpFile,
                                                    format));
                                    e1.initCause(savedRuntimeException);
                                    throw e1;
                                }
                                if (savedFileException != null)
                                {
                                    IOException e1 = new IOException(MessageFormat.format(
                                                    Messages.DTFJIndexBuilder_UnableToReadDumpInDTFJFormat, dumpFile,
                                                    format));
                                    e1.initCause(savedFileException);
                                    throw e1;
                                }
                            }
                            catch (CoreException e)
                            {
                                // From createExecutableException
                                IOException e1 = new IOException(MessageFormat.format(
                                                Messages.DTFJIndexBuilder_UnableToReadDumpInDTFJFormat, dump, format));
                                e1.initCause(e);
                                throw e1;
                            }
                        }
                    }
                }
            }
        }
        throw new IOException(MessageFormat.format(Messages.DTFJIndexBuilder_UnableToFindDTFJForFormat, format));
    }

    /**
     * Find the valid file extensions for the supplied file, assuming it is of
     * the requested type.
     * 
     * @param cext
     *            Requested type
     * @param name
     *            The file name
     * @param ctdump
     *            The best guess content type for the file
     * @param cts
     *            All content types based on name
     * @param cts2
     *            All content types not based on name
     * @param found
     *            Did the file type match the content-type?
     * @return array of extensions
     */
    private static String[] getActualExts(IContentType cext, String name, IContentType ctdump, IContentType cts[],
                    IContentType cts2[], boolean found[])
    {
        LinkedHashSet<String> exts = new LinkedHashSet<String>();

        debugPrint("Match " + cext); //$NON-NLS-1$

        // Add best guess extensions first
        if (ctdump != null)
        {
            if (ctdump.isKindOf(cext))
            {
                debugPrint("Found default type " + ctdump); //$NON-NLS-1$
                exts.addAll(Arrays.asList(ctdump.getFileSpecs(IContentType.FILE_EXTENSION_SPEC)));
            }
        }

        // Add other extensions
        if (cts.length > 0)
        {
            for (IContentType ct : cts)
            {
                if (ct.isKindOf(cext))
                {
                    debugPrint("Found possible type with file name " + ct); //$NON-NLS-1$
                    exts.addAll(Arrays.asList(ct.getFileSpecs(IContentType.FILE_EXTENSION_SPEC)));
                }
            }
        }

        if (cts.length == 0)
        {
            // No matching types including the file name
            // Try without file names
            debugPrint("No types"); //$NON-NLS-1$

            boolean foundType = false;
            for (IContentType ct : cts2)
            {
                if (ct.isKindOf(cext))
                {
                    debugPrint("Found possible type without file name " + ct); //$NON-NLS-1$
                    foundType = true;
                    exts.addAll(Arrays.asList(ct.getFileSpecs(IContentType.FILE_EXTENSION_SPEC)));
                }
            }
            if (foundType)
            {
                // We did find that this file is of the required type, but the
                // name might be wrong
                // Add the actual file extension, then this can be used later
                int lastDot = name.lastIndexOf('.');
                if (lastDot >= 0)
                {
                    exts.add(name.substring(lastDot + 1));
                }
                else
                {
                    exts.add(""); //$NON-NLS-1$
                }
            }
        }

        if (exts.isEmpty())
        {
            found[0] = false;
            exts.addAll(Arrays.asList(getPossibleExts(cext)));
        }
        else
        {
            found[0] = true;
        }

        debugPrint("All exts " + exts); //$NON-NLS-1$
        return exts.toArray(new String[exts.size()]);
    }

    /**
     * Get all the possible file extensions for a particular file type. Check
     * all the subtypes too.
     * 
     * @param cext
     * @return possible extensions
     */
    private static String[] getPossibleExts(IContentType cext)
    {
        LinkedHashSet<String> exts = new LinkedHashSet<String>();

        exts.addAll(Arrays.asList(cext.getFileSpecs(IContentType.FILE_EXTENSION_SPEC)));

        for (IContentType ct : Platform.getContentTypeManager().getAllContentTypes())
        {
            if (ct.isKindOf(cext))
            {
                exts.addAll(Arrays.asList(ct.getFileSpecs(IContentType.FILE_EXTENSION_SPEC)));
            }
        }

        return exts.toArray(new String[exts.size()]);
    }

    /**
     * See if one file name ends with an extension (ignoring case).
     * 
     * @param s1
     * @param s2
     * @return
     */
    private static boolean endsWithIgnoreCase(String s1, String s2)
    {
        int start = s1.length() - s2.length();
        return start >= 0 && s2.compareToIgnoreCase(s1.substring(start)) == 0;
    }

    /**
     * Try to spot Out of disk space errors
     * 
     * @param dumpFile
     * @param metaFile
     * @param e
     * @param format
     * @throws IOException
     */
    private static void checkIfDiskFull(File dumpFile, File metaFile, FileNotFoundException e, Serializable format)
                    throws IOException
    {
        if (e.getMessage().contains("Unable to write")) //$NON-NLS-1$
        {
            // Could be out of disk space, so give up now
            // Clear the cache to attempt to free some disk space
            clearCachedDumps();
            IOException e1 = new IOException(MessageFormat.format(
                            Messages.DTFJIndexBuilder_UnableToReadDumpMetaInDTFJFormat, dumpFile, metaFile, format));
            e1.initCause(e);
            throw e1;
        }
    }

    /**
     * To print out debugging messages
     * 
     * @param msg
     */
    private static void debugPrint(String msg)
    {
        if (debugInfo)
            System.out.println(msg);
    }


    /**
     * Get a debug option from the Eclipse platform .options file
     * @param option the option name
     * @param defaultValue the default value if it is not found
     * @return
     */
    private static boolean getDebugOption(String option, boolean defaultValue)
    {
        String val = Platform.getDebugOption(option);
        if (val != null)
        {
            return Boolean.parseBoolean(val);
        }
        return defaultValue;
    }

    /**
     * Get a debug option from the Eclipse platform .options file
     * @param option the option name
     * @param defaultValue the default value if it is not found
     * @return
     */
    private static int getDebugOption(String option, int defaultValue)
    {
        String val = Platform.getDebugOption(option);
        if (val != null)
        {
            try
            {
                return Integer.parseInt(val);
            }
            catch (NumberFormatException e)
            {
            }
        }
        return defaultValue;
    }

    /**
     * If explicit close failed for some reason then try using finalization.
     * @param closeFailed
     * @param softRefCleared
     */
    private static void cleanUp(boolean closeFailed, boolean softRefCleared)
    {
        if (closeFailed)
        {
            // We couldn't close a DTFJ image, so GC and finalize to
            // attempt to clean up any temporary files
            System.gc();
            System.runFinalization();
        }
        else if (softRefCleared)
        {
            System.runFinalization();
        }
    }
}

/**
 * Use ICU not java.text for message formatting
 */
class MessageFormat
{
    public static String format(String msg, Object... parms)
    {
        return com.ibm.icu.text.MessageFormat.format(msg, parms);
    }

    private MessageFormat()
    {}
}

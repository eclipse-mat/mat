/*******************************************************************************
 * Copyright (c) 2016 SAP AG
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ruby.parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.collect.SetLong;
import org.eclipse.mat.parser.IIndexBuilder;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.index.IndexManager.Index;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.io.BufferedRandomAccessInputStream;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.XGCRootInfo;
import org.eclipse.mat.parser.model.XSnapshotInfo;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.util.IProgressListener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonStreamParser;

public class RubyIndexBuilder implements IIndexBuilder
{
    private IOne2LongIndex id2position;

    private File file;
    private String prefix;

    private HashMapLongObject<List<XGCRootInfo>> gcRoots = new HashMapLongObject<List<XGCRootInfo>>(200);

    private Map<String, List<ClassImpl>> classesByName = new HashMap<String, List<ClassImpl>>();
    private HashMapLongObject<ClassImpl> classesByAddress = new HashMapLongObject<ClassImpl>();
    private SetLong missingClasses = new SetLong();
    private Set<String> typesWithNoClass = new HashSet<>();
    private Map<Long, Long> class2ItsClass = new HashMap<>();

    private IndexWriter.Identifier identifiers = null;
    private IndexWriter.IntArray1NWriter outbound = null;
    private IndexWriter.IntIndexCollector object2classId = null;
    private IndexWriter.LongIndexCollector object2position = null;
    private FileWriter stringValues = null;
    private IndexWriter.SizeIndexCollectorUncompressed array2size = null;

    HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>> thread2objects2roots = new HashMapIntObject<HashMapIntObject<List<XGCRootInfo>>>();

    private XSnapshotInfo info;

    long maxAddress = 0;
    private long fakeAddressSize = 16;

    private final long dummyClassLoaderAddress = 0;
    private ClassImpl dummyClassEntry;

    public RubyIndexBuilder()
    {

    }

    @Override
    public void cancel()
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void init(File file, String prefix) throws SnapshotException, IOException
    {

        this.file = file;
        this.prefix = prefix;

    }

    @Override
    public void fill(IPreliminaryIndex preliminaryIndex, IProgressListener listener) throws SnapshotException,
                    IOException
    {
        this.info = preliminaryIndex.getSnapshotInfo();
        this.identifiers = new IndexWriter.Identifier();

        System.out.println("Parsing file: " + file.getAbsolutePath());
        passOne();

        beforePassTwo();

        passTwo();

        id2position = fillIn(preliminaryIndex);

        checkClasses();

    }

    private void checkClasses()
    {
        for (int i = 0; i < identifiers.size(); i++)
        {
            int classId = object2classId.get(i);

            if (classId < 0)
            {
                System.out.println("BAMMM!");
            }

            long classAddr = identifiers.get(classId);

            ClassImpl clazz = classesByAddress.get(classAddr);
            if (clazz == null)
            {
                System.out.println("NULL CLASSS!!!!!");
                System.out.println("objectId: " + i + "; objectAddress: " + Long.toHexString(identifiers.get(i))
                                + "; classId: " + classId + "; classAddr: " + Long.toHexString(classAddr));
            }

       }

    }

    @Override
    public void clean(final int[] purgedMapping, IProgressListener listener) throws IOException
    {
        // TODO: need some impl here?
    }

    private void passOne() throws IOException
    {
        PositionInputStream in = new PositionInputStream(new BufferedRandomAccessInputStream(new RandomAccessFile(file,
                        "r")));
        Reader fileReader = new InputStreamReader(in);
        JsonStreamParser reader = new JsonStreamParser(fileReader);

        try
        {
            while (reader.hasNext())
            {
                JsonObject object = null;
                try
                {
                    object = reader.next().getAsJsonObject();
                    String type = object.get("type").getAsString();

                    if (type.equals("IMEMO")) continue; // TODO: not sure how to process it
                    
                    if (type.equals("ROOT"))
                    {
                        addRoot(object);
                    }
                    else
                    {
                        long objectAddress = getAddress(object);
                        if (type.equals("CLASS"))
                        {
                            addClass(readClass(object), in.position());
                        }
                        else if (type.equals("MODULE"))
                        {
                            addClass(readClass(object), in.position());
                        }
                        else
                        {
                            reportInstance(objectAddress, in.position());
                        }
                        createClassIfNeeded(object);
                        maxAddress = Math.max(maxAddress, objectAddress);
                    }
                }
                catch (Exception e)
                {
                    System.out.println("Error parsing object: ");
                    System.out.println(object);
                    e.printStackTrace();
                }
            }
        }
        finally
        {
            fileReader.close();
        }

        System.out.println("Found " + identifiers.size() + " objects in the dump");
    }

    private void dumpStringValue(JsonObject object) throws IOException
    {
        String address = object.get("address").getAsString();
        JsonElement valueElement = object.get("value");
        if (valueElement != null)
        {
            String value = valueElement.getAsString();
            stringValues.write(address + " " + URLEncoder.encode(value, "UTF8") + "\n");
        }

    }

    private void createClassIfNeeded(JsonObject object) throws IOException
    {
        JsonElement classElement = object.get("class");
        if (classElement == null)
        {
            // System.out.println("No class property for object: " +
            // object.toString());
            String type = object.get("type").getAsString();
            typesWithNoClass.add(type);
        }
        else
        {
            long classAddress = Long.parseLong(classElement.getAsString().substring(2), 16);
            if (!classesByAddress.containsKey(classAddress))
            {
                // System.out.println("Class " + classElement.toString() +
                // " is missing for " + object.toString());
                missingClasses.add(classAddress);
            }
        }
    }

    private void addMissingClasses() throws IOException
    {
        long fakeClassesAddr = maxAddress + fakeAddressSize;
        System.out.println("Starging from fake address:" + fakeClassesAddr + ", maxAddress was: " + maxAddress);

        // first initialize the dummyClassEntry
        dummyClassEntry = createFakeClass(fakeClassesAddr, "N/A");
        addClass(dummyClassEntry, -1);
        fakeClassesAddr += fakeAddressSize;

        long[] addrs = missingClasses.toArray();
        for (long classAddress : addrs)
        {
            if (!classesByAddress.containsKey(classAddress))
            {
                ClassImpl fakeClass = createFakeClass(fakeClassesAddr, "Class-0x" + Long.toHexString(classAddress));
                addClass(fakeClass, -1);
                fakeClassesAddr += fakeAddressSize;
                // System.out.println("Added class " + fakeClass.getName());
            }
        }

        for (String type : typesWithNoClass)
        {
            ClassImpl fakeClass = createFakeClass(fakeClassesAddr, type);
            addClass(fakeClass, -1);
            fakeClassesAddr += fakeAddressSize;
        }
    }

    private ClassImpl createFakeClass(long fakeClassesAddr, String type)
    {
        ClassImpl fakeClass = new ClassImpl(fakeClassesAddr, classNameFromType(type), 0, dummyClassLoaderAddress,
                        new Field[0], new FieldDescriptor[0]);
        System.out.println("Creating fake class: " + fakeClass.getName() + " at 0x" + Long.toHexString(fakeClassesAddr));
        fakeClass.setClassInstance(dummyClassEntry);
        return fakeClass;
    }

    private String classNameFromType(String type)
    {
        return "Type-" + type;
    }

    private void beforePassTwo() throws IOException
    {
        // add address for the dummyClassLoader
        identifiers.add(dummyClassLoaderAddress);
        ArrayList<XGCRootInfo> dummyClassRootInfos = new ArrayList<XGCRootInfo>();
        dummyClassRootInfos.add(new XGCRootInfo(dummyClassLoaderAddress, 0, GCRootInfo.Type.SYSTEM_CLASS));
        gcRoots.put(dummyClassLoaderAddress, dummyClassRootInfos);

        System.out.println("Classes before: " + classesByAddress.size());
        addMissingClasses();
        System.out.println("Classes after: " + classesByAddress.size());

        // sort and assign preliminary object ids
        identifiers.sort();

        int maxClassId = 0;
        // calculate instance size for all classes
        for (Iterator<?> e = classesByAddress.values(); e.hasNext();)
        {
            ClassImpl clazz = (ClassImpl) e.next();
            int classIndex = identifiers.reverse(clazz.getObjectAddress());
            clazz.setObjectId(classIndex);

            maxClassId = Math.max(maxClassId, classIndex);

            clazz.setHeapSizePerInstance(0); // (calculateInstanceSize(clazz));
            clazz.setUsedHeapSize(0); // (calculateClassSize(clazz));
        }

        // create index writers
        outbound = new IndexWriter.IntArray1NWriter(this.identifiers.size(), Index.OUTBOUND.getFile(info.getPrefix()
                        + "temp."));//$NON-NLS-1$
        object2classId = new IndexWriter.IntIndexCollector(this.identifiers.size(),
                        IndexWriter.mostSignificantBit(maxClassId));
        object2position = new IndexWriter.LongIndexCollector(this.identifiers.size(),
                        IndexWriter.mostSignificantBit(new File(this.info.getPath()).length()));
        array2size = new IndexWriter.SizeIndexCollectorUncompressed(this.identifiers.size());

        // log references for classes
        for (Iterator<?> e = classesByAddress.values(); e.hasNext();)
        {
            ClassImpl clazz = (ClassImpl) e.next();
            clazz.setClassLoaderIndex(identifiers.reverse(dummyClassLoaderAddress));

            // add class instance
            Long classAddress = class2ItsClass.get(clazz.getObjectAddress());
            if (classAddress != null)
            {
                {
                    ClassImpl classInstance = classesByAddress.get(classAddress);
                    clazz.setClassInstance(classInstance);
                }
            }
            else
            {
                System.out.println("Setting a fake class for " + clazz.getName());
                clazz.setClassInstance(dummyClassEntry);
            }

            object2classId.set(clazz.getObjectId(), clazz.getClazz().getObjectId());

        }

        object2classId.set(identifiers.reverse(0), dummyClassEntry.getClassId());

    }

    private void passTwo() throws IOException
    {
        PositionInputStream in = new PositionInputStream(new BufferedRandomAccessInputStream(new RandomAccessFile(file,
                        "r")));
        Reader fileReader = new InputStreamReader(in);
        JsonStreamParser reader = new JsonStreamParser(fileReader);

        stringValues = new FileWriter(this.info.getPath() + ".strings");

        int position = 0;

        try
        {
            while (reader.hasNext())
            {
                JsonObject object = null;
                try
                {
                    object = reader.next().getAsJsonObject();
                    String type = object.get("type").getAsString();
                    // System.out.println(in.position() + ": " + type);

                    if (type.equals("IMEMO")) continue;
                    
                    if (type.equals("CLASS"))
                    {
                        logReferencesForClass(object);
                    }
                    else if (type.equals("ROOT"))
                    {
                        // do nothing
                    }
                    else
                    {
                        addObject(object, position);
                        // FIXME: figure out how to get the position of each object in the file
                        // in order to use it in the ObjectReader. For the moment just extract the string contents
                        if (type.equals("STRING"))
                        {
                            dumpStringValue(object);
                        }
                    }
                    position += object.toString().length() * 2;
                }
                catch (Exception e)
                {
                    System.out.println("Error parsing object: ");
                    System.out.println(object);
                    e.printStackTrace();
                }
            }
        }
        finally
        {
            fileReader.close();
            stringValues.close();
        }

    }

    private long getAddress(JsonObject object)
    {
        return Long.parseLong(object.get("address").getAsString().substring(2), 16);
    }

    private ArrayLong getReferences(JsonObject object)
    {
        JsonElement refsElement = object.get("references");
        if (refsElement == null)
            return null;

        ArrayLong result = new ArrayLong();
        JsonArray refs = refsElement.getAsJsonArray();
        for (JsonElement jsonElement : refs)
        {
            long address = Long.parseLong(jsonElement.getAsString().substring(2), 16);
            result.add(address);
        }
        return result;
    }

    private void addRoot(JsonObject object)
    {
        JsonArray refs = object.get("references").getAsJsonArray();
        for (JsonElement jsonElement : refs)
        {
            Long address = Long.parseLong(jsonElement.getAsString().substring(2), 16);

            List<XGCRootInfo> r = gcRoots.get(address);
            if (r == null)
                gcRoots.put(address, r = new ArrayList<XGCRootInfo>(3));
            r.add(new XGCRootInfo(address, 0, GCRootInfo.Type.UNKNOWN));
        }
    }

    private ClassImpl readClass(JsonObject object)
    {
        long address = Long.parseLong(object.get("address").getAsString().substring(2), 16);

        JsonElement jsonElement = object.get("name");
        String name = jsonElement == null ? "Unknown Class" : jsonElement.getAsString();

        JsonElement classElement = object.get("class");
        if (classElement != null)
        {
            Long classAddress = Long.parseLong(classElement.getAsString().substring(2), 16);
            class2ItsClass.put(address, classAddress);
        }

        ClassImpl classImpl = new ClassImpl(address, name, 0, dummyClassLoaderAddress, new Field[0], //$NON-NLS-1$
                        new FieldDescriptor[0]);
        return classImpl;
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

    public void reportInstance(long id, long filePosition)
    {
        this.identifiers.add(id);
    }

    private int mapAddressToId(long address)
    {
        return this.identifiers.reverse(address);
    }

    public void addObject(JsonObject object, long filePosition) throws IOException
    {

        long address = getAddress(object);
        int index = mapAddressToId(address);

        ClassImpl clazz;
        JsonElement classElement = object.get("class");
        if (classElement != null)
        {
            Long classAddress = Long.parseLong(classElement.getAsString().substring(2), 16);
            clazz = classesByAddress.get(classAddress);
        }
        else
        {
            String type = object.get("type").getAsString();
            clazz = classesByName.get(classNameFromType(type)).get(0);
        }

        int classIndex = clazz.getObjectId();

        JsonElement memsizeElement = object.get("memsize");
        int memsize = memsizeElement == null ? 0 : memsizeElement.getAsInt();
        clazz.addInstance(memsize); // FIXME: histogram??

        // log references
        ArrayLong references = new ArrayLong();
        references.add(clazz.getObjectAddress()); // pseudo ref to the class
        ArrayLong objectRefs = getReferences(object);
        if (objectRefs != null && objectRefs.size() > 0)
        {
            references.addAll(objectRefs);
        }
        outbound.log(identifiers, index, references);

        // log address
        object2classId.set(index, classIndex);
        object2position.set(index, filePosition);

        // all instances have specific size
        array2size.set(index, memsize);
        
    }

    public void logReferencesForClass(JsonObject object) throws IOException
    {

        long address = getAddress(object);
        int index = mapAddressToId(address);

        ArrayLong classRefs = getReferences(object);
        ArrayLong references = new ArrayLong(classRefs.size() + 1);
        references.add(classesByAddress.get(address).getClassAddress());
        if (classRefs != null && classRefs.size() > 0)
        {
            references.addAll(classRefs);
        }
        outbound.log(identifiers, index, references);
    }

    public IOne2LongIndex fillIn(IPreliminaryIndex index) throws IOException
    {
        // classes model
        HashMapIntObject<ClassImpl> classesById = new HashMapIntObject<ClassImpl>(classesByAddress.size());
        for (Iterator<ClassImpl> iter = classesByAddress.values(); iter.hasNext();)
        {
            ClassImpl clazz = iter.next();
            classesById.put(clazz.getObjectId(), clazz);
        }
        index.setClassesById(classesById);

        index.setGcRoots(map2ids(gcRoots));

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

}

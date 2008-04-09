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
package org.eclipse.mat.inspections.query.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;


@Name("Hash Entries")
@CommandName("hash_entries")
@Category("Java Collections")
@Help("Extracts the key-value pairs from hash maps and hashtables.")
public class HashEntriesQuery implements IQuery
{
    private static final String NULL = "<null>";

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    static class Entry
    {
        public Entry(int collectionId, String collectionName, int keyId, int valueId)
        {
            this.collectionId = collectionId;
            this.collectionName = collectionName;
            this.keyId = keyId;
            this.valueId = valueId;
        }

        int collectionId;
        int keyId;
        int valueId;

        String collectionName;
        String keyValue;
        String valueValue;
    }

    public static class Result implements IResultTable
    {
        private ISnapshot snapshot;
        private List<Entry> entries;
        private Map<String, Entry> key2entry;

        private Result(ISnapshot snapshot, List<Entry> entries)
        {
            this.snapshot = snapshot;
            this.entries = entries;
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder() //

                            .addContext(new ContextProvider("Key")
                            {
                                public IContextObject getContext(Object row)
                                {
                                    return getKey(row);
                                }
                            }) //

                            .addContext(new ContextProvider("Value")
                            {
                                public IContextObject getContext(Object row)
                                {
                                    return getValue(row);
                                }
                            }) //

                            .build();
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column("Collection").sorting(Column.SortDirection.ASC), //
                            new Column("Key"), //
                            new Column("Value") };
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            Entry entry = (Entry) row;

            switch (columnIndex)
            {
                case 0:
                    return entry.collectionName;
                case 1:
                    if (entry.keyValue == null)
                        entry.keyValue = resolve(entry.keyId);
                    return entry.keyValue;
                case 2:
                    if (entry.valueValue == null)
                        entry.valueValue = resolve(entry.valueId);
                    return entry.valueValue;
            }

            return null;
        }

        private String resolve(int objectId)
        {
            try
            {
                if (objectId < 0)
                    return NULL;

                IObject object = snapshot.getObject(objectId);
                String name = object.getClassSpecificName();

                if (name == null)
                    name = object.getTechnicalName();

                return name;
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public int getRowCount()
        {
            return entries.size();
        }

        public Object getRow(int rowId)
        {
            return entries.get(rowId);
        }

        public IContextObject getContext(final Object row)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((Entry) row).collectionId;
                }
            };
        }

        private IContextObject getKey(Object row)
        {
            final int keyId = ((Entry) row).keyId;
            if (keyId >= 0)
            {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return keyId;
                    }
                };
            }
            else
            {
                return null;
            }
        }

        private IContextObject getValue(final Object row)
        {
            final int valueId = ((Entry) row).valueId;
            if (valueId >= 0)
            {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return valueId;
                    }
                };
            }
            else
            {
                return null;
            }
        }

        // //////////////////////////////////////////////////////////////
        // map-like getters
        // //////////////////////////////////////////////////////////////

        public String getString(String key, IProgressListener listener)
        {
            prepare(listener);

            Entry entry = key2entry.get(key);

            if (entry == null)
                return null;

            if (entry.valueValue == null)
                entry.valueValue = resolve(entry.valueId);

            return entry.valueValue == NULL ? null : entry.valueValue;
        }

        public int getObjectId(String key, IProgressListener listener)
        {
            prepare(listener);

            Entry entry = key2entry.get(key);

            if (entry == null)
                return -1;

            return entry.keyId;
        }

        private synchronized void prepare(IProgressListener listener)
        {
            if (key2entry != null)
                return;

            key2entry = new HashMap<String, Entry>();

            for (Entry entry : entries)
            {
                if (entry.keyValue == null)
                    entry.keyValue = resolve(entry.keyId);
                key2entry.put(entry.keyValue, entry);

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }
    }

    public Result execute(IProgressListener listener) throws Exception
    {
        listener.subTask("Extracting Key Value Pairs...");

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> hashes = new HashMapIntObject<CollectionUtil.Info>();
        for (CollectionUtil.Info info : CollectionUtil.knownCollections)
        {
            if (!info.isHashed())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    hashes.put(clasz.getObjectId(), info);
        }

        List<Entry> hashEntries = new ArrayList<Entry>();

        for (int[] ids : objects)
        {
            for (int id : ids)
            {
                CollectionUtil.Info info = hashes.get(snapshot.getClassOf(id).getObjectId());
                if (info == null)
                    continue;

                IInstance collection = (IInstance) snapshot.getObject(id);
                String collectionName = collection.getDisplayName();

                // read table w/o loading the big table object!
                Field table = collection.getField("table");
                int tableObjectId = ((ObjectReference) table.getValue()).getObjectId();

                int[] outbounds = snapshot.getOutboundReferentIds(tableObjectId);
                for (int ii = 0; ii < outbounds.length; ii++)
                    collectEntry(hashEntries, info, collection.getObjectId(), collectionName, outbounds[ii], listener);

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }

        listener.done();

        return new Result(snapshot, hashEntries);
    }

    private void collectEntry(List<Entry> hashEntries, CollectionUtil.Info info, int collectionId,
                    String collectionName, int entryId, IProgressListener listener) throws SnapshotException
    {
        // no recursion -> use entryId to collect overflow entries
        while (entryId >= 0)
        {
            // skip if it is the pseudo outgoing reference (all other elements
            // are of type Map$Entry)
            if (snapshot.isClass(entryId))
                return;

            IInstance entry = (IInstance) snapshot.getObject(entryId);

            int keyId, valueId;
            keyId = valueId = entryId = -1;

            // The java.util.WeakHashMap$Entry class extends WeakReference which
            // in turns extends ObjectReference. Both, the Entry as well as the
            // ObjectReference class, define a member variable "next". Only the
            // first next must be processed (fields are ordered ascending the
            // inheritance chain, i.e. from class to super class)
            boolean nextFieldProcessed = false;

            for (Field field : entry.getFields())
            {
                if (!nextFieldProcessed && "next".equals(field.getName()))
                {
                    nextFieldProcessed = true;

                    if (field.getValue() != null)
                        entryId = ((ObjectReference) field.getValue()).getObjectId();
                }
                else
                {
                    if (field.getValue() == null)
                        continue;

                    if (field.getType() != IObject.Type.OBJECT)
                        continue;

                    if (info.getKeyField().equals(field.getName()))
                        keyId = ((ObjectReference) field.getValue()).getObjectId();

                    if ("value".equals(field.getName()))
                        valueId = ((ObjectReference) field.getValue()).getObjectId();
                }
            }

            hashEntries.add(new Entry(collectionId, collectionName, keyId, valueId));

            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }
    }

}

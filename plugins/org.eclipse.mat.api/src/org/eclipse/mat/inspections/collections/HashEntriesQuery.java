/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.collectionextract.HashMapCollectionExtractor;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;

@CommandName("hash_entries")
@Icon("/META-INF/icons/hash_map.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
@Subjects({"java.util.AbstractMap",
    "java.util.jar.Attributes",
    "java.util.Dictionary",
    "java.lang.ThreadLocal$ThreadLocalMap",
    "java.util.concurrent.ConcurrentHashMap$Segment",
    "java.util.concurrent.ConcurrentHashMap$CollectionView",
    "java.util.Collections$SynchronizedMap",
    "java.util.Collections$UnmodifiableMap",
    "java.util.Collections$CheckedMap",
    "java.util.ImmutableCollections$AbstractImmutableMap",
    "java.util.ResourceBundle",
    "java.awt.RenderingHints",
    "sun.awt.WeakIdentityHashMap",
    "javax.script.SimpleBindings",
    "javax.management.openmbean.TabularDataSupport",
    "com.ibm.jvm.util.HashMapRT",
    "com.sap.engine.lib.util.AbstractDataStructure"
})
public class HashEntriesQuery implements IQuery
{
    private static final String NULL = "<null>"; //$NON-NLS-1$

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String array_attribute;

    @Argument(isMandatory = false)
    public String key_attribute;

    @Argument(isMandatory = false)
    public String value_attribute;

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

                            .addContext(new ContextProvider(Messages.HashEntriesQuery_Column_Key)
                            {
                                public IContextObject getContext(Object row)
                                {
                                    return getKey(row);
                                }
                            }) //

                            .addContext(new ContextProvider(Messages.HashEntriesQuery_Column_Value)
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
            return new Column[] {
                            new Column(Messages.HashEntriesQuery_Column_Collection).sorting(Column.SortDirection.ASC), //
                            new Column(Messages.HashEntriesQuery_Column_Key), //
                            new Column(Messages.HashEntriesQuery_Column_Value) };
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
                    break;
            }
        }
    }

    public Result execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        listener.subTask(Messages.HashEntriesQuery_Msg_Extracting);

        IMapExtractor specificExtractor;
        if (collection != null)
        {
            specificExtractor = new HashMapCollectionExtractor(null, array_attribute, key_attribute, value_attribute);
        }
        else
        {
            specificExtractor = null;
        }

        List<Entry> hashEntries = new ArrayList<Entry>();

        for (int[] ids : objects)
        {
            for (int id : ids)
            {
                IObject obj = snapshot.getObject(id);
                ExtractedMap map = CollectionExtractionUtils.extractMap(obj, collection, specificExtractor);

                if (map != null)
                {
                    for (Map.Entry<IObject, IObject> me : map)
                    {
                        Entry e;
                        if (me instanceof IObject)
                        {
                            IObject meObject = (IObject) me;
                            int keyId = (me.getKey() != null) ? me.getKey().getObjectId() : 0;
                            int valueId = (me.getValue() != null) ? me.getValue().getObjectId() : 0;
                            e = new Entry(meObject.getObjectId(), meObject.getDisplayName(), keyId, valueId);
                        }
                        else
                        {
                            e = new Entry(id, obj.getDisplayName(), me.getKey().getObjectId(), me.getValue().getObjectId());
                        }
                        hashEntries.add(e);
                    }
                }

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }

        listener.done();
        return new Result(snapshot, hashEntries);
    }
}

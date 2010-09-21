/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("hash_entries")
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

					.addContext(new ContextProvider(Messages.HashEntriesQuery_Column_Key) {
						public IContextObject getContext(Object row)
						{
							return getKey(row);
						}
					}) //

					.addContext(new ContextProvider(Messages.HashEntriesQuery_Column_Value) {
						public IContextObject getContext(Object row)
						{
							return getValue(row);
						}
					}) //

					.build();
		}

		public Column[] getColumns()
		{
			return new Column[] { new Column(Messages.HashEntriesQuery_Column_Collection).sorting(Column.SortDirection.ASC), //
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
				if (entry.keyValue == null) entry.keyValue = resolve(entry.keyId);
				return entry.keyValue;
			case 2:
				if (entry.valueValue == null) entry.valueValue = resolve(entry.valueId);
				return entry.valueValue;
			}

			return null;
		}

		private String resolve(int objectId)
		{
			try
			{
				if (objectId < 0) return NULL;

				IObject object = snapshot.getObject(objectId);
				String name = object.getClassSpecificName();

				if (name == null) name = object.getTechnicalName();

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
			return new IContextObject() {
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
				return new IContextObject() {
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
				return new IContextObject() {
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

			if (entry == null) return null;

			if (entry.valueValue == null) entry.valueValue = resolve(entry.valueId);

			return entry.valueValue == NULL ? null : entry.valueValue;
		}

		public int getObjectId(String key, IProgressListener listener)
		{
			prepare(listener);

			Entry entry = key2entry.get(key);

			if (entry == null) return -1;

			return entry.keyId;
		}

		private synchronized void prepare(IProgressListener listener)
		{
			if (key2entry != null) return;

			key2entry = new HashMap<String, Entry>();

			for (Entry entry : entries)
			{
				if (entry.keyValue == null) entry.keyValue = resolve(entry.keyId);
				key2entry.put(entry.keyValue, entry);

				if (listener.isCanceled()) break;
			}
		}
	}

	public Result execute(IProgressListener listener) throws Exception
	{
		InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
		listener.subTask(Messages.HashEntriesQuery_Msg_Extracting);

		// prepare meta-data of known collections
		HashMapIntObject<CollectionUtil.Info> hashes = CollectionUtil.getKnownMaps(snapshot);

		if (collection != null)
		{
			if (array_attribute == null || key_attribute == null || value_attribute == null)
			{
				String msg = Messages.HashEntriesQuery_ErrorMsg_MissingArguments;
				throw new SnapshotException(msg);
			}

			CollectionUtil.Info info = new CollectionUtil.Info(collection, null, array_attribute, key_attribute, value_attribute)
					.setCollectionExtractor(CollectionUtil.HASH_MAP_EXTRACTOR);
			Collection<IClass> classes = snapshot.getClassesByName(collection, true);

			if (classes == null || classes.isEmpty())
			{
				listener.sendUserMessage(IProgressListener.Severity.WARNING, MessageUtil.format(Messages.HashEntriesQuery_ErrorMsg_ClassNotFound, collection),
						null);
			}
			else
			{
				for (IClass clasz : classes)
					hashes.put(clasz.getObjectId(), info);
			}
		}

		List<Entry> hashEntries = new ArrayList<Entry>();

		for (int[] ids : objects)
		{
			for (int id : ids)
			{
				IClass clazz = snapshot.getClassOf(id);
				CollectionUtil.Info info = hashes.get(clazz.getObjectId());
				if (info == null) continue;

				ICollectionExtractor extractor = info.getCollectionExtractor();
				if (extractor != null)
				{
					int[] entryIds = extractor.extractEntries(id, info, snapshot, listener);
					for (int entryId : entryIds)
					{
						collectEntry(hashEntries, info, id, snapshot.getObject(id).getDisplayName(), entryId, listener);
					}
				}

				if (listener.isCanceled()) throw new IProgressListener.OperationCanceledException();
			}
		}

		listener.done();

		return new Result(snapshot, hashEntries);
	}

	private void collectEntry(List<Entry> hashEntries, CollectionUtil.Info info, int collectionId, String collectionName, int entryId,
			IProgressListener listener) throws SnapshotException
	{
		IInstance entry = (IInstance) snapshot.getObject(entryId);
		int keyId, valueId;
		keyId = valueId = entryId = -1;

		for (Field field : entry.getFields())
		{
			if (field.getValue() == null) continue;

			if (field.getType() != IObject.Type.OBJECT) continue;

			if (info.getEntryKeyField().equals(field.getName())) keyId = ((ObjectReference) field.getValue()).getObjectId();

			if (info.getEntryValueField().equals(field.getName())) valueId = ((ObjectReference) field.getValue()).getObjectId();
		}
		hashEntries.add(new Entry(collectionId, collectionName, keyId, valueId));

	}

}

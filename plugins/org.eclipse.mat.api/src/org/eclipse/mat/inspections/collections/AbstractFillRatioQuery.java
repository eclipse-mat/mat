/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.ArrayLongBig;
import org.eclipse.mat.collect.HashMapIntLong;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.collect.HashMapObjectLong;
import org.eclipse.mat.collect.QueueInt;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.collect.SetLong;
import org.eclipse.mat.inspections.collectionextract.AbstractExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class AbstractFillRatioQuery
{
    protected void runQuantizer(IProgressListener listener, Quantize quantize, ICollectionExtractor specificExtractor,
                    String specificClass, ISnapshot snapshot, Iterable<int[]> objects, String msg) throws SnapshotException
    {
        SnapshotInfo info = snapshot.getSnapshotInfo();
        final int refsize = info.getIdentifierSize() == 8
                        && Boolean.TRUE.equals((Boolean) info.getProperty("$useCompressedOops")) //$NON-NLS-1$
                                        ? 4
                                        : info.getIdentifierSize();
        final long LIMIT = 20;
        HashMapIntLong exceptions = new HashMapIntLong();
        int counter = 0;
        IClass type = null;
        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    return;

                IObject obj = snapshot.getObject(objectId);
                if (counter++ % 1000 == 0 && !obj.getClazz().equals(type))
                {
                    type = obj.getClazz();
                    listener.subTask(msg + "\n" + type.getName()); //$NON-NLS-1$
                }
                try
                {
                    AbstractExtractedCollection<?, ?> coll = CollectionExtractionUtils.extractCollection(obj, specificClass,
                                    specificExtractor);
                    if (coll != null)
                    {
                        Double fillRatio = coll.getFillRatio();
                        if (fillRatio != null)
                        {
                            long wasted = 0;
                            if (coll.hasCapacity())
                            {
                                Integer c = coll.getCapacity();
                                if (c != null)
                                {
                                    // These don't have reference sized slots
                                    int refsize2;
                                    if (obj.getClazz().getName().equals(SetInt.class.getName()))
                                        refsize2 = 4;
                                    else if (obj.getClazz().getName().equals(ArrayInt.class.getName()))
                                        refsize2 = 4;
                                    else if (obj.getClazz().getName().equals(QueueInt.class.getName()))
                                        refsize2 = 4;
                                    else if (obj.getClazz().getName().equals(SetLong.class.getName()))
                                        refsize2 = 8;
                                    else if (obj.getClazz().getName().equals(ArrayLong.class.getName()))
                                        refsize2 = 8;
                                    else if (obj.getClazz().getName().equals(HashMapIntLong.class.getName()))
                                        refsize2 = 13;
                                    else if (obj.getClazz().getName().equals(HashMapIntObject.class.getName()))
                                        refsize2 = 5 + refsize;
                                    else if (obj.getClazz().getName().equals(HashMapLongObject.class.getName()))
                                        refsize2 = 9 + refsize;
                                    else if (obj.getClazz().getName().equals(HashMapObjectLong.class.getName()))
                                        refsize2 = 9 + refsize;
                                    else if (obj.getClazz().getName().equals(ArrayIntBig.class.getName()))
                                        refsize2 = 4;
                                    else if (obj.getClazz().getName().equals(ArrayLongBig.class.getName()))
                                        refsize2 = 8;
                                    else
                                        refsize2 = refsize;
                                    wasted = (long)(c * refsize2 * (1 - fillRatio));
                                }
                            }
                            else if (coll.hasExtractableArray())
                            {
                                IObjectArray backing = coll.extractEntries();
                                if (backing != null)
                                {
                                    wasted = (long)(backing.getClazz().getHeapSizePerInstance() * (1 - fillRatio));
                                }
                            }
                            else if (coll.hasSize())
                            {
                                Integer size = coll.size();
                                if (size != null)
                                {
                                    int s = size;
                                    // Try to have some limits on what might be calculated
                                    if (fillRatio > 0)
                                    {
                                        wasted = (long) Math.min((s * refsize / (1 - fillRatio)),
                                                        snapshot.getSnapshotInfo().getUsedHeapSize());
                                    }
                                }
                            }
                            quantize.addValue(objectId, fillRatio, 1, new Bytes(coll.getUsedHeapSize()), new Bytes(wasted));
                        }
                    }
                }
                catch (RuntimeException e)
                {
                    int classId = obj.getClazz().getObjectId();
                    if (!exceptions.containsKey(classId))
                    {
                        exceptions.put(classId, 0);
                    }
                    long c =  exceptions.get(classId);
                    exceptions.put(classId, c + 1);
                    if (c < LIMIT)
                    {
                        listener.sendUserMessage(
                                        IProgressListener.Severity.INFO,
                                        MessageUtil.format(Messages.CollectionFillRatioQuery_IgnoringCollection,
                                                        obj.getTechnicalName()), e);
                    }
                }
            }
        }
    }
}

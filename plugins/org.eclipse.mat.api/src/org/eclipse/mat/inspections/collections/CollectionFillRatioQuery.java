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

import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.collectionextract.FieldSizeArrayCollectionExtractor;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;

@CommandName("collection_fill_ratio")
@Icon("/META-INF/icons/collection_fill.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
@Subjects({"java.util.AbstractCollection",
    "java.util.jar.Attributes",
    "java.util.Dictionary",
    "java.lang.ThreadLocal$ThreadLocalMap",
    "java.util.concurrent.ConcurrentHashMap$Segment",
    "java.util.concurrent.ConcurrentHashMap$CollectionView",
    "java.util.concurrent.CopyOnWriteArrayList",
    "java.util.Collections$SynchronizedCollection",
    "java.util.Collections$SynchronizedMap",
    "java.util.Collections$UnmodifiableCollection",
    "java.util.Collections$UnmodifiableMap",
    "java.util.Collections$CheckedCollection",
    "java.util.Collections$CheckedMap",
    "java.util.Collections$CheckedMap$CheckedEntrySet",
    "java.util.Collections$SetFromMap",
    "java.util.Properties$EntrySet",
    "java.util.ResourceBundle",
    "java.awt.RenderingHints",
    "java.beans.beancontext.BeanContextSupport",
    "sun.awt.WeakIdentityHashMap",
    "javax.script.SimpleBindings",
    "javax.management.openmbean.TabularDataSupport",
    "com.ibm.jvm.util.HashMapRT",
    "com.sap.engine.lib.util.AbstractDataStructure",
    "org.eclipse.mat.collect.SetInt",
    "org.eclipse.mat.collect.SetLong",
    "org.eclipse.mat.collect.QueueInt",
    "org.eclipse.mat.collect.ArrayInt",
    "org.eclipse.mat.collect.ArrayLong",
    "org.eclipse.mat.collect.HashMapIntLong",
    "org.eclipse.mat.collect.HashMapIntObject",
    "org.eclipse.mat.collect.HashMapLongObject",
    "org.eclipse.mat.collect.HashMapObjectLong",
    "org.eclipse.mat.collect.ArrayIntBig",
    "org.eclipse.mat.collect.ArrayLongBig",

    "java.util.AbstractMap",
    "java.util.jar.Attributes",
    "java.util.Dictionary",
    "java.lang.ThreadLocal$ThreadLocalMap",
    "java.util.concurrent.ConcurrentHashMap$Segment",
    "java.util.concurrent.ConcurrentHashMap$CollectionView",
    "java.util.Collections$SynchronizedMap",
    "java.util.Collections$UnmodifiableMap",
    "java.util.Collections$CheckedMap",
    "java.util.Collections$SetFromMap",
    "java.util.ResourceBundle",
    "java.beans.beancontext.BeanContextSupport",
    "sun.awt.WeakIdentityHashMap",
    "javax.script.SimpleBindings",
    "javax.management.openmbean.TabularDataSupport",
    "com.ibm.jvm.util.HashMapRT",
    "com.sap.engine.lib.util.AbstractDataStructure"
})
public class CollectionFillRatioQuery extends AbstractFillRatioQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public int segments = 5;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String size_attribute;

    @Argument(isMandatory = false)
    public String array_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.CollectionFillRatioQuery_ExtractingFillRatios);

        // create frequency distribution
        // The load factor should be <= 1, but for old PHD files with inaccurate array sizes can appear > 1.
        // Therefore we have a larger upper bound of 5, not 1 just in case
        // Using 5.0 and Quantize counting back from 5.0 using the reciprocal always seems to give 1.000 or 1.000+ 
        Quantize.Builder builder = Quantize.linearFrequencyDistribution(
                        Messages.CollectionFillRatioQuery_Column_FillRatio, 0, 5.0000000000, (double) 1 / (double) segments);
        builder.column(Messages.CollectionFillRatioQuery_ColumnNumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_BYTES);
        builder.column(Messages.Column_WastedHeap, Quantize.SUM_BYTES);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();


        ICollectionExtractor specificExtractor;
        if (size_attribute != null && array_attribute != null)
        {
            specificExtractor = new FieldSizeArrayCollectionExtractor(size_attribute, array_attribute);
        }
        else if (size_attribute == null && array_attribute == null)
        {
            specificExtractor = null;
        }
        else
        {
            throw new IllegalArgumentException(Messages.CollectionFillRatioQuery_NeedSizeAndArrayOrNone);
        }

        runQuantizer(listener, quantize, specificExtractor, collection, snapshot, objects, Messages.CollectionFillRatioQuery_ExtractingFillRatios);
        return quantize.getResult();
    }
}

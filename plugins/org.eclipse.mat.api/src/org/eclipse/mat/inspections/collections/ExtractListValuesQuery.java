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

import org.eclipse.mat.inspections.collectionextract.AbstractExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("extract_list_values")
@Icon("/META-INF/icons/list.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
@Subjects({"java.util.AbstractList",
    "java.util.AbstractQueue",
    "java.util.IdentityHashMap$Values",
    "java.util.LinkedHashMap$LinkedValues",
    "java.util.HashMap$Values", 
    "java.util.WeakHashMap$Values",
    "java.util.TreeMap$Values",
    "java.util.ArrayDeque",
    "java.util.AbstractMap$2",
    "java.util.AbstractQueue",
    "java.util.Hashtable$ValueCollection",
    "java.util.Collections$SynchronizedList",
    "java.util.Collections$UnmodifiableList",
    "java.util.Collections$CheckedList",
    "java.util.Collections$CheckedQueue",
    "java.util.Collections$EmptyList",

    "java.util.ImmutableCollections$AbstractImmutableList",
    "java.util.ImmutableCollect0ions$List0",
    "java.util.concurrent.CopyOnWriteArrayList",
    "java.util.concurrent.ConcurrentLinkedDeque",

    "java.util.EnumMap$Values",
    "java.lang.ProcessEnvironment$CheckedValues",
    "java.lang.ProcessEnvironment$StringValues",
})
public class ExtractListValuesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IObject list;

    public IResult execute(IProgressListener listener) throws Exception
    {
        AbstractExtractedCollection extractor = CollectionExtractionUtils.extractCollection(list);
        // FIXME: use a better message when it is a list but it's
        // non-extractable?
        if (extractor != null && extractor.hasExtractableContents())
        { // FIXME: keep this? && !extractor.isMap()
            return new ObjectListResult.Outbound(snapshot, extractor.extractEntryIds());
        }
        else
        {
            throw new IllegalArgumentException(MessageUtil.format(Messages.ExtractListValuesQuery_NotAWellKnownList,
                            list.getDisplayName()));
        }
    }
}

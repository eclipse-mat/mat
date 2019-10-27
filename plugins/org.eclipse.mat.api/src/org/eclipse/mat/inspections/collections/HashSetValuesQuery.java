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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.collectionextract.HashSetCollectionExtractor;
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

@CommandName("hash_set_values")
@Icon("/META-INF/icons/hash_set.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
@Subjects({"java.util.AbstractSet",
    "java.util.Collections$SynchronizedSet",
    "java.util.Collections$UnmodifiableSet",
    "java.util.Collections$CheckedSet",
    "java.util.Collections$CheckedMap$CheckedEntrySet",
    "java.util.Collections$SetFromMap",

    "java.util.concurrent.ConcurrentHashMap$KeySetView",
    "java.util.concurrent.ConcurrentHashMap$EntrySetView",
    "java.util.ImmutableCollections$AbstractImmutableSet",
    "java.util.ImmutableCollections$Set",
    "java.beans.beancontext.BeanContextSupport",
})
public class HashSetValuesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IObject hashSet;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String array_attribute;

    @Argument(isMandatory = false)
    public String key_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        ExtractedMap extractor;
        if (collection != null && hashSet.getClazz().doesExtend(collection))
        {
            if (array_attribute == null || key_attribute == null)
            {
                String msg = Messages.HashSetValuesQuery_ErrorMsg_MissingArgument;
                throw new SnapshotException(msg);
            }
            extractor = new ExtractedMap(hashSet, new HashSetCollectionExtractor(array_attribute, key_attribute));
        }
        else
        {
            extractor = CollectionExtractionUtils.extractMap(hashSet);
            if (extractor == null)
            {
                throw new IllegalArgumentException(hashSet.getTechnicalName());
            }
        }

        // TODO: refactor out code with ExtractListValuesQuery
        int[] result;
        if (!extractor.hasSize())
        {
            result = new int[0];
        }
        else if (extractor.hasExtractableContents())
        {
            result = extractor.extractEntryIds();
        }
        else
        {
            throw new IllegalArgumentException(hashSet.getTechnicalName());
        }

        return new ObjectListResult.Outbound(snapshot, result);
    }
}

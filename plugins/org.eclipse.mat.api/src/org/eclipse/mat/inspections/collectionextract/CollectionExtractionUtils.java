/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
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
package org.eclipse.mat.inspections.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.collectionextract.ArrayCollectionExtractor;
import org.eclipse.mat.internal.collectionextract.KnownCollectionInfo;
import org.eclipse.mat.internal.collectionextract.KnownCollectionInfo.Info;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

/**
 * @since 1.5
 */
public class CollectionExtractionUtils
{
    // in the original code, from-object searches in reverse order and
    // from-classname goes forwards?!?
    public static ICollectionExtractor findCollectionExtractor(IObject coll) throws SnapshotException
    {
        if (coll == null)
            return null;
        if (coll instanceof IObjectArray)
            return ArrayCollectionExtractor.INSTANCE;

        int version = KnownCollectionInfo.resolveVersion(coll.getSnapshot());
        IClass collectionClass = coll.getClazz();

        for (Info info : KnownCollectionInfo.knownCollections)
        {
            if ((info.version & version) == version)
            {
                if (collectionClass.doesExtend(info.className)) { return info.extractor; }
            }
        }

        return null;
    }

    public static ICollectionExtractor findCollectionExtractor(String className) throws SnapshotException
    {
        for (Info info : KnownCollectionInfo.knownCollections)
        {
            if (info.className.equals(className))
                return info.extractor;
        }
        return null;
    }

    public static AbstractExtractedCollection<?, ?> extractCollection(IObject coll) throws SnapshotException
    {
        ICollectionExtractor extractor = findCollectionExtractor(coll);
        if (extractor == null)
            return null;
        else if (extractor instanceof IMapExtractor)
            return new ExtractedMap(coll, (IMapExtractor) extractor);
        else
            return new ExtractedCollection(coll, extractor);
    }

    public static AbstractExtractedCollection<?, ?> extractCollection(IObject coll, String specificClass,
                    ICollectionExtractor preferredExtractor) throws SnapshotException
    {
        if (specificClass != null && coll.getClazz().doesExtend(specificClass))
        {
            return new ExtractedCollection(coll, preferredExtractor);
        }
        else
        {
            return extractCollection(coll);
        }
    }

    public static ExtractedMap extractMap(IObject coll, String specificClass,
                    IMapExtractor preferredExtractor) throws SnapshotException
    {
        if (specificClass != null && coll.getClazz().doesExtend(specificClass))
        {
            return new ExtractedMap(coll, preferredExtractor);
        }
        else
        {
            return extractMap(coll);
        }
    }

    public static ExtractedCollection extractList(IObject coll) throws SnapshotException
    {
        ICollectionExtractor extractor = findCollectionExtractor(coll);
        if (extractor == null)
            return null;
        else
            return new ExtractedCollection(coll, extractor);
    }

    public static ExtractedMap extractMap(IObject coll) throws SnapshotException
    {
        ICollectionExtractor extractor = findCollectionExtractor(coll);
        if (extractor == null)
            return null;
        else if (extractor instanceof IMapExtractor)
            return new ExtractedMap(coll, (IMapExtractor) extractor);
        else
            return null;
    }
}

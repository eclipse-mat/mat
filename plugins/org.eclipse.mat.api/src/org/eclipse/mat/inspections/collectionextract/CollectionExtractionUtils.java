/*******************************************************************************
 * Copyright (c) 2008, 2016 SAP AG, IBM Corporation and others
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
import org.eclipse.mat.internal.collectionextract.ExtractionUtils;
import org.eclipse.mat.snapshot.extension.CollectionExtractionInfo;
import org.eclipse.mat.snapshot.extension.JdkVersion;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.registry.CollectionExtractorProviderRegistry;

/**
 * Utility class providing helpers simplifying the extraction of data from
 * objects in the heap which represent collections
 * 
 * @since 1.5
 */
public class CollectionExtractionUtils
{

    /**
     * Finds a proper ICollectionExtractor for the object passed as parameter
     * 
     * @param collection
     *            An IObject representing a collection
     * @return ICollectionExtractor an extractor object for the collection
     * @throws SnapshotException
     */
    public static ICollectionExtractor findCollectionExtractor(IObject collection) throws SnapshotException
    {
        // in the original code, from-object searches in reverse order and
        // from-classname goes forwards?!?
        if (collection == null)
            return null;
        if (collection instanceof IObjectArray)
            return ArrayCollectionExtractor.INSTANCE;

        JdkVersion version = ExtractionUtils.resolveVersion(collection.getSnapshot());
        IClass collectionClass = collection.getClazz();

        for (CollectionExtractionInfo info : CollectionExtractorProviderRegistry.instance().getCollectionExtractionInfo())
        {
            if (info.version.contains(version))
            {
                if (collectionClass.doesExtend(info.className)) { return info.extractor; }
            }
        }

        return null;
    }

    /**
     * Finds a proper ICollectionExtractor for the object passed as parameter
     * 
     * @param className
     *            the name of a collection class
     * @return ICollectionExtractor an extractor object for the collection
     * @throws SnapshotException
     */
    public static ICollectionExtractor findCollectionExtractor(String className) throws SnapshotException
    {
        // FIXME? this does not account for JDK versions
        for (CollectionExtractionInfo info : CollectionExtractorProviderRegistry.instance().getCollectionExtractionInfo())
        {
            if (info.className.equals(className))
                return info.extractor;
        }
        return null;
    }

    /**
     * Extracts from the heap the content of objects which represent a
     * collection. The proper extractor is determined internally
     * 
     * @param collection
     *            an IObject representing a collection for which to extract the
     *            contents
     * @return AbstractExtractedCollection the extracted contents
     * @throws SnapshotException
     */
    public static AbstractExtractedCollection<?, ?> extractCollection(IObject collection) throws SnapshotException
    {
        ICollectionExtractor extractor = findCollectionExtractor(collection);
        if (extractor == null)
            return null;
        else if (extractor instanceof IMapExtractor)
            return new ExtractedMap(collection, (IMapExtractor) extractor);
        else
            return new ExtractedCollection(collection, extractor);
    }

    /**
     * Extracts from the heap the content of objects which represent a
     * collection. For objects of class specifiedClass the preferredExtractor
     * will be used, otherwise the extractor is selected internally
     * 
     * @param collection
     *            an IObject representing a collection for which to extract the
     *            contents
     * @param specificClass
     *            a class name for which the preferred extractor should be used
     * @param preferredExtractor
     *            an extractor object to be used to extract the contents
     * @return the extracted contents
     * @throws SnapshotException
     */
    public static AbstractExtractedCollection<?, ?> extractCollection(IObject collection, String specificClass,
                    ICollectionExtractor preferredExtractor) throws SnapshotException
    {
        if (specificClass != null && collection.getClazz().doesExtend(specificClass))
        {
            return new ExtractedCollection(collection, preferredExtractor);
        }
        else
        {
            return extractCollection(collection);
        }
    }

    /**
     * Extracts from the heap the content of objects which represent a
     * collection. The proper extractor is determined internally
     * 
     * @param collection
     *            an IObject representing a List for which to extract the
     *            contents
     * @return ExtractedCollection the extracted contents
     * @throws SnapshotException
     */
    public static ExtractedCollection extractList(IObject collection) throws SnapshotException
    {
        ICollectionExtractor extractor = findCollectionExtractor(collection);
        if (extractor == null)
            return null;
        else
            return new ExtractedCollection(collection, extractor);
    }

    /**
     * Extracts from the heap the content of objects which represent a Map. The
     * proper extractor is determined internally
     * 
     * @param collection
     *            an IObject representing a Map for which to extract the
     *            contents
     * @return ExtractedMap the extracted contents
     * @throws SnapshotException
     */
    public static ExtractedMap extractMap(IObject collection) throws SnapshotException
    {
        ICollectionExtractor extractor = findCollectionExtractor(collection);
        if (extractor == null)
            return null;
        else if (extractor instanceof IMapExtractor)
            return new ExtractedMap(collection, (IMapExtractor) extractor);
        else
            return null;
    }

    /**
     * Extracts from the heap the content of objects which represent a Map. For
     * objects of class specifiedClass the preferredExtractor will be used,
     * otherwise the extractor is selected internally
     * 
     * @param collection
     *            an IObject representing a Map for which to extract the
     *            contents
     * @param specificClass
     *            a class name for which the preferred extractor should be used
     * @param preferredExtractor
     *            an extractor object to be used to extract the contents
     * @return ExtractedMap the extracted contents
     * @throws SnapshotException
     */
    public static ExtractedMap extractMap(IObject collection, String specificClass, IMapExtractor preferredExtractor)
                    throws SnapshotException
    {
        if (specificClass != null && collection.getClazz().doesExtend(specificClass))
        {
            return new ExtractedMap(collection, preferredExtractor);
        }
        else
        {
            return extractMap(collection);
        }
    }
}

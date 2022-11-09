/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.model;

import java.util.Date;

import org.eclipse.mat.snapshot.SnapshotInfo;

/**
 * Basic information about the snapshot which can be updated as 
 * data is read from the dump.
 */
public final class XSnapshotInfo extends SnapshotInfo
{
    private static final long serialVersionUID = 3L;

    /**
     * Simple constructor, with real data added later using setters.
     */
    public XSnapshotInfo()
    {
        super(null, null, null, 0, null, 0, 0, 0, 0, 0);
    }

    /**
     * Set the prefix used for index files.
     * @param prefix the path prefix
     * @see #getPrefix()
     */
    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
    }

    /**
     * Sets the path to the snapshot file.
     * @param path the file path
     * @see #getPath()
     */
    public void setPath(String path)
    {
        this.path = path;
    }

    /**
     * Sets the creation date of the snapshot
     * @param creationDate the date of creation
     * @see #getCreationDate()
     */
    public void setCreationDate(Date creationDate)
    {
        this.creationDate = new Date(creationDate.getTime());
    }

    /**
     * Sets the identifier size for the snapshot.
     * @param identifierSize the size in bits
     * @see #getIdentifierSize()
     */
    public void setIdentifierSize(int identifierSize)
    {
        this.identifierSize = identifierSize;
    }

    /**
     * Sets information about the JVM.
     * @param jvmInfo a short description of the JVM
     * @see #getJvmInfo()
     */
    public void setJvmInfo(String jvmInfo)
    {
        this.jvmInfo = jvmInfo;
    }

    /**
     * Sets the total number of classes.
     * @param numberOfClasses how many classes
     * @see #getNumberOfClasses()
     */
    public void setNumberOfClasses(int numberOfClasses)
    {
        this.numberOfClasses = numberOfClasses;
    }

    /**
     * Sets the total number of class loaders.
     * @param numberOfClassLoaders how many class loaders
     * @see #getNumberOfClassLoaders()
     */
    public void setNumberOfClassLoaders(int numberOfClassLoaders)
    {
        this.numberOfClassLoaders = numberOfClassLoaders;
    }

    /**
     * Sets the number of GC roots.
     * @param numberOfGCRoots the number of GC roots
     * @see #getNumberOfGCRoots()
     */
    public void setNumberOfGCRoots(int numberOfGCRoots)
    {
        this.numberOfGCRoots = numberOfGCRoots;
    }

    /**
     * Sets the number of objects.
     * @param numberOfObjects the total number of objects
     * @see #getNumberOfObjects()
     */
    public void setNumberOfObjects(int numberOfObjects)
    {
        this.numberOfObjects = numberOfObjects;
    }

    /**
     * Sets the total used heap size.
     * @param usedHeapSize the total heap size in bytes
     * @see #getUsedHeapSize()
     */
    public void setUsedHeapSize(long usedHeapSize)
    {
        this.usedHeapSize = usedHeapSize;
    }
}

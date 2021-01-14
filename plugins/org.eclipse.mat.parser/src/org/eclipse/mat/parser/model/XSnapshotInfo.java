/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
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
     * 
     * @param prefix
     * @see #getPrefix()
     */
    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
    }

    /**
     * 
     * @param path
     * @see #getPath()
     */
    public void setPath(String path)
    {
        this.path = path;
    }

    /**
     * 
     * @param creationDate
     * @see #getCreationDate()
     */
    public void setCreationDate(Date creationDate)
    {
        this.creationDate = new Date(creationDate.getTime());
    }

    /**
     * 
     * @param identifierSize
     * @see #getIdentifierSize()
     */
    public void setIdentifierSize(int identifierSize)
    {
        this.identifierSize = identifierSize;
    }

    /**
     * 
     * @param jvmInfo
     * @see #getJvmInfo()
     */
    public void setJvmInfo(String jvmInfo)
    {
        this.jvmInfo = jvmInfo;
    }

    /**
     * 
     * @param numberOfClasses
     * @see #getNumberOfClasses()
     */
    public void setNumberOfClasses(int numberOfClasses)
    {
        this.numberOfClasses = numberOfClasses;
    }

    /**
     * 
     * @param numberOfClassLoaders
     * @see #getNumberOfClassLoaders()
     */
    public void setNumberOfClassLoaders(int numberOfClassLoaders)
    {
        this.numberOfClassLoaders = numberOfClassLoaders;
    }

    /**
     * 
     * @param numberOfGCRoots
     * @see #getNumberOfGCRoots()
     */
    public void setNumberOfGCRoots(int numberOfGCRoots)
    {
        this.numberOfGCRoots = numberOfGCRoots;
    }

    /**
     * 
     * @param numberOfObjects
     * @see #getNumberOfObjects()
     */
    public void setNumberOfObjects(int numberOfObjects)
    {
        this.numberOfObjects = numberOfObjects;
    }

    /**
     * 
     * @param usedHeapSize
     * @see #getUsedHeapSize()
     */
    public void setUsedHeapSize(long usedHeapSize)
    {
        this.usedHeapSize = usedHeapSize;
    }
}

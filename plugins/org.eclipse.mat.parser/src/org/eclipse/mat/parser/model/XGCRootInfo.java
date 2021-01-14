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

import org.eclipse.mat.snapshot.model.GCRootInfo;

/**
 * Holds details about a garbage collection root.
 * Allows the object id and the context id (the source reference) to be set
 * once the snapshot is reindexed.
 */
public final class XGCRootInfo extends GCRootInfo
{
    private static final long serialVersionUID = 1L;

    /**
     * Create a record of one GC root.
     * @param objectAddress the object being retained
     * @param contextAddress the object doing the retention such as a thread
     * @param type the type {@link org.eclipse.mat.snapshot.model.GCRootInfo.Type} of the root such as Java local, system class.
     */
    public XGCRootInfo(long objectAddress, long contextAddress, int type)
    {
        super(objectAddress, contextAddress, type);
    }

    /**
     * @param objectId the object
     * @see #getObjectId()
     */
    public void setObjectId(int objectId)
    {
        this.objectId = objectId;
    }

    /**
     * 
     * @param objectId
     * @see #getContextId()
     */
    public void setContextId(int objectId)
    {
        this.contextId = objectId;
    }
}

/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import org.eclipse.mat.snapshot.ISnapshot;

/**
 * The class represents a references from a running thread object to objects
 * which are local for this thread. Such objects could be for example java local
 * variables, objects used for synchronization in this thread, etc...
 */
public class ThreadToLocalReference extends PseudoReference
{
    private static final long serialVersionUID = 1L;
    private int localObjectId;
    private GCRootInfo[] gcRootInfo;

    /**
     * Create a thread to local reference
     * @param snapshot the snapshot
     * @param address the address of the object
     * @param name the description of the reference e.g. the root types surrounded by '<' '>'
     * @param localObjectId the local reference object id
     * @param gcRootInfo a description of the root type e.g. Java local etc.
     */
    public ThreadToLocalReference(ISnapshot snapshot, long address, String name, int localObjectId,
                    GCRootInfo[] gcRootInfo)
    {
        super(snapshot, address, name);
        this.localObjectId = localObjectId;
        this.gcRootInfo = gcRootInfo;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.mat.snapshot.model.ObjectReference#getObjectId()
     */
    @Override
    public int getObjectId()
    {
        return localObjectId;
    }

    /**
     * The description of the thread root information
     * Not currently used, so might be removed.
     * @return an array of GC information for the local reference
     */
    public GCRootInfo[] getGcRootInfo()
    {
        return gcRootInfo;
    }
}

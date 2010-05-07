/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.views.inspector;

import org.eclipse.mat.snapshot.model.NamedReference;

/* package */class NamedReferenceNode
{
    String name;
    long objectAddress;
    boolean isStatic;

    /**
     * must NOT keep a reference to the NamedReference object because the
     * TableViewerEditor will cache this node, and therefore the NameReference
     * and therefore the snapshot
     */
    public NamedReferenceNode(NamedReference reference, boolean isStatic)
    {
        this(reference.getName(), reference.getObjectAddress(), isStatic);
    }

    public NamedReferenceNode(String name, long objectAddress, boolean isStatic)
    {
        this.name = name;
        this.objectAddress = objectAddress;
        this.isStatic = isStatic;
    }

    public boolean isStatic()
    {
        return isStatic;
    }

    public String getName()
    {
        return name;
    }

    public long getObjectAddress()
    {
        return objectAddress;
    }
}

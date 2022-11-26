/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM) - handle missing objects
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.views.inspector;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.ui.snapshot.views.inspector.InspectorView.BaseNode;

/* package */class InspectorContextProvider extends ContextProvider
{
    ISnapshot snapshot;

    public InspectorContextProvider(ISnapshot snapshot)
    {
        super((String) null);
        this.snapshot = snapshot;
    }

    @Override
    public IContextObject getContext(Object row)
    {
        if (row instanceof NamedReferenceNode)
        {
            NamedReferenceNode node = (NamedReferenceNode) row;
            try
            {
                final int objectId = snapshot.mapAddressToId(node.objectAddress);
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return objectId;
                    }
                };
            }
            catch (SnapshotException e)
            {
                return new IContextObjectSet() {

                    @Override
                    public int getObjectId()
                    {
                        return -1;
                    }

                    @Override
                    public int[] getObjectIds()
                    {
                        return new int[0];
                    }

                    @Override
                    public String getOQL()
                    {
                        // Used when the address does not convert to an object ID,
                        // such as when objects are discarded.
                        return OQL.forAddress(node.objectAddress);
                    }
                };
            }
        }
        else if (row instanceof BaseNode)
        {
            final BaseNode node = (BaseNode) row;

            if (node.objectId >= 0)
                return new IContextObject()
            {
                public int getObjectId()
                {
                    return node.objectId;
                }
            };
            if (node.addr != Long.MIN_VALUE)
                return new IContextObjectSet()
            {

                @Override
                public int getObjectId()
                {
                    return -1;
                }

                @Override
                public int[] getObjectIds()
                {
                    return new int[0];
                }

                @Override
                public String getOQL()
                {
                    return OQL.forAddress(node.addr);
                }
            };
        }
        else if (row instanceof IClass)
        {
            final IClass clazz = (IClass) row;

            return new IContextObject()
            {
                public int getObjectId()
                {
                    return clazz.getObjectId();
                }
            };
        }
        return null;
    }
}

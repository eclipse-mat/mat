/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.views.inspector;

import org.eclipse.mat.snapshot.model.Field;

/* package */class FieldNode
{
    Field field;
    boolean isStatic;

    public FieldNode(Field reference, boolean isStatic)
    {
        this.field = reference;
        this.isStatic = isStatic;
    }

    public Field getField()
    {
        return field;
    }

    public boolean isStatic()
    {
        return isStatic;
    }
}

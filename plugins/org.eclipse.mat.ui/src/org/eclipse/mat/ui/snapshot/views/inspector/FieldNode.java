/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
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
package org.eclipse.mat.ui.snapshot.views.inspector;

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

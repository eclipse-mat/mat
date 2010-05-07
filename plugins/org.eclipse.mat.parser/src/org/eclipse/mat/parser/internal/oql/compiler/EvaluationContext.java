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
package org.eclipse.mat.parser.internal.oql.compiler;

import org.eclipse.mat.snapshot.ISnapshot;

public class EvaluationContext
{
    EvaluationContext parent;

    ISnapshot snapshot;
    Object subject;

    String alias;

    public EvaluationContext(EvaluationContext parent)
    {
        this.parent = parent;
        this.snapshot = parent != null ? parent.snapshot : null;
    }

    public ISnapshot getSnapshot()
    {
        return snapshot != null ? snapshot : parent != null ? parent.getSnapshot() : null;
    }

    public void setSnapshot(ISnapshot snapshot)
    {
        this.snapshot = snapshot;
    }

    public Object getSubject()
    {
        return subject;
    }

    public void setSubject(Object subject)
    {
        this.subject = subject;
    }

    public Object getAlias(String name)
    {
        if (name == null)
            return null;

        if (name.equals(alias))
            return subject;

        if ("snapshot".equals(name))//$NON-NLS-1$
            return snapshot;

        if (parent != null)
            return parent.getAlias(name);

        return null;
    }

    public void setAlias(String alias)
    {
        this.alias = alias;
    }

    public boolean isAlias(String value)
    {
        if (this.alias != null && this.alias.equals(value))
            return true;

        if (this.parent == null)
            return false;

        return this.parent.isAlias(value);
    }
}

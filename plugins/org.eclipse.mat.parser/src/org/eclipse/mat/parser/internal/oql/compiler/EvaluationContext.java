/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - progress listener
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

public class EvaluationContext
{
    EvaluationContext parent;

    ISnapshot snapshot;
    Object subject;
    IProgressListener listener;

    String alias;

    public EvaluationContext(EvaluationContext parent)
    {
        this.parent = parent;
        this.snapshot = parent != null ? parent.snapshot : null;
        this.listener = parent != null ? parent.listener : null;
    }

    public ISnapshot getSnapshot()
    {
        return snapshot != null ? snapshot : parent != null ? parent.getSnapshot() : null;
    }

    public void setSnapshot(ISnapshot snapshot)
    {
        this.snapshot = snapshot;
    }

    public IProgressListener getProgressListener()
    {
        return listener != null ? listener : parent != null ? parent.getProgressListener() : null;
    }

    public void setProgressListener(IProgressListener listener)
    {
        this.listener = listener;
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

    public String toString()
    {
        String val = alias + "=" + getSubject() + ";"; //$NON-NLS-1$ //$NON-NLS-2$
        return (parent != null ? val + parent.toString() : val);
    }
}

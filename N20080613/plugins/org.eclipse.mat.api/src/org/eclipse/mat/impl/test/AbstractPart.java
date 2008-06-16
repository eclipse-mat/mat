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
package org.eclipse.mat.impl.test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.test.ITestResult;
import org.eclipse.mat.test.QuerySpec;
import org.eclipse.mat.test.SectionSpec;
import org.eclipse.mat.test.Spec;
import org.eclipse.mat.util.IProgressListener;


public abstract class AbstractPart
{
    private static long ID_GENERATOR = 1;

    // //////////////////////////////////////////////////////////////
    // factory method
    // //////////////////////////////////////////////////////////////

    public static AbstractPart build(SectionPart parent, Spec spec)
    {
        if (spec instanceof SectionSpec)
            return new SectionPart(parent, (SectionSpec) spec);
        else if (spec instanceof QuerySpec)
            return new QueryPart(parent, (QuerySpec) spec);

        throw new RuntimeException("Unable to construct part for type " + spec.getClass().getName());
    }

    // //////////////////////////////////////////////////////////////
    // implementation
    // //////////////////////////////////////////////////////////////

    protected final String id = String.valueOf(ID_GENERATOR++);
    protected SectionPart parent;
    protected Spec spec;
    protected Parameters params;

    /** renderer can attach arbitrary objects to keep track of rendering status */
    protected Map<Class<?>, Object> objects = new HashMap<Class<?>, Object>();

    protected ITestResult.Status status;

    protected AbstractPart(SectionPart parent, Spec spec)
    {
        this.parent = parent;
        this.spec = spec;

        if (parent != null)
            this.params = new Parameters.Deep(parent.params(), spec.getParams());
        else
            this.params = new Parameters.Deep(spec.getParams());
    }

    public String getId()
    {
        return id;
    }

    public ITestResult.Status getStatus()
    {
        return status;
    }

    public SectionPart getParent()
    {
        return parent;
    }

    public Spec spec()
    {
        return spec;
    }

    public Parameters params()
    {
        return params;
    }

    @SuppressWarnings("unchecked")
    public <C> C getObject(Class<C> key)
    {
        return (C) objects.get(key);
    }

    @SuppressWarnings("unchecked")
    public <C> C putObject(Class<C> key, C value)
    {
        return (C) objects.put(key, value);
    }

    public abstract ITestResult.Status execute(ISnapshot snapshot, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException;

}

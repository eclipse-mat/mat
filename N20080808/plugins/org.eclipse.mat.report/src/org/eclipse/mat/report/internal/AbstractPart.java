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
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.ITestResult;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.util.IProgressListener;

public abstract class AbstractPart
{
    private static long ID_GENERATOR = 1;

    // //////////////////////////////////////////////////////////////
    // factory method
    // //////////////////////////////////////////////////////////////

    public static AbstractPart build(AbstractPart parent, Spec spec)
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
    protected final AbstractPart parent;
    protected final Spec spec;
    protected final Parameters params;

    protected final List<AbstractPart> children;

    protected String filename;

    protected long queryExecutionTime;
    protected long totalExecutionTime;

    /** renderer can attach arbitrary objects to keep track of rendering status */
    protected Map<String, Object> objects = new HashMap<String, Object>();

    protected ITestResult.Status status;

    protected AbstractPart(AbstractPart parent, Spec spec)
    {
        this.parent = parent;
        this.spec = spec;

        if (parent != null)
            this.params = new Parameters.Deep(parent.params(), spec.getParams());
        else
            this.params = new Parameters.Deep(spec.getParams());

        this.children = new ArrayList<AbstractPart>();
    }

    public String getId()
    {
        return id;
    }

    public ITestResult.Status getStatus()
    {
        return status;
    }

    public AbstractPart getParent()
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

    public Object getObject(String key)
    {
        return objects.get(key);
    }

    public Object putObject(String key, Object value)
    {
        return objects.put(key, value);
    }

    public List<AbstractPart> getChildren()
    {
        return children;
    }

    public void replace(AbstractPart part, AbstractPart other)
    {
        this.children.set(this.children.indexOf(part), other);
    }

    /* package */String getFilename()
    {
        return filename;
    }

    /* package */void setFilename(String filename)
    {
        this.filename = filename;
    }

    public abstract ITestResult.Status execute(IQueryContext context, ResultRenderer renderer,
                    IProgressListener listener) throws SnapshotException, IOException;

}

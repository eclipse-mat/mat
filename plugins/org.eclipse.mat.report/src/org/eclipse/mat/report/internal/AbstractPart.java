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
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.ITestResult;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.util.IProgressListener;

public abstract class AbstractPart
{
    // //////////////////////////////////////////////////////////////
    // implementation
    // //////////////////////////////////////////////////////////////

    protected final String id;

    protected final AbstractPart parent;
    protected final DataFile dataFile;
    protected final Spec spec;
    protected Parameters params;

    protected final List<AbstractPart> children;

    /** renderer can attach arbitrary objects to keep track of rendering status */
    protected Map<String, Object> objects = new HashMap<String, Object>();

    protected ITestResult.Status status;

    protected AbstractPart(String id, AbstractPart parent, DataFile artefact, Spec spec)
    {
        this.id = id;
        this.parent = parent;
        this.dataFile = artefact;
        this.spec = spec;

        if (parent != null)
            this.params = new Parameters.Deep(parent.params(), spec.getParams());
        else
            this.params = new Parameters.Deep(spec.getParams());

        this.children = new ArrayList<AbstractPart>();
    }

    /* package */abstract void init(PartsFactory factory);

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

    /* package */DataFile getDataFile()
    {
        return dataFile;
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

    public abstract AbstractPart execute(IQueryContext context, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException;

}

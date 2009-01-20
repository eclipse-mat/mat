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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.ITestResult.Status;
import org.eclipse.mat.util.IProgressListener;

public class SectionPart extends AbstractPart
{

    /* package */SectionPart(String id, AbstractPart parent, DataFile artefact, SectionSpec spec)
    {
        super(id, parent, artefact, spec);

        this.status = spec.getStatus();

        if (spec.getName() == null)
        {
            spec.setName("");
            params().put(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        }
    }
    
    /* package */void init(PartsFactory factory)
    {
        for (Spec child : ((SectionSpec) spec).getChildren())
            children.add(factory.create(this, child));
    }

    @Override
    public AbstractPart execute(IQueryContext context, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException
    {
        renderer.beginSection(this);

        for (int ii = 0; ii < this.children.size(); ii++)
        {
            AbstractPart part = this.children.get(ii).execute(context, renderer, listener);
            this.status = Status.max(this.status, part.status);
            this.children.set(ii, part);
        }

        renderer.endSection(this);

        return this;
    }

}

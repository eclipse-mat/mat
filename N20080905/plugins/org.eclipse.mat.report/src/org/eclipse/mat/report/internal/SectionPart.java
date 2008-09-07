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
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.ITestResult.Status;
import org.eclipse.mat.util.IProgressListener;

public class SectionPart extends AbstractPart
{

    public SectionPart(AbstractPart parent, SectionSpec spec)
    {
        super(parent, spec);

        this.status = spec.getStatus();

        for (Spec child : spec.getChildren())
            children.add(AbstractPart.build(this, child));
    }

    @Override
    public Status execute(IQueryContext context, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException
    {
        renderer.beginSection(this);

        // this list is dynamically changed while iterating over it. Therefore
        // we cannot use an iterator -> ConcurrentModificationException
        for (int ii = 0; ii < this.children.size(); ii++)
        {
            AbstractPart part = this.children.get(ii);
            Status status = part.execute(context, renderer, listener);
            this.status = Status.max(this.status, status);
        }

        renderer.endSection(this);

        return status;
    }

}

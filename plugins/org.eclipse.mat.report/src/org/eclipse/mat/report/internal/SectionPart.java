/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - progress monitors for section children
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.ITestResult.Status;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.SimpleMonitor;

public class SectionPart extends AbstractPart
{
    String command;

    /* package */SectionPart(String id, AbstractPart parent, DataFile artefact, SectionSpec spec, String command)
    {
        super(id, parent, artefact, spec);

        this.status = spec.getStatus();

        if (spec.getName() == null)
        {
            spec.setName(""); //$NON-NLS-1$
            params().put(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        }
        this.command = command;
    }

    @Override
    /* package */void init(PartsFactory factory)
    {
        for (Spec child : ((SectionSpec) spec).getChildren())
            children.add(factory.create(this, child));
    }

    @Override
    public String getCommand() {
        return command;
    }

    @Override
    public AbstractPart execute(IQueryContext context, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException
    {
        renderer.beginSection(this);

        int perc[] = new int[this.children.size()];
        Arrays.fill(perc, 100);
        SimpleMonitor sm = new SimpleMonitor(spec.getName(), listener, perc);
        for (int ii = 0; ii < this.children.size(); ii++)
        {
            IProgressListener mon = sm.nextMonitor();
            AbstractPart part = this.children.get(ii).execute(context, renderer, mon);
            this.status = Status.max(this.status, part.status);
            this.children.set(ii, part);
            mon.done();
        }

        renderer.endSection(this);

        return this;
    }

}

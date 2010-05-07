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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.util.IProgressListener;

public class LinkedPart extends AbstractPart
{
    AbstractPart linkedTo;

    public LinkedPart(String id, AbstractPart parent, DataFile artefact, Spec spec, AbstractPart linkedTo)
    {
        super(id, parent, artefact, spec);
        this.linkedTo = linkedTo;
    }

    @Override
    void init(PartsFactory factory)
    {}

    @Override
    public AbstractPart execute(IQueryContext context, ResultRenderer renderer, IProgressListener listener)
                    throws SnapshotException, IOException
    {
        renderer.processLink(this);
        return this;
    }

}

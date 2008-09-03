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
package org.eclipse.mat.snapshot.query;

import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.snapshot.Histogram;

/**
 * Wrapper to return a {@link org.eclipse.mat.snapshot.Histogram} as a query
 * result.
 * 
 * @deprecated Use {@link Histogram} instead.
 */
public final class HistogramResult implements IResult
{
    private Histogram histogram;

    public HistogramResult(Histogram histogram)
    {
        this.histogram = histogram;
    }

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public Histogram getHistogram()
    {
        return this.histogram;
    }

    public IResultTable asTable()
    {
        return this.histogram;
    }

}

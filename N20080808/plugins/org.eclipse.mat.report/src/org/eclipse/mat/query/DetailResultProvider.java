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
package org.eclipse.mat.query;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

public abstract class DetailResultProvider
{
    private String label;

    public DetailResultProvider(String label)
    {
        this.label = label;
    }

    public final String getLabel()
    {
        return label;
    }

    public abstract boolean hasResult(Object row);

    public abstract IResult getResult(Object row, IProgressListener listener) throws SnapshotException;

}

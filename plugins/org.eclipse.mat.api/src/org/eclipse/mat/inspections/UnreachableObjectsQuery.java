/*******************************************************************************
 * Copyright (c) 2009, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram;
import org.eclipse.mat.util.IProgressListener;

@CommandName("unreachable_objects")
public class UnreachableObjectsQuery implements IQuery
{
    @Argument
    public UnreachableObjectsHistogram histogram;

    public IResult execute(IProgressListener listener) throws Exception
    {
        return histogram;
    }
}

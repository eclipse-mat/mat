/*******************************************************************************
 * Copyright (c) 2009, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram;
import org.eclipse.mat.util.IProgressListener;

@CommandName("unreachable_objects")
@Icon("/META-INF/icons/unreachables_histogram.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/unreachable_objects.html")
public class UnreachableObjectsQuery implements IQuery
{
    @Argument
    public UnreachableObjectsHistogram histogram;

    public IResult execute(IProgressListener listener) throws Exception
    {
        return histogram;
    }
}

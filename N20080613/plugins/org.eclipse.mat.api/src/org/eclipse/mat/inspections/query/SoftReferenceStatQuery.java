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
package org.eclipse.mat.inspections.query;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

@Name("Soft References Statistics")
@Category("Java Basics/References")
@Help("Statistics to Soft References.")
public class SoftReferenceStatQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;
    
    public IResult execute(IProgressListener listener) throws Exception
    {
        return ReferenceQuery.execute("soft_stat", "java\\.lang\\.ref\\.SoftReference", snapshot, listener);
    }

}

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
package org.eclipse.mat.inspections;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

@Name("Weak References Statistics")
@Category("Java Basics/References")
@Help("Statistics to Weak References.")
public class WeakReferenceStatQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;
    
    public IResult execute(IProgressListener listener) throws Exception
    {
        return ReferenceQuery.execute("weak_stat", "java\\.lang\\.ref\\.WeakReference", snapshot, listener);
    }

}

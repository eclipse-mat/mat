/*******************************************************************************
 * Copyright (c) 2009, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - modification of soft reference query
 *    SAP AG - soft reference query
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.util.IProgressListener;

@CommandName("phantom_references_statistics")
@Icon("/META-INF/icons/phantom_reference.gif")
@Subject("java.lang.ref.PhantomReference")
public class PhantomReferenceStatQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        return ReferenceQuery.execute("java\\.lang\\.ref\\.PhantomReference", snapshot, //$NON-NLS-1$
                        Messages.PhantomReferenceStatQuery_Label_Referenced,
                        Messages.PhantomReferenceStatQuery_Label_Retained,
                        Messages.PhantomReferenceStatQuery_Label_StronglyRetainedReferents, listener);
    }

}

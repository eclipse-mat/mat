/*******************************************************************************
 * Copyright (c) 2009, 2019 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
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

@CommandName("weak_references_statistics")
@Icon("/META-INF/icons/weak_reference.gif")
@Subject("java.lang.ref.WeakReference")
public class WeakReferenceStatQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        return ReferenceQuery.execute("java\\.lang\\.ref\\.WeakReference", snapshot, //$NON-NLS-1$
                        Messages.WeakReferenceStatQuery_Label_Referenced,
                        Messages.WeakReferenceStatQuery_Label_Retained,
                        Messages.WeakReferenceStatQuery_Label_StronglyRetainedReferents, listener);
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - only make query available with relevant classes
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

@CommandName("finalizer_references_statistics")
@Icon("/META-INF/icons/finalizer.gif")
@Subject("java.lang.ref.Finalizer")
public class FinalizerReferenceStatQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        return ReferenceQuery.execute("java\\.lang\\.ref\\.Finalizer", snapshot, //$NON-NLS-1$
                        Messages.FinalizerReferenceStatQuery_Label_Referenced,
                        Messages.FinalizerReferenceStatQuery_Label_Retained,
                        Messages.FinalizerReferenceStatQuery_Label_StronglyRetainedReferents, listener);
    }

}

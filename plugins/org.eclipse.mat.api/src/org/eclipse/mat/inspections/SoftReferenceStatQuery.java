/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - add icon
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.util.IProgressListener;

@CommandName("soft_references_statistics")
@Icon("/META-INF/icons/soft_reference.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/reference_stats.html")
@Subject("java.lang.ref.SoftReference")
public class SoftReferenceStatQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        return ReferenceQuery.execute("java\\.lang\\.ref\\.SoftReference", snapshot, //$NON-NLS-1$
                        Messages.SoftReferenceStatQuery_Label_Referenced,
                        Messages.SoftReferenceStatQuery_Label_Retained,
                        Messages.SoftReferenceStatQuery_Label_StronglyRetainedReferents, listener);
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
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

import java.util.Collection;

import org.eclipse.mat.inspections.collections.HashEntriesQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@Subject("java.lang.System")
@CommandName("system_properties")
@Icon("/META-INF/icons/osgi/property.gif")
public class SystemPropertiesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public HashEntriesQuery.Result execute(IProgressListener listener) throws Exception
    {
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.System", false); //$NON-NLS-1$
        if (classes == null || classes.isEmpty())
            return null;
        IClass systemClass = classes.iterator().next();

        IObject properties = (IObject) systemClass.resolveValue("props"); //$NON-NLS-1$
        if (properties == null)
            properties = (IObject) systemClass.resolveValue("systemProperties"); //$NON-NLS-1$
        if (properties == null)
            return null;

        return (HashEntriesQuery.Result) SnapshotQuery.lookup("hash_entries", snapshot) //$NON-NLS-1$
                        .setArgument("objects", properties) //$NON-NLS-1$
                        .execute(listener);
    }

}

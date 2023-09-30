/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and others.
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
package org.eclipse.mat.inspections.eclipse;

import java.util.Collection;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("leaking_bundles")
@Icon("/META-INF/icons/osgi/registry.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/findingmemoryleak.html#ref_findingmemoryleak__leaking_bundles")
@Subjects({"org.eclipse.osgi.framework.internal.core.BundleLoaderProxy", "org.eclipse.osgi.internal.loader.BundleLoaderProxy"})
public class LeakingPlugins implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        // collect stale all class loaders
        Collection<IClass> classes = snapshot.getClassesByName(
                        "org.eclipse.osgi.framework.internal.core.BundleLoaderProxy", true); //$NON-NLS-1$
        // Eclipse 3.5 and later?
        if (classes == null || classes.isEmpty())
            classes = snapshot.getClassesByName("org.eclipse.osgi.internal.loader.BundleLoaderProxy", true); //$NON-NLS-1$

        ArrayInt result = new ArrayInt();

        if (classes != null)
            for (IClass clazz : classes)
            {
                for (int objectId : clazz.getObjectIds())
                {
                    IObject proxy = snapshot.getObject(objectId);
                    boolean isStale = (Boolean) proxy.resolveValue("stale"); //$NON-NLS-1$
                    if (isStale)
                    {
                        IClassLoader classLoader = (IClassLoader) proxy.resolveValue("loader.classloader"); //$NON-NLS-1$
                        result.add(classLoader.getObjectId());
                    }
                }
            }

        if (result.isEmpty())
            return new TextResult(Messages.LeakingPlugins_NoLeakingPlugInsDetected);

        return new ObjectListResult.Inbound(snapshot, result.toArray());
    }
}

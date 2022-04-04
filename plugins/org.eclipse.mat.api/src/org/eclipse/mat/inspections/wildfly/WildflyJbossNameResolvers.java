/*******************************************************************************
 * Copyright (c) 2022 Erik Brangs.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Erik Brangs - initial implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.wildfly;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

public class WildflyJbossNameResolvers
{

    @Subject("org.jboss.modules.ModuleClassLoader")
    public static class ModuleClassLoaderResolver implements IClassSpecificNameResolver
    {
        @Override
        public String resolve(IObject object) throws SnapshotException
        {
            IObject module = (IObject) object.resolveValue("module"); //$NON-NLS-1$
            if (module != null)
            {
                IObject moduleName = (IObject) module.resolveValue("name"); //$NON-NLS-1$
                if (moduleName != null)
                {
                    return MessageUtil.format(Messages.WildflyJbossNameResolvers_ModuleClassLoaderFor,
                                    moduleName.getClassSpecificName());
                }
            }
            return null;
        }
    }

}

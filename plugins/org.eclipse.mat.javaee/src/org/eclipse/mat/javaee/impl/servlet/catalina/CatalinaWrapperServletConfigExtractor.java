/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee.impl.servlet.catalina;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.servlet.api.ServletConfigExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class CatalinaWrapperServletConfigExtractor implements ServletConfigExtractor {
    public String getName(IObject config) {
        try {
            return ((IObject)config.resolveValue("name")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public String getApplication(IObject config) {
        try {
            return ((IObject)config.resolveValue("parent.name")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2015 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee.impl.jboss;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.jboss.api.JBossMscExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class JBoss7MscRegistrationExtractor implements JBossMscExtractor
{

    public String getServiceName(IObject obj)
    {
        try {
            IObject name = (IObject) obj.resolveValue("name");
            return (name != null) ? name.getClassSpecificName() : null;
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public String getMode(IObject obj)
    {
        try {
            IObject controller = getController(obj);
            if (controller != null) {
                IObject mode = (IObject) controller.resolveValue("mode");
                return mode.getClassSpecificName();
            } else {
                return null;
            }
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public String getState(IObject obj)
    {
        try {
            IObject controller = getController(obj);
            if (controller != null) {
                IObject mode = (IObject) getController(obj).resolveValue("state");
                return mode.getClassSpecificName();
            } else {
                return null;
            }
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    // returns the org.jboss.msc.service.ServiceControllerImpl
    private IObject getController(IObject obj) throws SnapshotException {
        return (IObject) obj.resolveValue("instance");
    }
}

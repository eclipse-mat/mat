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
package org.eclipse.mat.javaee.impl.servlet.undertow;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.servlet.api.RequestExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class UndertowRequestExtractor implements RequestExtractor {
    public String getRequestUri(IObject request) {
        try {
            return ((IObject)request.resolveValue("exchange.requestURI")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public boolean isInUse(IObject request) {
        return true;
    }
}

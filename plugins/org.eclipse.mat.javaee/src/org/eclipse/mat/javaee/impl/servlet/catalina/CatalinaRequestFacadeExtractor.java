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
import org.eclipse.mat.javaee.impl.ServletExtractors;
import org.eclipse.mat.javaee.servlet.api.RequestExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class CatalinaRequestFacadeExtractor implements RequestExtractor {
    public String getRequestUri(IObject requestFacade) {
        IObject root = findRootRequest(requestFacade);
        return "Wrapper: " + ServletExtractors.getRequestExtractor(root).getRequestUri(root);
    }

    public boolean isInUse(IObject requestFacade) {
        IObject root = findRootRequest(requestFacade);
        return ServletExtractors.getRequestExtractor(root).isInUse(root);
    }

    private IObject findRootRequest(IObject requestFacade) {
        try {
            for(IObject current = requestFacade;;) {
                IObject baseRequest = (IObject)current.resolveValue("request");
                String className = baseRequest.getClazz().getName();
                if (className.equals("org.apache.catalina.connector.RequestFacade"))
                    current = baseRequest;
                else if (className.equals("org.apache.catalina.connector.Request")) {
                    return baseRequest;
                } else
                    JavaEEPlugin.error("unhandled class: " + className);
                    return null;
            }
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }
}

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
package org.eclipse.mat.javaee.impl;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.impl.servlet.GenericServletExtractor;
import org.eclipse.mat.javaee.impl.servlet.HttpServletExtractor;
import org.eclipse.mat.javaee.impl.servlet.catalina.CatalinaRequestExtractor;
import org.eclipse.mat.javaee.impl.servlet.catalina.CatalinaRequestFacadeExtractor;
import org.eclipse.mat.javaee.impl.servlet.catalina.CatalinaSessionExtractor;
import org.eclipse.mat.javaee.impl.servlet.catalina.CatalinaWrapperFacadeServletConfigExtractor;
import org.eclipse.mat.javaee.impl.servlet.catalina.CatalinaWrapperServletConfigExtractor;
import org.eclipse.mat.javaee.impl.servlet.jasper.JasperJspServletExtractor;
import org.eclipse.mat.javaee.impl.servlet.undertow.UndertowRequestExtractor;
import org.eclipse.mat.javaee.impl.servlet.undertow.UndertowServletConfigExtractor;
import org.eclipse.mat.javaee.impl.servlet.undertow.UndertowSessionExtractor;
import org.eclipse.mat.javaee.servlet.api.RequestExtractor;
import org.eclipse.mat.javaee.servlet.api.ServletConfigExtractor;
import org.eclipse.mat.javaee.servlet.api.ServletExtractor;
import org.eclipse.mat.javaee.servlet.api.SessionExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class ServletExtractors extends Extractors {
    public static ArrayInt findHttpSessions(ISnapshot snapshot) throws SnapshotException {
        return findObjects(snapshot, SESSION_EXTRACTORS);
    }

    public static ArrayInt findHttpRequests(ISnapshot snapshot) throws SnapshotException {
        return findObjects(snapshot, REQUEST_EXTRACTORS);
    }

    public static ArrayInt findServlets(ISnapshot snapshot) throws SnapshotException {
        return findObjects(snapshot, SERVLET_EXTRACTORS);
    }

    public static SessionExtractor getSessionExtractor(IObject object) {
        return getExtractor(object, SESSION_EXTRACTORS);
    }

    public static RequestExtractor getRequestExtractor(IObject object) {
        return getExtractor(object, REQUEST_EXTRACTORS);
    }

    public static ServletExtractor getServletExtractor(IObject object) {
        return getExtractor(object, SERVLET_EXTRACTORS);
    }

    public static ServletConfigExtractor getServletConfigExtractor(IObject object) {
        return getExtractor(object, SERVLET_CONFIG_EXTRACTORS);
    }



    private static final Map<String, SessionExtractor> SESSION_EXTRACTORS;
    private static final Map<String, RequestExtractor> REQUEST_EXTRACTORS;
    private static final Map<String, ServletExtractor> SERVLET_EXTRACTORS;
    private static final Map<String, ServletConfigExtractor> SERVLET_CONFIG_EXTRACTORS;
    static {
        SESSION_EXTRACTORS = new HashMap<String, SessionExtractor>();
        SESSION_EXTRACTORS.put("org.apache.catalina.session.StandardSession", new CatalinaSessionExtractor());
        SESSION_EXTRACTORS.put("org.jboss.web.tomcat.service.session.SessionBasedClusteredSession", new CatalinaSessionExtractor());
        SESSION_EXTRACTORS.put("io.undertow.server.session.InMemorySessionManager$InMemorySession", new UndertowSessionExtractor());

        /* Keep this in sync with the annotations on HttpServletRequestResolver */
        REQUEST_EXTRACTORS = new HashMap<String, RequestExtractor>();
        REQUEST_EXTRACTORS.put("org.apache.catalina.connector.Request", new CatalinaRequestExtractor());
        REQUEST_EXTRACTORS.put("org.apache.catalina.connector.RequestFacade", new CatalinaRequestFacadeExtractor());
        REQUEST_EXTRACTORS.put("io.undertow.servlet.spec.HttpServletRequestImpl", new UndertowRequestExtractor());

        SERVLET_EXTRACTORS = new HashMap<String, ServletExtractor>();
        SERVLET_EXTRACTORS.put("org.apache.jasper.runtime.HttpJspBase", new JasperJspServletExtractor());
        SERVLET_EXTRACTORS.put("javax.servlet.http.HttpServlet", new HttpServletExtractor());
        SERVLET_EXTRACTORS.put("javax.servlet.GenericServlet", new GenericServletExtractor());

        SERVLET_CONFIG_EXTRACTORS = new HashMap<String, ServletConfigExtractor>();
        SERVLET_CONFIG_EXTRACTORS.put("io.undertow.servlet.spec.ServletConfigImpl", new UndertowServletConfigExtractor());
        SERVLET_CONFIG_EXTRACTORS.put("org.apache.catalina.core.StandardWrapperFacade", new CatalinaWrapperFacadeServletConfigExtractor());
        SERVLET_CONFIG_EXTRACTORS.put("org.apache.catalina.core.StandardWrapper", new CatalinaWrapperServletConfigExtractor());
    }
}

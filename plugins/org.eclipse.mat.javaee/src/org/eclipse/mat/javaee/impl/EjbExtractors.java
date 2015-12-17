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
import org.eclipse.mat.javaee.ejb.api.StatefulEjbExtractor;
import org.eclipse.mat.javaee.ejb.api.StatelessEjbExtractor;
import org.eclipse.mat.javaee.impl.ejb.jboss.JBossStatefulEjbExtractor;
import org.eclipse.mat.javaee.impl.ejb.jboss.JBossStatelessEjbExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class EjbExtractors extends Extractors {
    public static ArrayInt findStatefulEjbs(ISnapshot snapshot) throws SnapshotException {
        return findObjects(snapshot, STATEFUL_EJB_EXTRACTORS);
    }

    public static StatefulEjbExtractor getStatefulEjbExtractor(IObject object) {
        return getExtractor(object, STATEFUL_EJB_EXTRACTORS);
    }

    public static ArrayInt findStatelessEjbs(ISnapshot snapshot) throws SnapshotException {
        return findObjects(snapshot, STATELESS_EJB_EXTRACTORS);
    }

    public static StatelessEjbExtractor getStatelessEjbExtractor(IObject object) {
        return getExtractor(object, STATELESS_EJB_EXTRACTORS);
    }


    private static final Map<String, StatefulEjbExtractor> STATEFUL_EJB_EXTRACTORS;
    private static final Map<String, StatelessEjbExtractor> STATELESS_EJB_EXTRACTORS;
    static {
        STATEFUL_EJB_EXTRACTORS = new HashMap<String, StatefulEjbExtractor>();
        STATEFUL_EJB_EXTRACTORS.put("org.jboss.as.ejb3.component.stateful.StatefulSessionComponent", new JBossStatefulEjbExtractor());

        STATELESS_EJB_EXTRACTORS = new HashMap<String, StatelessEjbExtractor>();
        STATELESS_EJB_EXTRACTORS.put("org.jboss.as.ejb3.component.stateless.StatelessSessionComponent", new JBossStatelessEjbExtractor());
    }
}

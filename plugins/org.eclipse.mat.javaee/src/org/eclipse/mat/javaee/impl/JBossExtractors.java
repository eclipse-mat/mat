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
package org.eclipse.mat.javaee.impl;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.javaee.impl.jboss.JBoss7MscRegistrationExtractor;
import org.eclipse.mat.javaee.jboss.api.JBossMscExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class JBossExtractors extends Extractors 
{
    public static ArrayInt findMscServices(ISnapshot snapshot) throws SnapshotException
    {
        return findObjects(snapshot, MSC_REGISTRATION_EXTRACTORS);
    }

    public static JBossMscExtractor getJBossMscExtractor(IObject object)
    {
        return getExtractor(object, MSC_REGISTRATION_EXTRACTORS);
    }

    private static final Map<String, JBossMscExtractor> MSC_REGISTRATION_EXTRACTORS;

    static {
        MSC_REGISTRATION_EXTRACTORS = new HashMap<String, JBossMscExtractor>();
        MSC_REGISTRATION_EXTRACTORS.put("org.jboss.msc.service.ServiceRegistrationImpl", new JBoss7MscRegistrationExtractor());
    }
}

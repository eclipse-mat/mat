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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.servlet.api.SessionAttributeData;
import org.eclipse.mat.javaee.servlet.api.SessionExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class UndertowSessionExtractor implements SessionExtractor {
    public List<SessionAttributeData> buildSessionAttributes(IObject session) {
        try {
            ExtractedMap attrs = CollectionExtractionUtils.extractMap((IObject)session.resolveValue("attributes"));

            List<SessionAttributeData> nodes = new ArrayList<SessionAttributeData>();
            for (Map.Entry<IObject,IObject> entry: attrs) {
                nodes.add(new SessionAttributeData(entry.getKey(), entry.getValue()));
            }
            return nodes;
        } catch (SnapshotException e)  {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public String getSessionId(IObject session) {
        try {
            return ((IObject)session.resolveValue("session.sessionId")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }
}

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

public class CatalinaSessionExtractor implements SessionExtractor {
    // accessCount, thisAccessedTime, principal, notes, isValid, isNew, maxInactiveInterval, manager, listeners, lastAccessedTime, id, facade, expiring, creationTime, authType

    public List<SessionAttributeData> buildSessionAttributes(IObject object) {
        try {
            ExtractedMap attrs = CollectionExtractionUtils.extractMap((IObject)object.resolveValue("attributes"));
            ArrayList<SessionAttributeData> results = new ArrayList<SessionAttributeData>();

            for (Map.Entry<IObject,IObject> entry: attrs) {
                results.add(new SessionAttributeData(entry.getKey(), entry.getValue()));
            }

            return results;
        } catch (SnapshotException e) {
            JavaEEPlugin.warning("Unable to list session attributes",e);
            return new ArrayList<SessionAttributeData>();
        }
    }

    public String getSessionId(IObject object) {
        try {
            return ((IObject)object.resolveValue("id")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.warning("Unable to resolve id", e);
            return null;
        }
    }
}

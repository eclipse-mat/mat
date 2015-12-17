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

import java.util.Collection;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public class Extractors {
    protected static <T> ArrayInt findObjects(ISnapshot snapshot, Map<String, T> extractors) throws SnapshotException {
        ArrayInt ids = new ArrayInt();
        for (String name: extractors.keySet()) {
            Collection<IClass> classes = snapshot.getClassesByName(name, true);
            if (classes != null) {
                for (IClass clazz : classes) {
                    for (int id: clazz.getObjectIds())
                        ids.add(id);
                }
            }
        }

        // de-duplicate, since the extractors may overlap
        if (ids.isEmpty())
            return ids;

        ids.sort();
        int nextWrite = 1;
        for (int i = 1; i < ids.size(); i++) {
            int currentValue = ids.get(i);
            if (currentValue != ids.get(nextWrite - 1)) {
                ids.set(nextWrite++, currentValue);
            }
        }
        ids.truncate(nextWrite);

        return ids;
    }

    protected static <T> T getExtractor(IObject object, Map<String, T> extractors) {
        for (IClass klass = object.getClazz(); klass != null; klass = klass.getSuperClass()) {
            T extractor = extractors.get(klass.getName());
            if (extractor != null)
                return extractor;
        }
        return null;
    }
}

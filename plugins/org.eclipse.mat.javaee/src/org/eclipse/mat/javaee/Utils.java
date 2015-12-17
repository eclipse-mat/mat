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
package org.eclipse.mat.javaee;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;

public final class Utils {
    public static IObject findStaticObjectField(IClass clazz, String fieldName) throws SnapshotException {
        ObjectReference ref = (ObjectReference)findStaticField(clazz, fieldName);
        return (ref != null) ? ref.getObject() : null;
    }

    public static Object findStaticField(IClass clazz, String fieldName) {
        for (IClass c = clazz; c != null; c = c.getSuperClass()) {
            for (Field f: c.getStaticFields()) {
                if (f.getName().equals(fieldName))
                    return f.getValue();
            }
        }
        return null;
    }
}

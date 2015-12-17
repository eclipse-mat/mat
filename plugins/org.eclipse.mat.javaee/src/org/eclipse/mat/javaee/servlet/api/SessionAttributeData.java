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
package org.eclipse.mat.javaee.servlet.api;

import org.eclipse.mat.snapshot.model.IObject;

public class SessionAttributeData {
    private final IObject key;
    private final IObject value;

    public SessionAttributeData(IObject key, IObject value) {
        this.key = key;
        this.value = value;
    }

    public IObject getKey() {
        return key;
    }

    public IObject getValue() {
        return value;
    }
}
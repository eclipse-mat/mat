/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import org.eclipse.mat.snapshot.ISnapshot;

/**
 * A named reference.
 */
public class NamedReference extends ObjectReference
{
    private static final long serialVersionUID = 1L;

    private String name;

    /**
     * Constructs a reference to a Java object with a description of why the reference occurred.
     * @param snapshot the whole dump
     * @param address the address of the target object
     * @param name the description of the reference, for example the field name, an array index
     */
    public NamedReference(ISnapshot snapshot, long address, String name)
    {
        super(snapshot, address);
        this.name = name;
    }

    /**
     * Get the description of the reference.
     * @return the description
     */
    public String getName()
    {
        return name;
    }
}

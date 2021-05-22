/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - hashCode and equals
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.util.Objects;

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

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(name);
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        NamedReference other = (NamedReference) obj;
        return Objects.equals(name, other.name);
    }
}

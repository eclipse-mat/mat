/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.query;

import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;

/**
 * Base class for context provider which is an object which returns the heap
 * objects represented by an arbitrary row in a table/tree.
 * 
 * @see org.eclipse.mat.query.IContextObject
 * @see org.eclipse.mat.query.IContextObjectSet
 */
public abstract class ContextProvider
{
    private String label;
    private DerivedOperation[] operations;

    /**
     * @param label
     *            The label used for context menus.
     */
    public ContextProvider(String label)
    {
        this(label, new DerivedOperation[0]);
    }

    public ContextProvider(String label, DerivedOperation... operations)
    {
        this.label = label;
        this.operations = operations;
    }

    /**
     * Constructor using copying values from the give template context provider.
     */
    public ContextProvider(ContextProvider template)
    {
        this(template.label);
    }

    /**
     * The label for this context provider
     * @return the label
     */
    public String getLabel()
    {
        return label;
    }

    /**
     * The default context provider is that for the whole snapshot.
     * @return true if it represents the whole snapshot
     */
    public final boolean isDefault()
    {
        return label == null;
    }

    public final boolean hasSameTarget(ContextProvider other)
    {
        if (label == null)
            return other.label == null;
        else
            return label.equals(other.label);
    }

    public DerivedOperation[] getOperations()
    {
        return operations;
    }

    /**
     * Return the context object associated with the specified row.
     * @param row the row requested
     * @return details of the row
     */
    public abstract IContextObject getContext(Object row);

}

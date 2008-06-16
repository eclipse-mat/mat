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
package org.eclipse.mat.query;

/**
 * Base class for context provider, i.e. an object which returns the heap
 * objects represented by an arbitrary row in a table/tree.
 * 
 * @see org.eclipse.mat.query.IContextObject
 * @see org.eclipse.mat.query.IContextObjectSet
 */
public abstract class ContextProvider
{
    private String label;
    private boolean defaultShowApproximation;
    private boolean defaultShowPrecise;

    /**
     * @param label
     *            The label used for context menus.
     */
    public ContextProvider(String label)
    {
        this(label, false, false);
    }

    /**
     * @param label
     *            The label used for context menus.
     * @param defaultShowApproximation
     *            if true, the calculation of the approximate retained size is
     *            immediately started.
     * @param defaultShowPrecise
     *            if true, the calculation of the precise retained size is
     *            immediately started.
     */
    public ContextProvider(String label, boolean defaultShowApproximation, boolean defaultShowPrecise)
    {
        this.label = label;
        this.defaultShowApproximation = defaultShowApproximation && !defaultShowPrecise;
        this.defaultShowPrecise = defaultShowPrecise;
    }

    /**
     * Constructor using copying values from the give template context provider.
     */
    public ContextProvider(ContextProvider template)
    {
        this(template.label, template.defaultShowApproximation, template.defaultShowPrecise);
    }

    public String getLabel()
    {
        return label;
    }

    public final String getColumnLabel()
    {
        return label == null ? "Retained Heap" : "Retained Heap - " + label;
    }

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

    public boolean isDefaultShowApproximation()
    {
        return defaultShowApproximation;
    }

    public boolean isDefaultShowPrecise()
    {
        return defaultShowPrecise;
    }

    public abstract IContextObject getContext(Object row);


}

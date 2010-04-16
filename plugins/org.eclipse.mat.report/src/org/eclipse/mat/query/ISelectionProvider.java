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
 * Description of how to deal with a table or tree.
 */
public interface ISelectionProvider
{
    /**
     * Has the user selected this row
     * @param row
     * @return true if selected
     */
    boolean isSelected(Object row);

    /**
     * Should this node be expanded.
     * @param row
     * @return true if it should be expanded.
     */
    boolean isExpanded(Object row);

    /**
     * A basic selection provider where nothing is selected or expanded.
     */
    public static final ISelectionProvider EMPTY = new ISelectionProvider()
    {
        public boolean isExpanded(Object row)
        {
            return false;
        }

        public boolean isSelected(Object row)
        {
            return false;
        }
    };
}

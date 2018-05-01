/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

/**
 * Description of how to deal with a table or tree.
 */
public interface ISelectionProvider
{
    /**
     * Has the user selected this row?
     * @param row the chosen row
     * @return true if selected
     */
    boolean isSelected(Object row);

    /**
     * Should this node be expanded.
     * @param row the chosen row
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

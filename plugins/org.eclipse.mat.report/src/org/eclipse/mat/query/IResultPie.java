/*******************************************************************************
 * Copyright (c) 2008, 2012 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - improvements, bug 364505
 *******************************************************************************/
package org.eclipse.mat.query;

import java.awt.Color;
import java.util.List;

/**
 * Results as pie chart data.
 */
public interface IResultPie extends IResult
{
    /**
     * A slice of the pie.
     */
    public interface Slice
    {
        /**
         * The label for the pie chart
         * @return the label
         */
        String getLabel();

        double getValue();

        String getDescription();

        IContextObject getContext();
        
    }
    
    /**
     * A slice of the pie with color information
     * 
     * @since 1.2
     */
    public interface ColoredSlice extends Slice
    {
        Color getColor();
    }

    /**
     * All the slices of the pie.
     * @return a list of slices
     */
    List<? extends Slice> getSlices();
}

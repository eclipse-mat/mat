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
     * All the slices of the pie.
     * @return a list of slices
     */
    List<? extends Slice> getSlices();
}

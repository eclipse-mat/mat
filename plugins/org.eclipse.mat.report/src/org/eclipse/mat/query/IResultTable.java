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
 * Interface for results in table-form.
 */
public interface IResultTable extends IStructuredResult
{
    /**
     * Returns the number of rows in the result table.
     */
    int getRowCount();

    /**
     * Returns the object of the row with the given row number.
     * 
     * @param rowId
     *            The row number.
     * @return The row object.
     */
    Object getRow(int rowId);

}

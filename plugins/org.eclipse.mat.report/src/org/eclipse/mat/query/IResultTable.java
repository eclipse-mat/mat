/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

/**
 * Interface for results in table-form.
 */
public interface IResultTable extends IStructuredResult
{
    /**
     * Returns the number of rows in the result table.
     * @return the number of rows
     */
    int getRowCount();

    /**
     * Returns the object of the row with the given row number.
     * 
     * @param rowId
     *            The row number.
     * @return The row object, which can be passed to 
     * {@link IStructuredResult#getContext(Object)} or
     * {@link IStructuredResult#getColumnValue(Object, int)}. 
     * @return an opaque object representing this row
     */
    Object getRow(int rowId);

}

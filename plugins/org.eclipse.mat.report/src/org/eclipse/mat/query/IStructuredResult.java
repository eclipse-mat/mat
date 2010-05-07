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

/**
 * Interface for structured results (i.e. tree and tables).
 * <p>
 * Custom queries are expected to implement {@link IResultTable} or
 * {@link IResultTree}.
 */
public interface IStructuredResult extends IResult
{
    /**
     * The columns of the tree or table.
     */
    Column[] getColumns();

    /**
     * Returns the (unformatted) value of a table/tree cell.
     * 
     * @param row
     *            The row object as returned by the
     *            {@link IResultTable#getRow(int)} or
     *            {@link IResultTree#getChildren(Object)} methods
     * @param columnIndex
     *            The index of the column.
     */
    Object getColumnValue(Object row, int columnIndex);

    /**
     * The default context of the row which is used to display information in
     * the object inspector. Unless no context provider is given via the
     * {@link ResultMetaData}, it is also used for the context menu on a row.
     * 
     * @param row
     *            The row object as returned by the
     *            {@link IResultTable#getRow(int)} or
     *            {@link IResultTree#getChildren(Object)} methods.
     */
    IContextObject getContext(Object row);

}

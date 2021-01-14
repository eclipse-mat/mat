/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.regression.query;

import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;

public class StringResult implements IResultTable
{
    private List<String> dataElements;

    public StringResult(List<String> dataElements)
    {
        this.dataElements = dataElements;
    }

    public Object getRow(int rowId)
    {
        return dataElements.get(rowId);
    }

    public int getRowCount()
    {
        return dataElements.size();
    }

    public Column[] getColumns()
    {
        return new Column[] { new Column("Value") };
    }

    public Object getColumnValue(Object row, int columnIndex)
    {
        return row;
    }

    public IContextObject getContext(Object row)
    {
        return null;
    }

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

}

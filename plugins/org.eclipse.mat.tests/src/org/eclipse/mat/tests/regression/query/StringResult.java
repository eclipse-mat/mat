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

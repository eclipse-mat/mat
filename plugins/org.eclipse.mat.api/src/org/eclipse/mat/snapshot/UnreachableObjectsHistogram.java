/*******************************************************************************
 * Copyright (c) 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.snapshot.query.Icons;

/**
 * @since 0.7
 */
public class UnreachableObjectsHistogram implements IResultTable, IIconProvider, Serializable
{
    public static class Record implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private String className;
        private int objectCount;
        private long shallowHeapSize;

        public Record(String className, int nrOfObjects, long sizeOfObjects)
        {
            this.className = className;
            this.objectCount = nrOfObjects;
            this.shallowHeapSize = sizeOfObjects;
        }

        public String getClassName()
        {
            return className;
        }

        public int getObjectCount()
        {
            return objectCount;
        }

        public long getShallowHeapSize()
        {
            return shallowHeapSize;
        }
    }

    private static final long serialVersionUID = 1L;

    private List<Record> histogram;

    public UnreachableObjectsHistogram(Collection<Record> records)
    {
        histogram = new ArrayList<Record>(records);
    }

    public List<Record> getRecords()
    {
        return Collections.unmodifiableList(histogram);
    }

    // //////////////////////////////////////////////////////////////
    // result table, icon provider
    // //////////////////////////////////////////////////////////////

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public Column[] getColumns()
    {
        return new Column[] { new Column("Class Name"), //
                        new Column("Objects", long.class), //
                        new Column("Shallow Heap", long.class).sorting(SortDirection.DESC) };
    }

    public int getRowCount()
    {
        return histogram.size();
    }

    public Object getRow(int rowId)
    {
        return histogram.get(rowId);
    }

    public Object getColumnValue(Object row, int columnIndex)
    {
        Record r = (Record) row;
        switch (columnIndex)
        {
            case 0:
                return r.getClassName();
            case 1:
                return r.getObjectCount();
            case 2:
                return r.getShallowHeapSize();
        }

        return null;
    }

    public IContextObject getContext(Object row)
    {
        return null;
    }

    public URL getIcon(Object row)
    {
        return Icons.CLASS;
    }

}

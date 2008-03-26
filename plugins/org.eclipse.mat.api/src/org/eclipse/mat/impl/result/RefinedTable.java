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
package org.eclipse.mat.impl.result;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.SnapshotException;


public class RefinedTable extends RefinedStructuredResult implements IResultTable
{
    protected List<?> rows;

    public int getRowCount()
    {
        if (rows == null)
            reread();
        return rows.size();
    }
    
    public List<?> getRows()
    {
        if (rows == null)
            reread();
        return rows;
    }

    public Object getRow(int rowId)
    {
        if (rows == null)
            reread();
        return rows.get(rowId);
    }
    
    public synchronized void refresh()
    {
        rows = null;
    }
    
    // //////////////////////////////////////////////////////////////
    // private parts
    // //////////////////////////////////////////////////////////////

    private void reread()
    {
        try
        {
            rows = refine(asList());
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    private List<?> asList()
    {
        IResultTable table = (IResultTable)subject;
        
        int rowCount = table.getRowCount();
        List<Object> l = new ArrayList<Object>(rowCount);
        for (int ii = 0; ii < rowCount; ii++)
            l.add(table.getRow(ii));
        return l;
    }

    @Override
    public void filterChanged(Filter filter)
    {
        this.rows = null;
    }
}

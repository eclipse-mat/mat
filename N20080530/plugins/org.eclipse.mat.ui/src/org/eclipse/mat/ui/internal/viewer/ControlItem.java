/**
 * 
 */
package org.eclipse.mat.ui.internal.viewer;

import java.util.List;

import org.eclipse.mat.impl.result.TotalsRow;


/** not synchronized, all calls must be */
class ControlItem
{
    private List<?> children;
    private TotalsRow totals;

    private boolean isUpdated = true;

    private int level;

    public ControlItem(int level)
    {
        this.level = level;
    }

    public synchronized List<?> getChildren()
    {
        return children;
    }

    public void setChildren(List<?> children)
    {
        this.children = children;
        this.isUpdated = true;
    }

    public TotalsRow getTotals()
    {
        return totals;
    }

    public void setTotals(TotalsRow totals)
    {
        this.totals = totals;
    }

    public boolean isUpdated()
    {
        return isUpdated;
    }
    
    public void setUpdated(boolean isUpdated)
    {
        this.isUpdated = isUpdated;
    }

    public int getLevel()
    {
        return level;
    }

}

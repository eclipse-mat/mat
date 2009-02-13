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
package org.eclipse.mat.query.refined;

import java.net.URL;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

public class TotalsRow
{
    private static final URL SUM = TotalsRow.class.getResource("/META-INF/icons/misc/sum.gif"); //$NON-NLS-1$
    private static final URL SUM_PLUS = TotalsRow.class.getResource("/META-INF/icons/misc/sum_plus.gif"); //$NON-NLS-1$

    private static final NumberFormat fmt = DecimalFormat.getInstance();

    private Double[] totals;

    private int filteredItems;
    private int numberOfItems;
    private int visibleItems;

    public int getVisibleItems()
    {
        return visibleItems;
    }

    public void setVisibleItems(int visibleItems)
    {
        this.visibleItems = visibleItems;
    }

    public int getFilteredItems()
    {
        return filteredItems;
    }

    /* package */void setFilteredItems(int filteredItems)
    {
        this.filteredItems = filteredItems;
    }

    public int getNumberOfItems()
    {
        return numberOfItems;
    }

    /* package */void setNumberOfItems(int numberOfItems)
    {
        this.numberOfItems = numberOfItems;
    }

    /* package */void setTotals(Double[] totals)
    {
        this.totals = totals;
    }

    public URL getIcon()
    {
        return visibleItems < numberOfItems ? SUM_PLUS : SUM;
    }

    /** returns true if the totals row should be shown */
    public boolean isVisible()
    {
        return (numberOfItems > 1) //
                        || filteredItems > 0 //
                        || visibleItems < numberOfItems;
    }

    public String getLabel(int columnIndex)
    {
        if (columnIndex == 0)
        {
            return getFirstItemText();
        }
        else
        {
            // not calculated?
            if (totals == null)
                return ""; //$NON-NLS-1$

            // maybe for a row added later?
            if (columnIndex < 1 || columnIndex >= totals.length)
                return ""; //$NON-NLS-1$

            // no value present
            if (totals[columnIndex] == null)
                return ""; //$NON-NLS-1$

            return fmt.format(totals[columnIndex].doubleValue());
        }
    }

    private String getFirstItemText()
    {
        boolean hasMore = numberOfItems > visibleItems;
        boolean hasTotals = totals != null && totals[0] != null;
        boolean hasFiltered = filteredItems > 0;

        String msg = null;

        if (hasMore)
            msg = MessageUtil.format(Messages.TotalsRow_Label_TotalVisible, visibleItems, numberOfItems);
        else
            msg = MessageUtil.format(Messages.TotalsRow_Label_Total, numberOfItems);

        if (hasTotals)
            msg += " / " + fmt.format(totals[0].doubleValue()); //$NON-NLS-1$

        if (hasFiltered)
            msg += MessageUtil.format(" " + Messages.TotalsRow_Label_Filtered, filteredItems); //$NON-NLS-1$

        return msg;
    }
}

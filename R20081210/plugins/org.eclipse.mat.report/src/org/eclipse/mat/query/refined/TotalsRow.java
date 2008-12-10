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
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class TotalsRow
{
    private static final URL SUM = TotalsRow.class.getResource("/META-INF/icons/misc/sum.gif");
    private static final URL SUM_PLUS = TotalsRow.class.getResource("/META-INF/icons/misc/sum_plus.gif");

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
                return "";

            // maybe for a row added later?
            if (columnIndex < 1 || columnIndex >= totals.length)
                return "";

            // no value present
            if (totals[columnIndex] == null)
                return "";

            return fmt.format(totals[columnIndex].doubleValue());
        }
    }

    private String getFirstItemText()
    {
        NumberFormat fmt = DecimalFormat.getInstance();

        StringBuilder buf = new StringBuilder();
        buf.append("Total: ");

        if (numberOfItems > visibleItems)
            buf.append(fmt.format(visibleItems)).append(" of ");

        buf.append(fmt.format(numberOfItems));
        buf.append(numberOfItems == 1 ? " entry" : " entries");

        if (numberOfItems > visibleItems)
            buf.append(" displayed");

        if (totals != null)
        {
            Double total = totals[0];
            if (total != null)
                buf.append(" / ").append(fmt.format(total.doubleValue()));
        }

        if (filteredItems > 0)
            buf.append(" (").append(fmt.format(filteredItems)).append(" filtered)");

        return buf.toString();
    }
}

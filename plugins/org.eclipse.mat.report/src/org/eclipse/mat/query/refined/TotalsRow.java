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
 *    IBM Corporation - localization of icons
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query.refined;

import java.net.URL;
import java.text.Format;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.report.internal.ReportPlugin;
import org.eclipse.mat.util.MessageUtil;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;

public class TotalsRow
{
    private static final URL SUM = FileLocator.find(ReportPlugin.getDefault().getBundle(), new Path("$nl$/META-INF/icons/misc/sum.gif"), null); //$NON-NLS-1$
    private static final URL SUM_PLUS = FileLocator.find(ReportPlugin.getDefault().getBundle(), new Path("$nl$/META-INF/icons/misc/sum_plus.gif"), null); //$NON-NLS-1$
    private final NumberFormat fmt = DecimalFormat.getInstance();

    private TotalsResult[] totals;

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

    /* package */void setTotals(TotalsResult[] totals)
    {
        this.totals = totals;
    }

    public URL getIcon()
    {
        URL url = visibleItems < numberOfItems ? SUM_PLUS : SUM;
        return url;
    }

    /**
     * @return true if the totals row should be shown
     */
    public boolean isVisible()
    {
        return (numberOfItems > 1) //
                        || filteredItems > 0 //
                        || visibleItems < numberOfItems;
    }

    /**
     * The label for a totals tow.
     * @param columnIndex the particular column
     * @return what to display for this column for the totals row
     */
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

            TotalsResult result = totals[columnIndex];
            Object val = result.getValue();

            Format f = result.getFormat();
            if (f == null) {
                f = fmt;
            }

            return f.format(val);
        }
    }

    private String getFirstItemText()
    {
        boolean hasMore = numberOfItems > visibleItems;
        boolean hasTotals = totals != null && totals[0] != null;
        boolean hasFiltered = filteredItems > 0;

        String msg = null;

        if (hasMore)
            msg = MessageUtil.format(Messages.TotalsRow_Label_TotalVisible, visibleItems, numberOfItems, (numberOfItems - visibleItems));
        else
            msg = MessageUtil.format(Messages.TotalsRow_Label_Total, numberOfItems);

        if (hasTotals)
            msg += " / " + fmt.format(totals[0].getValue()); //$NON-NLS-1$

        if (hasFiltered)
            msg += MessageUtil.format(" " + Messages.TotalsRow_Label_Filtered, filteredItems); //$NON-NLS-1$

        return msg;
    }
}

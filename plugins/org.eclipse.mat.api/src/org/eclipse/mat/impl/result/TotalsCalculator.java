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

import java.util.List;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.util.IProgressListener;


/* package */class TotalsCalculator
{
    /* package */static final TotalsCalculator create(RefinedStructuredResult refined)
    {
        boolean needToCalculate = false;
        ArrayInt numericColumns = new ArrayInt(refined.columns.size());

        for (int ii = 0; ii < refined.columns.size(); ii++)
        {
            Column col = refined.columns.get(ii);
            if (col.getCalculateTotals() && col.isNumeric())
            {
                numericColumns.add(ii);
                needToCalculate = true;
            }
        }

        return new TotalsCalculator(refined.columns.size(), needToCalculate, numericColumns);
    }

    private final int noOfColumns;
    private final boolean needToCalculate;
    private final ArrayInt numericColumns;

    private TotalsCalculator(int noOfColumns, boolean needToCalculate, ArrayInt numericColumns)
    {
        this.noOfColumns = noOfColumns;
        this.needToCalculate = needToCalculate;
        this.numericColumns = numericColumns;
    }

    public Double[] calculate(IStructuredResult result, List<?> elements, IProgressListener listener)
    {
        Double[] answer = new Double[noOfColumns];

        if (!needToCalculate)
            return answer;

        // Local copy needed as possibly skip columns if the values turn out
        // not to be numeric. However, we do NOT want to skip this column
        // from now on always.
        ArrayInt thisNumericColumns = new ArrayInt(numericColumns);

        double[] sums = new double[thisNumericColumns.size()];

        int counter = 0;
        ForEachRowLoop: for (Object row : elements)
        {
            // check if canceled
            if (++counter % 100 == 0)
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();

            for (int ii = 0; ii < thisNumericColumns.size(); ii++)
            {
                int columnIndex = thisNumericColumns.get(ii);
                if (columnIndex < 0)
                    continue;

                Object o = result.getColumnValue(row, columnIndex);

                double v = 0;

                if (o == null)
                {
                    v = Double.valueOf(0);
                }
                else if (o instanceof Number)
                {
                    v = ((Number) o).doubleValue();
                }
                else
                {
                    try
                    {
                        v = Double.parseDouble(o.toString());
                    }
                    catch (NumberFormatException e)
                    {
                        // $JL-EXC$

                        // not a number -> ignore this column from now on
                        thisNumericColumns.set(ii, -1);

                        // check whether all the columns are non-numeric and
                        // exit the loop in this case
                        boolean needToCalculate = false;
                        for (int jj = 0; jj < thisNumericColumns.size(); jj++)
                            needToCalculate = needToCalculate || thisNumericColumns.get(jj) >= 0;
                        if (!needToCalculate)
                            break ForEachRowLoop;
                    }
                }

                sums[ii] += v;
            }
        }

        // prepare result
        for (int index = 0; index < sums.length; index++)
        {
            int columnIndex = thisNumericColumns.get(index);
            if (columnIndex < 0)
                continue;
            answer[columnIndex] = sums[index];
        }

        return answer;
    }
}

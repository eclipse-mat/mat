/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - also total sizes (Bytes)
 *******************************************************************************/
package org.eclipse.mat.query.refined;

import java.text.Format;
import java.util.List;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.BytesFormat;
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

        return new TotalsCalculator(refined.columns, refined.columns.size(), needToCalculate, numericColumns);
    }

    private final List<Column> columns;
    private final int noOfColumns;
    private final boolean needToCalculate;
    private final ArrayInt numericColumns;

    private TotalsCalculator(List<Column> columns, int noOfColumns, boolean needToCalculate, ArrayInt numericColumns)
    {
        this.columns = columns;
        this.noOfColumns = noOfColumns;
        this.needToCalculate = needToCalculate;
        this.numericColumns = numericColumns;
    }
    
    public TotalsResult[] calculate(IStructuredResult result, List<?> elements, IProgressListener listener)
    {
        TotalsResult[] answer = new TotalsResult[noOfColumns];

        if (!needToCalculate)
            return answer;

        // Local copy needed as possibly skip columns if the values turn out
        // not to be numeric. However, we do NOT want to skip this column
        // from now on always.
        ArrayInt thisNumericColumns = new ArrayInt(numericColumns);

        double[] sums = new double[thisNumericColumns.size()];

        Filter.ValueConverter converters[] = new Filter.ValueConverter[thisNumericColumns.size()];
        for (int ii = 0; ii < thisNumericColumns.size(); ii++)
        {
            int columnIndex = thisNumericColumns.get(ii);
            if (columnIndex < 0)
                continue;
            converters[ii] = (Filter.ValueConverter)columns.get(columnIndex).getData(Filter.ValueConverter.class);
        }

        int counter = 0;
        ForEachRowLoop: for (Object row : elements)
        {
            // check if canceled
            if (++counter % 100 == 0)
                if (listener.isCanceled())
                    return answer;

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
                else if (o instanceof Bytes)
                    v = ((Bytes) o).getValue();
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

                if (converters[ii] != null)
                    v = converters[ii].convert(v);
                sums[ii] += v;
            }
        }

        // prepare result
        for (int index = 0; index < sums.length; index++)
        {
            int columnIndex = thisNumericColumns.get(index);
            if (columnIndex < 0)
                continue;
            Column col = columns.get(columnIndex);
            Format formatter = col.getFormatter();
            Object val;
            if (formatter instanceof BytesFormat) {
                // We can assume that if a column's formatter is BytesFormat,
                // then the value must have been a long, number of bytes.
                val = new Bytes((long)sums[index]);
            } else {
                val = sums[index];
            }
            answer[columnIndex] = new TotalsResult(val, formatter);
        }

        return answer;
    }
}

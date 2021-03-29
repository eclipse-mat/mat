/*******************************************************************************
 * Copyright (c) 2021 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.text.Format;

import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.report.IOutputter;

import com.ibm.icu.text.DecimalFormat;

public abstract class OutputterBase implements IOutputter
{
    protected static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$

    protected static String getStringValue(Object columnValue, Filter.ValueConverter converter)
    {
        if (columnValue == null)
            return ""; //$NON-NLS-1$

        // check first the format: the converter can change the type to double!
        Format fmt = null;
        if (columnValue instanceof Long || columnValue instanceof Integer)
            fmt = new DecimalFormat("0"); //$NON-NLS-1$
        else if (columnValue instanceof Bytes)
        {
            // Extract actual value for formating
            columnValue = ((Bytes) columnValue).getValue();
            fmt = new DecimalFormat("0"); //$NON-NLS-1$
        }
        else if (columnValue instanceof Double || columnValue instanceof Float)
            fmt = new DecimalFormat("0.#####"); //$NON-NLS-1$

        if (converter != null)
            columnValue = converter.convert(((Number) columnValue).doubleValue());

        if (fmt != null)
            return fmt.format(columnValue);
        else
            return columnValue.toString();
    }
}

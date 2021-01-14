/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.query.refined;

import java.text.Format;

/**
 * Used by the {@link TotalsCalculator} to encapsulate the value and format to
 * display.
 * 
 * @since 1.5
 */
public class TotalsResult
{
    private final Object value;
    private final Format format;

    public TotalsResult(Object value, Format format)
    {
        this.value = value;
        this.format = format;
    }

    public Object getValue()
    {
        return value;
    }

    public Format getFormat()
    {
        return format;
    }
}

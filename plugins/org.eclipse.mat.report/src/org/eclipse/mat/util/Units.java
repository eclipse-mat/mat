/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.util;

import com.ibm.icu.text.NumberFormat;

/**
 * A way of adding units to values.
 */
public abstract class Units
{
    public enum Storage
    {
        BYTE("B", 1L), //$NON-NLS-1$
        KILOBYTE("KB", 1L << 10), //$NON-NLS-1$
        MEGABYTE("MB", 1L << 20), //$NON-NLS-1$
        GIGABYTE("GB", 1L << 30); //$NON-NLS-1$

        private final String symbol;
        private final long divider; // divider of BASE unit

        Storage(String name, long divider)
        {
            this.symbol = name;
            this.divider = divider;
        }

        public static Storage of(final long number)
        {
            final long n = number > 0 ? -number : number;
            if (n > -(1L << 10))
            {
                return BYTE;
            }
            else if (n > -(1L << 20))
            {
                return KILOBYTE;
            }
            else if (n > -(1L << 30))
            {
                return MEGABYTE;
            }
            else
            {
                return GIGABYTE;
            }
        }

        public String format(long number)
        {
            return nf.format((double) number / divider) + " " + symbol; //$NON-NLS-1$
        }
    }

    public enum Plain
    {
        BASE(null, 1L), //
        THOUSANDS("k", 1000L), //$NON-NLS-1$
        MILLIONS("m", 1000000L); //$NON-NLS-1$

        private final String symbol;
        private final long divider; // divider of BASE unit

        Plain(String name, long divider)
        {
            this.symbol = name;
            this.divider = divider;
        }

        public static Plain of(final long number)
        {
            final long n = number > 0 ? -number : number;
            if (n > -1000)
            {
                return BASE;
            }
            else if (n > -1000000)
            {
                return THOUSANDS;
            }
            else
            {
                return MILLIONS;
            }
        }

        public String format(long number)
        {
            String f = nf.format((double) number / divider);
            return symbol != null ? f + symbol : f;
        }
    }

    /**
     * NumberFormat is not thread safe.
     */
    private static NumberFormat nf = NumberFormat.getInstance();

    static
    {
        nf.setGroupingUsed(false);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(1);
    }

}

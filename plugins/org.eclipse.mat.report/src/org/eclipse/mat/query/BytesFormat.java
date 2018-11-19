/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates, use com.ibm.icu.text
 *******************************************************************************/
package org.eclipse.mat.query;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;


/**
 * This class formats an instance of {@link org.eclipse.mat.query.Bytes},
 * {@link java.lang.Long}, {@link java.lang.Integer}, or {@link java.lang.Short}
 * based on the currently configured {@link org.eclipse.mat.query.BytesDisplay}
 * preference.
 * 
 * @since 1.5
 */
public class BytesFormat extends Format
{
    private static final long serialVersionUID = 9162983935673281910L;
    private static final double KB = 1024;
    private static final double NEGKB = -KB;
    private static final double MB = 1048576;
    private static final double NEGMB = -MB;
    private static final double GB = 1073741824;
    private static final double NEGGB = -GB;

    /**
     * The defaultFormat is used when we are not formatting as
     * {@link org.eclipse.mat.query.BytesDisplay#Bytes}.
     */
    private static final ThreadLocal<Format> defaultFormat = new ThreadLocal<Format>()
    {
        @Override
        protected Format initialValue()
        {
            return DecimalFormat.getInstance();
        }
    };

    /**
     * The detailedFormat is used when we are formatting as anything other than
     * {@link org.eclipse.mat.query.BytesDisplay#Bytes}.
     */
    private static final ThreadLocal<Format> detailedFormat = new ThreadLocal<Format>()
    {
        @Override
        protected Format initialValue()
        {
            return new DecimalFormat(DETAILED_DECIMAL_FORMAT2);
        }
    };

    /**
     * The default format string using for decimal byte values.
     */
    public static final String DETAILED_DECIMAL_FORMAT = "#,##0.00"; //$NON-NLS-1$;
    /**
     * A slightly more internationalised version.
     */
    static final String DETAILED_DECIMAL_FORMAT2;
    static {
        NumberFormat nf = NumberFormat.getNumberInstance();
        String ret;
        if (nf instanceof DecimalFormat)
        {
            DecimalFormat df = (DecimalFormat)nf;
            // Set 2 digits for the fraction
            df.setMinimumFractionDigits(2);
            df.setMaximumFractionDigits(2);
            // But leave the thousands separator and default integer digits
            ret = df.toPattern();
            // Only use the positive part
            int i = ret.indexOf(';');
            if (i >= 0)
                ret = ret.substring(0, i);
        }
        else
        {
            ret = DETAILED_DECIMAL_FORMAT;
        }
        DETAILED_DECIMAL_FORMAT2 = ret;
    }

    private final Format encapsulatedNumberFormat;
    private final Format encapsulatedDecimalFormat;

    /**
     * Create an instance with default behavior.
     */
    public BytesFormat()
    {
        this(null, null);
    }

    /**
     * Create an instance with the behavior that if the display preference is
     * {@link org.eclipse.mat.query.BytesDisplay#Bytes}, always use
     * {@code encapsulatedNumberFormat}; otherwise, use
     * {@code encapsulatedDecimalFormat} if the value is more than 1KB.
     * @param encapsulatedNumberFormat the format for small sizes
     * @param encapsulatedDecimalFormat the format for larger sizes
     */
    public BytesFormat(Format encapsulatedNumberFormat, Format encapsulatedDecimalFormat)
    {
        this.encapsulatedNumberFormat = encapsulatedNumberFormat;
        this.encapsulatedDecimalFormat = encapsulatedDecimalFormat;
    }

    /**
     * If {@code obj} is an instance of Bytes, long, integer or short, then
     * consider the bytes display preference when formatting the value.
     * Otherwise, format {@code obj} using the default formatter.
     */
    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos)
    {
        Long target = null;
        if (obj instanceof Bytes)
        {
            target = ((Bytes) obj).getValue();
        }
        else if (obj instanceof Long)
        {
            target = ((Long) obj);
        }
        else if (obj instanceof Integer)
        {
            target = ((Integer) obj).longValue();
        }
        else if (obj instanceof Short)
        {
            target = ((Short) obj).longValue();
        }

        if (target != null)
        {
            obj = target;

            BytesDisplay currentDisplay = BytesDisplay.getCurrentValue();
            switch (currentDisplay)
            {
                case Kilobytes:
                    return formatKb(toAppendTo, target);
                case Megabytes:
                    return formatMb(toAppendTo, target);
                case Gigabytes:
                    return formatGb(toAppendTo, target);
                case Smart:
                    if (target >= GB || target <= NEGGB)
                    {
                        return formatGb(toAppendTo, target);
                    }
                    else if (target >= MB || target <= NEGMB)
                    {
                        return formatMb(toAppendTo, target);
                    }
                    else if (target >= KB || target <= NEGKB)
                    {
                        return formatKb(toAppendTo, target);
                    }
                    else
                    {
                        return formatB(toAppendTo, target);
                    }
                default:
                    // fall through
                    break;
            }
        }

        return getDefaultFormat().format(obj, toAppendTo, pos);
    }

    private Format getDefaultFormat()
    {
        return encapsulatedNumberFormat != null ? encapsulatedNumberFormat : defaultFormat.get();
    }

    private Format getDetailedFormat()
    {
        return encapsulatedDecimalFormat != null ? encapsulatedDecimalFormat : detailedFormat.get();
    }

    private StringBuffer formatGb(StringBuffer toAppendTo, double val)
    {
        double gb = (double) val / GB;
        toAppendTo.append(MessageUtil.format(Messages.BytesFormat_GB, getDetailedFormat().format(gb)));
        return toAppendTo;
    }

    private StringBuffer formatMb(StringBuffer toAppendTo, double val)
    {
        double mb = (double) val / MB;
        toAppendTo.append(MessageUtil.format(Messages.BytesFormat_MB, getDetailedFormat().format(mb)));
        return toAppendTo;
    }

    private StringBuffer formatKb(StringBuffer toAppendTo, double val)
    {
        double kb = (double) val / KB;
        toAppendTo.append(MessageUtil.format(Messages.BytesFormat_KB, getDetailedFormat().format(kb)));
        return toAppendTo;
    }

    private StringBuffer formatB(StringBuffer toAppendTo, double val)
    {
        toAppendTo.append(MessageUtil.format(Messages.BytesFormat_B, getDefaultFormat().format(val)));
        return toAppendTo;
    }

    @Override
    public Object parseObject(String source, ParsePosition pos)
    {
        return getDefaultFormat().parseObject(source, pos);
    }

    /**
     * Return a new instance of a BytesFormat with default options.
     * @return a default BytesFormat
     */
    public static BytesFormat getInstance()
    {
        return new BytesFormat();
    }
}

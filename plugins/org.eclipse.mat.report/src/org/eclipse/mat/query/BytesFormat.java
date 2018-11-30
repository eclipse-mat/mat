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
            StringBuffer ret;
            switch (currentDisplay)
            {
                case Kilobytes:
                    pos.setBeginIndex(toAppendTo.length());
                    ret = formatKb(toAppendTo, target);
                    pos.setEndIndex(toAppendTo.length());
                    return ret;
                case Megabytes:
                    pos.setBeginIndex(toAppendTo.length());
                    ret = formatMb(toAppendTo, target);
                    pos.setEndIndex(toAppendTo.length());
                    return ret;
                case Gigabytes:
                    pos.setBeginIndex(toAppendTo.length());
                    ret = formatGb(toAppendTo, target);
                    pos.setEndIndex(toAppendTo.length());
                    return ret;
                case Smart:
                    if (target >= GB || target <= NEGGB)
                    {
                        pos.setBeginIndex(toAppendTo.length());
                        ret = formatGb(toAppendTo, target);
                        pos.setEndIndex(toAppendTo.length());
                        return ret;
                    }
                    else if (target >= MB || target <= NEGMB)
                    {
                        pos.setBeginIndex(toAppendTo.length());
                        ret = formatMb(toAppendTo, target);
                        pos.setEndIndex(toAppendTo.length());
                        return ret;
                    }
                    else if (target >= KB || target <= NEGKB)
                    {
                        pos.setBeginIndex(toAppendTo.length());
                        ret = formatKb(toAppendTo, target);
                        pos.setEndIndex(toAppendTo.length());
                        return ret;
                    }
                    else
                    {
                        pos.setBeginIndex(toAppendTo.length());
                        ret = formatB(toAppendTo, target);
                        pos.setEndIndex(toAppendTo.length());
                        return ret;
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
        toAppendTo.append(getDetailedFormat().format(gb)).append(Messages.BytesFormat_GB);
        return toAppendTo;
    }

    private StringBuffer formatMb(StringBuffer toAppendTo, double val)
    {
        double mb = (double) val / MB;
        toAppendTo.append(getDetailedFormat().format(mb)).append(Messages.BytesFormat_MB);
        return toAppendTo;
    }

    private StringBuffer formatKb(StringBuffer toAppendTo, double val)
    {
        double kb = (double) val / KB;
        toAppendTo.append(getDetailedFormat().format(kb)).append(Messages.BytesFormat_KB);
        return toAppendTo;
    }

    private StringBuffer formatB(StringBuffer toAppendTo, double val)
    {
        toAppendTo.append(getDefaultFormat().format(val)).append(Messages.BytesFormat_B);
        return toAppendTo;
    }

    /**
     * Parses the input string according to the display mode.
     * Returns a {@ Bytes} object
     */
    @Override
    public Object parseObject(String source, ParsePosition pos)
    {
        BytesDisplay currentDisplay = BytesDisplay.getCurrentValue();
        if (currentDisplay != BytesDisplay.Bytes)
        {
            // Output formatting has units, so input should
            int pi = pos.getIndex();
            Object o1 = this.getDetailedFormat().parseObject(source, pos);
            if (o1 instanceof Number)
            {
                Number n1 = (Number)o1;

                if (currentDisplay == BytesDisplay.Smart &&
                                source.regionMatches(pos.getIndex(), Messages.BytesFormat_B, 0, 2))
                {
                    pos.setIndex(pos.getIndex() + 2);
                    return new Bytes(n1.longValue());
                }
                if ((currentDisplay == BytesDisplay.Kilobytes || currentDisplay == BytesDisplay.Smart) &&
                                source.regionMatches(pos.getIndex(), Messages.BytesFormat_KB, 0, 3))
                {
                    pos.setIndex(pos.getIndex() + 3);
                    return new Bytes((long)(n1.longValue() * KB));
                }
                if ((currentDisplay == BytesDisplay.Megabytes || currentDisplay == BytesDisplay.Smart) &&
                                source.regionMatches(pos.getIndex(), Messages.BytesFormat_MB, 0, 3))
                {
                    pos.setIndex(pos.getIndex() + 3);
                    return new Bytes((long)(n1.longValue() * MB));
                }
                if ((currentDisplay == BytesDisplay.Gigabytes || currentDisplay == BytesDisplay.Smart) &&
                                source.regionMatches(pos.getIndex(), Messages.BytesFormat_GB, 0, 3))
                {
                    pos.setIndex(pos.getIndex() + 3);
                    return new Bytes((long)(n1.longValue() * GB));
                }
                pos.setErrorIndex(pos.getIndex());
                pos.setIndex(pi);
                // Given a format, but no suffix given
                return null;
            }
            // Not parsed as a Number, so can't check the suffix
            pos.setErrorIndex(pi);
            pos.setIndex(pi);
            return null;
        }
        else
        {
            Object ret = getDefaultFormat().parseObject(source, pos);
            if (ret instanceof Number)
                return new Bytes(((Number)ret).longValue());
            return ret;
        }
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

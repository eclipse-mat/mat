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

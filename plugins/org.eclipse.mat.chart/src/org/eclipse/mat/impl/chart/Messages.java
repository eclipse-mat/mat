package org.eclipse.mat.impl.chart;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.impl.chart.messages"; //$NON-NLS-1$
    public static String ChartBuilder_Total;
    public static String HtmlPieChartRenderer_ErrorRenderingChart;
    public static String HtmlPieChartRenderer_SeeLogForDetails;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

package org.eclipse.mat.ui.internal.chart;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.ui.internal.chart.messages"; //$NON-NLS-1$
    public static String ChartCanvas_Error_DisableChartRendering;
    public static String PieChartPane_Chart;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

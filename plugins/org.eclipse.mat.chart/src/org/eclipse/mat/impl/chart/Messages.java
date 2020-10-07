/*******************************************************************************
 * Copyright (c) 2010,2020 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *     Andrew Johnson (IBM Corporation) - more links
 *******************************************************************************/
package org.eclipse.mat.impl.chart;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.impl.chart.messages"; //$NON-NLS-1$
    public static String ChartBuilder_Total;
    public static String HtmlPieChartRenderer_AreaAltReplace;
    public static String HtmlPieChartRenderer_ErrorRenderingChart;
    public static String HtmlPieChartRenderer_LabelTooltipWithStorage;
    public static String HtmlPieChartRenderer_PieChartSlices;
    public static String HtmlPieChartRenderer_SeeLogForDetails;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

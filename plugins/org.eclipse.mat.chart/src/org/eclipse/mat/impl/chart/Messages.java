/*******************************************************************************
 * Copyright (c) 2010,2020 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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

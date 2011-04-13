/*******************************************************************************
 * Copyright (c) 2008, 2011 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - accessibility improvements
 *******************************************************************************/
package org.eclipse.mat.impl.chart;

import org.eclipse.birt.chart.computation.DataPointHints;
import org.eclipse.birt.chart.model.component.Label;
import org.eclipse.birt.chart.script.ChartEventHandlerAdapter;
import org.eclipse.birt.chart.script.IChartScriptContext;
import org.eclipse.mat.util.Units;

public class LabelRenderScript extends ChartEventHandlerAdapter
{

    public void beforeDrawDataPointLabel(DataPointHints dph, Label label, IChartScriptContext icsc)
    {
        double value = ((Double) dph.getOrthogonalValue()).doubleValue();
        long longValue = (long)value;
        String newLabel = ChartBuilder.withPrefix(dph.getIndex(), Units.Storage.of(longValue).format(longValue));
        label.getCaption().setValue(newLabel);
    }

}

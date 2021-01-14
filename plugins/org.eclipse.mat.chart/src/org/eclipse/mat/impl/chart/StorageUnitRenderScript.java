/*******************************************************************************
 * Copyright (c) 2008, 2011 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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

public class StorageUnitRenderScript extends ChartEventHandlerAdapter
{
    public void beforeDrawDataPointLabel(DataPointHints dph, Label label, IChartScriptContext icsc)
    {
        double value = ((Double) dph.getOrthogonalValue()).doubleValue();
        long longValue = (long)value;
        label.getCaption().setValue(Units.Storage.of(longValue).format(longValue));
    }

}

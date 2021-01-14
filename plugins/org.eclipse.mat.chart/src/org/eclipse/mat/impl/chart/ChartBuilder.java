/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - accessibility improvements, bug 364505
 *******************************************************************************/
package org.eclipse.mat.impl.chart;

import java.awt.Color;
import java.util.List;

import org.eclipse.birt.chart.model.Chart;
import org.eclipse.birt.chart.model.ChartWithoutAxes;
import org.eclipse.birt.chart.model.attribute.ActionType;
import org.eclipse.birt.chart.model.attribute.Anchor;
import org.eclipse.birt.chart.model.attribute.ColorDefinition;
import org.eclipse.birt.chart.model.attribute.Fill;
import org.eclipse.birt.chart.model.attribute.Position;
import org.eclipse.birt.chart.model.attribute.TriggerCondition;
import org.eclipse.birt.chart.model.attribute.impl.CallBackValueImpl;
import org.eclipse.birt.chart.model.attribute.impl.ColorDefinitionImpl;
import org.eclipse.birt.chart.model.component.Series;
import org.eclipse.birt.chart.model.component.impl.SeriesImpl;
import org.eclipse.birt.chart.model.data.SeriesDefinition;
import org.eclipse.birt.chart.model.data.impl.ActionImpl;
import org.eclipse.birt.chart.model.data.impl.NumberDataSetImpl;
import org.eclipse.birt.chart.model.data.impl.SeriesDefinitionImpl;
import org.eclipse.birt.chart.model.data.impl.TextDataSetImpl;
import org.eclipse.birt.chart.model.data.impl.TriggerImpl;
import org.eclipse.birt.chart.model.impl.ChartWithoutAxesImpl;
import org.eclipse.birt.chart.model.layout.LabelBlock;
import org.eclipse.birt.chart.model.layout.Plot;
import org.eclipse.birt.chart.model.layout.impl.LabelBlockImpl;
import org.eclipse.birt.chart.model.type.PieSeries;
import org.eclipse.birt.chart.model.type.impl.PieSeriesImpl;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.EList;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultPie.ColoredSlice;
import org.eclipse.mat.query.IResultPie.Slice;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.Units;

public class ChartBuilder
{
    public static final Chart create(IResultPie pie, boolean isInteractive, ColorDefinition background,
                    ColorDefinition foreground)
    {
        float fontSize = Platform.OS_MACOSX.equals(Platform.getOS()) ? 10f : 8f;

        List<? extends Slice> slices = pie.getSlices();

        // labels
        String[] labels = new String[slices.size()];

        if (isInteractive)
            for (int ii = 0; ii < labels.length; ii++)
                labels[ii] = slices.get(ii).getLabel();
        else
            for (int ii = 0; ii < labels.length; ii++)
                labels[ii] = withPrefix(ii, slices.get(ii).getLabel());

        // values
        double total = 0d;
        double[] values = new double[slices.size()];
        for (int ii = 0; ii < values.length; ii++)
        {
            values[ii] = slices.get(ii).getValue();
            total += values[ii];
        }

        ChartWithoutAxes chart = ChartWithoutAxesImpl.create();
        if (isInteractive)
            chart.setScript(StorageUnitRenderScript.class.getName());
        else
            chart.setScript(LabelRenderScript.class.getName());

        // title
        chart.getTitle().setVisible(false);

        // total label
        long t = (long)total;
        LabelBlock label = (LabelBlock) LabelBlockImpl.create();
        label.getLabel().getCaption().setValue(
                        MessageUtil.format(Messages.ChartBuilder_Total, Units.Storage.of(t).format(t)));

        // use default font -> no dependency on jface -> no usage of SWT which
        // is not safe in batch mode
        label.getLabel().getCaption().getFont().setSize(fontSize);
        label.getLabel().getCaption().getFont().setBold(true);
        if (foreground != null)
        {
            label.getLabel().getCaption().setColor(ColorDefinitionImpl.create(foreground.getRed(), foreground.getGreen(),
                            foreground.getBlue()));
        }
        label.setAnchor(Anchor.SOUTH_LITERAL);
        chart.getPlot().add(label);

        // legend
        if (isInteractive)
        {
            chart.getLegend().setVisible(false);
        }
        else
        {
            chart.getLegend().setVisible(true);

            if (background != null)
            {
                chart.getLegend().setBackground(
                                ColorDefinitionImpl.create(background.getRed(), background.getGreen(),
                                                background.getBlue()));
            }
            chart.getLegend().getText().getFont().setSize(fontSize);
            if (foreground != null)
            {
                chart.getLegend().getText().setColor(ColorDefinitionImpl.create(foreground.getRed(), foreground.getGreen(),
                                foreground.getBlue()));
            }
        }

        if (background != null)
        {
            Plot p = chart.getPlot();
            p.setBackground(ColorDefinitionImpl.create(background.getRed(), background.getGreen(), background.getBlue()));
            p.getClientArea()
                            .setBackground(ColorDefinitionImpl.create(background.getRed(), background.getGreen(),
                                            background.getBlue()));
            chart.getBlock()
                            .setBackground(ColorDefinitionImpl.create(background.getRed(), background.getGreen(),
                                            background.getBlue()));
        }

        Series seLabels = SeriesImpl.create();
        seLabels.setDataSet(TextDataSetImpl.create(labels));

        // color palette
        SeriesDefinition sd = SeriesDefinitionImpl.create();
        sd.getSeries().add(seLabels);

        chart.getSeriesDefinitions().add(sd);

        EList<Fill> entries = sd.getSeriesPalette().getEntries();
        entries.clear();
        int index = 0;
        while (index < slices.size() - 1)
        {
            int[] color = COLORS[index % COLORS.length];
            ColorDefinition defn;
            Color explicitColor = null;
            Slice slice = slices.get(index); 
            if (slice instanceof ColoredSlice)
            {
                explicitColor = ((ColoredSlice) slice).getColor();
            }
            if (explicitColor == null)
            {
                defn = ColorDefinitionImpl.create(color[0], color[1], color[2]);
            }
            else
            {
                defn = ColorDefinitionImpl.create(explicitColor.getRed(), explicitColor.getGreen(), explicitColor.getBlue());
            }
            entries.add(defn);

            index++;
        }
        int[] lastColor = slices.get(slices.size() - 1).getContext() == null ? COLOR_REST //
                        : COLORS[(slices.size() - 1) % COLORS.length];
        entries.add(ColorDefinitionImpl.create(lastColor[0], lastColor[1], lastColor[2]));

        PieSeries pieSeries = (PieSeries) PieSeriesImpl.create();
        pieSeries.setDataSet(NumberDataSetImpl.create(values));
        pieSeries.setLabelPosition(Position.OUTSIDE_LITERAL);
        pieSeries.getLabel().getCaption().getFont().setSize(fontSize);
        pieSeries.setExplosion(5);

        if (foreground != null)
        {
            pieSeries.getLabel()
                            .getCaption()
                            .setColor(ColorDefinitionImpl.create(foreground.getRed(), foreground.getGreen(),
                                            foreground.getBlue()));
            pieSeries.getLeaderLineAttributes()
                            .setColor(ColorDefinitionImpl.create(foreground.getRed(), foreground.getGreen(),
                                            foreground.getBlue()));
        }
        if (background != null)
        {
            pieSeries.getLabel()
                            .setBackground(ColorDefinitionImpl.create(background.getRed(), background.getGreen(),
                                            background.getBlue()));
        }

        if (isInteractive)
        {
            pieSeries.getTriggers().add(
                            TriggerImpl.create(TriggerCondition.ONFOCUS_LITERAL, ActionImpl.create(
                                            ActionType.CALL_BACK_LITERAL, CallBackValueImpl.create("focus"))));//$NON-NLS-1$

            pieSeries.getTriggers().add(
                            TriggerImpl.create(TriggerCondition.ONCLICK_LITERAL, ActionImpl.create(
                                            ActionType.CALL_BACK_LITERAL, CallBackValueImpl.create("click"))));//$NON-NLS-1$

            pieSeries.getTriggers().add(
                            TriggerImpl.create(TriggerCondition.ONMOUSEOVER_LITERAL, ActionImpl.create(
                                            ActionType.CALL_BACK_LITERAL, CallBackValueImpl.create("tooltip"))));//$NON-NLS-1$
            pieSeries.getTriggers().add(
                            TriggerImpl.create(
                                            TriggerCondition.ONKEYDOWN_LITERAL,
                                            ActionImpl.create(ActionType.CALL_BACK_LITERAL,
                                                            CallBackValueImpl.create("keypress"))));//$NON-NLS-1$
            
        }

        SeriesDefinition sdBase = SeriesDefinitionImpl.create();
        sd.getSeriesDefinitions().add(sdBase);
        sdBase.getSeries().add(pieSeries);

        return chart;
    }

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz";//$NON-NLS-1$

    public static final String withPrefix(int index, String label)
    {
        StringBuilder buf = new StringBuilder();

        buf.append("("); //$NON-NLS-1$
        int d = index / CHARS.length();
        if (d > 0)
            buf.append(CHARS.charAt(d % CHARS.length()));
        buf.append(CHARS.charAt(index % CHARS.length()));
        buf.append(")  ");//$NON-NLS-1$
        buf.append(label);

        return buf.toString();
    }

    private static final int[] COLOR_REST = new int[] { 220, 220, 220 };

    private static final int[][] COLORS = new int[][] { { 96, 127, 143 }, // 85%
                    { 98, 146, 147 }, //
                    { 110, 138, 79 }, //
                    { 140, 101, 87 }, //
                    { 123, 96, 114 }, //
                    { 101, 129, 120 }, //
                    { 148, 132, 75 }, //
                    { 150, 103, 110 }, //

                    { 152, 173, 183 }, // 55%
                    { 154, 185, 185 }, //
                    { 162, 180, 141 }, //
                    { 181, 156, 147 }, //
                    { 170, 152, 164 }, //
                    { 156, 175, 168 }, //
                    { 186, 176, 139 }, //
                    { 188, 157, 162 }, //

                    { 68, 105, 125 }, // 100%
                    { 21, 101, 112 }, //
                    { 85, 118, 48 }, //
                    { 119, 74, 57 }, //
                    { 100, 68, 89 }, //
                    { 73, 108, 96 }, //
                    { 129, 110, 44 }, //
                    { 132, 76, 84 } //
    };
}

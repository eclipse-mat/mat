/*******************************************************************************
 * Copyright (c) 2008, 2011 SAP AG.
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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.chart.computation.LegendItemHints;
import org.eclipse.birt.chart.device.EmptyUpdateNotifier;
import org.eclipse.birt.chart.device.IDeviceRenderer;
import org.eclipse.birt.chart.device.IImageMapEmitter;
import org.eclipse.birt.chart.event.StructureSource;
import org.eclipse.birt.chart.event.StructureType;
import org.eclipse.birt.chart.exception.ChartException;
import org.eclipse.birt.chart.factory.GeneratedChartState;
import org.eclipse.birt.chart.factory.Generator;
import org.eclipse.birt.chart.factory.RunTimeContext;
import org.eclipse.birt.chart.model.Chart;
import org.eclipse.birt.chart.model.attribute.ActionType;
import org.eclipse.birt.chart.model.attribute.Bounds;
import org.eclipse.birt.chart.model.attribute.TooltipValue;
import org.eclipse.birt.chart.model.attribute.TriggerCondition;
import org.eclipse.birt.chart.model.attribute.impl.BoundsImpl;
import org.eclipse.birt.chart.model.attribute.impl.ColorDefinitionImpl;
import org.eclipse.birt.chart.model.attribute.impl.TooltipValueImpl;
import org.eclipse.birt.chart.model.data.Action;
import org.eclipse.birt.chart.model.data.Trigger;
import org.eclipse.birt.chart.model.data.impl.ActionImpl;
import org.eclipse.birt.chart.model.data.impl.TriggerImpl;
import org.eclipse.birt.chart.render.IActionRenderer;
import org.eclipse.birt.chart.script.IScriptClassLoader;
import org.eclipse.birt.chart.util.PluginSettings;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Renderer;

@Renderer(target = "html", result = IResultPie.class)
public class HtmlPieChartRenderer implements IOutputter
{

    @SuppressWarnings("unchecked")
	public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        try
        {
            IResultPie pie = (IResultPie) result;

            String imageFile = "chart" + context.getId() + ".png"; //$NON-NLS-1$ //$NON-NLS-2$

            Chart chart = ChartBuilder.create(pie, false, ColorDefinitionImpl.WHITE(), ColorDefinitionImpl.BLACK());
         
            Trigger trigger = TriggerImpl.create(TriggerCondition.ONMOUSEOVER_LITERAL,
            		ActionImpl.create(ActionType.SHOW_TOOLTIP_LITERAL,
                    		TooltipValueImpl.create(200, "tooltip"))); //$NON-NLS-1$
			chart.getLegend().getTriggers().add(trigger); 
   			
            PluginSettings ps = PluginSettings.instance();
            IDeviceRenderer render = ps.getDevice("dv.PNG"); //$NON-NLS-1$
            render.setProperty(IDeviceRenderer.FILE_IDENTIFIER, new File(context.getOutputDirectory(), imageFile));

            Bounds bo = BoundsImpl.create(0, 0, 800, 350);
            int resolution = render.getDisplayServer().getDpiResolution();
            bo.scale(72d / resolution);

            RunTimeContext rtc = new RunTimeContext();
            rtc.setScriptClassLoader(new IScriptClassLoader()
            {
                public Class<?> loadClass(String className, ClassLoader parentLoader) throws ClassNotFoundException
                {
                    return getClass().getClassLoader().loadClass(className);
                }
            });

            Generator gr = Generator.instance();
            GeneratedChartState state = gr.build(render.getDisplayServer(), chart, bo, null, rtc, null);
            state.getRunTimeContext().setActionRenderer(new IActionRenderer() {
			
				public void processAction(Action action, StructureSource source) {
					if (ActionType.SHOW_TOOLTIP_LITERAL.equals(action.getType())) {
						TooltipValue tooltip = (TooltipValue) action.getValue();
						if (StructureType.LEGEND_ENTRY.equals(source.getType())) {
							LegendItemHints item = (LegendItemHints) source.getSource();
							tooltip.setText(item.getItemText());
						}
					}
			
				}
			});
            render.setProperty(IDeviceRenderer.UPDATE_NOTIFIER, new EmptyUpdateNotifier(chart, state.getChartModel()));
            gr.render(render, state);
            String imageMap = ((IImageMapEmitter) render).getImageMap();
            String mapName = "chart" + context.getId() + "map"; //$NON-NLS-1$ //$NON-NLS-2$
            writer.append("<map name='").append(mapName).append("'>").append(imageMap).append("</map>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writer.append("<img src=\"").append(imageFile).append("\" width=\"800\" height=\"350\" usemap='#").append(mapName).append("'>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        catch (LinkageError e)
        {
            handleError(e, writer);
        }
        catch (ChartException e)
        {
            handleError(e, writer);
        }
    }

    private void handleError(Throwable e, Writer writer) throws IOException
    {
        StringBuilder message = new StringBuilder();
        message.append(Messages.HtmlPieChartRenderer_ErrorRenderingChart);
        message.append(e.getClass().getName());

        if (e.getMessage() != null)
            message.append(": ").append(e.getMessage()); //$NON-NLS-1$

        String msg = message.toString();

        writer.append(msg).append(Messages.HtmlPieChartRenderer_SeeLogForDetails);
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, msg, e);
    }

    public void process(Context context, IResult result, Writer writer) throws IOException
    {}

}

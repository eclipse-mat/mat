/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.impl.chart;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.chart.device.IDeviceRenderer;
import org.eclipse.birt.chart.exception.ChartException;
import org.eclipse.birt.chart.factory.GeneratedChartState;
import org.eclipse.birt.chart.factory.Generator;
import org.eclipse.birt.chart.factory.RunTimeContext;
import org.eclipse.birt.chart.model.Chart;
import org.eclipse.birt.chart.model.attribute.Bounds;
import org.eclipse.birt.chart.model.attribute.impl.BoundsImpl;
import org.eclipse.birt.chart.script.IScriptClassLoader;
import org.eclipse.birt.chart.util.PluginSettings;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Renderer;

@Renderer(target = "html", result = IResultPie.class)
public class HtmlPieChartRenderer implements IOutputter
{

    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        try
        {
            IResultPie pie = (IResultPie) result;

            String imageFile = "chart" + context.getId() + ".png";

            Chart chart = ChartBuilder.create(pie, false);

            PluginSettings ps = PluginSettings.instance();
            IDeviceRenderer render = ps.getDevice("dv.PNG");
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
            gr.render(render, state);

            writer.append("<img src=\"").append(imageFile).append("\" width=\"800\" height=\"350\">");
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
        message.append("Error rendering chart: ");
        message.append(e.getClass().getName());

        if (e.getMessage() != null)
            message.append(": ").append(e.getMessage());

        String msg = message.toString();

        writer.append(msg).append(" (See Log for Details)");
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, msg, e);
    }

    public void process(Context context, IResult result, Writer writer) throws IOException
    {}

}

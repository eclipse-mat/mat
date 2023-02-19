/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - disposal of resources
 *******************************************************************************/
package org.eclipse.mat.ui.internal.chart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

public class ChartCanvas extends Canvas
{

    /* package */IDeviceRenderer renderer = null;

    private Chart chart = null;

    private GeneratedChartState state = null;

    private Image cachedImage = null;

    private boolean needsGeneration = true;
    private boolean isDisabled = false;
    private boolean usePNG = false;

    public ChartCanvas(Composite parent, int style)
    {
        super(parent, style);

        try
        {
            // initialize the SWT rendering device
            PluginSettings ps = PluginSettings.instance();
            renderer = ps.getDevice("dv.SWT"); //$NON-NLS-1$
            if (renderer == null)
            {
                // initialize the PNG rendering device instead
                renderer = ps.getDevice("dv.PNG"); //$NON-NLS-1$
                if (renderer != null)
                {
                    usePNG = true;
                }
                else
                {
                    isDisabled = true;
                    UnsupportedOperationException e = new UnsupportedOperationException("dv.SWT");
                    MemoryAnalyserPlugin.log(e, MessageUtil.format(Messages.ChartCanvas_Error_DisableChartRendering, e.getMessage()
                                    ));
                    throw e;
                }
            }
        }
        catch (ChartException e)
        {
            throw new RuntimeException(e);
        }
        
        addPaintListener(new PaintListener()
        {

            public void paintControl(PaintEvent e)
            {
                if (isDisabled)
                    return;

                try
                {
                    Composite co = (Composite) e.getSource();
                    final Rectangle rect = co.getClientArea();

                    if (needsGeneration)
                        drawToCachedImage(rect);

                    if (usePNG)
                    {
                        setBackgroundImage(cachedImage);
                    }
                    else
                    {
                        e.gc.drawImage(cachedImage, 0, 0, cachedImage.getBounds().width, cachedImage.getBounds().height, 0,
                                        0, rect.width, rect.height);
                    }
                }
                catch (LinkageError t)
                {
                    handleError(t);
                }
                catch (ChartException t)
                {
                    handleError(t);
                }
            }

            private void handleError(Throwable t)
            {
                isDisabled = true;
                MemoryAnalyserPlugin.log(t, MessageUtil.format(Messages.ChartCanvas_Error_DisableChartRendering, t
                                .getMessage()));
            }
        });

        addControlListener(new ControlAdapter()
        {
            public void controlResized(ControlEvent e)
            {
                needsGeneration = true;
            }
        });
    }

    private void generateChartState() throws ChartException
    {
        Point size = getSize();
        Bounds bo = BoundsImpl.create(0, 0, size.x, size.y);
        int resolution = renderer.getDisplayServer().getDpiResolution();
        bo.scale(72d / resolution);
        Generator gr = Generator.instance();

        RunTimeContext rtc = new RunTimeContext();
        rtc.setScriptClassLoader(new IScriptClassLoader()
        {

            public Class<?> loadClass(String className, ClassLoader parentLoader) throws ClassNotFoundException
            {
                return getClass().getClassLoader().loadClass(className);
            }

        });

        state = gr.build(renderer.getDisplayServer(), chart.copyInstance(), bo, null, rtc, null);

        needsGeneration = false;
    }

    private void drawToCachedImage(Rectangle size) throws ChartException
    {
        GC gc = null;
        try
        {
            if (cachedImage != null)
                cachedImage.dispose();

            ByteArrayOutputStream os = null;
            if (usePNG)
            {
                os = new ByteArrayOutputStream();
                renderer.setProperty(IDeviceRenderer.FILE_IDENTIFIER, os);
            }
            else
            {
                cachedImage = new Image(getDisplay(), size.width, size.height);

                gc = new GC(cachedImage);
                renderer.setProperty(IDeviceRenderer.GRAPHICS_CONTEXT, gc);
            }

            if (chart != null)
            {
                generateChartState();
                Generator gr = Generator.instance();
                gr.render(renderer, state);
            }
            if (os != null)
                os.close();
            if (usePNG)
            {
                try (InputStream inputStream = new ByteArrayInputStream(os.toByteArray());)
                {
                    cachedImage = new Image(getDisplay(), inputStream);
                }
            }
        }
        catch (IOException e)
        {
            isDisabled = true;
            MemoryAnalyserPlugin.log(e,
                            MessageUtil.format(Messages.ChartCanvas_Error_DisableChartRendering, e.getMessage()));
        }
        finally
        {
            if (gc != null)
                gc.dispose();
        }
    }

    public Chart getChart()
    {
        return chart;
    }

    public void setChart(Chart chart)
    {
        needsGeneration = true;
        this.chart = chart;
    }

    @Override
    public void dispose()
    {
        if (cachedImage != null)
            cachedImage.dispose();
        if (renderer != null)
            renderer.dispose();
        super.dispose();
    }

    public Chart getRunTimeModel()
    {
        return state.getChartModel();
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others.
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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

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
import org.eclipse.birt.chart.model.attribute.Anchor;
import org.eclipse.birt.chart.model.attribute.Bounds;
import org.eclipse.birt.chart.model.attribute.Insets;
import org.eclipse.birt.chart.model.attribute.TooltipValue;
import org.eclipse.birt.chart.model.attribute.TriggerCondition;
import org.eclipse.birt.chart.model.attribute.impl.BoundsImpl;
import org.eclipse.birt.chart.model.attribute.impl.ColorDefinitionImpl;
import org.eclipse.birt.chart.model.attribute.impl.TooltipValueImpl;
import org.eclipse.birt.chart.model.attribute.impl.URLValueImpl;
import org.eclipse.birt.chart.model.data.Action;
import org.eclipse.birt.chart.model.data.Trigger;
import org.eclipse.birt.chart.model.data.impl.ActionImpl;
import org.eclipse.birt.chart.model.data.impl.TriggerImpl;
import org.eclipse.birt.chart.model.layout.Block;
import org.eclipse.birt.chart.model.layout.LabelBlock;
import org.eclipse.birt.chart.model.layout.Legend;
import org.eclipse.birt.chart.render.ActionRendererAdapter;
import org.eclipse.birt.chart.script.IScriptClassLoader;
import org.eclipse.birt.chart.util.PluginSettings;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultPie.Slice;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Renderer;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.MessageUtil;

@Renderer(target = "html", result = IResultPie.class)
public class HtmlPieChartRenderer implements IOutputter
{

    public void embedd(final Context context, IResult result, Writer writer) throws IOException
    {
        try
        {
            final IResultPie pie = (IResultPie) result;

            String imageFile = "chart" + context.getId() + ".png"; //$NON-NLS-1$ //$NON-NLS-2$

            Chart chart = ChartBuilder.create(pie, false, ColorDefinitionImpl.WHITE(), ColorDefinitionImpl.BLACK());

            // For hover text with title=
            Trigger trigger = TriggerImpl.create(TriggerCondition.ONMOUSEOVER_LITERAL, ActionImpl
                            .create(ActionType.SHOW_TOOLTIP_LITERAL, TooltipValueImpl.create(200, "tooltip"))); //$NON-NLS-1$
            chart.getLegend().getTriggers().add(trigger);
            // For open mat:// URL
            Trigger trigger2 = TriggerImpl.create(TriggerCondition.ONCLICK_LITERAL, ActionImpl.create(
                            ActionType.URL_REDIRECT_LITERAL, URLValueImpl.create(null, null, null, null, null)));
            chart.getLegend().getTriggers().add(trigger2);
            // For open mat:// URL
            Trigger trigger3 = TriggerImpl.create(TriggerCondition.ONKEYPRESS_LITERAL, ActionImpl.create(
                            ActionType.URL_REDIRECT_LITERAL, URLValueImpl.create(null, null, null, null, null)));
            chart.getLegend().getTriggers().add(trigger3);

            PluginSettings ps = PluginSettings.instance();
            IDeviceRenderer render = ps.getDevice("dv.PNG"); //$NON-NLS-1$
            render.setProperty(IDeviceRenderer.FILE_IDENTIFIER, new File(context.getOutputDirectory(), imageFile));

            // Set size for chart
            int width = 850, height = 350;
            final int slices = pie.getSlices().size();
            // Make room for more columns of labels
            if (slices > 13)
                width += 350 * (slices / 13);
            Bounds bo = BoundsImpl.create(0, 0, width, height);
            /**
             * Windows on high res display.
             * 100% =  96 15", 22" display, 1920 x 1080
             * 125% = 120
             * 150% = 144
             * 175% = 168
             */
            int resolution = render.getDisplayServer().getDpiResolution();
            bo.scale(72d / resolution);

            // Make a bit more room for long class names in the legend
            Legend lg = chart.getLegend();
            Insets is = lg.getInsets();
            is.setLeft(is.getLeft() - 10);
            // Normally 40%
            lg.setMaxPercent((width - 510.0) / width);
            lg.setAnchor(Anchor.EAST_LITERAL);

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
            String plotLabel = null;
            for (Block o : chart.getPlot().getChildren()) {
                if (o instanceof LabelBlock)
                {
                    plotLabel = ((LabelBlock)o).getLabel().getCaption().getValue();
                }
            }
            state.getRunTimeContext().setActionRenderer(new ActionRendererAdapter()
            {

                public void processAction(Action action, StructureSource source)
                {
                    if (ActionType.SHOW_TOOLTIP_LITERAL.equals(action.getType()))
                    {
                        TooltipValue tooltip = (TooltipValue) action.getValue();
                        if (StructureType.LEGEND_ENTRY.equals(source.getType()))
                        {
                            LegendItemHints item = (LegendItemHints) source.getSource();
                            Slice slice = pie.getSlices().get(item.getIndex());
                            String descs[] = slice.getDescription().split("<br/>", 2); //$NON-NLS-1$
                            if (descs.length >= 2)
                            {
                                // Extended description - we use the first half from BIRT as it has the slice letter
                                // Remove HTML tags <p> etc.
                                String desc = descs[1].replaceAll("<[/a-z]+>", ""); //$NON-NLS-1$ //$NON-NLS-2$
                                /*
                                 * Should all be escaped by org.eclipse.mat.snapshot.query.PieFactory.PieImpl
                                 * except for double-quote, but BIRT will escape the values before putting into HTML.
                                 */
                                // Add the extra size information to the label to give the hover text (title).
                                tooltip.setText(MessageUtil.format(Messages.HtmlPieChartRenderer_LabelTooltipWithStorage, item.getItemText(), desc));
                            }
                            else
                            {
                                tooltip.setText(item.getItemText());
                            }
                        }
                    }
                    else if (ActionType.URL_REDIRECT_LITERAL.equals(action.getType()))
                    {
                        URLValueImpl cb = (URLValueImpl) action.getValue();
                        if (StructureType.LEGEND_ENTRY.equals(source.getType()))
                        {
                            LegendItemHints item = (LegendItemHints) source.getSource();
                            Slice slice = pie.getSlices().get(item.getIndex());
                            if (slice.getContext() != null)
                            {
                                try
                                {
                                    int id = slice.getContext().getObjectId();
                                    if (id != -1)
                                    {
                                        String externalIdentifier = context.getQueryContext().mapToExternalIdentifier(id);
                                        cb.setBaseUrl(QueryObjectLink.forObject(externalIdentifier));
                                    }
                                }
                                catch (SnapshotException e)
                                {
                                    return;
                                }
                            }
                        }

                    }
                }
                /*
                 * BIRT in Kepler M7 calls this method instead
                 */
                public void processAction(Action action, StructureSource source, RunTimeContext ctx)
                {
                    processAction(action, source);
                }
            });
            render.setProperty(IDeviceRenderer.UPDATE_NOTIFIER, new EmptyUpdateNotifier(chart, state.getChartModel()));
            gr.render(render, state);
            String imageMap = ((IImageMapEmitter) render).getImageMap();
            // fix up HTML errors
            imageMap = fixupMapAreas(imageMap, resolution);
            String mapName = "chart" + context.getId() + "map"; //$NON-NLS-1$ //$NON-NLS-2$
            writer.append("<map name='").append(mapName).append("'>").append(imageMap).append("</map>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String slicesText = MessageUtil.format(Messages.HtmlPieChartRenderer_PieChartSlices, slices);
            String altText;
            if (plotLabel != null)
            {
                altText =  MessageUtil.format(Messages.HtmlPieChartRenderer_LabelTooltipWithStorage, slicesText, plotLabel);
            }
            else
            {
                altText = slicesText;
            }
            writer.append("<img src=\"").append(imageFile).append("\" width=\"" + width + "\" height=\"" + height + "\" usemap='#").append(mapName).append("' alt=\"").append(altText).append("\" title=\"").append(slicesText).append("\">"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
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

    /**
     * BIRT has some problems with map area
     * @param imageMap a fragment of HTML; a list of &lt;area&gt;
     * @param resolution in DPI - normal is 96
     * @return a fixed-up fragment of HTML
     */
    private String fixupMapAreas(String imageMap, int resolution)
    {
        // Fix up for HTML4
        imageMap = imageMap.replaceAll("/><area","><area").replaceFirst("/>$", ">"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        // Fix up alternate text
        String altReplace = MessageFormat.format(Matcher.quoteReplacement(Messages.HtmlPieChartRenderer_AreaAltReplace), "$2"); //$NON-NLS-1$
        imageMap = imageMap.replaceAll("alt=\"\"([^>]*)title=\"([^\"]*)\"", "alt=\"" + altReplace + "\"$1title=\"$2\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        // Fix up no destination
        imageMap = imageMap.replace("href=\"\"", ""); //$NON-NLS-1$ //$NON-NLS-2$
        // Fix up order of areas - BIRT generates in reverse order
        String areas[] = imageMap.split("<area "); //$NON-NLS-1$
        Arrays.sort(areas, new Comparator<String>() {
            public int compare(String o1, String o2)
            {
                int i1 = o1.indexOf("title"); //$NON-NLS-1$
                int i2 = o2.indexOf("title"); //$NON-NLS-1$
                return o1.substring(i1 + 1).compareTo(o2.substring(i2 + 1));
            }
        });
        /*
         * Fix up scaling of area coordinates - they seem too small in BIRT 4.7
         * 
         * Set up scaling as things go wrong on Windows
         * with high DPI scaling of 125%, 150% etc.
         * Expect first coordinate to be about 850*0.6=510
         * Round to nearest 25%.
         */
        double scaling = resolution / 96.0;
        for (int i = 0; i < areas.length; ++i)
        {
            String m = "coords=\""; //$NON-NLS-1$
            int i1 = areas[i].indexOf(m);
            if (i1 >= 0)
            {
                i1 += m.length();
                int i2 = areas[i].indexOf("\"", i1); //$NON-NLS-1$
                if (i2 > 0) {
                    StringBuilder sb = new StringBuilder(areas[i].substring(0, i1));
                    String coords[] = areas[i].substring(i1, i2).split(","); //$NON-NLS-1$
                    for (int j = 0; j < coords.length; ++j)
                    {
                        if (j > 0)
                            sb.append(',');
                        int v = Integer.parseInt(coords[j]);
                        sb.append((int)(v * scaling));
                    }
                    sb.append(areas[i].substring(i2));
                    areas[i] = sb.toString();
                }
            }
        }
        imageMap = String.join("<area ", areas); //$NON-NLS-1$
        return imageMap;
    }

    private void handleError(Throwable e, Writer writer) throws IOException
    {
        StringBuilder message = new StringBuilder();
        message.append(Messages.HtmlPieChartRenderer_ErrorRenderingChart);
        message.append(HTMLUtils.escapeText(e.getClass().getName()));

        if (e.getMessage() != null)
            message.append(": ").append(HTMLUtils.escapeText(e.getMessage())); //$NON-NLS-1$

        String msg = message.toString();

        writer.append(msg).append(Messages.HtmlPieChartRenderer_SeeLogForDetails);
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, msg, e);
    }

    public void process(Context context, IResult result, Writer writer) throws IOException
    {}

}

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
package org.eclipse.mat.ui.internal.chart;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.birt.chart.computation.DataPointHints;
import org.eclipse.birt.chart.device.ICallBackNotifier;
import org.eclipse.birt.chart.device.IDeviceRenderer;
import org.eclipse.birt.chart.event.StructureSource;
import org.eclipse.birt.chart.model.Chart;
import org.eclipse.birt.chart.model.attribute.CallBackValue;
import org.eclipse.birt.chart.model.attribute.impl.ColorDefinitionImpl;
import org.eclipse.birt.chart.model.layout.Plot;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.impl.chart.ChartBuilder;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultPie.Slice;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.actions.OpenHelpPageAction;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.forms.widgets.FormText;

public class PieChartPane extends AbstractEditorPane implements ISelectionProvider
{
    List<ISelectionChangedListener> selectionListeners = new ArrayList<ISelectionChangedListener>();
    private TraverseListener traverseListener;

    FormText label;
    ChartCanvas canvas;

    QueryResult queryResult;
    QueryContextMenu contextMenu;

    List<? extends Slice> slices;
    Slice current;
    private Menu menu;
    String sliceName = null; // Used for screen reader

    @Override
    public void initWithArgument(Object argument)
    {
        if (argument != null)
        {
            contextMenu = new QueryContextMenu(this, new ContextProvider((String) null)
            {
                @Override
                public IContextObject getContext(Object row)
                {
                    return ((Slice) row).getContext();
                }
            });

            queryResult = (QueryResult) argument;
            IResultPie pie = (IResultPie) (queryResult).getSubject();
            slices = pie.getSlices();

            // Get the system colors
            Color bgColor = canvas.getParent().getBackground();
            Color fgColor = canvas.getParent().getForeground();

            Chart chart = ChartBuilder.create(pie, true, ColorDefinitionImpl.create(bgColor.getRed(), bgColor
                            .getGreen(), bgColor.getBlue()), ColorDefinitionImpl.create(fgColor.getRed(), fgColor
                            .getGreen(), fgColor.getBlue()));

            canvas.setChart(chart);
            canvas.getAccessible().addAccessibleListener(new AccessibleAdapter()
            {

                @Override
                public void getName(AccessibleEvent e)
                {
                    e.result = sliceName;
                }

            });

            canvas.addPaintListener(new PaintListener()
            {
                public void paintControl(PaintEvent e)
                {
                    // Only draw the bounding box if we are in focus
                    if (canvas.isFocusControl())
                    {
                        e.gc.drawFocus(canvas.getBounds().x, canvas.getBounds().y, canvas.getBounds().width, canvas
                                        .getBounds().height);
                    }
                }
            });
            canvas.addFocusListener(new FocusListener()
            {

                public void focusGained(FocusEvent e)
                {
                    canvas.redraw();
                }

                public void focusLost(FocusEvent e)
                {
                    Color tmpCol = canvas.getParent().getBackground();
                    Plot p = canvas.getChart().getPlot();
                    p.setBackground(ColorDefinitionImpl.create(tmpCol.getRed(), tmpCol.getGreen(), tmpCol.getBlue()));
                    canvas.setChart(canvas.getChart());
                    canvas.redraw();
                }

            });

            canvas.redraw();
        }
    }

    public void createPartControl(Composite parent)
    {
        Composite top = new Composite(parent, SWT.NONE);

        // Add a traverse listener or the canvas breaks the
        // ability to tab between the different viewers.
        traverseListener = new TraverseListener()
        {
            public void keyTraversed(TraverseEvent e)
            {
                if (e.detail == SWT.TRAVERSE_TAB_NEXT || e.detail == SWT.TRAVERSE_TAB_PREVIOUS)
                {
                    e.doit = true;
                }
            }
        };

        GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 0).spacing(0, 0).applyTo(top);

        canvas = new ChartCanvas(top, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 300).applyTo(canvas);

        canvas.addTraverseListener(traverseListener);

        label = new FormText(top, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, false).indent(5, 0).hint(SWT.DEFAULT, 45).applyTo(label);

        canvas.renderer.setProperty(IDeviceRenderer.UPDATE_NOTIFIER, new CallBackListener());
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        if (queryResult.getQuery() != null && queryResult.getQuery().getHelpUrl() != null)
            manager.appendToGroup("help", new OpenHelpPageAction(queryResult.getQuery().getHelpUrl())); //$NON-NLS-1$

        super.contributeToToolBar(manager);
    }

    public String getTitle()
    {
        return Messages.PieChartPane_Chart;
    }

    @Override
    public Image getTitleImage()
    {
        return queryResult != null ? MemoryAnalyserPlugin.getDefault().getImage(queryResult.getQuery()) : null;
    }

    class CallBackListener implements ICallBackNotifier
    {

        public void callback(Object event, Object source, CallBackValue value)
        {
            StructureSource structuredSource = (StructureSource) source;
            DataPointHints dph = (DataPointHints) structuredSource.getSource();
            Slice slice = slices.get(dph.getIndex());
            KeyEvent keyEvent = null;

            try
            {
                if (event instanceof KeyEvent)
                {
                    keyEvent = (KeyEvent) event;
                    switch (keyEvent.keyCode)
                    {
                        case SWT.ARROW_UP:
                            if (dph.getIndex() == 0)
                            {
                                dph.setIndex(slices.size() - 1);
                            }
                            else
                            {
                                dph.setIndex(dph.getIndex() - 1);
                            }
                            break;
                        case SWT.ARROW_DOWN:
                            if (dph.getIndex() == (slices.size() - 1))
                            {
                                dph.setIndex(0);
                            }
                            else
                            {
                                dph.setIndex(dph.getIndex() + 1);
                            }
                            break;
                        default:
                    }
                    slice = slices.get(dph.getIndex());
                }
            }
            catch (IndexOutOfBoundsException e)
            {
                dph.setIndex(0);
            }
            if (current != slice)
            {
                label.setText("<form>" + slice.getDescription() + "</form>", true, false); //$NON-NLS-1$//$NON-NLS-2$
                current = slice;
                sliceName = slice.getDescription().toString();
                formatSliceName();
                // Trigger a change text event so that screen readers will read
                // out the new value
                canvas.getAccessible().textSelectionChanged();
                fireSelectionEvent();
            }

            if (slice != null
                            && ("click".equals(value.getIdentifier()) || (keyEvent != null && keyEvent.character == ' '))) //$NON-NLS-1$
            {
                IContextObject ctx = slice.getContext();
                if (ctx != null)
                {
                    if (menu != null && !menu.isDisposed())
                        menu.dispose();

                    PopupMenu popupMenu = new PopupMenu();
                    contextMenu.addContextActions(popupMenu, new StructuredSelection(slice), null);
                    menu = popupMenu.createMenu(getEditorSite().getActionBars().getStatusLineManager(), canvas);
                    menu.setVisible(true);
                }
            }
        }

        /*
         * remove any html tags so they are not read by the screen reader
         */
        private void formatSliceName()
        {
            sliceName = sliceName.replaceAll("<b>", ""); //$NON-NLS-1$ //$NON-NLS-2$
            sliceName = sliceName.replaceAll("<p>", ""); //$NON-NLS-1$ //$NON-NLS-2$
            sliceName = sliceName.replaceAll("</p>", ""); //$NON-NLS-1$ //$NON-NLS-2$
            sliceName = sliceName.replaceAll("</b>", ""); //$NON-NLS-1$ //$NON-NLS-2$
            sliceName = sliceName.replaceAll("<br/>", ""); //$NON-NLS-1$ //$NON-NLS-2$
        }

        public Chart getDesignTimeModel()
        {
            return canvas.getChart();
        }

        public Chart getRunTimeModel()
        {
            return canvas.getRunTimeModel();
        }

        public Object peerInstance()
        {
            return canvas;
        }

        public void regenerateChart()
        {
            canvas.redraw();
        }

        public void repaintChart()
        {
            canvas.redraw();
        }
    }

    // //////////////////////////////////////////////////////////////
    // selection provider
    // //////////////////////////////////////////////////////////////

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        selectionListeners.add(listener);
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        selectionListeners.remove(listener);
    }

    public ISelection getSelection()
    {
        if (current != null)
        {
            IContextObject ctx = current.getContext();
            if (ctx != null)
                return new StructuredSelection(ctx);
        }
        return StructuredSelection.EMPTY;
    }

    public void setSelection(ISelection selection)
    {}

    private void fireSelectionEvent()
    {
        List<ISelectionChangedListener> receivers = new ArrayList<ISelectionChangedListener>(selectionListeners);
        SelectionChangedEvent event = new SelectionChangedEvent(this, getSelection());
        for (ISelectionChangedListener listener : receivers)
            listener.selectionChanged(event);
    }

    @Override
    public void dispose()
    {
        super.dispose();
        if (menu != null && !menu.isDisposed())
            menu.dispose();
    }

}

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
package org.eclipse.mat.ui.internal.chart;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.birt.chart.computation.DataPointHints;
import org.eclipse.birt.chart.device.ICallBackNotifier;
import org.eclipse.birt.chart.device.IDeviceRenderer;
import org.eclipse.birt.chart.event.StructureSource;
import org.eclipse.birt.chart.model.Chart;
import org.eclipse.birt.chart.model.attribute.CallBackValue;
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
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.forms.widgets.FormText;


public class PieChartPane extends AbstractEditorPane implements ISelectionProvider
{
    List<ISelectionChangedListener> listeners = new ArrayList<ISelectionChangedListener>();

    FormText label;
    ChartCanvas canvas;

    QueryResult queryResult;
    QueryContextMenu contextMenu;

    List<? extends Slice> slices;
    Slice current;
    private Menu menu;

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
            canvas.setChart(ChartBuilder.create(pie, true));
            canvas.redraw();
        }
    }

    public void createPartControl(Composite parent)
    {
        Composite top = new Composite(parent, SWT.NONE);
        top.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));

        GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 0).spacing(0, 0).applyTo(top);

        canvas = new ChartCanvas(top, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 300).applyTo(canvas);

        label = new FormText(top, SWT.NONE);
        label.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
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

            if (current != slice)
            {
                label.setText("<form>" + slice.getDescription() + "</form>", true, false);  //$NON-NLS-1$//$NON-NLS-2$
                current = slice;
                fireSelectionEvent();
            }

            if (slice != null && "click".equals(value.getIdentifier())) //$NON-NLS-1$
            {
                IContextObject ctx = slice.getContext();
                if (ctx != null)
                {
                    if (menu != null && !menu.isDisposed())
                        menu.dispose();

                    PopupMenu popupMenu = new PopupMenu();
                    contextMenu.addContextActions(popupMenu, new StructuredSelection(slice));
                    menu = popupMenu.createMenu(getEditorSite().getActionBars().getStatusLineManager(), canvas);
                    menu.setVisible(true);
                }
            }
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
        listeners.add(listener);
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.remove(listener);
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
        List<ISelectionChangedListener> receivers = new ArrayList<ISelectionChangedListener>(listeners);
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

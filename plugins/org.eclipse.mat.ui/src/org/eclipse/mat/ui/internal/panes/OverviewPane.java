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
package org.eclipse.mat.ui.internal.panes;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.impl.query.CommandLine;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.impl.test.SpecFactory;
import org.eclipse.mat.inspections.query.DominatorQuery;
import org.eclipse.mat.inspections.query.DuplicatedClassesQuery;
import org.eclipse.mat.inspections.query.HistogramQuery;
import org.eclipse.mat.inspections.query.LeakHunterQuery;
import org.eclipse.mat.inspections.query.TopConsumers2Query;
import org.eclipse.mat.inspections.query.sidecar.AddonResolverRegistry;
import org.eclipse.mat.inspections.tests.RunExpertSystemTest;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.HeapEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ImageHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.Units;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.events.IHyperlinkListener;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;
import org.eclipse.ui.forms.widgets.TableWrapLayout;

public class OverviewPane extends HeapEditorPane implements IHyperlinkListener, ISelectionProvider
{
    private List<ISelectionChangedListener> listeners = new ArrayList<ISelectionChangedListener>();
    private ISelectionProvider delegate;

    private FormToolkit toolkit;
    private AbstractEditorPane pane;
    private Form form;

    public void createPartControl(Composite parent)
    {
        toolkit = new FormToolkit(parent.getDisplay());
        form = toolkit.createForm(parent);

        GridLayout layout = new GridLayout(2, true);
        layout.verticalSpacing = 20;
        form.getBody().setLayout(layout);

        Section section = createDetailsSection();
        GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(section);

        section = createBiggestObjectsSection();
        GridDataFactory.fillDefaults().grab(true, false).span(2, 1).hint(SWT.DEFAULT, 0).applyTo(section);

        for (AddonResolverRegistry.AddonRecord addonRecord : AddonResolverRegistry.instance().delegates())
        {
            try
            {
                if (getSnapshotInput().getSnapshot().getSnapshotAddons(addonRecord.getQueryInterface()) != null)
                {
                    section = createSidecarSection();
                    GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(section);
                    break;
                }
            }
            catch (InvalidRegistryObjectException e)
            {
                logMessage(e);
            }
            catch (ClassNotFoundException e)
            {
                logMessage(e);
            }
            catch (SnapshotException e)
            {
                logMessage(e);
            }
        }

        section = createActionSection();
        GridDataFactory.fillDefaults().grab(true, false).applyTo(section);

        section = createReportsSection();
        GridDataFactory.fillDefaults().grab(true, false).applyTo(section);

        form.getBody().layout();
    }

    private void logMessage(Exception e)
    {
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, e.getMessage());
    }

    // //////////////////////////////////////////////////////////////
    // details
    // //////////////////////////////////////////////////////////////

    private Section createSidecarSection()
    {
        Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        section.setText("Additional Information");
        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());
        FormText text = toolkit.createFormText(sectionClient, true);
        text.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
        StringBuilder buf = new StringBuilder();
        buf.append("<form>");

        for (AddonResolverRegistry.AddonRecord addonRecord : AddonResolverRegistry.instance().delegates())
        {
            try
            {
                if (getSnapshotInput().getSnapshot().getSnapshotAddons(addonRecord.getQueryInterface()) == null)
                    continue;

                final QueryDescriptor descriptor = QueryRegistry.instance().getQuery(addonRecord.getQueryIdentifier());
                if (descriptor != null)
                {
                    Class<? extends IQuery> queryClass = addonRecord.getQuery().getClass();
                    addButton(buf, text, queryClass, null, addonRecord.getName(), queryClass.getAnnotation(Help.class)
                                    .value());
                }
            }
            catch (InvalidRegistryObjectException e)
            {
                logMessage(e);
            }
            catch (ClassNotFoundException e)
            {
                logMessage(e);
            }
            catch (SnapshotException e)
            {
                logMessage(e);
            }
            catch (InstantiationException e)
            {
                logMessage(e);
            }
            catch (IllegalAccessException e)
            {
                logMessage(e);
            }
        }

        buf.append("</form>");
        text.setText(buf.toString(), true, false);
        text.addHyperlinkListener(this);

        section.setClient(sectionClient);
        return section;
    }

    private Section createDetailsSection()
    {
        Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        section.setText("Details");
        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());
        FormText text = toolkit.createFormText(sectionClient, true);
        text.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));

        SnapshotInfo info = getSnapshotInput().getSnapshot().getSnapshotInfo();

        StringBuilder buf = new StringBuilder();
        buf.append("<form>");

        buf.append("<p>Size: ");
        long heapSize = info.getUsedHeapSize();
        String size = Units.Storage.of(heapSize).format(heapSize);
        buf.append("<b>" + size + "</b>");

        buf.append("  Classes: ");
        buf.append("<b>" + formatNumber(info.getNumberOfClasses()) + "</b>");

        buf.append("  Objects: ");
        buf.append("<b>" + formatNumber(info.getNumberOfObjects()) + "</b>");

        buf.append("  Class Loader: ");
        buf.append("<b>" + formatNumber(info.getNumberOfClassLoaders()) + "</b></p>");

        buf.append("</form>");
        text.setText(buf.toString(), true, false);
        text.addHyperlinkListener(this);

        section.setClient(sectionClient);
        return section;
    }

    private String formatNumber(int number)
    {
        return Units.Plain.of(number).format(number);
    }

    // //////////////////////////////////////////////////////////////
    // actions
    // //////////////////////////////////////////////////////////////

    private Section createActionSection()
    {
        Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        section.setText("Actions");

        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());

        FormText text = toolkit.createFormText(sectionClient, true);
        text.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
        StringBuilder buf = new StringBuilder();
        buf.append("<form>");

        addButton(buf, text, HistogramQuery.class, null, "Histogram", "Lists number of instances per class");
        addButton(buf, text, DominatorQuery.class, null, "Dominator Tree", "Lists biggest objects");
        addButton(buf, text, TopConsumers2Query.class, null, "Top Consumers",
                        "Prints most expensive objects and groups them by class and by package");
        addButton(buf, text, LeakHunterQuery.class, null, "Leak Hunter", "Automatically checks for leak suspects");
        addButton(buf, text, DuplicatedClassesQuery.class, null, "Duplicate Classes",
                        "Lists classes loaded by more than one class loader");

        buf.append("</form>");
        text.setText(buf.toString(), true, false);
        text.addHyperlinkListener(this);

        section.setClient(sectionClient);
        return section;
    }

    private Section createReportsSection()
    {
        Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        section.setText("Reports");

        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());

        FormText text = toolkit.createFormText(sectionClient, true);
        StringBuilder buf = new StringBuilder();
        buf.append("<form>");

        for (SpecFactory.Report report : SpecFactory.instance().delegates())
        {
            addButton(buf, text, RunExpertSystemTest.class, "default_report " + report.getExtensionIdentifier(), report
                            .getName(), report.getDescription());
        }

        buf.append("</form>");
        text.setText(buf.toString(), true, false);
        text.addHyperlinkListener(this);

        section.setClient(sectionClient);
        return section;
    }

    private void addButton(StringBuilder buf, FormText formText, Class<? extends IQuery> query, String command,
                    String title, String help)
    {
        final QueryDescriptor descriptor = QueryRegistry.instance().getQuery(query);
        if (descriptor == null)
            return;

        if (command == null)
            command = descriptor.getIdentifier();

        Image image = ImageHelper.getImage(descriptor);
        if (image != null)
        {
            buf.append("<li style=\"image\" value=\"").append(descriptor.getIdentifier()).append("\">");
            formText.setImage(descriptor.getIdentifier(), image);
        }
        else
        {
            buf.append("<li style=\"text\" value=\"\">");
        }

        buf.append("<a href=\"").append(command).append("\">").append(title).append("</a>");
        if (help != null)
            buf.append(": ").append(help);
        buf.append("</li>");
    }

    // //////////////////////////////////////////////////////////////
    // biggest objects
    // //////////////////////////////////////////////////////////////

    private Section createBiggestObjectsSection()
    {
        final Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED
                        | Section.TWISTIE);
        section.setText("Biggest Objects by Retained Size");

        final Composite sectionClient = toolkit.createComposite(section);

        if (Platform.getBundle("org.eclipse.mat.chart.ui") == null)
        {
            sectionClient.setLayout(new TableWrapLayout());

            FormText text = toolkit.createFormText(sectionClient, true);
            StringBuilder buf = new StringBuilder(256);

            QueryDescriptor h = QueryRegistry.instance().getQuery(DominatorQuery.class);
            QueryDescriptor tc = QueryRegistry.instance().getQuery(TopConsumers2Query.class);

            buf.append(
                            "<form><li style=\"text\" value=\"\">BIRT Chart Engine (>2.2.2) not available. No pie today. "
                                            + "Check-out the <a href=\"").append(h.getIdentifier()).append(
                            "\">Dominator Tree</a> or <a href=\"").append(tc.getIdentifier()).append(
                            "\">Top Consumers</a>.</li></form>");

            text.setText(buf.toString(), true, false);
            text.addHyperlinkListener(this);
        }
        else
        {
            FillLayout layout = new FillLayout();
            sectionClient.setLayout(layout);

            final ISnapshot snapshot = getSnapshotInput().getSnapshot();

            new AbstractPaneJob("Extracting Biggest Objects", this)
            {
                @Override
                protected IStatus doRun(IProgressMonitor monitor)
                {
                    try
                    {
                        final IResult result = CommandLine.execute(snapshot, "pie_biggest_objects",
                                        new ProgressMonitorWrapper(monitor));

                        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                        {
                            public void run()
                            {
                                try
                                {
                                    pane = QueryExecution.createPane(result, null);

                                    pane.init(getEditorSite(), getEditorInput());
                                    pane.createPartControl(sectionClient);
                                    pane.initWithArgument(new QueryResult(null, "pie_biggest_objects", result));
                                    form.getBody().layout();

                                    if (pane instanceof ISelectionProvider)
                                    {
                                        delegate = (ISelectionProvider) pane;

                                        for (ISelectionChangedListener l : listeners)
                                            delegate.addSelectionChangedListener(l);
                                    }
                                }
                                catch (PartInitException e)
                                {
                                    ErrorHelper.logThrowableAndShowMessage(e);
                                }
                            }
                        });

                        return Status.OK_STATUS;
                    }
                    catch (SnapshotException e)
                    {
                        return ErrorHelper.createErrorStatus(e);
                    }

                }
            }.schedule();
        }

        section.setClient(sectionClient);
        return section;
    }

    // //////////////////////////////////////////////////////////////
    // hyper-link listener
    // //////////////////////////////////////////////////////////////

    public void linkActivated(HyperlinkEvent e)
    {
        String command = String.valueOf(e.getHref());
        runCommand(command);
    }

    private void runCommand(String command)
    {
        try
        {
            HeapEditor heapEditor = (HeapEditor) ((MultiPaneEditorSite) getSite()).getMultiPageEditor();
            QueryExecution.executeCommandLine(heapEditor, this.getPaneState(), command);
        }
        catch (SnapshotException exp)
        {
            ErrorHelper.logThrowableAndShowMessage(exp);
        }
    }

    public void linkEntered(HyperlinkEvent e)
    {}

    public void linkExited(HyperlinkEvent e)
    {}

    // //////////////////////////////////////////////////////////////
    // misc
    // //////////////////////////////////////////////////////////////

    public String getTitle()
    {
        return "Overview";
    }

    @Override
    public Image getTitleImage()
    {
        return MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.INFO);
    }

    @Override
    public void setFocus()
    {
        form.setFocus();
    }

    // //////////////////////////////////////////////////////////////
    // selection provider
    // //////////////////////////////////////////////////////////////

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.add(listener);
        if (delegate != null)
            delegate.addSelectionChangedListener(listener);
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        listeners.remove(listener);
        if (delegate != null)
            delegate.removeSelectionChangedListener(listener);
    }

    public ISelection getSelection()
    {
        if (delegate != null)
            return delegate.getSelection();
        else
            return StructuredSelection.EMPTY;
    }

    public void setSelection(ISelection selection)
    {}

    @Override
    public void dispose()
    {
        super.dispose();
        if (pane != null)
        {
            pane.dispose();
            pane = null;
        }
    }
}

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
package org.eclipse.mat.ui.snapshot.panes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.report.SpecFactory;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.EditorPaneRegistry;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.snapshot.editor.HeapEditor;
import org.eclipse.mat.ui.snapshot.editor.HeapEditorPane;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.Units;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.cheatsheets.OpenCheatSheetAction;
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

        GridLayout layout = new GridLayout(3, true);
        layout.verticalSpacing = 20;
        form.getBody().setLayout(layout);

        Section section = createDetailsSection();
        GridDataFactory.fillDefaults().grab(true, false).span(3, 1).applyTo(section);

        section = createBiggestObjectsSection();
        GridDataFactory.fillDefaults().grab(true, false).span(3, 1).hint(SWT.DEFAULT, 0).applyTo(section);

        section = createSidecarSection();
        if (section != null)
            GridDataFactory.fillDefaults().grab(true, false).span(3, 1).applyTo(section);

        section = createActionSection();
        GridDataFactory.fillDefaults().grab(true, false).applyTo(section);

        section = createReportsSection();
        GridDataFactory.fillDefaults().grab(true, false).applyTo(section);

        section = createStepByStepSection();
        GridDataFactory.fillDefaults().grab(true, false).applyTo(section);

        form.getBody().layout();
    }

    // //////////////////////////////////////////////////////////////
    // details
    // //////////////////////////////////////////////////////////////

    private Section createSidecarSection()
    {
        Section section = null;
        FormText text = null;

        List<QueryDescriptor> queries = QueryRegistry.instance().getQueries(Pattern.compile("supplement_.*")); //$NON-NLS-1$
        if (queries.isEmpty())
            return null;

        Collections.sort(queries, new Comparator<QueryDescriptor>()
        {
            public int compare(QueryDescriptor o1, QueryDescriptor o2)
            {
                return o1.getName().compareTo(o2.getName());
            }
        });

        StringBuilder buf = new StringBuilder();
        buf.append("<form>");//$NON-NLS-1$
        for (QueryDescriptor query : queries)
        {
            if (query.accept(getQueryContext()))
            {
                if (section == null)
                {
                    section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED
                                    | Section.TWISTIE);
                    section.setText(Messages.OverviewPane_AdditionalInfo);
                    Composite sectionClient = toolkit.createComposite(section);
                    sectionClient.setLayout(new TableWrapLayout());
                    text = toolkit.createFormText(sectionClient, true);
                    text.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
                    text.addHyperlinkListener(this);

                    section.setClient(sectionClient);
                }

                addButton(buf, text, query.getIdentifier(), null, query.getName(), query.getHelp());
            }
        }
        buf.append("</form>");//$NON-NLS-1$

        if (text != null)
            text.setText(buf.toString(), true, false);

        return section;
    }

    private Section createDetailsSection()
    {
        Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        section.setText(Messages.OverviewPane_Details);
        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());
        FormText text = toolkit.createFormText(sectionClient, true);
        text.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));

        SnapshotInfo info = getSnapshotInput().getSnapshot().getSnapshotInfo();

        StringBuilder buf = new StringBuilder();
        buf.append("<form>");//$NON-NLS-1$

        buf.append("<p>" + Messages.OverviewPane_Size);//$NON-NLS-1$
        long heapSize = info.getUsedHeapSize();
        String size = Units.Storage.of(heapSize).format(heapSize);
        buf.append("<b>" + size + "</b>");//$NON-NLS-1$//$NON-NLS-2$

        buf.append("  " + Messages.OverviewPane_Classes); //$NON-NLS-1$
        buf.append("<b>" + formatNumber(info.getNumberOfClasses()) + "</b>");//$NON-NLS-1$//$NON-NLS-2$

        buf.append("  " + Messages.OverviewPane_Objects); //$NON-NLS-1$
        buf.append("<b>" + formatNumber(info.getNumberOfObjects()) + "</b>");//$NON-NLS-1$//$NON-NLS-2$

        buf.append("  " + Messages.OverviewPane_ClassLoader); //$NON-NLS-1$
        buf.append("<b>" + formatNumber(info.getNumberOfClassLoaders()) + "</b>");//$NON-NLS-1$//$NON-NLS-2$

        QueryDescriptor descriptor = QueryRegistry.instance().getQuery("unreachable_objects"); //$NON-NLS-1$
        if (descriptor != null && descriptor.accept(getQueryContext()))
        {
            buf.append("  <a href=\"").append(descriptor.getIdentifier()) // //$NON-NLS-1$
                            .append("\">").append(descriptor.getName()).append("</a>");//$NON-NLS-1$//$NON-NLS-2$
        }

        buf.append("</p></form>");//$NON-NLS-1$
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
        section.setText(Messages.OverviewPane_Actions);

        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());

        FormText text = toolkit.createFormText(sectionClient, true);
        text.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
        StringBuilder buf = new StringBuilder();
        buf.append("<form>");//$NON-NLS-1$

        addButton(buf, text, "histogram", null, "Histogram", Messages.OverviewPane_HistogramInfo); //$NON-NLS-1$ //$NON-NLS-2$
        addButton(buf, text, "dominator_tree", null, "Dominator Tree", //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.OverviewPane_DominatorTreeInfo);
        addButton(buf, text, "top_consumers_html", null, "Top Consumers", //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.OverviewPane_TopConsumersInfo);
        addButton(buf, text, "duplicate_classes", null, "Duplicate Classes", //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.OverviewPane_DuplicateClassesInfo);

        buf.append("</form>");//$NON-NLS-1$
        text.setText(buf.toString(), true, false);
        text.addHyperlinkListener(this);

        section.setClient(sectionClient);
        return section;
    }

    private Section createReportsSection()
    {
        Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        section.setText(Messages.OverviewPane_Reports);

        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());

        FormText text = toolkit.createFormText(sectionClient, true);
        StringBuilder buf = new StringBuilder();
        buf.append("<form>");//$NON-NLS-1$

        addReportsByPattern(text, buf, Pattern.compile(".*:suspects"));//$NON-NLS-1$
        addReportsByPattern(text, buf, Pattern.compile(".*:top_components"));//$NON-NLS-1$

        buf.append("</form>");//$NON-NLS-1$
        text.setText(buf.toString(), true, false);
        text.addHyperlinkListener(this);

        section.setClient(sectionClient);
        return section;
    }

    private void addReportsByPattern(FormText text, StringBuilder buf, Pattern pattern)
    {
        for (SpecFactory.Report report : SpecFactory.instance().delegates())
        {
            if (pattern.matcher(report.getExtensionIdentifier()).matches())
                addButton(buf, text, "create_report", "default_report " + report.getExtensionIdentifier(), report//$NON-NLS-1$//$NON-NLS-2$
                                .getName(), report.getDescription());
        }
    }

    private Section createStepByStepSection()
    {
        Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        section.setText(Messages.OverviewPane_StepByStep);

        Composite sectionClient = toolkit.createComposite(section);
        sectionClient.setLayout(new TableWrapLayout());

        FormText text = toolkit.createFormText(sectionClient, true);
        StringBuilder buf = new StringBuilder();
        buf.append("<form>");//$NON-NLS-1$

        addCheatSheetLink(buf, "org.eclipse.mat.tutorials.component_report", "Component Report",//$NON-NLS-1$//$NON-NLS-2$
                        Messages.OverviewPane_ComponentReportInfo);

        buf.append("</form>");//$NON-NLS-1$
        text.setText(buf.toString(), true, false);
        text.addHyperlinkListener(new IHyperlinkListener()
        {
            public void linkActivated(HyperlinkEvent e)
            {
                new OpenCheatSheetAction(String.valueOf(e.getHref())).run();
            }

            public void linkEntered(HyperlinkEvent e)
            {}

            public void linkExited(HyperlinkEvent e)
            {}
        });

        section.setClient(sectionClient);
        return section;
    }

    private void addCheatSheetLink(StringBuilder buf, String cheatSheetId, String title, String help)
    {
        buf.append("<li style=\"text\" value=\"\">");//$NON-NLS-1$
        buf.append("<a href=\"").append(cheatSheetId).append("\">").append(title).append("</a>");//$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
        if (help != null)
            buf.append(": ").append(help);//$NON-NLS-1$
        buf.append("</li>");//$NON-NLS-1$
    }

    private void addButton(StringBuilder buf, FormText formText, String commandId, String command, String title,
                    String help)
    {
        QueryDescriptor descriptor = QueryRegistry.instance().getQuery(commandId);
        if (descriptor == null)
            return;

        if (command == null)
            command = descriptor.getIdentifier();

        Image image = MemoryAnalyserPlugin.getDefault().getImage(descriptor);
        if (image != null)
        {
            buf.append("<li style=\"image\" value=\"").append(descriptor.getIdentifier()).append("\">");//$NON-NLS-1$//$NON-NLS-2$
            formText.setImage(descriptor.getIdentifier(), image);
        }
        else
        {
            buf.append("<li style=\"text\" value=\"\">");//$NON-NLS-1$
        }

        buf.append("<a href=\"").append(command).append("\">").append(title).append("</a>");//$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
        if (help != null)
            buf.append(": ").append(help);//$NON-NLS-1$
        buf.append("</li>");//$NON-NLS-1$
    }

    // //////////////////////////////////////////////////////////////
    // biggest objects
    // //////////////////////////////////////////////////////////////

    private Section createBiggestObjectsSection()
    {
        final Section section = toolkit.createSection(form.getBody(), Section.TITLE_BAR | Section.EXPANDED
                        | Section.TWISTIE);
        section.setText(Messages.OverviewPane_BiggestObjectsByRetainedSIze);

        final Composite sectionClient = toolkit.createComposite(section);

        if (Platform.getBundle("org.eclipse.mat.chart.ui") == null)//$NON-NLS-1$
        {
            sectionClient.setLayout(new TableWrapLayout());

            FormText text = toolkit.createFormText(sectionClient, true);
            StringBuilder buf = new StringBuilder(256);

            buf.append("<form><li style=\"text\" value=\"\">" + Messages.OverviewPane_NoPie + "<a href=\""); //$NON-NLS-1$ //$NON-NLS-2$
            buf.append("dominator_tree").append("\">Dominator Tree</a> or <a href=\"").append("top_consumers_html") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            .append("\">Top Consumers</a>.</li></form>"); //$NON-NLS-1$

            text.setText(buf.toString(), true, false);
            text.addHyperlinkListener(this);
        }
        else
        {
            FillLayout layout = new FillLayout();
            sectionClient.setLayout(layout);

            final ISnapshot snapshot = getSnapshotInput().getSnapshot();

            new AbstractPaneJob(Messages.OverviewPane_ExtractingBigObjects, this)
            {
                @Override
                protected IStatus doRun(IProgressMonitor monitor)
                {
                    try
                    {
                        SnapshotQueryContext ctx = new SnapshotQueryContext(snapshot);
                        final IResult result = CommandLine.execute(ctx, "pie_biggest_objects",//$NON-NLS-1$
                                        new ProgressMonitorWrapper(monitor));

                        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                        {
                            public void run()
                            {
                                try
                                {
                                    pane = EditorPaneRegistry.instance().createNewPane(result, null);

                                    pane.init(getEditorSite(), getEditorInput());
                                    pane.createPartControl(sectionClient);
                                    pane.initWithArgument(new QueryResult(null, "pie_biggest_objects", result));//$NON-NLS-1$
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
        return Messages.OverviewPane_Overview;
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

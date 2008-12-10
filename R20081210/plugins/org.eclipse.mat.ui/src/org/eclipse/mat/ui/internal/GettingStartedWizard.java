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
package org.eclipse.mat.ui.internal;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.runtime.Preferences;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.actions.ImportReportAction;
import org.eclipse.mat.ui.internal.actions.ExecuteQueryAction;
import org.eclipse.mat.ui.snapshot.editor.HeapEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

public class GettingStartedWizard extends Wizard
{
    public static final String HIDE_WIZARD_KEY = "hideGettingStartedWizard";

    /* package */Action action;
    /* package */HeapEditor editor;

    /* package */ChoicePage choicePage;
    /* package */PackagePage packagePage;
    /* package */OpenReportsPage openReportsPage;

    public GettingStartedWizard(HeapEditor editor)
    {
        this.editor = editor;

        setWindowTitle("Getting Started Wizard");
    }

    @Override
    public void addPages()
    {
        choicePage = new ChoicePage();
        addPage(choicePage);
        packagePage = new PackagePage();
        addPage(packagePage);
        openReportsPage = new OpenReportsPage();
        addPage(openReportsPage);
    }

    @Override
    public boolean canFinish()
    {
        return action != null;
    }

    @Override
    public boolean performFinish()
    {
        Preferences prefs = MemoryAnalyserPlugin.getDefault().getPluginPreferences();
        prefs.setValue(HIDE_WIZARD_KEY, !choicePage.askAgain.getSelection());

        if (action != null)
            action.run();

        return true;
    }

    @Override
    public boolean performCancel()
    {
        Preferences prefs = MemoryAnalyserPlugin.getDefault().getPluginPreferences();
        prefs.setValue(HIDE_WIZARD_KEY, !choicePage.askAgain.getSelection());

        return true;
    }

    // //////////////////////////////////////////////////////////////
    // internal classes
    // //////////////////////////////////////////////////////////////

    private static class ChoicePage extends WizardPage implements Listener
    {
        private Button componentReport;
        private Button leakReport;
        private Button openReports;
        private Button askAgain;

        public ChoicePage()
        {
            super("");

            setTitle("Getting Started");
            setDescription("Choose one of the common reports below. Press Escape to close this dialog.");
        }

        public void createControl(Composite parent)
        {
            Composite composite = new Composite(parent, SWT.NONE);
            GridLayoutFactory.fillDefaults().applyTo(composite);

            Composite choices = new Composite(composite, SWT.NONE);
            GridDataFactory.fillDefaults().grab(false, true).applyTo(choices);

            GridLayoutFactory.fillDefaults().applyTo(choices);

            leakReport = new Button(choices, SWT.RADIO);
            leakReport.setText("Leak Suspects Report");
            leakReport.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DIALOG_FONT));
            leakReport.addListener(SWT.Selection, this);

            Label description = new Label(choices, SWT.WRAP);
            description.setText("Automatically check the heap dump for leak suspects. "
                            + "Report what objects are kept alive and why they are not garbage collected.");
            GridDataFactory.fillDefaults().indent(18, 0).hint(description.computeSize(300, SWT.DEFAULT)).applyTo(
                            description);

            componentReport = new Button(choices, SWT.RADIO);
            componentReport.setText("Component Report");
            componentReport.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DIALOG_FONT));
            componentReport.addListener(SWT.Selection, this);
            description = new Label(choices, SWT.WRAP);
            description.setText("Analyze a set of objects for suspected memory issues: "
                            + "duplicate strings, empty collections, finalizer, weak references, etc.");
            GridDataFactory.fillDefaults().indent(18, 0).hint(description.computeSize(300, SWT.DEFAULT)).applyTo(
                            description);

            openReports = new Button(choices, SWT.RADIO);
            openReports.setText("Re-open previously run reports");
            openReports.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DIALOG_FONT));
            openReports.addListener(SWT.Selection, this);
            description = new Label(choices, SWT.WRAP);
            description.setText("Existing reports are stored in ZIP files next to the heap dump.");
            GridDataFactory.fillDefaults().indent(18, 0).hint(description.computeSize(300, SWT.DEFAULT)).applyTo(
                            description);

            askAgain = new Button(composite, SWT.CHECK);
            askAgain.setSelection(true);
            askAgain.setText("Show this dialog when opening an heap dump.");

            Preferences prefs = MemoryAnalyserPlugin.getDefault().getPluginPreferences();
            askAgain.setSelection(!prefs.getBoolean(HIDE_WIZARD_KEY));

            setControl(composite);
        }

        @Override
        public boolean canFlipToNextPage()
        {
            return componentReport.getSelection() || openReports.getSelection();
        }

        @Override
        public IWizardPage getNextPage()
        {
            if (componentReport.getSelection())
            {
                return getWizard().packagePage;
            }
            else if (openReports.getSelection())
            {
                getWizard().openReportsPage.loadReportList();
                return getWizard().openReportsPage;
            }
            else
            {
                return null;
            }
        }

        public void handleEvent(Event event)
        {
            if (event.widget == componentReport)
            {
                getWizard().action = null;
            }
            else if (event.widget == openReports)
            {
                getWizard().action = null;
            }
            else if (event.widget == leakReport)
            {
                getWizard().action = new ExecuteQueryAction(getWizard().editor,
                                "default_report org.eclipse.mat.api:suspects");
            }

            getWizard().getContainer().updateButtons();
        }

        @Override
        public void setVisible(boolean visible)
        {
            if (visible)
                getWizard().action = null;
            super.setVisible(visible);
        }

        @Override
        public GettingStartedWizard getWizard()
        {
            return (GettingStartedWizard) super.getWizard();
        }

    }

    private static class PackagePage extends WizardPage implements ModifyListener
    {
        public PackagePage()
        {
            super("");

            setTitle("Select Classes");
            setDescription("Specify a regular expression which matches the fully qualified class names of your component, "
                            + "for example org\\.eclipse\\.mat\\.*.");
        }

        public void createControl(Composite parent)
        {
            Composite composite = new Composite(parent, SWT.NONE);
            GridLayoutFactory.fillDefaults().applyTo(composite);

            Label label = new Label(composite, SWT.NONE);
            label.setText("Package (regular expression):");

            Text regex = new Text(composite, SWT.BORDER);
            GridDataFactory.fillDefaults().grab(true, false).applyTo(regex);

            regex.addModifyListener(this);

            setControl(composite);
        }

        public void modifyText(ModifyEvent event)
        {
            try
            {
                String text = ((Text) event.widget).getText();
                if (!"".equals(text))
                {
                    Pattern.compile(text);

                    getWizard().action = new ExecuteQueryAction(getWizard().editor, "component_report " + text);
                }
                if (getErrorMessage() != null)
                    setErrorMessage(null);
            }
            catch (PatternSyntaxException e)
            {
                setErrorMessage(e.getMessage());
                getWizard().action = null;
            }

            getWizard().getContainer().updateButtons();
        }

        @Override
        public boolean canFlipToNextPage()
        {
            return false;
        }

        @Override
        public GettingStartedWizard getWizard()
        {
            return (GettingStartedWizard) super.getWizard();
        }

    }

    private static class OpenReportsPage extends WizardPage
    {
        TableViewer viewer;

        public OpenReportsPage()
        {
            super("");

            setTitle("Re-Open Report");
            setDescription("Re-open a report that has been created previously. "
                            + "The reposts are stored as ZIP files next to the heap dump file.");
        }

        public void createControl(Composite parent)
        {
            Composite composite = new Composite(parent, SWT.NONE);
            TableColumnLayout layout = new TableColumnLayout();
            composite.setLayout(layout);

            viewer = new TableViewer(composite, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);

            TableColumn column = new TableColumn(viewer.getTable(), SWT.NONE);
            column.setText("Report");
            layout.setColumnData(column, new ColumnWeightData(100, true));

            viewer.setLabelProvider(new LabelProvider()
            {
                @Override
                public String getText(Object element)
                {
                    return ((File) element).getName();
                }
            });

            viewer.setContentProvider(new IStructuredContentProvider()
            {
                File[] fileList;

                public Object[] getElements(Object inputElement)
                {
                    return fileList;
                }

                public void dispose()
                {}

                public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
                {
                    if (newInput instanceof ISnapshot)
                    {
                        try
                        {
                            ISnapshot snapshot = (ISnapshot) newInput;

                            String prefix = snapshot.getSnapshotInfo().getPath();

                            int p = prefix.lastIndexOf(File.separatorChar);
                            if (p >= 0)
                                prefix = prefix.substring(p + 1);
                            
                            p = prefix.lastIndexOf('.');
                            if (p >= 0)
                                prefix = prefix.substring(0, p);

                            final String fragment = prefix;
                            final Pattern regex = Pattern.compile(".*\\.zip$");

                            fileList = new File(snapshot.getSnapshotInfo().getPath()).getParentFile().listFiles(
                                            new FilenameFilter()
                                            {
                                                public boolean accept(File dir, String name)
                                                {
                                                    return name.startsWith(fragment) && regex.matcher(name).matches();
                                                }
                                            });
                        }
                        catch (PatternSyntaxException ignore)
                        {}
                    }
                }
            });

            viewer.addSelectionChangedListener(new ISelectionChangedListener()
            {
                public void selectionChanged(SelectionChangedEvent event)
                {
                    getWizard().action = new ImportReportAction(getWizard().editor,
                                    (File) ((IStructuredSelection) event.getSelection()).getFirstElement());
                    getWizard().getContainer().updateButtons();
                }
            });

            setControl(composite);
        }

        public void loadReportList()
        {
            viewer.setInput(getWizard().editor.getSnapshotInput().getSnapshot());
        }

        @Override
        public boolean canFlipToNextPage()
        {
            return false;
        }

        @Override
        public GettingStartedWizard getWizard()
        {
            return (GettingStartedWizard) super.getWizard();
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2022, 2023 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.diagnostics;

import java.lang.management.ManagementFactory;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.diagnostics.actions.HotSpotJVMHeapDump;
import org.eclipse.mat.ui.internal.diagnostics.actions.J9JVMHeapDump;
import org.eclipse.mat.ui.internal.diagnostics.actions.J9JVMSystemDump;
import org.eclipse.mat.ui.internal.diagnostics.actions.J9JVMThreadDump;
import org.eclipse.mat.ui.internal.diagnostics.actions.ThreadMXBeanDump;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;

/**
 * Select which diagnostic to perform
 */
public class DiagnosticsSelectionWizardPage extends WizardPage
{
    private Table actionsTable;

    public DiagnosticsSelectionWizardPage()
    {
        super("diagnostics_selection"); //$NON-NLS-1$
    }

    public void createControl(Composite parent)
    {
        setTitle(Messages.DiagnosticsSelectionWizardPage_DialogName);
        setDescription(Messages.DiagnosticsSelectionWizardPage_DialogDescription);

        final Composite top = new Composite(parent, SWT.NONE);
        top.setLayout(new GridLayout(1, false));

        Label l1 = new Label(top, SWT.NONE);
        l1.setText(Messages.DiagnosticsSelectionWizardPage_ChooseProcess);
        GridDataFactory.swtDefaults().span(1, 1).applyTo(l1);

        Composite tableComposite = new Composite(top, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).indent(0, 0).applyTo(tableComposite);

        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        tableComposite.setLayout(tableColumnLayout);

        actionsTable = new Table(tableComposite, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.FULL_SELECTION);
        actionsTable.setHeaderVisible(true);
        actionsTable.setLinesVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).span(1, 1).minSize(0, 100).applyTo(actionsTable);
        actionsTable.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                selectionChanged();
            }

            public void widgetDefaultSelected(SelectionEvent e)
            {
                getWizard().performFinish();
            }
        });

        TableColumn column = new TableColumn(actionsTable, SWT.LEFT);
        column.setText(Messages.DiagnosticsSelectionWizardPage_ColumnDescription);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(100, 250));
        tableComposite.layout();
        tableComposite.pack();

        actionsTable.setFocus();
        setControl(top);

        loadTableItems();
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "org.eclipse.mat.ui.help.diagnostics"); //$NON-NLS-1$
    }

    private void loadTableItems()
    {
        addTableItem(Messages.DiagnosticsAction_ThreadMXBeanDump_Description, new ThreadMXBeanDump());

        boolean j9vm = true;
        try
        {
            // Check if this is a J9 JVM
            Class.forName("com.ibm.jvm.Dump").getMethod("triggerDump", new Class<?>[] { String.class }); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (ClassNotFoundException | NoSuchMethodException e)
        {
            // This is okay for a non-J9 JVM
            j9vm = false;
        }
        catch (Exception e)
        {
            // This is okay for a non-J9 JVM
            j9vm = false;
        }

        if (j9vm)
        {
            // If the class and method were found, then assume this is a J9 JVM
            // and add some actions
            addTableItem(Messages.DiagnosticsAction_J9JVMThreadDump_Description, new J9JVMThreadDump());
            addTableItem(Messages.DiagnosticsAction_J9JVMHeapDump_Description, new J9JVMHeapDump());
            addTableItem(Messages.DiagnosticsAction_J9JVMSystemDump_Description, new J9JVMSystemDump());
        }

        boolean hotspotvm = true;
        try
        {
            // Check if this is a HotSpot JVM
            Class<?> dumpcls = Class.forName("com.sun.management.HotSpotDiagnosticMXBean"); //$NON-NLS-1$
            ManagementFactory.newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(),
                            "com.sun.management:type=HotSpotDiagnostic", dumpcls); //$NON-NLS-1$
        }
        catch (ClassNotFoundException | IllegalArgumentException e)
        {
            // This is okay for a non-HotSpot JVM
            hotspotvm = false;
        }
        catch (Exception e)
        {
            // This is okay for a non-HotSpot JVM
            hotspotvm = false;
        }

        if (hotspotvm)
        {
            // If the class is found, then assume this is a HotSpot JVM
            // and add some actions
            addTableItem(Messages.DiagnosticsAction_HotSpotJVMHeapDump_Description, new HotSpotJVMHeapDump());
        }

        // For diagnosing issues with wizard cancellation:
        /*-
        addTableItem(Messages.DiagnosticsAction_Sleep_Description,
                        new org.eclipse.mat.ui.internal.diagnostics.actions.Sleep());
         */
    }

    private void addTableItem(String description, DiagnosticsAction action)
    {
        TableItem item = new TableItem(actionsTable, SWT.NONE);
        item.setText(0, description);
        item.setData(action);
    }

    private void selectionChanged()
    {
        getContainer().updateButtons();
    }

    @Override
    public void performHelp()
    {
        PlatformUI.getWorkbench().getHelpSystem().displayHelp("org.eclipse.mat.ui.help.diagnostics"); //$NON-NLS-1$
    }

    @Override
    public boolean isPageComplete()
    {
        return actionsTable.getSelectionIndex() != -1;
    }

    public DiagnosticsAction getSelectedAction()
    {
        return (DiagnosticsAction) actionsTable.getItem(actionsTable.getSelectionIndex()).getData();
    }
}

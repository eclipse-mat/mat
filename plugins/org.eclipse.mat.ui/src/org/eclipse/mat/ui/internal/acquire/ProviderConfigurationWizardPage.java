/*******************************************************************************
 * Copyright (c) 2010, 2012 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - refactor for new/import wizard as WizardPage,
 *       rename from ProviderConfigurationDialog
 *******************************************************************************/
package org.eclipse.mat.ui.internal.acquire;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.HeapDumpProviderRegistry;
import org.eclipse.mat.internal.acquire.VmInfoDescriptor;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;
import org.eclipse.mat.query.registry.AnnotatedObjectArgumentsSet;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentFactory;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.accessibility.AccessibleCompositeAdapter;
import org.eclipse.mat.ui.internal.acquire.AcquireDialog.ProcessSelectionListener;
import org.eclipse.mat.ui.internal.acquire.ProviderArgumentsTable.ITableListener;
import org.eclipse.mat.ui.internal.browser.QueryContextHelp;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;

/**
 * Handles configuring arguments for a particular dump provider.
 *
 */
public class ProviderConfigurationWizardPage extends WizardPage implements ITableListener, ProcessSelectionListener
{

    private ProviderArgumentsTable table;
    private Table availableProvidersTable;
    private AcquireDialog acquireDialog;
    private QueryContextHelp helpPopup;
    // Has a provider been changed in the UI
    private boolean changed;
    // Has that change been applied to the provider
    private boolean applied;
    
    public ProviderConfigurationWizardPage(AcquireDialog acquireDialog)
    {
        super(Messages.ProviderConfigurationDialog_ConfigureProvidersDialogTitle, Messages.ProviderConfigurationDialog_ConfigureProvidersDialogTitle, null);
        this.acquireDialog = acquireDialog;
    }
    
    //@Override
    public void createControl(Composite parent)
    {

        Composite composite = new Composite(parent, SWT.RESIZE);
        composite.setLayout(new GridLayout(1, false));
        GridDataFactory.fillDefaults().grab(true, true).span(1, 1).applyTo(composite);
        
        Label providersLabel = new Label(composite, SWT.NONE);
        providersLabel.setText(Messages.ProviderConfigurationDialog_AvailableProvidersLabel);
        GridDataFactory.swtDefaults().span(1, 1).applyTo(providersLabel);
        
        createProvidersTable(composite);
        
        Label argumentsLabel = new Label(composite, SWT.NONE);
        argumentsLabel.setText(Messages.ProviderConfigurationDialog_ConfigurableParameteresLabel);
        GridDataFactory.swtDefaults().span(1, 1).applyTo(argumentsLabel);
        
        createArgumentsTable(composite);
        
        availableProvidersTable.addSelectionListener(new SelectionAdapter() {
            
            public void widgetSelected(SelectionEvent e)
            {
                if (e.item instanceof TableItem)
                {
                    AnnotatedObjectArgumentsSet argumentsSet = (AnnotatedObjectArgumentsSet) ((TableItem) e.item).getData();
                    table.providerSelected(argumentsSet);
                    onFocus(null);
                    relocateHelp(true);
                }
            }
        });

        // If a process is selected, make the configuration provider match the process
        acquireDialog.addProcessSelectionListener(this);

        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "org.eclipse.mat.ui.help.query_arguments"); //$NON-NLS-1$

        Listener listener = new Listener()
        {
            public void handleEvent(Event event)
            {
                relocateHelp(false);
            }
        };
        getShell().addListener(SWT.Resize, listener);
        getShell().addListener(SWT.Move, listener);
        
        setControl(composite);

    }

    public IWizardPage getNextPage() {
        try 
        {
            if (changed)
            {
                applied = true;
                applyChanges();
                onError(null);
            }
        }
        catch (SnapshotException e)
        {
            onError(e.getLocalizedMessage());
            return null;
        }
        return super.getNextPage();
    }

    /*
     * getPreviousPage() gets called whenever updateButtons is called
     * so it isn't useful for things just before we go back
     */

    private void applyChanges() throws SnapshotException
    {
        AnnotatedObjectArgumentsSet currentSet = table.getArgumentSet();
        if (currentSet != null)
        {
            // Throw exception if current selection fails
            applyProviderChanges(currentSet);
        }
        for (TableItem item : availableProvidersTable.getItems())
        {
            AnnotatedObjectArgumentsSet argumentSet = (AnnotatedObjectArgumentsSet) item.getData();
            if (argumentSet != currentSet)
            {
                try
                {
                    applyProviderChanges(argumentSet);
                }
                catch (SnapshotException e)
                {
                    // Ignore errors from non-selected providers
                }
            }
        }
    }

    private void applyProviderChanges(AnnotatedObjectArgumentsSet argumentSet) throws SnapshotException
    {
        try
        {
            HeapDumpProviderDescriptor providerDescriptor = (HeapDumpProviderDescriptor) argumentSet.getDescriptor();
            IHeapDumpProvider impl = providerDescriptor.getHeapDumpProvider();

            for (ArgumentDescriptor parameter : providerDescriptor.getArguments())
            {
                Object value = argumentSet.getArgumentValue(parameter);

                if (value == null && parameter.isMandatory())
                {
                    value = parameter.getDefaultValue();
                    if (value == null)
                        throw new SnapshotException(MessageUtil.format(
                                Messages.ProviderConfigurationDialog_MissingParameterErrorMessage, parameter.getName()));
                }

                if (value == null)
                {
                    if (argumentSet.getValues().containsKey(parameter))
                    {
                        Logger.getLogger(getClass().getName()).log(Level.INFO,
                                        MessageUtil.format("Setting null value for: {0}", parameter.getName())); //$NON-NLS-1$
                        parameter.getField().set(impl, null);
                    }
                    continue;
                }

                try
                {
                    if (value instanceof ArgumentFactory)
                    {
                        parameter.getField().set(impl, ((ArgumentFactory) value).build(parameter));
                    }
                    else if (parameter.isArray())
                    {
                        List<?> list = (List<?>) value;
                        Object array = Array.newInstance(parameter.getType(), list.size());

                        int ii = 0;
                        for (Object v : list)
                            Array.set(array, ii++, v);

                        parameter.getField().set(impl, array);
                    }
                    else if (parameter.isList())
                    {
                        // handle ArgumentFactory inside list
                        List<?> source = (List<?>) value;
                        List<Object> target = new ArrayList<Object>(source.size());
                        for (int ii = 0; ii < source.size(); ii++)
                        {
                            Object v = source.get(ii);
                            if (v instanceof ArgumentFactory)
                                v = ((ArgumentFactory) v).build(parameter);
                            target.add(v);
                        }

                        parameter.getField().set(impl, target);
                    }
                    else
                    {
                        parameter.getField().set(impl, value);
                    }
                }
                catch (IllegalArgumentException e)
                {
                    throw new SnapshotException(MessageUtil.format(Messages.ProviderConfigurationDialog_IllegalArgumentErrorMessage, value,
                                    value.getClass().getName(), parameter.getName(), parameter.getType().getName()), e);
                }
                catch (IllegalAccessException e)
                {
                    // should not happen as we check accessibility when
                    // registering queries
                    throw new SnapshotException(MessageUtil.format("Unable to access field {0} of type {1}", parameter //$NON-NLS-1$
                                    .getName(), parameter.getType().getName()), e);
                }
            }

        }
        catch (IProgressListener.OperationCanceledException e)
        {
            throw e; // no nesting!
        }
        catch (SnapshotException e)
        {
            throw e; // no nesting!
        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }
    }
    
    private void createProvidersTable(Composite parent)
    {
        Composite tableComposite = new Composite(parent, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, false).indent(0, 0).applyTo(tableComposite);

        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        tableComposite.setLayout(tableColumnLayout);

        availableProvidersTable = new Table(tableComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);

        TableColumn column1 = new TableColumn(availableProvidersTable, SWT.NONE);
        column1.setText(Messages.ProviderConfigurationDialog_NameColumnHeader);
        tableColumnLayout.setColumnData(column1, new ColumnWeightData(0, 200));

        TableColumn column2 = new TableColumn(availableProvidersTable, SWT.NONE);
        column2.setText(Messages.ProviderConfigurationDialog_DescriptionColumnHeader);
        tableColumnLayout.setColumnData(column2, new ColumnWeightData(100, 250));

        availableProvidersTable.setHeaderVisible(true);
        availableProvidersTable.setLinesVisible(true);
        AccessibleCompositeAdapter.access(availableProvidersTable);

        Collection<HeapDumpProviderDescriptor> providers = HeapDumpProviderRegistry.instance().getHeapDumpProviders();
        for (HeapDumpProviderDescriptor heapDumpProviderDescriptor : providers)
        {
            TableItem item = new TableItem(availableProvidersTable, SWT.NONE, 0);
            item.setData(new AnnotatedObjectArgumentsSet(heapDumpProviderDescriptor));
            item.setText(0, heapDumpProviderDescriptor.getName());
            if (heapDumpProviderDescriptor.getHelp() != null)
                item.setText(1, heapDumpProviderDescriptor.getHelp());
        }

        tableComposite.layout();
        tableComposite.pack();
    }
    
    private void createArgumentsTable(Composite parent)
    {
        ScrolledComposite composite = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);

        composite.setLayout(new GridLayout());
        composite.setExpandHorizontal(true);
        composite.setExpandVertical(true);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(composite);

        Composite tableComposite = new Composite(composite, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).indent(0, 0).applyTo(tableComposite);

        Dialog.applyDialogFont(composite);
        table = new ProviderArgumentsTable(tableComposite, SWT.FULL_SELECTION | SWT.SINGLE);
        table.addListener(this);

        tableComposite.layout();
        tableComposite.pack();
        
        composite.setContent(tableComposite);
        GridDataFactory.fillDefaults().hint(300, 100).grab(true, true).span(1, 2).applyTo(composite);
    }

    public void relocateHelp(final boolean create)
    {
        final AnnotatedObjectArgumentsSet argumentSet = table.getArgumentSet();
        if (argumentSet == null) return;

        if (argumentSet.getDescriptor().isHelpAvailable() && //
                (create || (helpPopup != null && helpPopup.getShell() != null)))
        {
            if (getShell() == null)
            {
                helpPopup.close();
                return;
            }
            getShell().getDisplay().timerExec(100, new Runnable() {
                public void run()
                {
                    if (getShell() != null && !getShell().isDisposed())
                    {
                        Rectangle myBounds = getShell().getBounds();
                        Rectangle helpBounds = new Rectangle(myBounds.x, myBounds.y + myBounds.height, myBounds.width, SWT.DEFAULT);

                        if (helpPopup != null)
                        {
                            if (!create)
                            {
                                helpPopup.resize(helpBounds);
                                return;
                            }
                            else
                            {
                                // Get ready to create a new pop-up with new help text
                                helpPopup.close();
                            }
                        }

                        helpPopup = new QueryContextHelp(getShell(), argumentSet.getDescriptor(), helpBounds);
                        helpPopup.open();
                    }
                }
            });
        }
    }

    public void setVisible(boolean f)
    {
        if (!f)
        {
            if (applied)
            {
                applied = false;
                // Delay retrieving the VM information until the wizard is displayed
                getControl().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        acquireDialog.refresh();
                    }
                });
            }
            if (helpPopup != null)
                helpPopup.close();

        }
        else
        {
            relocateHelp(true);
        }
        super.setVisible(f);
    }

    public void onInputChanged()
    {
        // A new dump provider configuration has been selected.
        // Update next button e.g. if new provider isn't executable.
        onError(null);
        getContainer().updateButtons();
    }

    public void onValueChanged()
    {
        if (!changed)
        {
            // We do care if the user changes a parameter for finding VMs
            // The list of VMs will be refreshed when we leave this page, but clear the 
            // selection now so the finish button doesn't work.
            acquireDialog.clearSelection();
            // set the changed flag afterwards so updating the buttons doesn't
            // force an applychanges immediately
            changed = true;
        }
        getContainer().updateButtons();
    }

    public void onError(String message)
    {
        setErrorMessage(message);
        // a work around: calling onFocus ensures a correct update of the error
        // message. Without this call it doesn't update the message correct.
        onFocus(null);
    }

    public void onFocus(String message)
    {
        if (getErrorMessage() != null) setMessage(getErrorMessage(), IMessageProvider.ERROR);
        else if (message != null) setMessage(message, IMessageProvider.INFORMATION);
        else setMessage(table.getProviderDescriptor().getName());
        // Causes recursion from getNextPage();
        //getContainer().updateButtons();
    }
    
    /**
     * Use canFlipToNextPage() instead of {@link #isPageComplete()}
     * so that getNextPage() is not called all the time, only when moving.
     */
    @Override
    public boolean canFlipToNextPage()
    {
        boolean exec = false;
        AnnotatedObjectArgumentsSet argumentSet = table.getArgumentSet();
        if (argumentSet != null)
        {
            // If a provider is selected it should be executable
            exec |= argumentSet.isExecutable();
        }
        else
        {
            // So long as one provider is executable then we can attempt to get a dump
            for (TableItem item : availableProvidersTable.getItems())
            {
                argumentSet = (AnnotatedObjectArgumentsSet) item.getData();
                exec |= argumentSet.isExecutable();
            }
        }
        // If an error message is displayed then don't allow next
        return exec && getErrorMessage() == null;
    }

    @Override
    public void performHelp()
    {
        String helpUrl = table.getArgumentSet().getDescriptor().getHelpUrl();
        if (helpUrl != null)
        {
             PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(helpUrl);
        }
        relocateHelp(true);
        PlatformUI.getWorkbench().getHelpSystem().displayHelp("org.eclipse.mat.ui.help.acquire_arguments"); //$NON-NLS-1$
    }

    /**
     * Called from AcquireDialog with a process argument set, use to find the provider
     */
    public void processSelected(AnnotatedObjectArgumentsSet argumentSet)
    {
        if (argumentSet == null)
            return;
        IAnnotatedObjectDescriptor newProviderDescriptor = argumentSet.getDescriptor();
        if (newProviderDescriptor instanceof VmInfoDescriptor)
        {
            IHeapDumpProvider prov = ((VmInfoDescriptor)newProviderDescriptor).getVmInfo().getHeapDumpProvider();
            // Walk through table
            int index = 0;
            for (TableItem item : availableProvidersTable.getItems())
            {
                AnnotatedObjectArgumentsSet argumentSet2 = (AnnotatedObjectArgumentsSet) item.getData();
                IAnnotatedObjectDescriptor desc2 = argumentSet2.getDescriptor();
                if (desc2 instanceof HeapDumpProviderDescriptor)
                {
                    if (prov.equals(((HeapDumpProviderDescriptor)desc2).getHeapDumpProvider()))
                    {
                        // Set the provider to match the selected process
                        availableProvidersTable.setSelection(index);
                        table.providerSelected(argumentSet2);
                        break;
                    }
                }
                ++index;
            }
        }
    }
}

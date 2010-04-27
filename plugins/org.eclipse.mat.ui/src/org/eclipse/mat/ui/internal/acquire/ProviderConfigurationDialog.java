/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.acquire;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.HeapDumpProviderRegistry;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentFactory;
import org.eclipse.mat.query.registry.AnnotatedObjectArgumentsSet;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;

public class ProviderConfigurationDialog extends Dialog
{

	private ProviderArgumentsTable table;
	private Table availableProvidersTable;
	private QueryContextHelp helpPopup;
	
	protected ProviderConfigurationDialog(Shell parentShell)
	{
		super(parentShell);
//		setShellStyle(getShellStyle() | SWT.RESIZE);
	}
	
	@Override
	protected Control createDialogArea(Composite parent)
	{

		Composite composite = new Composite(parent, SWT.RESIZE);
		composite.setLayout(new GridLayout(1, false));
        GridDataFactory.fillDefaults().span(1, 1).applyTo(composite);
        
        Label providersLabel = new Label(composite, SWT.NONE);
        providersLabel.setText("Available Heap Dump Providers");
        GridDataFactory.swtDefaults().span(1, 1).applyTo(providersLabel);
		
		createProvidersTable(composite);
		GridDataFactory.fillDefaults().grab(true, true).span(1, 1).applyTo(availableProvidersTable);
		
        Label argumentsLabel = new Label(composite, SWT.NONE);
        argumentsLabel.setText("Configurable Parameters");
        GridDataFactory.swtDefaults().span(1, 1).applyTo(argumentsLabel);
		
		createArgumentsTable(composite);
		
		availableProvidersTable.addSelectionListener(new SelectionAdapter() {
			
			public void widgetSelected(SelectionEvent e)
			{
				if (e.item instanceof TableItem)
				{
					AnnotatedObjectArgumentsSet argumentsSet = (AnnotatedObjectArgumentsSet) ((TableItem) e.item).getData();
					table.providerSelected(argumentsSet);
					relocateHelp(true);
				}
			}
		});



		
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
        
		return composite;

	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent)
	{
		createButton(parent, IDialogConstants.YES_ID, "Apply", false);
		super.createButtonsForButtonBar(parent);
	}
	
	@Override
	protected void buttonPressed(int buttonId)
	{
		if (buttonId == IDialogConstants.YES_ID || buttonId == IDialogConstants.OK_ID)
		{
			try
			{
				applyChanges();
			}
			catch (SnapshotException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		super.buttonPressed(buttonId);
	}
	
	private void applyChanges() throws SnapshotException
	{
		for (TableItem item : availableProvidersTable.getItems())
		{
			AnnotatedObjectArgumentsSet argumentSet = (AnnotatedObjectArgumentsSet) item.getData();
			
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
	                        		"Missing required parameter: {0}", parameter.getName()));
	                }

	                if (value == null)
	                {
	                    if (argumentSet.getValues().containsKey(parameter))
	                    {
	                        Logger.getLogger(getClass().getName()).log(Level.INFO,
	                                        MessageUtil.format("Setting null value for: {0}", parameter.getName()));
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
	                    throw new SnapshotException(MessageUtil.format("Illegal argument: {0} of type {1} cannot be set to field {2} of type {3}", value,
	                                    value.getClass().getName(), parameter.getName(), parameter.getType().getName()), e);
	                }
	                catch (IllegalAccessException e)
	                {
	                    // should not happen as we check accessibility when
	                    // registering queries
	                    throw new SnapshotException(MessageUtil.format("Unable to access field {0} of type {1}", parameter
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
	}
	
	private void createProvidersTable(Composite parent)
	{
		availableProvidersTable = new Table(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
		TableColumn column1 = new TableColumn(availableProvidersTable, SWT.NONE);
		column1.setText("Name");
		column1.setWidth(200);
		
		TableColumn column2 = new TableColumn(availableProvidersTable, SWT.NONE);
		column2.setText("Description");
		column2.setWidth(300);
		
		availableProvidersTable.setHeaderVisible(true);
		availableProvidersTable.setLinesVisible(true);
		
		Collection<HeapDumpProviderDescriptor> providers = HeapDumpProviderRegistry.instance().getHeapDumpProviders();
		for (HeapDumpProviderDescriptor heapDumpProviderDescriptor : providers)
		{
			TableItem item = new TableItem(availableProvidersTable, SWT.NONE, 0);
			item.setData(new AnnotatedObjectArgumentsSet(heapDumpProviderDescriptor));
			item.setText(0, heapDumpProviderDescriptor.getName());
			item.setText(1, heapDumpProviderDescriptor.getHelp());
		}
		
		availableProvidersTable.layout();
		availableProvidersTable.pack();
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
}

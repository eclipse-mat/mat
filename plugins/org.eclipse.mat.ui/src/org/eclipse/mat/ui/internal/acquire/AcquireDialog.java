/*******************************************************************************
 * Copyright (c) 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.acquire;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.HeapDumpProviderRegistry;
import org.eclipse.mat.internal.acquire.VmInfoDescriptor;
import org.eclipse.mat.query.registry.AnnotatedObjectArgumentsSet;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class AcquireDialog extends WizardPage
{
    private static final String LAST_DIRECTORY_KEY = AcquireDialog.class.getName() + ".lastDir"; //$NON-NLS-1$

    private Table localVMsTable;
    private Text folderText;
    private Button configureButton;
    private Button refreshButton;
    private Collection<HeapDumpProviderDescriptor> providerDescriptors;
    private List<ProcessSelectionListener> listeners = new ArrayList<ProcessSelectionListener>();

    interface ProcessSelectionListener
    {
    	void processSelected(AnnotatedObjectArgumentsSet argumentSet);
    }
    
    public AcquireDialog(Collection<HeapDumpProviderDescriptor> dumpProviders)
    {
        super("acq"); //$NON-NLS-1$
        this.providerDescriptors = dumpProviders;
    }

    public void createControl(Composite parent)
    {
        setTitle(Messages.AcquireDialog_DialogName);
        setDescription(Messages.AcquireDialog_DialogDescription);

        final Composite top = new Composite(parent, SWT.NONE);
        top.setLayout(new GridLayout(2, false));

        Label l1 = new Label(top, SWT.NONE);
        l1.setText(Messages.AcquireDialog_ChooseProcess);
        GridDataFactory.swtDefaults().span(2, 1).applyTo(l1);
        
        localVMsTable = new Table(top, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.FULL_SELECTION);
        localVMsTable.setHeaderVisible(true);
        localVMsTable.setLinesVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).span(1, 1).minSize(0, 100).applyTo(localVMsTable);
        localVMsTable.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                getContainer().updateButtons();
                String proposedFileName = getProcess().getProposedFileName();
                if (proposedFileName == null)
                	proposedFileName = "java_%pid%"; //$NON-NLS-1$
                
                proposedFileName = proposedFileName.replace("%pid%", String.valueOf(getProcess().getPid())); //$NON-NLS-1$
                proposedFileName = getSelectedDirectory() + File.separatorChar + proposedFileName;
                folderText.setText(proposedFileName);
                
                // notify listeners
                for (ProcessSelectionListener listener : listeners)
				{
					listener.processSelected(getProcessArgumentsSet());
				}
            }

            public void widgetDefaultSelected(SelectionEvent e)
            {
                if (getWizard().performFinish())
                    getShell().close();
            }
        });

        TableColumn column = new TableColumn(localVMsTable, SWT.RIGHT);
        column.setText(Messages.AcquireDialog_ColumnDescription);
        column.setWidth(150);
        column = new TableColumn(localVMsTable, SWT.LEFT);
        column.setText(Messages.AcquireDialog_ColumnPID);
        column.setWidth(40);
        column = new TableColumn(localVMsTable, SWT.LEFT);
        column.setText("Heap Dump Provider");
        column.setWidth(200);


        refreshTable();

        Composite buttons = new Composite(top, SWT.NONE);
        buttons.setLayout(new GridLayout(1, false));
        GridDataFactory.fillDefaults().span(1, 1).applyTo(buttons);
        
        refreshButton = new Button(buttons, SWT.NONE);
        refreshButton.setText("Refresh");
        GridDataFactory.fillDefaults().grab(true, false).span(1, 1).applyTo(refreshButton);
        refreshButton.addSelectionListener(new SelectionAdapter() {
			
			public void widgetSelected(SelectionEvent e)
			{
				refreshTable();
			}
		});
        
        configureButton = new Button(buttons, SWT.NONE);
        configureButton.setText("Configure ...");
        GridDataFactory.fillDefaults().grab(true, false).span(1, 1).applyTo(configureButton);
        configureButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
            	ProviderConfigurationDialog configDialog = new ProviderConfigurationDialog(getShell());
            	if (configDialog.open() == IDialogConstants.OK_ID)
            	{
            		refreshTable();
            	}

            }
        });
        
        
        Label saveLocationLabel = new Label(top, SWT.NONE);
        saveLocationLabel.setText(Messages.AcquireDialog_SaveLocation);
        GridDataFactory.swtDefaults().span(2, 1).applyTo(saveLocationLabel);

        folderText = new Text(top, SWT.BORDER);
        GridDataFactory.fillDefaults().minSize(300, 0).grab(true, false).applyTo(folderText);
        String lastDir = Platform.getPreferencesService().getString(MemoryAnalyserPlugin.PLUGIN_ID, LAST_DIRECTORY_KEY, "", null); //$NON-NLS-1$
        folderText.setText(lastDir);
        folderText.addModifyListener(new ModifyListener()
        {
        	public void modifyText(ModifyEvent e)
        	{
        		getContainer().updateButtons();
        	}
        });

        Button b = new Button(top, SWT.NONE);
        b.setText(Messages.AcquireDialog_BrowseButton); 
        b.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                DirectoryDialog dialog = new DirectoryDialog(top.getShell());
                String folder = dialog.open();

                if (folder != null && folder.length() > 0)
                    folderText.setText(folder);
            }
        });

        localVMsTable.setFocus();
        setControl(top);
    }
    
    private void refreshTable()
    {
    	localVMsTable.removeAll();
        List<? extends VmInfo> vms = getAvailableVms();

        if (vms != null)
        {
        	for (VmInfo process : vms)
        	{
        		
        		try
				{
					VmInfoDescriptor descriptor = VmInfoDescriptor.createDescriptor(process);
					TableItem item = new TableItem(localVMsTable, SWT.NONE);
					item.setText(0, process.getDescription());
					item.setText(1, Integer.toString(process.getPid()));
					item.setText(2, getProviderDescriptor(process).getName());
					item.setData(new AnnotatedObjectArgumentsSet(descriptor));
				}
				catch (SnapshotException e)
				{
					Logger.getLogger(MemoryAnalyserPlugin.PLUGIN_ID).log(Level.SEVERE, "Problems refreshing process list", e);
				}
        	}
        }
    }

    private List<VmInfo> getAvailableVms()
	{
    	List<VmInfo> vms = new ArrayList<VmInfo>();
    	
    	for (HeapDumpProviderDescriptor providerDescriptor : providerDescriptors)
		{
        	GetVMListRunnable getListOperation = new GetVMListRunnable(providerDescriptor, getContainer());
        	if (getListOperation.run().isOK())
        	{
        		List<? extends VmInfo> providerVMs = getListOperation.getResult();
        		if (providerVMs != null)
        		{
        			vms.addAll(providerVMs);
        		}
        	}
		}
		return vms;
	}
    
    @Override
    public boolean canFlipToNextPage()
    {
        return localVMsTable.getSelectionIndex() != -1 && folderText.getText().length() > 0;
    }

	@Override
    public boolean isPageComplete()
    {
        return canFlipToNextPage() && getProcessArgumentsSet().getDescriptor().getArguments().size() == 0;
    }

	public AnnotatedObjectArgumentsSet getProcessArgumentsSet()
	{
		if (localVMsTable.getSelectionIndex() == -1) return null;
		AnnotatedObjectArgumentsSet argumentsSet = (AnnotatedObjectArgumentsSet) localVMsTable.getSelection()[0].getData();

		return argumentsSet;
	}
	
	public VmInfo getProcess()
	{
		if (localVMsTable.getSelectionIndex() == -1) return null;
		AnnotatedObjectArgumentsSet argumentsSet = (AnnotatedObjectArgumentsSet) localVMsTable.getSelection()[0].getData();
		VmInfoDescriptor descriptor = (VmInfoDescriptor) argumentsSet.getDescriptor();

		return descriptor.getVmInfo();
	}
	

    public String getSelectedPath()
    {
        return folderText.getText();
    }
    
    private String getSelectedDirectory()
    {
    	String selectedPath = folderText.getText();
    	if (selectedPath == null)
    		return ""; //$NON-NLS-1$
    	
    	// if the selection is a folder, just return it
    	File f = new File(selectedPath);
    	if (f.exists() && f.isDirectory())
    		return selectedPath;
    	
    	// otherwise return what seems to be the deepest folder
    	int i = selectedPath.lastIndexOf(File.separatorChar);
    	return i == -1 ? selectedPath : selectedPath.substring(0, i);
    }

    public void saveSettings()
    {
    	new InstanceScope().getNode(MemoryAnalyserPlugin.PLUGIN_ID).put(LAST_DIRECTORY_KEY, getSelectedDirectory());
    }
    
    private HeapDumpProviderDescriptor getProviderDescriptor(VmInfo vmInfo)
    {
    	return HeapDumpProviderRegistry.instance().getHeapDumpProvider(vmInfo.getHeapDumpProvider().getClass());
    }
    
    synchronized void addProcessSelectionListener(ProcessSelectionListener listener)
    {
    	listeners.add(listener);
    }
    
	
	private  class GetVMListRunnable implements IRunnableWithProgress
	{
		private IStatus status;
		private IRunnableContext context;
		private List<? extends VmInfo> result;
		private HeapDumpProviderDescriptor provider;
		
		public GetVMListRunnable(HeapDumpProviderDescriptor provider, IRunnableContext context)
		{
			this.provider = provider;
			this.context = context;
		}

		public List<? extends VmInfo> getResult()
		{
			return result;
		}

		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
		{
			status = doOperation(monitor);
		}
		
		private IStatus doOperation(IProgressMonitor monitor)
		{
			IProgressListener listener = new ProgressMonitorWrapper(monitor);
			try
			{
				result = provider.getHeapDumpProvider().getAvailableVMs(listener);
				if (listener.isCanceled()) return Status.CANCEL_STATUS;
			}
			catch (Exception e)
			{
				return ErrorHelper.createErrorStatus(e);
			}

			return Status.OK_STATUS;
		}
		
		public final IStatus run()
		{
			try
			{
				context.run(true, true, this);
			}
			catch (Exception e)
			{
				status = ErrorHelper.createErrorStatus(Messages.AcquireSnapshotAction_UnexpectedException, e);
			}

			// report error if any occurred
			if (!status.isOK() && status != Status.CANCEL_STATUS)
				Logger.getLogger(MemoryAnalyserPlugin.PLUGIN_ID).log(Level.INFO, MessageUtil.format("Error getting list of VMs with [{0}] provider", provider.getName()), status.getException());

			return status;
		}
	}
}

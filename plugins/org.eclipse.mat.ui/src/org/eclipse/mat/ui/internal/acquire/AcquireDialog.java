/*******************************************************************************
 * Copyright (c) 2009, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - refactor for new/import wizard
 *    IBM Corporation - disabled dumps
 *******************************************************************************/
package org.eclipse.mat.ui.internal.acquire;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.HeapDumpProviderRegistry;
import org.eclipse.mat.internal.acquire.VmInfoDescriptor;
import org.eclipse.mat.query.registry.AnnotatedObjectArgumentsSet;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.acquire.AcquireSnapshotAction.AcquireWizard;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

/**
 * Handles the list of all VMs.
 *
 */
public class AcquireDialog extends WizardPage
{
    private static final String LAST_DIRECTORY_KEY = AcquireDialog.class.getName() + ".lastDir"; //$NON-NLS-1$
    
    private LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());
    private Font italicFont;

    private Table localVMsTable;
    private Label saveLocationLabel;
    private Text folderText;
    private Button configureButton;
    private Button refreshButton;
    private Collection<HeapDumpProviderDescriptor> providerDescriptors;
    private List<ProcessSelectionListener> listeners = new ArrayList<ProcessSelectionListener>();

    private int sortpid = 1;
    private int sortproc = 2;
    private List<? extends VmInfo> vms;

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

        Composite tableComposite = new Composite(top, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).indent(0, 0).applyTo(tableComposite);

        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        tableComposite.setLayout(tableColumnLayout);
        
        localVMsTable = new Table(tableComposite, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.FULL_SELECTION);
        localVMsTable.setHeaderVisible(true);
        localVMsTable.setLinesVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).span(1, 1).minSize(0, 100).applyTo(localVMsTable);
        localVMsTable.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                selectionChanged();
            }

            public void widgetDefaultSelected(SelectionEvent e)
            {
                if (getWizard().performFinish())
                    getShell().close();
            }
        });

        final int descWidth = 250;
        final int pidWidth = 70;
        final int provWidth = 200;
        TableColumn column = new TableColumn(localVMsTable, SWT.RIGHT);
        column.setText(Messages.AcquireDialog_ColumnDescription);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(100, descWidth));
        column = new TableColumn(localVMsTable, SWT.RIGHT);
        column.setText(Messages.AcquireDialog_ColumnPID);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(0, pidWidth));
        column.addSelectionListener(new SelectionListener() {
            public void widgetSelected(SelectionEvent arg0)
            {
                if (sortpid == 0)
                {
                    sortpid = 2;
                }
                else if (sortpid > 0)
                {
                    sortpid = -2;
                }
                else
                {
                    sortpid = 0;
                }
                if (Math.abs(sortproc) >= 2)
                    sortproc /= 2;
                resort();
            }

            public void widgetDefaultSelected(SelectionEvent arg0)
            {
            }
        });
        column = new TableColumn(localVMsTable, SWT.LEFT);
        column.setText(Messages.AcquireDialog_HeapDumpProviderColumnHeader);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(0, provWidth));
        column.addSelectionListener(new SelectionListener() {
            public void widgetSelected(SelectionEvent arg0)
            {
                if (sortproc == 0)
                {
                    sortproc = 2;
                }
                else if (sortproc > 0)
                {
                    sortproc = -2;
                }
                else
                {
                    sortproc = 0;
                }
                if (Math.abs(sortpid) >= 2)
                    sortpid /= 2;
                resort();
            }

            public void widgetDefaultSelected(SelectionEvent arg0)
            {
            }
        });
        localVMsTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == 'c' && (e.stateMask & SWT.MOD1) != 0)
                    Copy.copyToClipboard(localVMsTable);
            }
        });

        tableComposite.layout();
        tableComposite.pack();

        Control control = localVMsTable.getParent();
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "org.eclipse.mat.ui.help.acquire_arguments"); //$NON-NLS-1$

        italicFont = resourceManager.createFont(FontDescriptor.createFrom(column.getParent().getFont()).setStyle(SWT.ITALIC));

        Composite buttons = new Composite(top, SWT.NONE);
        buttons.setLayout(new GridLayout(1, false));
        GridDataFactory.fillDefaults().span(1, 1).applyTo(buttons);

        refreshButton = new Button(buttons, SWT.NONE);
        refreshButton.setText(Messages.AcquireDialog_RefreshButtonLabel);
        GridDataFactory.fillDefaults().grab(true, false).span(1, 1).applyTo(refreshButton);
        refreshButton.addSelectionListener(new SelectionAdapter() {

            public void widgetSelected(SelectionEvent e)
            {
                refresh();
            }
        });

        configureButton = new Button(buttons, SWT.NONE);
        configureButton.setText(Messages.AcquireDialog_ConfigureButtonLabel);
        GridDataFactory.fillDefaults().grab(true, false).span(1, 1).applyTo(configureButton);
        configureButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                AcquireWizard aw = (AcquireWizard)AcquireDialog.this.getWizard();
                aw.getContainer().showPage(aw.configPage);
            }
        });
        
        saveLocationLabel = new Label(top, SWT.NONE);
        saveLocationLabel.setText(Messages.AcquireDialog_SaveLocation);
        GridDataFactory.swtDefaults().span(2, 1).applyTo(saveLocationLabel);

        folderText = new Text(top, SWT.BORDER);
        GridDataFactory.fillDefaults().minSize(300, 0).grab(true, false).applyTo(folderText);
        String lastDir = Platform.getPreferencesService().getString(MemoryAnalyserPlugin.PLUGIN_ID, LAST_DIRECTORY_KEY, "", null); //$NON-NLS-1$
        if (lastDir == null || lastDir.trim().equals("")) //$NON-NLS-1$
        	lastDir = System.getProperty("user.home");  //$NON-NLS-1$
        
        folderText.setText(lastDir);
        folderText.addModifyListener(new ModifyListener()
        {
        	public void modifyText(ModifyEvent e)
        	{
        		getContainer().updateButtons();
        		String errorMessage = null;
        		int level= NONE;
        		if (localVMsTable.getSelectionIndex() != -1 && folderText.getText().length() > 0)
        		{
        		    File f = new File(AcquireDialog.this.getSelectedPath());
        		    if (f.isDirectory())
        		    {
        		        errorMessage = Messages.AcquireDialog_FileIsDirectory;
        		        level = ERROR;
        		    }
        		    else if (f.exists())
        		    {
        		        errorMessage = Messages.AcquireDialog_FileExists;
        		        level = WARNING;
        		    }
        		}
        		AcquireDialog.this.setMessage(errorMessage, level);
        	}
        });

        Button b = new Button(top, SWT.NONE);
        b.setText(Messages.AcquireDialog_BrowseButton); 
        b.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent e)
            {
                String folder;
                if (localVMsTable.getSelectionIndex() == -1)
                {
                    // No VM selected, so just choose the directory as the file will be
                    // overwritten once a VM is chosen.
                    DirectoryDialog dialog = new DirectoryDialog(top.getShell());
                    dialog.setFilterPath(AcquireDialog.this.getSelectedDirectory());
                    dialog.setText(Messages.AcquireDialog_ChooseDestinationDirectory);
                    dialog.setMessage(Messages.AcquireDialog_ChooseDestinationDirectoryMessage);
                    folder = dialog.open();
                }
                else
                {
                    FileDialog dialog = new FileDialog(top.getShell(), SWT.SAVE);
                    File f = new File(AcquireDialog.this.getSelectedPath());
                    if (f.isDirectory())
                    {
                        dialog.setFilterPath(f.getPath());
                    }
                    else
                    {
                        dialog.setFilterPath(f.getParent());
                        dialog.setFileName(f.getName());
                        String name = f.getName();
                        int i = name.lastIndexOf('.');
                        if (i >= 0)
                        {
                            dialog.setFilterExtensions(new String[] { "*" + name.substring(i), "*" }); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                    dialog.setText(Messages.AcquireDialog_ChooseDestinationDirectoryAndFile);
                    dialog.setOverwrite(true);
                    folder = dialog.open();
                }

                if (folder != null && folder.length() > 0)
                    folderText.setText(folder);
            }
        });

        localVMsTable.setFocus();
        setControl(top);
        
        // Delay retrieving the VM information until the wizard is displayed
        getControl().getDisplay().asyncExec(new Runnable()
        {
            public void run()
            {
                refresh();
            }
        });
    }
    
    private void refreshTable()
    {
        localVMsTable.removeAll();
        vms = getAvailableVms();
        fillTable();
    }

    private void fillTable()
    {
        localVMsTable.removeAll();
        if (vms != null)
        {
            for (VmInfo process : sort(vms))
            {

                try
                {
                    VmInfoDescriptor descriptor = VmInfoDescriptor.createDescriptor(process);
                    TableItem item = new TableItem(localVMsTable, SWT.NONE);
                    item.setGrayed(!process.isHeapDumpEnabled());
                    if (!process.isHeapDumpEnabled())
                    {
                        item.setFont(italicFont);
                    }
                    item.setText(0, process.getDescription());
                    item.setText(1, Integer.toString(process.getPid()));
                    item.setText(2, getProviderDescriptor(process).getName());
                    item.setData(new AnnotatedObjectArgumentsSet(descriptor));
                }
                catch (SnapshotException e)
                {
                    Logger.getLogger(MemoryAnalyserPlugin.PLUGIN_ID).log(Level.SEVERE, "Problems refreshing process list", e); //$NON-NLS-1$
                }
            }
        }
    }

    private List<? extends VmInfo> sort(List<? extends VmInfo> vms)
    {
        if (sortpid == 0 && sortproc == 0)
            return vms;
        List<? extends VmInfo>vms2 = new ArrayList<VmInfo>(vms);
        Collections.sort(vms2, new Comparator<VmInfo>() {
            private int sort(int a, int b) {
                if (a < b)
                    return -1;
                else if (a > b)
                    return +1;
                else
                    return 0;
            }
            private int signum(int x)
            {
                return sort(x, 0);
            }
            public int compare(VmInfo o1, VmInfo o2)
            {
                // First sort by indicator +/- 2, then by the indicator +/- 1
                return sortpid * sort(o1.getPid(), o2.getPid()) + sortproc *
                               signum(AcquireDialog.this.getProviderDescriptor(o1).getName().compareTo(
                                    AcquireDialog.this.getProviderDescriptor(o2).getName()));
            } 
        });
        return vms2;
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
    public boolean isPageComplete()
    {
        // There needs to be a valid process and a valid dump name
	    // Also if the heap dump is disabled (indicated by italic font), also disable (getGrayed() doesn't work).
	    if (localVMsTable.getSelectionIndex() == -1)
	    {
	        return false;
	    }
	    // See if the enabled status has changed (for example getting a dump has failed).
        boolean isEnabled = !localVMsTable.getItem(localVMsTable.getSelectionIndex()).getFont().equals(italicFont);
        boolean isEnabled2 = ((VmInfoDescriptor)((AnnotatedObjectArgumentsSet)localVMsTable.getItem(localVMsTable.getSelectionIndex()).getData()).getDescriptor()).getVmInfo().isHeapDumpEnabled();
        if (isEnabled && !isEnabled2)
        {
            // newly disabled
            localVMsTable.getItem(localVMsTable.getSelectionIndex()).setFont(italicFont);
            isEnabled = false;
        }
        return localVMsTable.getSelectionIndex() != -1 && isEnabled &&
                        folderText.getText().length() > 0 && !(new File(getSelectedPath()).isDirectory());
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
    	if (f.isDirectory())
    		return selectedPath;
    	
    	// otherwise return what seems to be the deepest folder
    	String dir = f.getParent();
    	return dir == null ? selectedPath : dir;
    }

    public void saveSettings()
    {
    	InstanceScope.INSTANCE.getNode(MemoryAnalyserPlugin.PLUGIN_ID).put(LAST_DIRECTORY_KEY, getSelectedDirectory());
    }
    
    private HeapDumpProviderDescriptor getProviderDescriptor(VmInfo vmInfo)
    {
    	return HeapDumpProviderRegistry.instance().getHeapDumpProvider(vmInfo.getHeapDumpProvider().getClass());
    }
    
    synchronized void addProcessSelectionListener(ProcessSelectionListener listener)
    {
    	listeners.add(listener);
    }

    void clearSelection() {
        localVMsTable.deselectAll();
        selectionChanged();
    }

    void refresh() {
        localVMsTable.deselectAll();
        refreshTable();
        selectionChanged();
    }

    void resort() {
        localVMsTable.deselectAll();
        selectionChanged();
        fillTable();
    }
	
    void updateFileName()
    {
        VmInfo process = getProcess();
        if (process == null)
        {
            folderText.setText(getSelectedDirectory());
            saveLocationLabel.setText(Messages.AcquireDialog_SaveLocation);
            saveLocationLabel.pack();
        } else {
            String proposedFileName = process.getProposedFileName();
            if (proposedFileName == null)
                proposedFileName = "java_%pid%"; //$NON-NLS-1$
            proposedFileName = proposedFileName.replace("%pid%", String.valueOf(getProcess().getPid())); //$NON-NLS-1$

            // Also replace date, time, and sequence number in {0} {1} {2}
            int pid = process.getPid();
            Date date = new Date();
            String errorMessage = null;
            String proposedFilePath;
            int i = 1;
            do
            {
                String proposedFileName2;
                try
                {
                    proposedFileName2 = MessageUtil.format(proposedFileName, date, pid, i);
                }
                catch (IllegalArgumentException e)
                {
                    errorMessage = e.getLocalizedMessage();
                    proposedFileName2 = proposedFileName;
                }
                final File proposedFile = new File(getSelectedDirectory(), proposedFileName2);
                proposedFilePath = proposedFile.getPath();
                if (proposedFileName2.equals(proposedFileName) || !proposedFile.exists())
                    break;
            }
            while (++i < 10000);
            folderText.setText(proposedFilePath);
            saveLocationLabel.setText(Messages.AcquireDialog_SaveFileLocation);
            saveLocationLabel.pack();
            if (errorMessage != null)
            {
                // Set error after setting the text, which would clear the error message
                setMessage(MessageUtil.format(Messages.AcquireDialog_InvalidFilenameTemplate, errorMessage, getProviderDescriptor(process).getName()), WARNING);
            }
        }
    }

	private void selectionChanged()
    {
        updateFileName();
        // notify listeners
        for (ProcessSelectionListener listener : listeners)
        {
        	listener.processSelected(getProcessArgumentsSet());
        }
        // The button states might depend on the listeners changing
        getContainer().updateButtons();
    }

    @Override
    public void performHelp()
    {
        if (localVMsTable.getSelectionIndex() >= 0)
        {
            AnnotatedObjectArgumentsSet argumentsSet = (AnnotatedObjectArgumentsSet) localVMsTable.getSelection()[0].getData();
            String helpUrl = argumentsSet.getDescriptor().getHelpUrl();
            if (helpUrl != null)
            {
                PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(helpUrl);
            }
        }
        PlatformUI.getWorkbench().getHelpSystem().displayHelp("org.eclipse.mat.ui.help.acquire_arguments"); //$NON-NLS-1$
    }

    private static class GetVMListRunnable implements IRunnableWithProgress
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
				Logger.getLogger(MemoryAnalyserPlugin.PLUGIN_ID).log(Level.INFO, MessageUtil.format("Error getting list of VMs with [{0}] provider", provider.getName()), status.getException()); //$NON-NLS-1$

			return status;
		}
	}
}

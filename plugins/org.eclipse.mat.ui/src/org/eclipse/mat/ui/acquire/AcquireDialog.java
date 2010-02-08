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
package org.eclipse.mat.ui.acquire;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
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
    private List<IHeapDumpProvider> dumpProviders;

    public AcquireDialog(List<IHeapDumpProvider> dumpProviders)
    {
        super("acq"); //$NON-NLS-1$
        this.dumpProviders = dumpProviders;
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
        GridDataFactory.fillDefaults().grab(true, true).span(2, 1).minSize(0, 100).applyTo(localVMsTable);
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
            }

            public void widgetDefaultSelected(SelectionEvent e)
            {
                if (getWizard().performFinish())
                    getShell().close();
            }
        });

        TableColumn column = new TableColumn(localVMsTable, SWT.RIGHT);
        column.setText(Messages.AcquireDialog_ColumnDescription);
        column.setWidth(230);
        column = new TableColumn(localVMsTable, SWT.LEFT);
        column.setText(Messages.AcquireDialog_ColumnPID);
        column.setWidth(90);


        List<VmInfo> vms = getAvailableVms();
        if (vms != null)
        {
        	for (VmInfo process : vms)
        	{
        		TableItem item = new TableItem(localVMsTable, SWT.NONE);
        		item.setText(0, process.getDescription());
        		item.setText(1, Integer.toString(process.getPid()));
        		item.setData(process);
        	}
        }

        folderText = new Text(top, SWT.BORDER);
        GridDataFactory.fillDefaults().minSize(300, 0).grab(true, false).applyTo(folderText);
        folderText.setText(MemoryAnalyserPlugin.getDefault().getPluginPreferences().getString(LAST_DIRECTORY_KEY));
        folderText.addModifyListener(new ModifyListener()
        {
        	public void modifyText(ModifyEvent e)
        	{
        		getContainer().updateButtons();
        	}
        });
        Label l2 = new Label(top, SWT.NONE);
        l2.setText(Messages.AcquireDialog_SaveLocation);
        GridDataFactory.swtDefaults().span(2, 1).applyTo(l2);


        Button b = new Button(top, SWT.NONE);
        b.setText("..."); //$NON-NLS-1$
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

    private List<VmInfo> getAvailableVms()
	{
    	List<VmInfo> vms = new ArrayList<VmInfo>();
    	for (IHeapDumpProvider provider : dumpProviders)
		{
    		List<VmInfo> providerVMs = provider.getAvailableVMs();
    		if (providerVMs != null)
			{
    			vms.addAll(providerVMs);
			}
		}
		return vms;
	}

	@Override
    public boolean isPageComplete()
    {
        return localVMsTable.getSelectionIndex() != -1 && folderText.getText().length() > 0;
    }

	public VmInfo getProcess()
	{
		if (localVMsTable.getSelectionIndex() == -1) return null;

		return (VmInfo) localVMsTable.getSelection()[0].getData();
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
        MemoryAnalyserPlugin.getDefault().getPluginPreferences().setValue(LAST_DIRECTORY_KEY, getSelectedDirectory());
    }
}

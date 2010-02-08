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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

public class AcquireSnapshotAction extends Action implements IWorkbenchWindowActionDelegate
{

	public void run()
	{
		final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

		List<IHeapDumpProvider> dumpProviders = getProviders();
		if (dumpProviders == null || dumpProviders.size() == 0)
		{
			showError(Messages.AcquireSnapshotAction_NoProviderError);
			return;
		}

		final AcquireDialog acquireDialog = new AcquireDialog(dumpProviders);

		Wizard wizard = new Wizard() {
			public boolean performFinish()
			{

				VmInfo selectedProcess = acquireDialog.getProcess();

				try
				{
					String selectedPath = acquireDialog.getSelectedPath();
					File preferredLocation = new File(selectedPath);
					if (!validatePath(preferredLocation)) return false;

					// request the heap dump and check if result is OK
					AcquireDumpOperation dumpOperation = new AcquireDumpOperation(selectedProcess, preferredLocation, getContainer());
					if (!dumpOperation.run().isOK()) return false;

					File destFile = dumpOperation.getResult();

					// open the heapdump
					Path path = new Path(destFile.getAbsolutePath());
					IEditorDescriptor descriptor = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(path.toOSString());

					try
					{
						IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), new PathEditorInput(path), descriptor.getId(),
								true);
						if (PlatformUI.getWorkbench().getIntroManager().getIntro() != null)
						{
							// if this action was called with open welcome page
							// - set it to standby mode.
							PlatformUI.getWorkbench().getIntroManager().setIntroStandby(PlatformUI.getWorkbench().getIntroManager().getIntro(), true);
						}
					}
					catch (Exception e)
					{
						ErrorHelper.logThrowableAndShowMessage(e, Messages.AcquireSnapshotAction_UnableToOpenEditor + path);
					}

					if (new File(acquireDialog.getSelectedPath()).exists()) acquireDialog.saveSettings();

				}
				catch (Exception e)
				{
					ErrorHelper.logThrowableAndShowMessage(e);
					return false;
				}
				return true;
			}

			private boolean validatePath(File destFile)
			{
				if (destFile.exists())
				{
					if (MessageDialog.openConfirm(shell, Messages.AcquireSnapshotAction_Confirmation, Messages.AcquireSnapshotAction_FileAlreadyExists))
					{
						destFile.delete();
					}
					else
					{
						return false;
					}
				}
				else if (!destFile.getParentFile().exists())
				{
					if (MessageDialog.openConfirm(shell, Messages.AcquireSnapshotAction_Confirmation, Messages.AcquireSnapshotAction_DirectoryDoesntExist))
					{
						if (!destFile.getParentFile().mkdirs())
						{
							showError(Messages.AcquireSnapshotAction_UnableToCreateDirectory);
							destFile = null;
						}
					}
					else
					{
						return false;
					}
				}

				return true;
			}

		};

		wizard.addPage(acquireDialog);
		wizard.setWindowTitle(Messages.AcquireSnapshotAction_AcquireDialogName);
		wizard.setNeedsProgressMonitor(true);

		new WizardDialog(shell, wizard).open();
	}

	private List<IHeapDumpProvider> getProviders()
	{
		IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.mat.api.heapDumpProvider"); //$NON-NLS-1$
		if (config.length == 0) return null;

		List<IHeapDumpProvider> providers = new ArrayList<IHeapDumpProvider>();
		for (IConfigurationElement configurationElement : config)
		{
			String bundleName = configurationElement.getContributor().getName();
			Logger.getLogger(getClass().getName()).info("Loaded heapDumpProvider from " + bundleName); //$NON-NLS-1$

			try
			{
				Object provider = configurationElement.createExecutableExtension("impl"); //$NON-NLS-1$
				providers.add((IHeapDumpProvider) provider);
			}
			catch (CoreException e)
			{
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, Messages.AcquireSnapshotAction_FailedToCreateProvider, e);
			}
		}

		return providers;
	}

	private void showError(String msg)
	{
		ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Error", null, ErrorHelper.createErrorStatus(msg)); //$NON-NLS-1$
	}

	public void dispose()
	{}

	public void init(IWorkbenchWindow window)
	{}

	public void run(IAction action)
	{
		run();
	}

	public void selectionChanged(IAction action, ISelection selection)
	{}

	public static class Handler extends AbstractHandler
	{

		public Handler()
		{}

		public Object execute(ExecutionEvent executionEvent)
		{
			new AcquireSnapshotAction().run();
			return null;
		}
	}

	static class AcquireDumpOperation implements IRunnableWithProgress
	{
		private IStatus status;
		private IRunnableContext context;
		private VmInfo vmInfo;
		private File preferredLocation;
		private File result;

		public AcquireDumpOperation(VmInfo vmInfo, File preferredLocation, IRunnableContext context)
		{
			this.vmInfo = vmInfo;
			this.preferredLocation = preferredLocation;
			this.context = context;
		}

		private IStatus doOperation(IProgressMonitor monitor)
		{
			IProgressListener listener = new ProgressMonitorWrapper(monitor);
			try
			{
				result = vmInfo.getHeapDumpProvider().acquireDump(vmInfo, preferredLocation, listener);

				if (listener.isCanceled()) return Status.CANCEL_STATUS;
			}
			catch (InterruptedException ignore)
			{
				// $JL-EXC$
			}
			catch (Exception e)
			{
				return ErrorHelper.createErrorStatus(e);
			}

			return Status.OK_STATUS;

		}

		private File getResult()
		{
			return result;
		}

		public final void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
		{
			status = doOperation(monitor);
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
				ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Error", null, status); //$NON-NLS-1$

			return status;
		}
	}
}

/*******************************************************************************
 * Copyright (c) 2008, 2012 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.query.arguments;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.browser.QueryContextHelp;
import org.eclipse.mat.ui.internal.query.arguments.LinkEditor.Mode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.PlatformUI;

public class ArgumentsWizardPage extends WizardPage implements ArgumentsTable.ITableListener
{
    public final static String HIDE_QUERY_HELP = "pref:HideQueryHelp";
    private IQueryContext context;
    private ArgumentSet argumentSet;
    private ArgumentsTable table;
    private QueryContextHelp helpPopup;

    public ArgumentsWizardPage(IQueryContext context, ArgumentSet argumentSet)
    {
        super(Messages.ArgumentsWizardPage_QueryArguments, argumentSet.getQueryDescriptor().getName(), null);

        setDescription(argumentSet.getQueryDescriptor().getShortDescription());

        this.context = context;
        this.argumentSet = argumentSet;
    }

    public void createControl(Composite parent)
    {
        ScrolledComposite composite = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);

        composite.setLayout(new GridLayout());
        composite.setExpandHorizontal(true);
        composite.setExpandVertical(true);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(composite);

        Composite tableComposite = new Composite(composite, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).indent(0, 0).applyTo(tableComposite);
        Mode mode = Mode.SIMPLE_MODE;
        if (getDialogSettings().get(ArgumentsWizard.class.getName()) != null
                        && Mode.ADVANCED_MODE.getModeType().equals(
                                        getDialogSettings().get(ArgumentsWizard.class.getName())))
        {
            mode = Mode.ADVANCED_MODE;
        }
        Dialog.applyDialogFont(composite);
        table = new ArgumentsTable(tableComposite, SWT.FULL_SELECTION | SWT.SINGLE, context, argumentSet, mode);
        table.addListener(this);

        tableComposite.layout();
        tableComposite.pack();

        composite.setContent(tableComposite);
        setControl(composite);
        
        Listener listener = new Listener()
        {
            public void handleEvent(Event event)
            {
                relocateHelp(false);
            }
        };
        getShell().addListener(SWT.Resize, listener);
        getShell().addListener(SWT.Move, listener);
        IPreferenceStore prefs = MemoryAnalyserPlugin.getDefault().getPreferenceStore();
        if (!prefs.getBoolean(HIDE_QUERY_HELP))
        {
            relocateHelp(true);
        }
    }

    @Override
    public boolean isPageComplete()
    {
        return (argumentSet.isExecutable() && getErrorMessage() == null);
    }

    public void onInputChanged()
    {
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
        if (getErrorMessage() != null)
            setMessage(getErrorMessage(), IMessageProvider.ERROR);
        else if (message != null)
            setMessage(message, IMessageProvider.INFORMATION);
        else
            setMessage(argumentSet.getQueryDescriptor().getShortDescription());
        if (getContainer() != null)
            getContainer().updateButtons();
    }

    public void onModeChange(Mode mode)
    {
        IDialogSettings settings = getDialogSettings();
        settings.put(ArgumentsWizard.class.getName(), mode.getModeType());
    }

    public void relocateHelp(boolean create)
    {
        if (argumentSet.getQueryDescriptor().isHelpAvailable() && //
                        (create || (helpPopup != null && helpPopup.getShell() != null)))
        {            
            if (getShell()==null)
            {
                if (helpPopup != null)
                    helpPopup.close();
                return;
            }
            getShell().getDisplay().timerExec(100, new Runnable()
            {
                public void run()
                {
                    if (getShell() != null && !getShell().isDisposed())
                    {
                        Rectangle myBounds = getShell().getBounds();
                        Rectangle helpBounds = new Rectangle(myBounds.x, myBounds.y + myBounds.height, myBounds.width,
                                        SWT.DEFAULT);
    
                        if (helpPopup != null && helpPopup.getShell() != null)
                        {
                            helpPopup.resize(helpBounds);
                            return;
                        }
    
                        helpPopup = new QueryContextHelp(getShell(), argumentSet.getQueryDescriptor(), helpBounds);
                        helpPopup.open();
                    }
                }
            });
        }
    }

    //@Override
    public void performHelp()
    {
        String helpUrl = argumentSet.getQueryDescriptor().getHelpUrl();
        if (helpUrl != null)
        {
             PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(helpUrl);
        }
        relocateHelp(true);
        PlatformUI.getWorkbench().getHelpSystem().displayHelp("org.eclipse.mat.ui.help.query_arguments"); //$NON-NLS-1$
    }

}

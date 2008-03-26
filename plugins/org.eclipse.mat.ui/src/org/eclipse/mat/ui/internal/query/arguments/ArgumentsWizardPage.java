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
package org.eclipse.mat.ui.internal.query.arguments;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mat.impl.query.ArgumentSet;
import org.eclipse.mat.ui.internal.query.arguments.LinkEditor.Mode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.HelpEvent;
import org.eclipse.swt.events.HelpListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;


public class ArgumentsWizardPage extends WizardPage implements ArgumentsTable.ITableListener
{

    private ArgumentSet argumentSet;
    private ArgumentsTable table;

    public ArgumentsWizardPage(ArgumentSet argumentSet)
    {
        super("Query Arguments", argumentSet.getQueryDescriptor().getName(), null);

        setDescription(argumentSet.getQueryDescriptor().getShortDescription());
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
        table = new ArgumentsTable(tableComposite, SWT.FULL_SELECTION | SWT.SINGLE, argumentSet, mode);
        table.addListener(this);

        tableComposite.layout();
        tableComposite.pack();

        PlatformUI.getWorkbench().getHelpSystem().setHelp(getShell(), "org.eclipse.mat.ui.query.arguments");
        composite.setContent(tableComposite);
        composite.addHelpListener(new HelpListener()
        {
            public void helpRequested(HelpEvent e)
            {
                PlatformUI
                                .getWorkbench()
                                .getHelpSystem()
                                .displayHelpResource(
                                                "/org.eclipse.mat.ui.help/html/46/9f9d1c446952b8e10000000a155369/content.htm#argumentWizard");
            }
        });

        setControl(composite);
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
        getContainer().updateButtons();
    }

    public void onModeChange(Mode mode)
    {
        IDialogSettings settings = getDialogSettings();
        settings.put(ArgumentsWizard.class.getName(), mode.getModeType());
    }

}

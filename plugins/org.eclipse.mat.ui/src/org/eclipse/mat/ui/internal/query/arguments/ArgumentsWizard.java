/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
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
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.internal.browser.QueryContextHelp;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

public class ArgumentsWizard extends Wizard
{
    private IQueryContext context;
    private ArgumentSet argumentSet;
    private QueryContextHelp helpPopup;

    public ArgumentsWizard(IQueryContext context, ArgumentSet argumentSet)
    {
        this.context = context;
        this.argumentSet = argumentSet;

        setWindowTitle(queryFullName(argumentSet.getQueryDescriptor()));
        setForcePreviousAndNextButtons(false);
        setDefaultPageImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.ARGUMENTS_WIZARD));
    }

    private static String queryFullName(QueryDescriptor query)
    {
        String name = query.getName();
        String category = query.getCategory();
        if (!Category.HIDDEN.equals(category) && category != null)
        {
            String categoryName = QueryRegistry.instance().getRootCategory().resolve(category).getFullName();
            if (categoryName != null)
            {
                name = categoryName + " / " + name; //$NON-NLS-1$
            }
        }
        return name;
    }

    @Override
    public void createPageControls(Composite pageContainer)
    {
        super.createPageControls(pageContainer);

        Listener listener = new Listener()
        {
            public void handleEvent(Event event)
            {
                relocateHelp(false);
            }
        };
        getShell().addListener(SWT.Resize, listener);
        getShell().addListener(SWT.Move, listener);
    }

    @Override
    public IDialogSettings getDialogSettings()
    {
        IDialogSettings workbenchDialogSettings = MemoryAnalyserPlugin.getDefault().getDialogSettings();
        IDialogSettings result = workbenchDialogSettings.getSection(ArgumentsWizard.class.getName());
        if (result == null)
            result = workbenchDialogSettings.addNewSection(ArgumentsWizard.class.getName());
        return result;
    }

    @Override
    public void addPages()
    {
        addPage(new ArgumentsWizardPage(context, argumentSet));
        relocateHelp(true);
    }

    public void relocateHelp(boolean create)
    {
        if (argumentSet.getQueryDescriptor().isHelpAvailable() && //
                        (create || (helpPopup != null && helpPopup.getShell() != null)))
        {            
            if (getShell()==null)
            {
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

                        if (helpPopup != null)
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

    @Override
    public boolean performFinish()
    {
        return true;
    }

}

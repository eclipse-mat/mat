/*******************************************************************************
 * Copyright (c) 2011,2012 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.query.arguments.ArgumentsWizardPage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class UIPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage
{

    public UIPreferencePage()
    {
        super(GRID);
        setPreferenceStore(MemoryAnalyserPlugin.getDefault().getPreferenceStore());
        setDescription(Messages.UIPreferencePage_PreferencesSubtitle);
    }

    /**
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    public void createFieldEditors()
    {
        addField(new BooleanFieldEditor(PreferenceConstants.P_KEEP_UNREACHABLE_OBJECTS, Messages.UIPreferencePage_KeepUnreachableObjects,
                        getFieldEditorParent()));
        addField(new BooleanFieldEditor(GettingStartedWizard.HIDE_WIZARD_KEY, Messages.UIPreferencePage_HideGettingStartedWizard,
                        getFieldEditorParent()));
        addField(new BooleanFieldEditor(ArgumentsWizardPage.HIDE_QUERY_HELP, Messages.UIPreferencePage_HideQueryHelp,
                        getFieldEditorParent()));
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench)
    {}

}

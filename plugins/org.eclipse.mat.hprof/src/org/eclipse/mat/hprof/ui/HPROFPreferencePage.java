/*******************************************************************************
 * Copyright (c) 2011,2019 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof.ui;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.mat.hprof.HprofPlugin;
import org.eclipse.mat.hprof.Messages;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing { link org.eclipse.jface.preference.FieldEditorPreferencePage}, we can use the
 * field support built into JFace that allows us to create a page that is small
 * and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class HPROFPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage
{
    /**
     * Create the preference page.
     */
    public HPROFPreferencePage()
    {
        super(GRID);
        setPreferenceStore((IPreferenceStore)HprofPlugin.getDefault().getPreferenceStore());
        setDescription(Messages.HPROFPreferences_Description);
    }

    /**
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    public void createFieldEditors()
    {
        // Create a choice for the parser strictness
        addField(new RadioGroupFieldEditor(
                        HprofPreferences.STRICTNESS_PREF,
                        Messages.HPROFPreferences_Strictness,
                        1,
                        new String[][] {
                                        { Messages.HPROFPreferences_Strictness_Stop,
                                                        HprofPreferences.HprofStrictness.STRICTNESS_STOP.toString() },
                                        {
                                                        Messages.HPROFPreferences_Strictness_Warning,
                                                        HprofPreferences.HprofStrictness.STRICTNESS_WARNING
                                                                        .toString() },
                        // Don't show the permissive option until we actually do
                        // something interesting with it
                        // {
                        // Messages.HPROFPreferences_Strictness_Permissive,
                        // PreferenceConstants.HprofStrictness.STRICTNESS_PERMISSIVE
                        // .toString() }
                        }, getFieldEditorParent(), true));
        addField(new BooleanFieldEditor(HprofPreferences.ADDITIONAL_CLASS_REFERENCES, Messages.HPROFPreferences_Additional_Class_References,
                        getFieldEditorParent()));
    }

    /**
     * No-op
     */
    public void init(IWorkbench workbench)
    {}

    @Override
    protected Control createContents(Composite parent) {
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "org.eclipse.mat.ui.help.preferences_hprof_assist"); //$NON-NLS-1$
        return super.createContents(parent);
    }
}

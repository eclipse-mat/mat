/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
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

public class DTFJPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage
{

    public DTFJPreferencePage()
    {
        super(GRID);
        setPreferenceStore(InitDTFJ.getDefault().getPreferenceStore());
        setDescription(Messages.DTFJPreferencePage_Description);
    }

    /**
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    public void createFieldEditors()
    {
        addField(new RadioGroupFieldEditor(
                        PreferenceConstants.P_METHODS,
                        Messages.DTFJPreferencePage_MethodsAsClasses,
                        1,
                        new String[][] {
                                        { Messages.DTFJPreferencePage_NoMethods, PreferenceConstants.NO_METHODS_AS_CLASSES },
                                        { Messages.DTFJPreferencePage_RunningMethods, PreferenceConstants.RUNNING_METHODS_AS_CLASSES },
                                        { Messages.DTFJPreferencePage_AllMethods, PreferenceConstants.ALL_METHODS_AS_CLASSES } },
                        getFieldEditorParent(), true));
        final StringFieldEditor editor = new StringFieldEditor(PreferenceConstants.P_RUNTIMEID, Messages.DTFJPreferencePage_RuntimeID, 8, getFieldEditorParent());
        addField(editor);
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench)
    {}

}

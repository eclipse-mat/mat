/*******************************************************************************
 * Copyright (c) 2011,2019 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PathEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.mat.dtfj.bridge.api.DTFJBridgeConnector;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing {@link org.eclipse.jface.preference.FieldEditorPreferencePage}, we
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
        setPreferenceStore((IPreferenceStore)InitDTFJ.getDefault().getPreferenceStore());
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
                                        { Messages.DTFJPreferencePage_OnlyStackFrames, PreferenceConstants.FRAMES_ONLY },
                                        { Messages.DTFJPreferencePage_RunningMethods, PreferenceConstants.RUNNING_METHODS_AS_CLASSES },
                                        { Messages.DTFJPreferencePage_AllMethods, PreferenceConstants.ALL_METHODS_AS_CLASSES } },
                        getFieldEditorParent(), true));
        addField(new BooleanFieldEditor(PreferenceConstants.P_SUPPRESS_CLASS_NATIVE_SIZES, Messages.DTFJPreferencePage_SuppressClassNativeSizes,
                        getFieldEditorParent()));
        
        addField(new PathEditor(DTFJBridgeConnector.DTFJ_PARENT_DIRECTORIES, Messages.DTFJPreferencePage_DTFJLocations, null, getFieldEditorParent()));
        addField(new BooleanFieldEditor(DTFJBridgeConnector.DTFJ_SKIP_PLUGIN, Messages.DTFJPreferencePage_DTFJSkipPlugin,
                        getFieldEditorParent()));
   }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench)
    {}

    @Override
    protected Control createContents(Composite parent) {
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "org.eclipse.mat.ui.help.preferences_dtfj_assist"); //$NON-NLS-1$
        return super.createContents(parent);
    }
}

/*******************************************************************************
 * Copyright (c) 2011,2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof.ui;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.mat.hprof.HprofPlugin;
import org.eclipse.mat.hprof.Messages;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
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
     * Edit a String which will be used as a RegEx pattern.
     */
    private static final class PatternFieldEditor extends StringFieldEditor
    {
        private PatternFieldEditor(String name, String labelText, Composite parent)
        {
            super(name, labelText, parent);
        }

        public boolean doCheckState() {
            try {
                Pattern.compile(getStringValue());
            } catch (PatternSyntaxException e) {
                setErrorMessage(e.getDescription());
                return false;
            }
            return true;
        }
    }

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
        
        /* Discard options */
        BooleanFieldEditor enable = new BooleanFieldEditor(HprofPreferences.DISCARD_ENABLE, 
                        Messages.HPROFPreferencePage_EnableDiscard,
                        getFieldEditorParent());
        addField(enable);
        IntegerFieldEditor discardRatio = new IntegerFieldEditor(HprofPreferences.DISCARD_RATIO, Messages.HPROFPreferencePage_DiscardPercentage,
                        getFieldEditorParent());
        discardRatio.setValidRange(0, 100);
        addField(discardRatio);
        StringFieldEditor discardPattern = new PatternFieldEditor(HprofPreferences.DISCARD_PATTERN, Messages.HPROFPreferencePage_DiscardPattern, getFieldEditorParent());
        addField(discardPattern);
        IntegerFieldEditor discardOffset = new IntegerFieldEditor(HprofPreferences.DISCARD_OFFSET, Messages.HPROFPreferencePage_DiscardOffset,
                        getFieldEditorParent());
        discardOffset.setValidRange(0, 99);
        addField(discardOffset);
        IntegerFieldEditor discardSeed = new IntegerFieldEditor(HprofPreferences.DISCARD_SEED, Messages.HPROFPreferencePage_DiscardSeed,
                        getFieldEditorParent());
        discardSeed.setValidRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        addField(discardSeed);
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

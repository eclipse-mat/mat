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
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.mat.query.BytesDisplay;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.query.arguments.ArgumentsWizardPage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing { link org.eclipse.jface.preference.FieldEditorPreferencePage}, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class UIPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage
{
    private String lastBytesDisplay;

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
        addField(new BooleanFieldEditor(PreferenceConstants.P_HIDE_WELCOME_SCREEN, Messages.UIPreferencePage_HideWelcomeScreen,
                        getFieldEditorParent()));
        addField(new RadioGroupFieldEditor(BytesDisplay.PROPERTY_NAME, Messages.UIPreferencePage_BytesDisplay, 1,
                    new String[][] {
                      { Messages.UIPreferencePage_BytesDisplay_Bytes, BytesDisplay.Bytes.toString() },
                      { Messages.UIPreferencePage_BytesDisplay_Kilobytes, BytesDisplay.Kilobytes.toString() },
                      { Messages.UIPreferencePage_BytesDisplay_Megabytes, BytesDisplay.Megabytes.toString() },
                      { Messages.UIPreferencePage_BytesDisplay_Gigabytes, BytesDisplay.Gigabytes.toString() },
                      { Messages.UIPreferencePage_BytesDisplay_Smart, BytesDisplay.Smart.toString() },
                    }, getFieldEditorParent(), true));
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench)
    {}

    @Override
    public boolean performOk()
    {
        // Asking the preference store here will still give the old value, so 
        // we track value changes and use the latest value.
        if (lastBytesDisplay != null)
        {
            BytesDisplay.setCurrentValue(BytesDisplay.parse(lastBytesDisplay));
        }
        return super.performOk();
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent event)
    {
        super.propertyChange(event);
        Object source = event.getSource();
        if (source instanceof RadioGroupFieldEditor)
        {
            RadioGroupFieldEditor rgfe = (RadioGroupFieldEditor) source;
            if (rgfe.getPreferenceName().equals(BytesDisplay.PROPERTY_NAME))
            {
                lastBytesDisplay = event.getNewValue().toString();
            }
        }
    }
}

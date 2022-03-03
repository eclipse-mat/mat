/*******************************************************************************
 * Copyright (c) 2011,2022 IBM Corporation.
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
package org.eclipse.mat.dtfj;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.ListEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.mat.dtfj.InitDTFJ.DynamicInfo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.List;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

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
        addField(new RadioGroupFieldEditor(
                        PreferenceConstants.P_RELIABILITY_CHECK,
                        Messages.DTFJPreferencePage_ReliabilityCheck,
                        1,
                        new String[][] {
                                        { Messages.DTFJPreferencePage_ReliabilityCheck_Fatal, PreferenceConstants.RELIABILITY_FATAL },
                                        { Messages.DTFJPreferencePage_ReliabilityCheck_Warning, PreferenceConstants.RELIABILITY_WARNING },
                                        { Messages.DTFJPreferencePage_ReliabilityCheck_Skip, PreferenceConstants.RELIABILITY_SKIP } },
                        getFieldEditorParent(), true));
        addField(new BooleanFieldEditor(PreferenceConstants.P_SUPPRESS_CLASS_NATIVE_SIZES, Messages.DTFJPreferencePage_SuppressClassNativeSizes,
                        getFieldEditorParent()));
        addField(new ListEditor(PreferenceConstants.INCOMPATIBLE_DTFJ_VERSIONS, Messages.DTFJPreferencePage_IncompatibleDTFJVersionsLabel, getFieldEditorParent()) {

            List l;
            static final String defaultPattern = "JRE 1[0-9].*"; //$NON-NLS-1$

            @Override
            protected String createList(String[] items)
            {
                return String.join(DTFJIndexBuilder.DTFJ_PATTERN_SEPARATOR, items);
            }

            @Override
            public List getListControl(Composite parent)
            {
                l = super.getListControl(parent);
                return l;
            }

            @Override
            protected String getNewInputObject()
            {
                // Use first selected item as an initial value
                String sel[] = l.getSelection();
                if (sel.length == 0)
                {
                    /*
                     * No selection so allow the user to choose from an
                     * implementation
                     */
                    java.util.List<String>l2 = new ArrayList<String>();
                    DynamicInfo di = new DynamicInfo();
                    for (Map<String, String>m : di.values())
                    {
                        l2.add(m.get("id")); //$NON-NLS-1$
                    }
                    ElementListSelectionDialog esd = new ElementListSelectionDialog(getShell(), new LabelProvider());
                    esd.setTitle(Messages.DTFJPreferencePage_ChooseDTFJImplementation);
                    esd.setMessage(Messages.DTFJPreferencePage_ChooseDTFJImplementationMsg);
                    esd.setElements(l2.toArray());
                    if (esd.open() == Window.OK)
                    {
                        Object r = esd.getFirstResult();
                        if (r != null)
                        {
                            sel = new String[] {r.toString() + ":" + defaultPattern}; //$NON-NLS-1$
                        }
                    }
                    else
                    {
                        return null;
                    }
                }
                InputDialog id = new InputDialog(getShell(), Messages.DTFJPreferencePage_IncompatibleDTFJVersionsTitle, Messages.DTFJPreferencePage_IncompatibleDTFJVersionsInput, sel.length == 0 ? "" : sel[0], new IInputValidator() { //$NON-NLS-1$

                    public String isValid(String newText)
                    {
                        try
                        {
                            Pattern.compile(newText);
                        }
                        catch (PatternSyntaxException e)
                        {
                            return e.getDescription();
                        }
                        return null;
                    }
                });

                if (id.open() == Window.OK)
                {
                    return id.getValue();
                }
                else
                {
                    return null;
                }
            }

            @Override
            protected String[] parseString(String stringList)
            {
                return stringList.split(DTFJIndexBuilder.DTFJ_PATTERN_SEPARATOR);
            }

        });
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench)
    {}

    @Override
    protected Control createContents(Composite parent)
    {
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "org.eclipse.mat.ui.help.preferences_dtfj_assist"); //$NON-NLS-1$
        return super.createContents(parent);
    }
}

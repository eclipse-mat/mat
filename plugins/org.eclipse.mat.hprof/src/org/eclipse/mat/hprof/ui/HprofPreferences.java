/*******************************************************************************
 * Copyright (c) 2011, 2023 IBM Corporation.
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

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.hprof.HprofPlugin;

/**
 * Constant definitions for plug-in preferences
 */
public class HprofPreferences
{
    /** Strictness of the HPROF parser */
    public static final String STRICTNESS_PREF = "hprofStrictness"; //$NON-NLS-1$

    /** Default strictness for preferences and value parsing */
    public static final HprofStrictness DEFAULT_STRICTNESS = HprofStrictness.STRICTNESS_STOP;

    /** Additional references for classes */
    public static final String ADDITIONAL_CLASS_REFERENCES = "hprofAddClassRefs"; //$NON-NLS-1$

    /** Whether to treat stack frames as psuedo-objects and methods as pseudo-classes */
    public static final String P_METHODS = "methodsAsClasses"; //$NON-NLS-1$
    public static final String NO_METHODS_AS_CLASSES = "none"; //$NON-NLS-1$
    public static final String RUNNING_METHODS_AS_CLASSES = "running"; //$NON-NLS-1$
    public static final String FRAMES_ONLY = "frames"; //$NON-NLS-1$

    /**
     * Return the currently selected preference for strictness. This first
     * checks the preference store, and then checks for any -D$(STRICTNESS)=true
     * command line arguments.
     * 
     * @return Current strictness preference or reflection of command line
     *         setting.
     */
    public static HprofStrictness getCurrentStrictness()
    {
        HprofPreferences.HprofStrictness strictnessPreference = HprofPreferences.HprofStrictness.parse(Platform
                        .getPreferencesService().getString(HprofPlugin.getDefault().getBundle().getSymbolicName(),
                                        HprofPreferences.STRICTNESS_PREF, "", null)); //$NON-NLS-1$

        // Check if the user overrides on the command line
        for (HprofStrictness strictness : HprofStrictness.values())
        {
            if (Boolean.getBoolean(strictness.toString()))
            {
                strictnessPreference = strictness;
                break;
            }
        }

        return strictnessPreference;
    }

    /**
     * Enumeration for the parser strictness.
     */
    public enum HprofStrictness
    {
        /**
         * Throw an error and stop processing the dump.
         */
        STRICTNESS_STOP("hprofStrictnessStop"), //$NON-NLS-1$

        /**
         * Raise a warning and continue.
         */
        STRICTNESS_WARNING("hprofStrictnessWarning"), //$NON-NLS-1$

        /**
         * Raise a warning and try to "fix" the dump.
         */
        STRICTNESS_PERMISSIVE("hprofStrictnessPermissive"); //$NON-NLS-1$

        private final String name;

        /**
         * Enumeration value with a preference key.
         * 
         * @param name
         *            The preference key.
         */
        HprofStrictness(String name)
        {
            this.name = name;
        }

        /**
         * Return the preference key.
         */
        @Override
        public String toString()
        {
            return name;
        }

        /**
         * Given a stored preference value, return the enumeration value, or
         * otherwise the default strictness.
         * 
         * @param value
         *            The preference value.
         * @return Given a stored preference value, return the enumeration
         *         value, or otherwise the default strictness.
         */
        public static HprofStrictness parse(String value)
        {
            if (value != null && value.length() > 0)
            {
                for (HprofStrictness strictness : values())
                {
                    if (strictness.toString().equals(value)) { return strictness; }
                }
            }
            return DEFAULT_STRICTNESS;
        }
    }

    public static boolean useAdditionalClassReferences()
    {
        return Platform.getPreferencesService().getBoolean(HprofPlugin.getDefault().getBundle().getSymbolicName(),
                        HprofPreferences.ADDITIONAL_CLASS_REFERENCES, false, null);
    }

    /**
     * Preference for whether pseudo-objects should be created
     * for stack frames and methods.
     */
    public static String methodsAsClasses()
    {
        String pref = Platform.getPreferencesService().getString(HprofPlugin.getDefault().getBundle().getSymbolicName(),
                        HprofPreferences.P_METHODS, HprofPreferences.NO_METHODS_AS_CLASSES, null);
        return pref;
    }
}

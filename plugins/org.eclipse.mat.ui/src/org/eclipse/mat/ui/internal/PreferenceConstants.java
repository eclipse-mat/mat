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
package org.eclipse.mat.ui.internal;

/**
 * Constant definitions for plug-in preferences
 */
public class PreferenceConstants {
    public static final String P_KEEP_UNREACHABLE_OBJECTS = "keep_unreachable_objects"; //$NON-NLS-1$
    public static final String P_HIDE_WELCOME_SCREEN = "hide_welcome_screen"; //$NON-NLS-1$

    /** Whether to discard some objects when parsing */
    public static final String DISCARD_ENABLE = "discardEnable"; //$NON-NLS-1$

    /** How often to discard objects when parsing */
    public static final String DISCARD_RATIO = "discard_ratio_percentage"; //$NON-NLS-1$

    /** The types of object to discard */
    public static final String DISCARD_PATTERN = "discard_pattern"; //$NON-NLS-1$

    /** How often to discard objects when parsing */
    public static final String DISCARD_SEED = "discard_seed"; //$NON-NLS-1$

    /** How often to discard objects when parsing */
    public static final String DISCARD_OFFSET = "discard_offset_percentage"; //$NON-NLS-1$

    /** Expand lines for UI */
    public static final String EXPAND_ENTRIES = "expand_entries"; //$NON-NLS-1$
}

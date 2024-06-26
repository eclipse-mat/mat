/*******************************************************************************
 * Copyright (c) 2011,2023 IBM Corporation.
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

/**
 * Constant definitions for plug-in preferences
 */
public class PreferenceConstants
{
	/** Whether to treat stack frames as pseudo-objects and methods as pseudo-classes */
    public static final String P_METHODS = "methodsAsClasses"; //$NON-NLS-1$
    public static final String P_SUPPRESS_CLASS_NATIVE_SIZES = "suppressClassNativeSizes"; //$NON-NLS-1$
    public static final String P_RELIABILITY_CHECK = "reliabilityCheck"; //$NON-NLS-1$

    public static final String NO_METHODS_AS_CLASSES = "none"; //$NON-NLS-1$
    public static final String RUNNING_METHODS_AS_CLASSES = "running"; //$NON-NLS-1$
    public static final String ALL_METHODS_AS_CLASSES = "all"; //$NON-NLS-1$
    public static final String FRAMES_ONLY = "frames"; //$NON-NLS-1$
    
    public static final String RELIABILITY_FATAL = "fatal"; //$NON-NLS-1$
    public static final String RELIABILITY_WARNING = "warning"; //$NON-NLS-1$
    public static final String RELIABILITY_SKIP = "skip"; //$NON-NLS-1$
}

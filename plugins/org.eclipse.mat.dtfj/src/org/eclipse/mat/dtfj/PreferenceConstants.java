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

/**
 * Constant definitions for plug-in preferences
 */
public class PreferenceConstants
{
	/** Whether to treat stack frames as psuedo-objects and methods as pseudo-classes */
    public static final String P_METHODS = "methodsAsClasses"; //$NON-NLS-1$
    public static final String NO_METHODS_AS_CLASSES = "none"; //$NON-NLS-1$
    public static final String RUNNING_METHODS_AS_CLASSES = "running"; //$NON-NLS-1$
    public static final String ALL_METHODS_AS_CLASSES = "all"; //$NON-NLS-1$
    public static final String FRAMES_ONLY = "frames"; //$NON-NLS-1$

    /** Runtime id for use when a dump contains more than one Java runtime */
    public static final String P_RUNTIMEID = "runtimeId"; //$NON-NLS-1$
}

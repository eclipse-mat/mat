/*******************************************************************************
 * Copyright (c) 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.jdt;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.jdt.messages"; //$NON-NLS-1$
    public static String OpenSourceFileJob_LookingFor;
    public static String OpenSourceFileJob_NotFound;
    public static String OpenSourceFileJob_SelectFile;
    public static String OpenSourceFileJob_SelectFileToOpen;
    public static String OpenSourceFileJob_TypeNotFound;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

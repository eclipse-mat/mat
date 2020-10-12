/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj.bridge;

import org.eclipse.osgi.util.NLS;

/**
 * Messages for the DTFJ bridge.
 */
public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.dtfj.bridge.messages"; //$NON-NLS-1$

    public static String CustomClassLoader_nodir;
    public static String CustomClassLoader_addedjar;
    public static String CustomClassLoader_searching_install;
    public static String CustomClassLoader_searching_jvm;
    public static String CustomClassLoader_searching_jvm_skipdtfj;
    public static String CustomClassLoader_deferring_jvm;
    public static String CustomClassLoader_deferring_jvm_skipdtfj;
    public static String CustomClassLoader_error_searching;
    public static String CustomClassLoader_error_searching_multiple;
    public static String CustomClassLoader_error_resolving_platform;

    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

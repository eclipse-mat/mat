/*******************************************************************************
 * Copyright (c) 2022, 2022 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.diagnostics.actions;

import java.lang.reflect.Method;

import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsAction;

/**
 * Base class for interacting with the J9 JVM
 */
public abstract class J9JVMBase implements DiagnosticsAction
{
    protected Class<?> j9Dump;
    protected Method triggerDump;

    public J9JVMBase()
    {
        try
        {
            j9Dump = Class.forName("com.ibm.jvm.Dump"); //$NON-NLS-1$
            triggerDump = j9Dump.getMethod("triggerDump", new Class<?>[] { String.class }); //$NON-NLS-1$
        }
        catch (ClassNotFoundException | NoSuchMethodException | SecurityException e)
        {
            // We expect the caller will have already checked for the existence
            // of the above objects, so this shouldn't happen
            e.printStackTrace();
        }
    }
}

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

import java.lang.reflect.InvocationTargetException;

import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsProgress;
import org.eclipse.mat.util.MessageUtil;

/**
 * Request a heap dump on a J9 JVM
 */
public class J9JVMHeapDump extends J9JVMBase
{
    @Override
    public void run(DiagnosticsProgress progress)
    {
        try
        {
            String fileName = (String) triggerDump.invoke(null, new Object[] { "heap:request=exclusive+prepwalk" }); //$NON-NLS-1$
            progress.appendText(MessageUtil.format(Messages.DiagnosticsAction_J9JVMDump_Completed, fileName));
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
    }
}

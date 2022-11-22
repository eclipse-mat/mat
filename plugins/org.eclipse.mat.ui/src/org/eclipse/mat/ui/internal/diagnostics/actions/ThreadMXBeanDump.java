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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsAction;
import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsProgress;

/**
 * Get all current thread stacks
 */
public class ThreadMXBeanDump implements DiagnosticsAction
{
    @Override
    public void run(DiagnosticsProgress progress)
    {
        ThreadMXBean threadMxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadMxbean.dumpAllThreads(true, true);
        for (ThreadInfo thread : threads)
        {
            progress.appendText(thread.toString());
        }
    }
}

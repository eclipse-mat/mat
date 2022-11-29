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

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsAction;
import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsProgress;
import org.eclipse.mat.util.MessageUtil;

/**
 * Request a heap dump on a HotSpot JVM
 */
public class HotSpotJVMHeapDump implements DiagnosticsAction
{
    @Override
    public void run(DiagnosticsProgress progress)
    {
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss"); //$NON-NLS-1$
            String fileName = "matdump_" + sdf.format(new Date()) + ".hprof"; //$NON-NLS-1$ //$NON-NLS-2$
            Class<?> dumpcls = Class.forName("com.sun.management.HotSpotDiagnosticMXBean"); //$NON-NLS-1$
            Object beanProxy = ManagementFactory.newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(),
                            "com.sun.management:type=HotSpotDiagnostic", dumpcls); //$NON-NLS-1$
            Method m = dumpcls.getMethod("dumpHeap", String.class, boolean.class); //$NON-NLS-1$
            m.invoke(beanProxy, fileName, false);
            progress.appendText(MessageUtil.format(Messages.DiagnosticsAction_Dump_Completed,
                            new File(fileName).getAbsolutePath()));
        }
        catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                        | NoSuchMethodException | SecurityException | IOException e)
        {
            progress.handleException(e);
        }
    }
}

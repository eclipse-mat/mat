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

import java.util.Date;

import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsAction;
import org.eclipse.mat.ui.internal.diagnostics.DiagnosticsProgress;

/**
 * Sleep for some time. Useful to test the wizard's cancellation.
 */
public class Sleep implements DiagnosticsAction
{
    @Override
    public void run(DiagnosticsProgress progress)
    {
        progress.appendText("Started at " + new Date());
        try
        {
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        progress.appendText("Finished at " + new Date());
    }
}

/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation.
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
package org.eclipse.mat.ui.snapshot;

import org.eclipse.mat.snapshot.MultipleSnapshotsException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

public class RuntimeSelector implements Runnable
{
    private String selectedId;
    private Display display;
    private MultipleSnapshotsException mre;

    RuntimeSelector(MultipleSnapshotsException mre, Display display)
    {
        this.mre = mre;
        this.display = display;
    }

    public void run()
    {
        RuntimeListDialog runtimeSelector = new RuntimeListDialog(PlatformUI.getWorkbench().getDisplay()
                        .getActiveShell(), mre);
        runtimeSelector.open();
        Object[] result = runtimeSelector.getResult();
        if (result != null)
        {
            selectedId = ((MultipleSnapshotsException.Context) result[0]).getRuntimeId();
        }
    }

    String getSelectedRuntimeId()
    {
        if (selectedId == null)
        {
            display.syncExec(this);
        }
        return selectedId;
    }

}

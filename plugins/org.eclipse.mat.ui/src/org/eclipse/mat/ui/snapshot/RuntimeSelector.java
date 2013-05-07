/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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

/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.regression;

import java.io.File;

public class CleanAllApplication
{
    private File dumpDir;

    public CleanAllApplication(File dumpDir)
    {
        this.dumpDir = dumpDir;
    }

    public void run() throws Exception
    {
        remove(dumpDir);
    }

    private void remove(File dir)
    {
        File[] filesToRemove = dir.listFiles(RegTestUtils.cleanupFilter);
        for (File file : filesToRemove)
        {
            if (file.isDirectory())
            {
                remove(file);
                if (file.listFiles().length == 0)
                    RegTestUtils.removeFile(file);
            }
            else
            {
                RegTestUtils.removeFile(file);
            }
        }
    }

}

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
    private String[] args;   
    
    public CleanAllApplication(String[] args)
    {
        this.args = args;
    }

    public void run() throws Exception
    {        
        File dumpDir = new File(args[0]);
        if (!dumpDir.isDirectory())
        {
            System.err.println("Please provide a directory");
            return;
        }

        File[] indexes = dumpDir.listFiles(RegTestUtils.cleanupFilter);
        for (File indexFile : indexes)
        {
            if (indexFile.isDirectory())
            {
                for (File file : indexFile.listFiles(RegTestUtils.cleanupFilter))
                {
                    RegTestUtils.removeFile(file);
                }
            }
            RegTestUtils.removeFile(indexFile);
        }

    }

   

}

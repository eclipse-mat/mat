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
import java.util.List;

public class ForceNewBaselineApplication
{
    private String[] args;
    
    public ForceNewBaselineApplication(String[] args)
    {
        this.args = args;
    }

    public void run()
    {
        File dumpDir = new File(args[0]);
        if (!dumpDir.isDirectory())
        {
            System.err.println("Please provide a directory");
            return;
        }

        List<File> dumps = Utils.collectDumps(dumpDir);
        for (File dump : dumps)
        {
            // delete old baseline
            File baselineFolder = new File(dump.getAbsolutePath() + Utils.BASELINE_EXTENSION);
            if (baselineFolder.exists())
            {
                for (File file : baselineFolder.listFiles())
                {
                    Utils.removeFile(file);

                }
                Utils.removeFile(baselineFolder);
            }
            else
            {
                System.err.println("WARNING> Heap dump " + dump.getName() + "has no baseline");
            }

            // rename test result folder into baseline folder
            File testFolder = new File(dump.getAbsolutePath() + Utils.TEST_EXTENSION);
            if (testFolder.exists() && testFolder.listFiles().length > 0)
            {
                // create new baseline folder
                File newBaselineFolder = new File(dump.getAbsolutePath() + Utils.BASELINE_EXTENSION);
                newBaselineFolder.mkdir();
                File[] baselineFiles = testFolder.listFiles();
                for (File baselineFile : baselineFiles)
                {
                    File newBaselineFile = new File(newBaselineFolder, baselineFile.getName());
                    boolean succeed = baselineFile.renameTo(newBaselineFile);
                    if (succeed)
                    {
                        System.out.println("OUTPUT> New baseline was created for heap dump: " + dump.getName()
                                        + " file: " + baselineFile.getName());
                    }
                    else
                    {
                        System.err.println("ERROR> Failed overriding the baseline for heap dump: " + dump.getName()
                                        + " file: " + baselineFile.getName());
                    }
                }
                testFolder.delete();

            }
            else
            {
                System.err.println("WARNING> Heap dump " + dump.getName() + " has no test results");
            }
        }

    }
}

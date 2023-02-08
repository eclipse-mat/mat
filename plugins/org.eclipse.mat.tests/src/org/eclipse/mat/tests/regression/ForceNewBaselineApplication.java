/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - check file return codes
 *******************************************************************************/
package org.eclipse.mat.tests.regression;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ForceNewBaselineApplication
{
    private File dumpDir;

    public ForceNewBaselineApplication(File dumpDir)
    {
        this.dumpDir = dumpDir;
    }

    public void run()
    {

        List<File> dumps = RegTestUtils.collectDumps(dumpDir, new ArrayList<File>());
        for (File dump : dumps)
        {
            // delete old baseline
            File baselineFolder = new File(dump.getAbsolutePath() + RegTestUtils.BASELINE_EXTENSION);
            if (baselineFolder.exists())
            {
                File[] listFiles = baselineFolder.listFiles();
                if (listFiles != null)
                {
                    for (File file : listFiles)
                    {
                        RegTestUtils.removeFile(file);

                    }
                }
                RegTestUtils.removeFile(baselineFolder);
            }
            else
            {
                System.err.println("Info: Heap dump " + dump.getName() + "has no baseline");
            }

            // rename test result folder into baseline folder
            File testFolder = new File(dump.getAbsolutePath() + RegTestUtils.TEST_EXTENSION);
            File[] baselineFiles;
            if (testFolder.exists() && (baselineFiles = testFolder.listFiles()) != null
                            && baselineFiles.length > 0)
            {
                // create new baseline folder
                File newBaselineFolder = new File(dump.getAbsolutePath() + RegTestUtils.BASELINE_EXTENSION);
                if (!newBaselineFolder.mkdir() && !newBaselineFolder.exists())
                {
                    System.err.println("ERROR: Unable to create new baseline folder " + newBaselineFolder);
                }
                for (File baselineFile : baselineFiles)
                {
                    File newBaselineFile = new File(newBaselineFolder, baselineFile.getName());
                    boolean succeed = baselineFile.renameTo(newBaselineFile);
                    if (succeed)
                    {
                        System.out.println("New baseline was created for heap dump: " + dump.getName() + " file: "
                                        + baselineFile.getName());
                    }
                    else
                    {
                        System.err.println("ERROR: Failed overriding the baseline for heap dump: " + dump.getName()
                                        + " file: " + baselineFile.getName());
                    }
                }
                if (!testFolder.delete())
                {
                    System.err.println("ERROR: Failed to delete test folder " + testFolder + " after creating new baseline");
                }

            }
            else
            {
                System.err.println("ERROR: Heap dump " + dump.getName() + " has no test results");
            }
        }

    }
}

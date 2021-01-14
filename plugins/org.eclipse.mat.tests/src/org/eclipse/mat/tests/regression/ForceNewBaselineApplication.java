/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
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
                for (File file : baselineFolder.listFiles())
                {
                    RegTestUtils.removeFile(file);

                }
                RegTestUtils.removeFile(baselineFolder);
            }
            else
            {
                System.err.println("Info: Heap dump " + dump.getName() + "has no baseline");
            }

            // rename test result folder into baseline folder
            File testFolder = new File(dump.getAbsolutePath() + RegTestUtils.TEST_EXTENSION);
            if (testFolder.exists() && testFolder.listFiles().length > 0)
            {
                // create new baseline folder
                File newBaselineFolder = new File(dump.getAbsolutePath() + RegTestUtils.BASELINE_EXTENSION);
                newBaselineFolder.mkdir();
                File[] baselineFiles = testFolder.listFiles();
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
                testFolder.delete();

            }
            else
            {
                System.err.println("ERROR: Heap dump " + dump.getName() + " has no test results");
            }
        }

    }
}

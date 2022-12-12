/*******************************************************************************
 * Copyright (c) 2008,2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - fix deprecated method
 *******************************************************************************/
package org.eclipse.mat.tests.regression.comparator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.tests.regression.Difference;
import org.eclipse.mat.util.MessageUtil;

public class CSVComparator implements IComparator
{

    public List<Difference> compare(File baseline, File testFile) throws Exception
    {
        String testName = baseline.getName().substring(0, baseline.getName().lastIndexOf("."));

        System.out.println(MessageUtil.format("Comparing: {0}", testName));

        List<Difference> differences = new ArrayList<Difference>();
        if (baseline.length() != testFile.length())
        {
            differences.add(new Difference("", "baseLine length: " + baseline.length(), "testFile length: "
                            + testFile.length()));
            System.err.println(MessageUtil.format("ERROR: ({0}) Files have different lengths", testName));
        }
        try (
            BufferedReader baselineReader = new BufferedReader(new FileReader(baseline.getAbsolutePath()), 1024);
            BufferedReader testFileReader = new BufferedReader(new FileReader(testFile.getAbsolutePath()), 1024);)
        {
            String baseLine;
            String testLine;
            int lineNumber = 1;
            while ((baseLine = baselineReader.readLine()) != null)
            {
                if (!(baseLine).equals(testLine = testFileReader.readLine()))
                {
                    differences.add(new Difference(String.valueOf(lineNumber), baseLine, testLine));
                }
                if (differences.size() >= 10) // add only first 10 differences
                    break;
                lineNumber = lineNumber + 1;
            }
            if (baseLine == null)
            {
                // Also log extra lines
                while ((testLine = testFileReader.readLine()) != null)
                {
                    differences.add(new Difference(String.valueOf(lineNumber), null, testLine));
                    if (differences.size() >= 10) // add only first 10 differences
                        break;
                    lineNumber = lineNumber + 1;
                }
            }
            if (!differences.isEmpty())
                System.err.println(MessageUtil.format("ERROR: ({0}) Differences detected", testName));

        }
        catch (IOException e)
        {
            System.err.println(MessageUtil.format("ERROR: ({0}) Error reading file {0}", testName, e.getMessage()));
        }
        return differences;
    }
}

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
package org.eclipse.mat.tests.regression.comparator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.tests.regression.Difference;

public class CSVComparator implements IComparator
{

    public List<Difference> compare(File baseline, File testFile) throws Exception
    {
        String testName = baseline.getName().substring(0, baseline.getName().lastIndexOf("."));

        System.out.println(MessageFormat.format("Comparing: {0}", testName));

        List<Difference> differences = new ArrayList<Difference>();
        if (baseline.length() < testFile.length())
        {
            differences.add(new Difference("", "baseLine length: " + baseline.length(), "testFile length: "
                            + testFile.length()));
            System.err.println(MessageFormat.format("ERROR: ({0}) Files have different lengths", testName));
            return differences;
        }
        BufferedReader baselineReader = null;
        BufferedReader testFileReader = null;
        try
        {
            baselineReader = new BufferedReader(new FileReader(baseline.getAbsolutePath()), 1024);
            testFileReader = new BufferedReader(new FileReader(testFile.getAbsolutePath()), 1024);

            String baseLine;
            String testLine;
            int lineNumber = 1;
            while ((baseLine = baselineReader.readLine()) != null)
            {
                if (!(baseLine).equals(testLine = testFileReader.readLine()))
                {
                    differences.add(new Difference(new Integer(lineNumber).toString(), baseLine, testLine));
                }
                if (differences.size() == 10) // add only first 10 differences
                    break;
                lineNumber = lineNumber + 1;
            }

            if (!differences.isEmpty())
                System.err.println(MessageFormat.format("ERROR: ({0}) Differences detected", testName));

        }
        catch (IOException e)
        {
            System.err.println(MessageFormat.format("ERROR: ({0}) Error reading file {0}", testName, e.getMessage()));
        }
        finally
        {
            try
            {
                if (testFileReader != null)
                    testFileReader.close();
                if (baselineReader != null)
                    baselineReader.close();
            }
            catch (IOException e)
            {
                System.err.println(MessageFormat.format("ERROR: ({0}) Error closing BufferedReader: {0}", //
                                testName, e.getMessage()));
            }
        }
        return differences;
    }
}

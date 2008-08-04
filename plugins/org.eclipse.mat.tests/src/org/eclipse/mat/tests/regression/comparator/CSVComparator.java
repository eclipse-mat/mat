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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.tests.regression.Difference;

public class CSVComparator implements IComparator
{

    public List<Difference> compare(File baseline, File testFile) throws Exception
    {
        System.out.println("OUTPUT> Task: comparing two result files for "
                        + baseline.getName().substring(0, baseline.getName().lastIndexOf(".")));
        List<Difference> differences = new ArrayList<Difference>();
        if (baseline.length() < testFile.length())
        {
            differences.add(new Difference("", "baseLine length: " + baseline.length(), "testFile length: "
                            + testFile.length()));
            System.err.println("FAILED> Files have different lengths");
            return differences;
        }
        BufferedReader baselineReader = new BufferedReader(new FileReader(baseline.getAbsolutePath()), 1024);
        BufferedReader testFileReader = new BufferedReader(new FileReader(testFile.getAbsolutePath()), 1024);
        try
        {
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
            if (differences.isEmpty())
                System.out.println("OK> Result files are identical");
            else
                System.err.println("FAILED> Differences to the baseline were found");

        }
        catch (IOException e)
        {            
            System.err.println("ERROR> Failed to read the file " + e);
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
                // $JL-EXC$
                System.err.println("ERROR> Failed to close the BufferReader: " + e);
            }
        }
        return differences;
    }

}

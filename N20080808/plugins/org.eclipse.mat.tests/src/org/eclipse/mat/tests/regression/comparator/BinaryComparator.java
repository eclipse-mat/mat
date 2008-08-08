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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.mat.tests.regression.Difference;

public class BinaryComparator implements IComparator
{
    private final static int BUFFSIZE = 1024;
    private byte baselineBuffer[] = new byte[BUFFSIZE];
    private byte testBuffer[] = new byte[BUFFSIZE];

    private final String FAILED_MESSAGE = "Test result differs from the baseline";

    public List<Difference> compare(File baseline, File testFile) throws Exception
    {
        List<Difference> differences = new ArrayList<Difference>();
        System.out.println("OUTPUT> Task: comparing two result files for binary Dominator Tree");
        InputStream baselineStream = null;
        InputStream testStream = null;
        if (baseline.length() != testFile.length())
        {
            String errorMessage = "Files have different lengths: baseline file length = " + baseline.length()
            + ", test file length = " + testFile.length();
            differences.add(new Difference(errorMessage));
            System.err.println("FAILED> "+errorMessage);
            return differences;
        }

        try
        {
            baselineStream = new FileInputStream(baseline);
            testStream = new FileInputStream(testFile);

            if (inputStreamEquals(baselineStream, testStream))
            {
                System.out.println("OK> Files are identical");
                return null;
            }
            else
            {
                differences.add(new Difference(FAILED_MESSAGE));
                System.err.println("FAILED> " + FAILED_MESSAGE);
                return differences;
            }

        }
        catch (Exception e)
        {
            System.err.println("FAILED> Failed to compare two binary files: " + baseline.getName());
            return null;
        }
        finally
        {
            try
            {
                if (baselineStream != null)
                    baselineStream.close();
                if (testStream != null)
                    testStream.close();
            }
            catch (Exception ex)
            {}
        }
    }

    private boolean inputStreamEquals(InputStream baselineStream, InputStream testStream)
    {
        if (baselineStream == testStream)
            return true;
        if (baselineStream == null && testStream == null)
            return true;
        if (baselineStream == null || testStream == null)
            return false;
        try
        {
            int readBaseline = -1;
            int readTest = -1;

            do
            {
                int baselineOffset = 0;
                while (baselineOffset < BUFFSIZE
                                && (readBaseline = baselineStream.read(baselineBuffer, baselineOffset, BUFFSIZE
                                                - baselineOffset)) >= 0)
                {
                    baselineOffset = baselineOffset + readBaseline;
                }

                int testOffset = 0;
                while (testOffset < BUFFSIZE
                                && (readTest = testStream.read(testBuffer, testOffset, BUFFSIZE - testOffset)) >= 0)
                {
                    testOffset = testOffset + readTest;
                }
                if (baselineOffset != testOffset)
                    return false;

                if (baselineOffset != BUFFSIZE)
                {
                    Arrays.fill(baselineBuffer, baselineOffset, BUFFSIZE, (byte) 0);
                    Arrays.fill(testBuffer, testOffset, BUFFSIZE, (byte) 0);
                }
                if (!Arrays.equals(baselineBuffer, testBuffer))
                    return false;
            }
            while (readBaseline >= 0 && readTest >= 0);

            if (readBaseline < 0 && readTest < 0)
                return true; // EOF for both

            return false;

        }
        catch (Exception e)
        {
            System.err.println("FAILED> Failed to compare two binary files");
            return false;
        }
    }

}

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
package org.eclipse.mat.tests.regression.comparator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.mat.tests.regression.Difference;
import org.eclipse.mat.util.MessageUtil;

public class BinaryComparator implements IComparator
{
    private final static int BUFFSIZE = 1024;
    private byte baselineBuffer[] = new byte[BUFFSIZE];
    private byte testBuffer[] = new byte[BUFFSIZE];

    private final String FAILED_MESSAGE = "Test result differs from the baseline";

    public List<Difference> compare(File baseline, File testFile) throws Exception
    {
        String testName = baseline.getName().substring(0, baseline.getName().lastIndexOf("."));
        System.out.println(MessageUtil.format("Comparing: {0}", testName));

        List<Difference> differences = new ArrayList<Difference>();

        InputStream baselineStream = null;
        InputStream testStream = null;
        if (baseline.length() != testFile.length())
        {
            String errorMessage = MessageUtil.format(
                            "Files have different lengths: baseline file length = {0}, test file length = {1}",
                            baseline.length(), testFile.length());
            differences.add(new Difference(errorMessage));
            System.err.println(MessageUtil.format("ERROR: ({0}) {1}", testName, errorMessage));
            return differences;
        }

        try
        {
            baselineStream = new FileInputStream(baseline);
            testStream = new FileInputStream(testFile);

            if (inputStreamEquals(testName, baselineStream, testStream))
            {
                return null;
            }
            else
            {
                differences.add(new Difference(FAILED_MESSAGE));
                System.err.println(MessageUtil.format("ERROR: ({0}) {1}", testName, FAILED_MESSAGE));
                return differences;
            }

        }
        catch (Exception e)
        {
            System.err.println(MessageUtil.format("ERROR: ({0}) Error comparing binary files: {0}", testName, e
                            .getMessage()));
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

    private boolean inputStreamEquals(String testName, InputStream baselineStream, InputStream testStream)
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
            System.err.println(MessageUtil.format("ERROR: ({0}) Error comparing binary files: {0}", testName, e
                            .getMessage()));
            return false;
        }
    }

}

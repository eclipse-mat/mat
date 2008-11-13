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
import java.util.ArrayList;
import java.util.List;

/*package*/class TestSuiteResult
{
    private File snapshot;
    private List<SingleTestResult> singleTestResult = new ArrayList<SingleTestResult>();
    private List<String> errorMessages = new ArrayList<String>();
    private List<PerfData> perfData = new ArrayList<PerfData>();

    public TestSuiteResult(File snapshot)
    {
        this.snapshot = snapshot;
    }

    public String getDumpName()
    {
        return snapshot.getName();
    }

    public File getSnapshot()
    {
        return snapshot;
    }

    public List<SingleTestResult> getTestData()
    {
        return singleTestResult;
    }

    public List<String> getErrorMessages()
    {
        return errorMessages;
    }

    public void addErrorMessage(String message)
    {
        errorMessages.add(message);
    }

    public void addTestData(SingleTestResult data)
    {
        singleTestResult.add(data);
    }

    public void addPerfData(PerfData data)
    {
        perfData.add(data);
    }

    public List<PerfData> getPerfData()
    {
        return perfData;
    }

    public boolean isSuccessful()
    {
        if (!errorMessages.isEmpty())
            return false;

        for (SingleTestResult result : singleTestResult)
        {
            if (!result.isSuccessful())
                return false;
        }

        return true;
    }
}

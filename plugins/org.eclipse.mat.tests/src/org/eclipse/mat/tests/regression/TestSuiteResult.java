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

import java.util.ArrayList;
import java.util.List;

/*package*/class TestSuiteResult
{
    String dumpName;   
    String parsingTime;   
    List<SingleTestResult> singleTestResult;
    List<String> errorMessages;

    public TestSuiteResult(String dumpName)
    {
        this.dumpName = dumpName;
        singleTestResult = new ArrayList<SingleTestResult>();
        errorMessages = new ArrayList<String>();
    }

    public String getDumpName()
    {
        return dumpName;
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

    public String getParsingTime()
    {
        return (parsingTime == null) ? "N/A" : parsingTime;
    }

    public void setParsingTime(String parsingTime)
    {
        this.parsingTime = parsingTime;
    }
}

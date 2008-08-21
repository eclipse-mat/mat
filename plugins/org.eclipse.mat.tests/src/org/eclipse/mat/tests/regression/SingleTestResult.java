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

/*package*/class SingleTestResult
{
    private String testName;
    private String result;
    private List<Difference> differences;

    public SingleTestResult(String testName, String result, List<Difference> differences)
    {
        this.testName = testName;
        this.result = result;
        this.differences = differences;
    }

    public String getTestName()
    {
        return testName;
    }

    public String getResult()
    {
        return result;
    }

    public List<Difference> getDifferences()
    {
        return differences == null ? new ArrayList<Difference>(0) : differences;
    }

}

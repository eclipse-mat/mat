/*******************************************************************************
 * Copyright (c) 2008,2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - Xmx and thread numbers
 *******************************************************************************/

package org.eclipse.mat.tests.regression;

/* package */class PerfData
{
    private String testName;
    private String time;
    private String usedMem;
    private String freeMem;
    private String totalMem;
    private String maxMem;

    public PerfData(String testName, String time, String usedMem, String freeMem, String totalMem, String maxMem)
    {
        this.testName = testName;
        this.time = time;
        this.usedMem = usedMem;
        this.freeMem = freeMem;
        this.totalMem = totalMem;
        this.maxMem = maxMem;
    }

    public String getTestName()
    {
        return testName;
    }

    public String getTime()
    {
        return time;
    }

    public String getUsedMem()
    {
        return usedMem;
    }

    public String getFreeMem()
    {
        return freeMem;
    }

    public String getTotalMem()
    {
        return totalMem;
    }

    public String getMaxMem()
    {
        return maxMem;
    }

}

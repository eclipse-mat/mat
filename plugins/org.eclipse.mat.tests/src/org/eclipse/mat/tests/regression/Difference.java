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

public class Difference
{
    private String lineNumber;
    private String baseline;
    private String testLine;
    private String problem;

    public Difference(String lineNumber, String baseline, String testLine)
    {
        this.lineNumber = lineNumber;
        this.baseline = baseline;
        this.testLine = testLine;
    }

    public Difference(String problem)
    {
        this.problem = problem;
    }

    public String getProblem()
    {
        return problem;
    }

    public String getLineNumber()
    {
        return lineNumber;
    }

    public String getBaseline()
    {
        return baseline;
    }

    public String getTestLine()
    {
        return testLine;
    }

}

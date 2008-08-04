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

class HeapdumpTestsResult
{   
    String dumpName;
    String fileSize;
    String numberOfObjects;
    String bitSize;
    List<TestData> testData;
    List<String> errorMessages;
    
    public HeapdumpTestsResult(String dumpName)
    {        
        this.dumpName = dumpName;
        testData = new ArrayList<TestData>();
        errorMessages = new ArrayList<String>();
    }

    public String getDumpName()
    {
        return dumpName;
    }

    public List<TestData> getTestData()
    {
        return testData;
    }

    public List<String> getErrorMessages()
    {
        return errorMessages;
    }
    
    public void setErrorMessages(List<String> errors)
    {
        this.errorMessages = errors;
    }
    
    public void addTestData(TestData data)
    {
        testData.add(data);
    }

    public String getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(String fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getNumberOfObjects()
    {
        return numberOfObjects;
    }

    public void setNumberOfObjects(String numberOfObjects)
    {
        this.numberOfObjects = numberOfObjects;
    }

    public String getBitSize()
    {
        return bitSize;
    }

    public void setBitSize(String bitSize)
    {
        this.bitSize = bitSize;
    }
}

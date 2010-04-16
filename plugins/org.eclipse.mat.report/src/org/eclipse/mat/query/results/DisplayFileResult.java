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
package org.eclipse.mat.query.results;

import java.io.File;

import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.report.internal.Messages;

/**
 * Used to display an external file as a report.
 * An example might be a saved HTML report.
 */
public class DisplayFileResult implements IResult
{
    private File file;

    /**
     * Create a report from a saved report file
     * @param file the file
     */
    public DisplayFileResult(File file)
    {
        this.file = file;
    }

    /**
     * Get the meta data
     * @return null
     */
    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    /**
     * Get the file used to generate this report.
     * @return the file
     */
    public File getFile()
    {
        return file;
    }

    @Override
    public String toString()
    {
        return file != null ? file.getAbsolutePath() : Messages.DisplayFileResult_Label_NoFile;
    }

}

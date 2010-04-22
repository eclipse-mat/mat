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
package org.eclipse.mat.report;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;

/**
 * Converts a result to a report.
 * See extension point <a href="../../../../schema/renderer.exsd">org.eclipse.mat.report.renderer</a>
 */
public interface IOutputter
{
    /**
     * Holds information which controls how to format a report.
     * @noimplement
     */
    public interface Context
    {
        String getId();

        IQueryContext getQueryContext();

        File getOutputDirectory();

        String getPathToRoot();

        /**
         * Get string to represent an icon
         * @param icon
         * @return a string which can be used for the icon
         */
        String addIcon(URL icon);

        String addContextResult(String name, IResult result);

        boolean hasLimit();

        int getLimit();

        boolean isColumnVisible(int columnIndex);

        boolean isTotalsRowVisible();

        String param(String key);

        String param(String key, String defaultValue);
    }

    /**
     * Add this result to the output.
     * @param context the context, which controls how the output should be done 
     * @param result the result to be formatted
     * @param writer where the formatted output should go
     * @throws IOException
     */
    void embedd(Context context, IResult result, Writer writer) throws IOException;

    /**
     * Write this result to the output, presuming the writer has just been opened.
     * @param context the context, which controls how the output should be done 
     * @param result the result to be formatted
     * @param writer where the formatted output should go
     * @throws IOException
     */
    void process(Context context, IResult result, Writer writer) throws IOException;

}

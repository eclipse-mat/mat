/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.report;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import org.eclipse.mat.query.DetailResultProvider;
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

        /**
         * The query context for the result. Could be used to convert object identifiers
         * to addresses.
         * @return the query context
         */
        IQueryContext getQueryContext();

        /**
         * Where files for the report can be generated.
         * @return the directory
         */
        File getOutputDirectory();

        String getPathToRoot();

        /**
         * Get string to represent an icon
         * @param icon where the icon can be found
         * @return a string which can be used for the icon
         */
        String addIcon(URL icon);

        /**
         * Add a {@link IResult} from a {@link DetailResultProvider}
         * to the report.
         * @param name
         * @param result
         * @return a String which can be used by the IOutputter to identify
         * the formatted result
         */
        String addContextResult(String name, IResult result);

        /**
         * Whether there is a limit on the number of rows to generate.
         * @return true if there is a limit
         */
        boolean hasLimit();

        /**
         * The limit on the number of rows to generate in the report from the result.
         * @return number of rows
         */
        int getLimit();

        /**
         * Whether a column of the result should be displayed in the report.
         * @param columnIndex
         * @return true if column should be displayed
         */
        boolean isColumnVisible(int columnIndex);

        /**
         * Whether to display a totals row from the result in the report.
         * @return
         */
        boolean isTotalsRowVisible();

        /**
         * Get the value of a parameter from {@link Params} controlling generation of a report.
         * @param key
         * @return the value or null
         */
        String param(String key);

        /**
         * Get the value of a parameter from {@link Params} controlling generation of a report.
         * @param key
         * @param defaultValue the value to be used if no parameter with that key is set.
         * @return the value, or defaultValue if no parameter with that key
         */
        String param(String key, String defaultValue);
    }

    /**
     * Add this result to the output.
     * @param context the context, which controls how the output should be done 
     * @param result the result to be formatted
     * @param writer where the formatted output should go
     * @throws IOException if something went wrong writing this result
     */
    void embedd(Context context, IResult result, Writer writer) throws IOException;

    /**
     * Write this result to the output, presuming the writer has just been opened.
     * @param context the context, which controls how the output should be done 
     * @param result the result to be formatted
     * @param writer where the formatted output should go
     * @throws IOException if something went wrong writing this result
     */
    void process(Context context, IResult result, Writer writer) throws IOException;

}

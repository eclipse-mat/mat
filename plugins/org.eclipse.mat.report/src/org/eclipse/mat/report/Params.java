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

/**
 * Available parameters for use in the report XML files.
 * 
 * @noimplement
 */
@SuppressWarnings("nls")
public interface Params
{
    String TIMESTAMP = "timestamp";
    String SNAPSHOT = "snapshot";
    String SNAPSHOT_PREFIX = "snapshot_prefix";

    /**
     * The format parameter determines the renderer to be used. By default, the
     * "html" is used, but one could use "csv" to create a comma separated file
     * from the data.
     */
    String FORMAT = "format";

    /**
     * If given, the filename is used to create the output file. Depending on
     * the output format, this could be HTML or CSV. The property can be
     * configured for every Spec. If the output format is HTML, the Spec must
     * also specify {@link Params.Html.SEPARATE_FILE}.
     */
    String FILENAME = "filename";

    /**
     * @noimplement
     */
    public interface Html
    {
        String COLLAPSED = "html.collapsed";
        String SEPARATE_FILE = "html.separate_file";

        String IS_IMPORTANT = "html.is_important";

        String SHOW_TABLE_HEADER = "html.show_table_header";
        String SHOW_HEADING = "html.show_heading";
        String SHOW_TOTALS = "html.show_totals";

        /**
         * A result can have embedded details (see DetailResultProvider). If set
         * to "false", those detail results are not included in the HTML output.
         */
        String RENDER_DETAILS = "html.render_details";
    }

    /**
     * @noimplement
     */
    public interface Rendering
    {
        String PATTERN = "rendering.pattern";

        String PATTERN_OVERVIEW_DETAILS = "overview_details";
        String PATTERN_SEQUENTIAL = "sequential";

        String SORT_COLUMN = "sort_column";
        String FILTER = "filter";
        String LIMIT = "limit";
        String HIDE_COLUMN = "hide_column";

        String DERIVED_DATA_COLUMN = "derived_data_column";
    }

}

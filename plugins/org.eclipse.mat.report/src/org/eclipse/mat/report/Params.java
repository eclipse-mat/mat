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
    /**
     * Set to the time the report is generated
     */
    String TIMESTAMP = "timestamp";
    /**
     * Not used
     */
    String SNAPSHOT = "snapshot";
    /**
     * Not used
     */
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
     * also specify {@link Params.Html#SEPARATE_FILE}.
     */
    String FILENAME = "filename";
    
    /**
     * If given, used to create a zip file name by adding
     * the suffix to the prefix. Allows the suffix to be separately
     * specified in XML report definition from the report title,
     * so it is possible to translate the title but not the suffix if
     * required.
     */
    String FILENAME_SUFFIX = "filename_suffix";

    /**
     * @noimplement
     */
    public interface Html
    {
        /**
         * Collapse this section in the HTML report
         * if set to Boolean.TRUE.toString()
         */
        String COLLAPSED = "html.collapsed";
        /**
         * Used to specify this report should be in a separate HTML file
         */
        String SEPARATE_FILE = "html.separate_file";

        /**
         * Used to emphasise a section. For example
         * a possible memory leak.
         */
        String IS_IMPORTANT = "html.is_important";

        /**
         * Whether to show a table header.
         * Defaults to true.
         */
        String SHOW_TABLE_HEADER = "html.show_table_header";
        /**
         * Whether to show a heading.
         */
        String SHOW_HEADING = "html.show_heading";
        /**
         * Whether to show totals for a table
         */
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
        /**
         * Options appear to be {@link #PATTERN_OVERVIEW_DETAILS}
         */
        String PATTERN = "rendering.pattern";

        String PATTERN_OVERVIEW_DETAILS = "overview_details";
        String PATTERN_SEQUENTIAL = "sequential";

        /**
         * Which columns to sort by, separated by ','
         */
        String SORT_COLUMN = "sort_column";
        /**
         * filter1=criteria,filter2=criteria 
         */
        String FILTER = "filter";
        /**
         * Limit, as a number
         */
        String LIMIT = "limit";
        /**
         * Which columns to hide, separated by ','
         */
        String HIDE_COLUMN = "hide_column";

        /**
         * Controls the derived column.
         * For example for retained sizes
         * _default_=APPROXIMATE
         * _default_=PRECISE
         */
        String DERIVED_DATA_COLUMN = "derived_data_column";
    }

}

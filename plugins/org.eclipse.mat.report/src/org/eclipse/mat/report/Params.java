/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - documentation
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
     * Set to the time the report is generated.
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
     * "html" format is used, but one could use "csv" to create a comma separated
     * file or "txt" to create a text file. Matches {@link Renderer#target()} of
     * an {@link IOutputter}.
     */
    String FORMAT = "format";

    /**
     * If given, the filename is used to create the output file. Depending on
     * the output format, this could be HTML or CSV. The property can be
     * configured for every Spec. If the output format is HTML, the Spec must
     * also specify {@link Params.Html#SEPARATE_FILE}. For the HTML outputter
     * this is not inherited from outer Specs.
     */
    String FILENAME = "filename";
    
    /**
     * If given, used to create a zip file name by adding
     * the suffix to the prefix. Allows the suffix to be separately
     * specified in XML report definition from the report title,
     * so it is possible to translate the title but not the suffix if
     * required.
     * @since 1.0
     */
    String FILENAME_SUFFIX = "filename_suffix";

    /**
     * Parameters specific to HTML reports.
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
         * Used to specify this report should be in a separate HTML file.
         * For the HTML outputter this is not inherited from outer Specs.
         */
        String SEPARATE_FILE = "html.separate_file";

        /**
         * Used to emphasise a section. For example
         * a possible memory leak.
         * For the HTML outputter this is usually not inherited from outer Specs.
         */
        String IS_IMPORTANT = "html.is_important";

        /**
         * Whether to show a table header.
         * Defaults to true.
         */
        String SHOW_TABLE_HEADER = "html.show_table_header";
        /**
         * Whether to show a heading.
         * For the HTML outputter this is not inherited from outer Specs.
         */
        String SHOW_HEADING = "html.show_heading";
        /**
         * Whether to show totals for a table.
         */
        String SHOW_TOTALS = "html.show_totals";

        /**
         * Include embedded report details. 
         * A result can have embedded details {@link org.eclipse.mat.query.DetailResultProvider}. If set
         * to "false", those detail results are not included in the HTML output.
         */
        String RENDER_DETAILS = "html.render_details";
    }

    /**
     * Control the rendering of a report.
     * @noimplement
     */
    public interface Rendering
    {
        /**
         * Options appear to be {@link #PATTERN_OVERVIEW_DETAILS}
         */
        String PATTERN = "rendering.pattern";

        /**
         * Possible value for  key given by {@link #PATTERN}.
         */
        String PATTERN_OVERVIEW_DETAILS = "overview_details";

        /**
         * Possible value for  key given by {@link #PATTERN}.
         */
        String PATTERN_SEQUENTIAL = "sequential";

        /**
         * Which columns to sort by, separated by ','. Columns specified by name or #0, #1, #2 etc.
         */
        String SORT_COLUMN = "sort_column";
        /**
         * filter1=criteria,filter2=criteria 
         * filter1 etc. is a column specified by name or #0, #1, #2 etc.
         * criteria is a numeric or string filter such as &gt;100, 10..200, com\.*
         */
        String FILTER = "filter";
        /**
         * Limit, as a decimal number, for the number of items to display.
         */
        String LIMIT = "limit";
        /**
         * Which columns to hide, separated by ','. Columns specified by name or #0, #1, #2 etc.
         */
        String HIDE_COLUMN = "hide_column";

        /**
         * Controls the calculation of the derived column.
         * For example for retained sizes
         * _default_=APPROXIMATE  {@link org.eclipse.mat.snapshot.query.RetainedSizeDerivedData#APPROXIMATE}
         * _default_=PRECISE {@link org.eclipse.mat.snapshot.query.RetainedSizeDerivedData#PRECISE}
         */
        String DERIVED_DATA_COLUMN = "derived_data_column";
    }

}

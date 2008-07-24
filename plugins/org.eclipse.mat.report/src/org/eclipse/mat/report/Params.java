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
 * @noimplement
 */
public interface Params
{
    String TIMESTAMP = "timestamp";
    String SNAPSHOT = "snapshot";
    String SNAPSHOT_PREFIX = "snapshot_prefix";
    String FORMAT = "format";

    /**
     * @noimplement
     */
    public interface Html
    {
        String FILENAME = "html.filename";
        String COLLAPSED = "html.collapsed";
        String SEPARATE_FILE = "html.separate_file";
        String SHOW_TABLE_HEADER = "html.show_table_header";
        String SHOW_HEADING = "html.show_heading";
        String IS_IMPORTANT = "html.is_important";
        String SHOW_TOTALS = "html.show_totals";
    }
    
    /**
     * @noimplement
     */
    public interface Rendering
    {
        String PATTERN ="rendering.pattern";
        
        String PATTERN_OVERVIEW_DETAILS = "overview_details";
        String PATTERN_SEQUENTIAL = "sequential";
        
        String SORT_COLUMN = "sort_column";
        String FILTER = "filter";
        String LIMIT = "limit";
        String HIDE_COLUMN = "hide_column";
        
        String DERIVED_DATA_COLUMN = "derived_data_column";
    }

}

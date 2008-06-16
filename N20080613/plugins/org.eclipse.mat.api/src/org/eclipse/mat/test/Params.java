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
package org.eclipse.mat.test;

public interface Params
{
    String TIMESTAMP = "timestamp";
    String SNAPSHOT = "snapshot";
    String SNAPSHOT_PREFIX = "snapshot_prefix";
    String FORMAT = "format";

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
    
    public interface Rendering
    {
        String PATTERN ="rendering.pattern";
        
        String PATTERN_OVERVIEW_DETAILS = "overview_details";
        String PATTERN_SEQUENTIAL = "sequential";
        
        String SORT_COLUMN = "sort_column";
        String SORT_ORDER = "sort_order";
        String THRESHOLD = "threshold";
        String THRESHOLD_COLUMN = "threshold_column";
        String FILTER = "filter";
        String LIMIT = "limit";
        String HIDE_COLUMN = "hide_column";
        String CALCULATE_RETAINED_SIZE_FOR = "retained_size_for";
        String RETAINED_SIZE_APPROX = "retained_size_approx";

        String APPROXIMATE_RETAINED_SIZE = "approximate_retained_size";
    }

}

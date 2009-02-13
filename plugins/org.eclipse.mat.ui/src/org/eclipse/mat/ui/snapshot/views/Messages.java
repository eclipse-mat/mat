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
package org.eclipse.mat.ui.snapshot.views;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.ui.snapshot.views.messages"; //$NON-NLS-1$

    public static String resource;
    public static String general_info;
    public static String statistic_info;

    public static String format;
    public static String time;
    public static String date;
    public static String identifier_size;
    public static String identifier_format;
    public static String file_path;
    public static String file_length;

    public static String heap;
    public static String number_of_objects;
    public static String number_of_classes;
    public static String number_of_classloaders;
    public static String number_of_gc_roots;

    public static String col_property;
    public static String col_file;
    public static String col_baseline;
    public static String col_name;
    public static String col_usages;
    public static String col_time;
    public static String col_avg_time;
    public static String col_filesize;
    public static String col_header;
    public static String col_body;

    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

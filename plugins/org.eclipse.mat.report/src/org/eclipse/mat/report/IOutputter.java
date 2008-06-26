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

public interface IOutputter
{
    public interface Context
    {
        IQueryContext getQueryContext();

        String getRelativeIconLink(URL icon);

        File getOutputDirectory();

        boolean hasLimit();

        boolean isVisible(int columnIndex);

        boolean showTotals();

        String getId();

        int getLimit();

        String param(String key);
        
        String param(String key, String defaultValue);
    }

    void embedd(Context context, IResult result, Writer writer) throws IOException;

    void process(Context context, IResult result, Writer writer) throws IOException;

}

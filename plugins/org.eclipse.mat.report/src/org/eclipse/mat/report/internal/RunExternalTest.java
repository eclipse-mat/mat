/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - add parameters
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.SpecFactory;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.util.IProgressListener;

@CommandName("create_report")
@Category(Category.HIDDEN)
@HelpUrl("/org.eclipse.mat.ui.help/reference/extendingmat.html")
@Icon("/META-INF/icons/expert.gif")
public class RunExternalTest implements IQuery
{
    @Argument
    public IQueryContext context;

    @Argument(flag = Argument.UNFLAGGED)
    public File testSuite;

    @Argument(isMandatory = false)
    public String params[];

    public IResult execute(IProgressListener listener) throws Exception
    {
        Spec template;
        try
        {
            SpecFactory factory = SpecFactory.instance();
            template = factory.create(testSuite);

            // Add options
            Map<String, String> opts = parseOptions(params);
            template.putAll(opts);

            factory.resolve(template);
        }
        catch (Exception e)
        {
            throw new SnapshotException(e);
        }

        TestSuite suite = new TestSuite.Builder(template).build(context);

        suite.execute(listener);

        for (File f : suite.getResults())
        {
            if ("index.html".equals(f.getName())) //$NON-NLS-1$
                return new DisplayFileResult(f);
        }

        return null;
    }

    Map<String,String>parseOptions(String opts[])
    {
        Map<String,String>m = new HashMap<String,String>();
        if (opts != null)
        {
            for (String opt : opts)
            {
                int i = opt.indexOf('=');
                if (i < 0)
                    throw new IllegalArgumentException(opt);
                m.put(opt.substring(0, i), opt.substring(i + 1));
            }
        }
        return m;
    }
}

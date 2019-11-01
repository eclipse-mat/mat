/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - add parameters
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
import org.eclipse.mat.util.MessageUtil;

@CommandName("default_report")
@Category(Category.HIDDEN)
@HelpUrl("/org.eclipse.mat.ui.help/tasks/batch.html")
@Icon("/META-INF/icons/expert.gif")
public class RunRegisterdReport implements IQuery
{
    @Argument
    public IQueryContext queryContext;

    @Argument(flag = Argument.UNFLAGGED)
    public String extensionIdentifier;

    @Argument(isMandatory = false)
    public String params[];

    public IResult execute(IProgressListener listener) throws Exception
    {
        SpecFactory factory = SpecFactory.instance();
        Spec spec = factory.create(extensionIdentifier);
        if (spec == null)
            throw new Exception(MessageUtil
                            .format(Messages.RunRegisterdReport_Error_UnknownReport, extensionIdentifier));

        // Add options
        Map<String, String> opts = parseOptions(params);
        spec.putAll(opts);

        factory.resolve(spec);

        TestSuite suite = new TestSuite.Builder(spec).build(queryContext);

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

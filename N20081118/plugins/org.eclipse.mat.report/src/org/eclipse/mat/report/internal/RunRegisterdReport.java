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
package org.eclipse.mat.report.internal;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.SpecFactory;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.util.IProgressListener;

@CommandName("default_report")
@Category(Category.HIDDEN)
public class RunRegisterdReport implements IQuery
{
    @Argument
    public IQueryContext queryContext;

    @Argument(flag = "none")
    public String extensionIdentifier;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SpecFactory factory = SpecFactory.instance();
        Spec spec = factory.create(extensionIdentifier);
        if (spec == null)
            throw new Exception(MessageFormat.format("Unknown report: {0}", extensionIdentifier));

        factory.resolve(spec);

        TestSuite suite = new TestSuite.Builder(spec).build(queryContext);

        suite.execute(listener);

        for (File f : suite.getResults())
        {
            if ("index.html".equals(f.getName()))
                return new DisplayFileResult(f);
        }

        return null;
    }

}

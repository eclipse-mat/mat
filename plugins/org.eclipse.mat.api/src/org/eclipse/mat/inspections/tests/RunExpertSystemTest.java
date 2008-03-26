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
package org.eclipse.mat.inspections.tests;

import java.io.File;

import org.eclipse.mat.impl.test.TestSuite;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;


@Name("Run Expert System Test")
@CommandName("create_report")
@Category(Category.HIDDEN)
public class RunExpertSystemTest implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public File testSuite;

    public IResult execute(IProgressListener listener) throws Exception
    {
        TestSuite suite = new TestSuite.Builder(testSuite) //
                        .snapshot(snapshot) //
                        .build();

        suite.execute(listener);

        for (File f : suite.getResults())
        {
            if ("index.html".equals(f.getName()))
                return new DisplayFileResult(f);
        }

        return null;
    }

}

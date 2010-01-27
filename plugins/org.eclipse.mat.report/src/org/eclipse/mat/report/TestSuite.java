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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.report.ITestResult.Status;
import org.eclipse.mat.report.internal.AbstractPart;
import org.eclipse.mat.report.internal.PartsFactory;
import org.eclipse.mat.report.internal.ResultRenderer;
import org.eclipse.mat.util.FileUtils;
import org.eclipse.mat.util.IProgressListener;

public class TestSuite
{
    // //////////////////////////////////////////////////////////////
    // factory methods
    // //////////////////////////////////////////////////////////////

    public static class Builder
    {
        private Spec template;
        private File output;

        public Builder(String identifier) throws SnapshotException
        {
            try
            {
                SpecFactory factory = SpecFactory.instance();
                template = factory.create(identifier);
                factory.resolve(template);
            }
            catch (Exception e)
            {
                throw new SnapshotException(e);
            }
        }

        public Builder(File specFile) throws SnapshotException
        {
            try
            {
                SpecFactory factory = SpecFactory.instance();
                template = factory.create(specFile);
                factory.resolve(template);
            }
            catch (Exception e)
            {
                throw new SnapshotException(e);
            }
        }

        public Builder(Spec spec)
        {
            template = spec;
        }

        public Builder output(File file)
        {
            output = file;
            return this;
        }

        public TestSuite build(IQueryContext queryContext)
        {
            template.set(Params.TIMESTAMP, String.valueOf(System.currentTimeMillis())); //$NON-NLS-1$

            if (output == null)
                output = queryContext.getPrimaryFile().getParentFile();

            if (output.isDirectory())
            {
                String prefix = queryContext.getPrimaryFile().getName();
                int p = prefix.lastIndexOf('.');
                if (p >= 0)
                    prefix = prefix.substring(0, p);

                String suffix = template.getParams().get(Params.FILENAME_SUFFIX);
                if (suffix == null)
                    suffix = template.getName();

                output = new File(output, prefix + "_" + FileUtils.toFilename(suffix, "zip")); //$NON-NLS-1$ //$NON-NLS-2$
            }

            TestSuite testSuite = new TestSuite(template, queryContext);
            testSuite.output = output;
            return testSuite;
        }

    }

    // //////////////////////////////////////////////////////////////
    // implementation
    // //////////////////////////////////////////////////////////////

    private final Spec spec;
    private final IQueryContext queryContext;

    private File output;
    private final List<File> results = new ArrayList<File>();

    private TestSuite(Spec spec, IQueryContext queryContext)
    {
        this.spec = spec;
        this.queryContext = queryContext;
    }

    public Status execute(IProgressListener listener) throws IOException, SnapshotException
    {
        PartsFactory factory = new PartsFactory();

        AbstractPart part = factory.createRoot(spec);

        ResultRenderer renderer = new ResultRenderer();

        renderer.beginSuite(this, part);
        part = part.execute(queryContext, renderer, listener);
        renderer.endSuite(part);

        return part.getStatus();
    }

    public Spec spec()
    {
        return spec;
    }

    public IQueryContext getQueryContext()
    {
        return queryContext;
    }

    public File getOutput()
    {
        return output != null ? output : queryContext.getPrimaryFile().getParentFile();
    }

    public List<File> getResults()
    {
        return Collections.unmodifiableList(results);
    }

    public void addResult(File result)
    {
        this.results.add(result);
    }
}

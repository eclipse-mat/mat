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
package org.eclipse.mat.impl.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.test.Params;
import org.eclipse.mat.test.Spec;
import org.eclipse.mat.test.ITestResult.Status;
import org.eclipse.mat.util.IProgressListener;


public class TestSuite
{
    // //////////////////////////////////////////////////////////////
    // factory methods
    // //////////////////////////////////////////////////////////////

    public static class Builder
    {
        private Spec template;
        private Map<String, Object> context;

        public Builder(File specFile) throws SnapshotException
        {

            try
            {
                context = new HashMap<String, Object>();
                SpecFactory factory = SpecFactory.instance();
                template = factory.create(specFile);
                factory.resolve(template);
            }
            catch (Exception e)
            {
                throw new SnapshotException(e);
            }
        }

        public Builder(Spec spec) throws SnapshotException
        {
            try
            {
                context = new HashMap<String, Object>();
                template = spec;
            }
            catch (Exception e)
            {
                throw new SnapshotException(e);
            }
        }

        public Builder param(String key, String value)
        {
            template.set(key, value);
            return this;
        }

        public Builder snapshot(ISnapshot snapshot)
        {
            context.put(Params.SNAPSHOT, snapshot);

            String filename = snapshot.getSnapshotInfo().getPath();
            template.set(Params.SNAPSHOT, filename);

            int p1 = filename.lastIndexOf(File.separatorChar);
            if (p1 < 0)
                p1 = 0;

            int p2 = filename.lastIndexOf('.');
            if (p2 < 0)
                p2 = filename.length();

            String prefix = filename.substring(p1, p2);
            template.set(Params.SNAPSHOT_PREFIX, prefix);

            return this;
        }

        public TestSuite build()
        {
            template.set("timestamp", String.valueOf(System.currentTimeMillis()));
            return new TestSuite(template, context);
        }

    }

    // //////////////////////////////////////////////////////////////
    // implementation
    // //////////////////////////////////////////////////////////////

    private AbstractPart part;
    private Map<String, Object> context = new HashMap<String, Object>();
    private List<File> results = new ArrayList<File>();

    private TestSuite(Spec spec, Map<String, Object> context)
    {
        this.context = context;
        this.part = AbstractPart.build(null, spec);
    }

    public AbstractPart part()
    {
        return this.part;
    }

    public Status execute(IProgressListener listener) throws IOException, SnapshotException
    {
        ISnapshot snapshot = (ISnapshot) context.get(Params.SNAPSHOT);
        if (snapshot == null)
        {
            snapshot = SnapshotFactory.openSnapshot(new File(part.params().get(Params.SNAPSHOT)), listener);
            context.put(Params.SNAPSHOT, snapshot);
        }
        else
        {
            // how to inject parameters event though they could be resolved
            // already?
        }

        ResultRenderer renderer = new ResultRenderer();

        renderer.beginSuite(this);
        part.execute(snapshot, renderer, listener);
        renderer.endSuite(this);

        return part.getStatus();
    }

    public ISnapshot getSnapshot()
    {
        return (ISnapshot) context.get(Params.SNAPSHOT);
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

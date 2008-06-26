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
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.hprof.extension.IRuntimeEnhancer;
import org.eclipse.mat.hprof.internal.EnhancerRegistry;
import org.eclipse.mat.parser.IObjectReader;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.index.IndexReader;
import org.eclipse.mat.parser.model.AbstractArrayImpl.IContentDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class HprofHeapObjectReader implements IObjectReader
{
    public static final String VERSION_PROPERTY = "hprof.version";

    private ISnapshot snapshot;
    private HprofRandomAccessParser hprofDump;
    private IIndexReader.IOne2LongIndex o2hprof;
    private List<IRuntimeEnhancer> enhancers;

    public void open(ISnapshot snapshot) throws IOException
    {
        this.snapshot = snapshot;

        AbstractParser.Version version = AbstractParser.Version.valueOf((String) snapshot.getSnapshotInfo()
                        .getProperty(VERSION_PROPERTY));

        this.hprofDump = new HprofRandomAccessParser(new File(snapshot.getSnapshotInfo().getPath()), //
                        version, //
                        snapshot.getSnapshotInfo().getIdentifierSize());
        this.o2hprof = new IndexReader.LongIndexReader(new File(snapshot.getSnapshotInfo().getPrefix()
                        + "o2hprof.index"));

        this.enhancers = new ArrayList<IRuntimeEnhancer>();
        for (EnhancerRegistry.Enhancer enhancer : EnhancerRegistry.instance().delegates())
            this.enhancers.add(enhancer.runtime());
    }

    public Object read(IContentDescriptor descriptor, int offset, int length) throws IOException
    {
        return hprofDump.read(descriptor, offset, length);
    }

    public Object read(IContentDescriptor descriptor) throws IOException
    {
        return hprofDump.read(descriptor);
    }

    public IObject read(int objectId, ISnapshot snapshot) throws SnapshotException, IOException
    {
        long filePosition = o2hprof.get(objectId);
        return hprofDump.read(objectId, filePosition, snapshot);
    }

    public <A> A getAddon(Class<A> addon) throws SnapshotException
    {
        for (IRuntimeEnhancer enhancer : enhancers)
        {
            A answer = enhancer.getAddon(snapshot, addon);
            if (answer != null)
                return answer;
        }
        return null;
    }

    public void close() throws IOException
    {
        try
        {
            hprofDump.close();
        }
        catch (IOException ignore)
        {}

        try
        {
            o2hprof.close();
        }
        catch (IOException ignore)
        {}
    }

}

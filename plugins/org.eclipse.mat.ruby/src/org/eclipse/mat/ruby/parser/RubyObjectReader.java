/*******************************************************************************
 * Copyright (c) 2016 SAP AG
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ruby.parser;

import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.IObjectReader;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.InstanceImpl;
import org.eclipse.mat.parser.model.ObjectArrayImpl;
import org.eclipse.mat.parser.model.PrimitiveArrayImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IObject;

public class RubyObjectReader implements IObjectReader
{

    public RubyObjectReader()
    {
    }

    @Override
    public void close() throws IOException
    {
    }

    @Override
    public void open(ISnapshot snapshot) throws SnapshotException, IOException
    {
    }

    @Override
    public IObject read(int objectId, ISnapshot snapshot) throws SnapshotException, IOException
    {
        InstanceImpl instanceImpl = new InstanceImpl(objectId, snapshot.mapIdToAddress(objectId), (ClassImpl) snapshot.getClassOf(objectId), new ArrayList<Field>(0));
        instanceImpl.setSnapshot(snapshot);
        return instanceImpl;
    }

    @Override
    public Object readPrimitiveArrayContent(PrimitiveArrayImpl array, int offset, int length) throws IOException,
                    SnapshotException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long[] readObjectArrayContent(ObjectArrayImpl array, int offset, int length) throws IOException,
                    SnapshotException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <A> A getAddon(Class<A> addon) throws SnapshotException
    {
        // TODO Auto-generated method stub
        return null;
    }

}

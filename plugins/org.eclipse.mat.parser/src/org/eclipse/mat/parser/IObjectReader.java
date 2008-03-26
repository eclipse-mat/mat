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
package org.eclipse.mat.parser;

import java.io.IOException;

import org.eclipse.mat.parser.model.AbstractArrayImpl.ArrayContentDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.ISnapshotAddon;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;


public interface IObjectReader
{
    void open(ISnapshot snapshot) throws IOException;

    IObject read(int objectId, ISnapshot snapshot) throws SnapshotException, IOException;

    void close() throws IOException;

    Object read(ArrayContentDescriptor descriptor) throws IOException;

    Object read(ArrayContentDescriptor content, int offset, int length) throws IOException;

    <A extends ISnapshotAddon> A getAddon(Class<A> addon) throws SnapshotException;
}

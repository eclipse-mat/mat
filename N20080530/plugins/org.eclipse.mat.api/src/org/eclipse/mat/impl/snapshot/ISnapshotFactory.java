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
package org.eclipse.mat.impl.snapshot;

import java.io.File;
import java.util.List;

import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFormat;
import org.eclipse.mat.util.IProgressListener;


public interface ISnapshotFactory
{
    ISnapshot openSnapshot(File file, IProgressListener listener) throws SnapshotException;

    void dispose(ISnapshot snapshot);

    IOQLQuery createQuery(String queryString) throws OQLParseException, SnapshotException;
    
    List<SnapshotFormat> getSupportedFormats();
}

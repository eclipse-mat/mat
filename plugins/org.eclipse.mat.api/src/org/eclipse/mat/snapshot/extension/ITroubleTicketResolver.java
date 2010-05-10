/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.extension;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.util.IProgressListener;

public interface ITroubleTicketResolver
{
    public String getTicketSystem();

    public String resolveByClass(IClass object, IProgressListener listener) throws SnapshotException;

    public String resolveByClassLoader(IClassLoader classLoader, IProgressListener listener) throws SnapshotException;

}

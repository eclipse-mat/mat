/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.osgi.model;

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

public class BundleReaderFactory
{
    /**
     * Check for the type of framework and return corresponding IBundleReader
     * 
     * @param snapshot
     * @return IBundleReader
     * @throws SnapshotException
     */
    public static IBundleReader getBundleReader(ISnapshot snapshot) throws SnapshotException
    {

        Collection<IClass> classes = snapshot.getClassesByName(
                        "org.eclipse.osgi.framework.internal.core.BundleRepository", false); //$NON-NLS-1$
        if (classes != null && !classes.isEmpty())
            // Equinox OSGi framework
            return new EquinoxBundleReader(snapshot);
        classes = snapshot.getClassesByName(
                        "org.eclipse.osgi.container.ModuleLoader", false); //$NON-NLS-1$
        if (classes != null && !classes.isEmpty())
            // Equinox OSGi framework
            return new EquinoxBundleReader2(snapshot);
        else
            throw new SnapshotException(Messages.BundleReaderFactory_ErrorMsg_EquinoxNotFound);

    }

}

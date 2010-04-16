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
package org.eclipse.mat.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.util.IProgressListener;

/**
 * Performs an OQL Query.
 * 
 * @noimplement
 */
public interface IOQLQuery
{
    /**
     * A result which also describes the OQL query that generated it.
     */
    public interface Result extends IResult
    {
        /**
         * The OQL query 
         * @return the query
         */
        String getOQLQuery();
    }

    /**
     * Execute the OQL query. Returns a result object, either a primitive
     * integer array containing object ids or IResultTable.
     */
    Object execute(ISnapshot snapshot, IProgressListener monitor) throws SnapshotException;
}

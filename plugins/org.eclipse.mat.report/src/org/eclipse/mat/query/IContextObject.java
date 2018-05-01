/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

/**
 * Interface to describe the heap objects.
 * <p>
 * Rows in tables or tree represent a set of objects. Using a
 * {@link ContextProvider}, the Memory Analyzer determines the object set
 * associated with a row and is able to execute other queries on this set.
 * <p>
 * If a row represents more than one object, use {@link IContextObjectSet}. *
 * 
 * @see org.eclipse.mat.query.ContextProvider
 * @see org.eclipse.mat.query.IContextObjectSet
 */
public interface IContextObject
{
    /**
     * Context for a single row.
     * @return the object id.
     */
    int getObjectId();
}

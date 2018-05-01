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
 * Interface to describe a set of objects.
 * 
 * @see org.eclipse.mat.query.ContextProvider
 * @see org.eclipse.mat.query.IContextObject
 */
public interface IContextObjectSet extends IContextObject
{
    /**
     * Context for a row representing several objects.
     * @return the set of objects.
     */
    int[] getObjectIds();

    /**
     * (Optionally) return the OQL representing this set of objects.
     * @return an OQL query representing this set of objects
     */
    String getOQL();
}

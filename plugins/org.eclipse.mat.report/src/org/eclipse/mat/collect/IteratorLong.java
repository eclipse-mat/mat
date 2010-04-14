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
package org.eclipse.mat.collect;

/**
 * Simple iterator to go through ints 
 */
public interface IteratorLong
{
    /**
     * is there a next entry
     * @return true if next entry available
     */
    boolean hasNext();

    /**
     * get the next entry
     * @return the entry
     */
    long next();
}

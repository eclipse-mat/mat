/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

/**
 * Marker interface to represent object arrays in the heap dump.
 * 
 * @noimplement
 */
public interface IObjectArray extends IArray
{
    /**
     * Get an array with the object addresses. 0 indicates <code>null</code>
     * values in the array.
     */
    long[] getReferenceArray();

    /**
     * Get an array with the object addresses, beginning at <code>offset</code>
     * and <code>length</code> number of elements.
     */
    long[] getReferenceArray(int offset, int length);

}

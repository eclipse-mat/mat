/*******************************************************************************
 * Copyright (c) 2015 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

/**
 * Additional details for stack frames.
 * 
 * @noimplement
 * @since 1.5
 */
public interface IStackFrame2 extends IStackFrame
{

    /**
     * Returns the object IDs of all monitor objects this frame is blocked on.
     * 
     * @return int[] an array containing the object IDs of all monitor objects
     *         this frame is blocked on. If there are none, an empty array will
     *         be returned.
     */
    public int[] getBlockedOnIds();
}

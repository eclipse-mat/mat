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
package org.eclipse.mat.snapshot.query;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * An argument of type IHeapObjectArgument can be used to inject heap objects
 * into Query.
 * <p>
 * 
 * <pre>
 * &#064;Argument
 * public IHeapObjectArgument objects;
 * 
 * public IResult execute(IProgressListener listener) throws Exception
 * {
 *     for (int[] objectIds : objects)
 *     {
 *         for (int objectId : objectIds)
 *         {
 *             // do something
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * There are two advantages over using primitive Integer arrays: First, the
 * object set is chunked (if accessed through the iterator) and second, there is
 * no need to annotate the variable with isHeapObject = true.
 * 
 * <pre>
 * &#064;Argument(isHeapObject = true)
 * public int[] objects;
 * </pre>
 * 
 * @noimplement
 */
public interface IHeapObjectArgument extends Iterable<int[]>
{
    /**
     * This method returns one (possibly big) integer array with the selected
     * object ids. This method can be much slower than the approach described in
     * the class documentation.
     * 
     * @param listener
     *            progress listener
     * @return an integer array with the selected object ids
     */
    int[] getIds(IProgressListener listener) throws SnapshotException;

    /**
     * A user-friendly label for the object set.
     */
    String getLabel();
}

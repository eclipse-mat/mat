/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

import java.util.List;

/**
 * Interface for results in tree-form.
 */
public interface IResultTree extends IStructuredResult
{
    /**
     * Returns the root elements of the tree.
     * @return list of elements which can be passed to 
     * {@link IResultTree#getChildren(Object)} or 
     * {@link IStructuredResult#getContext(Object)} or
     * {@link IStructuredResult#getColumnValue(Object, int)}.
     * @return a list of all the root elements of the tree
     */
    List<?> getElements();

    /**
     * Returns whether the given element has children.
     * @param element the opaque object used to indicate which branch
     * @return true if this element has children
     */
    boolean hasChildren(Object element);

    /**
     * Returns the child elements of the given parent.
     * @param parent
     *            The row object as returned by the
     *            {@link IResultTree#getElements()} or
     *            {@link IResultTree#getChildren(Object)} methods.
     * @return a list of children of this branch of the tree
     */
    List<?> getChildren(Object parent);
}

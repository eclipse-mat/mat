/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
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
     */
    List<?> getElements();

    /**
     * Returns whether the given element has children.
     */
    boolean hasChildren(Object element);

    /**
     * Returns the child elements of the given parent.
     * @param parent
     *            The row object as returned by the
     *            {@link IResultTree#getElements()} or
     *            {@link IResultTree#getChildren(Object)} methods.
     */
    List<?> getChildren(Object parent);
}

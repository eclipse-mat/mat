/*******************************************************************************
 * Copyright (c) 2008,2022 SAP AG and IBM Corporation.
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

import java.util.List;

/**
 * Interface for a plain vanilla object instance in the heap dump.
 * 
 * @noimplement
 */
public interface IInstance extends IObject
{
    /**
     * Returns all fields of the object.
     * <p>
     * Fields are ordered in such a way, that first fields defined in the
     * current class and then fields of the super class and its super classes
     * are returned. This order is important to know, if a class declares a
     * field by the same name as the class it inherits from.
     * @return a list of the fields
     */
    public List<Field> getFields();

    /**
     * Returns the field identified by the name.
     * <p>
     * If declares a member variable by the same name as the parent class does,
     * then the result of this method is undefined.
     * @param name the name of the field
     * @return the field
     */
    public Field getField(String name);
}

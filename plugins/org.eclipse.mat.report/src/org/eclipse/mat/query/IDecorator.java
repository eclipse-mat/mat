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
 * Used for rows of reports
 */
public interface IDecorator
{
    /**
     * Add before the object for example {@literal <local>}
     * @param row used to find the row
     * @return the prefix or null
     */
    String prefix(Object row);

    /**
     * Add after the object
     * @param row used to find the row
     * @return the suffix or null
     */
    String suffix(Object row);
}

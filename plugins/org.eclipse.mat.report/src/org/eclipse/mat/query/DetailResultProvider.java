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

import java.net.URL;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Used to give more detailed information about rows in a table as another IResult.
 */
public abstract class DetailResultProvider
{
    private String label;

    /**
     * Constructor of object to enhance details of rows of a table.
     * Used to give more detailed information about rows in a table as another IResult.
     * @param label the description used for example as a query menu item or as a link in an HTML report.
     */
    public DetailResultProvider(String label)
    {
        this.label = label;
    }

    /**
     * The description, which can be used as an extra menu item name.
     * @return the description
     */
    public final String getLabel()
    {
        return label;
    }

    /**
     * The icon associated with this provider. This could be used on a context menu.
     * @return a URL which can be used to get the icon, can be null
     * @since 1.1
     */
    public URL getIcon()
    {
        return null;
    }

    /**
     * Whether there is any data for this row
     * @param row the opaque data representing the row
     * @return true if getResult is to be called
     */
    public abstract boolean hasResult(Object row);

    /**
     * Get more data about the row.
     * @param row the opaque object for finding the row
     * @param listener to indicate progress or errors
     * @return the extra generated results
     * @throws SnapshotException if there was a problem getting the result
     */
    public abstract IResult getResult(Object row, IProgressListener listener) throws SnapshotException;

}

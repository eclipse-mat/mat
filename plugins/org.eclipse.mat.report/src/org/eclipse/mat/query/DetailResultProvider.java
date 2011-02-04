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
     * @return
     */
    public final String getLabel()
    {
        return label;
    }

    /**
     * The icon associated with this provider. This could be used on a context menu.
     * @return
     * @since 1.1
     */
    public URL getIcon()
    {
        return null;
    }

    /**
     * Whether there is any data for this row
     * @param row
     * @return true if getResult is to be called
     */
    public abstract boolean hasResult(Object row);

    /**
     * Get more data about the row.
     * @param row
     * @param listener to indicate progress or errors
     * @return the extra generated results
     * @throws SnapshotException
     */
    public abstract IResult getResult(Object row, IProgressListener listener) throws SnapshotException;

}

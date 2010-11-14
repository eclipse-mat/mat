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

/**
 * Interface to mark a query result.
 */
public interface IResult
{

    /**
     * (Optionally) Return meta data of the result needed to fine-tune the
     * display of the result.
     * This could include an additional context, an additional query to run on
     * selected data from the result , additional calculated columns,
     * or an indication that the results are already presorted.
     */
    ResultMetaData getResultMetaData();
}

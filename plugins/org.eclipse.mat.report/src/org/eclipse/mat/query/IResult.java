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
 * Interface to mark a query result.
 * There are several implementations of IResult supplied with Memory Analyzer,
 * together with user interface code to display them.
 * The org.eclipse.mat.ui.editorPanes extension can be used to extend 
 * the Memory Analyzer user interface to display new custom IResult types.
 */
public interface IResult
{

    /**
     * (Optionally) Return meta data of the result needed to fine-tune the
     * display of the result.
     * This could include an additional context, an additional query to run on
     * selected data from the result , additional calculated columns,
     * or an indication that the results are already presorted.
     * @return the metadata for the result, used to obtain extra data
     */
    ResultMetaData getResultMetaData();
}

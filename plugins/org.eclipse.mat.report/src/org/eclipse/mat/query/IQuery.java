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

import org.eclipse.mat.util.IProgressListener;

/**
 * Interface representing a query on the heap dump.
 */
public interface IQuery
{
    /**
     * The execute method is called after all arguments have been injected into
     * the query instance.
     * 
     * @param listener
     *            Monitor to report progress and check for cancellation.
     * @return The result of the query.
     */
    IResult execute(IProgressListener listener) throws Exception;
}

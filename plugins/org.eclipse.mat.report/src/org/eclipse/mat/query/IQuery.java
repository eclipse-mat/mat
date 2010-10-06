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

import java.util.regex.Pattern;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.util.IProgressListener;

/**
 * Interface representing a query on the heap dump.
 * Arguments can be injected into the query using public fields marked with the {@link Argument} annotation.
 * Typical arguments are
 * {@link ISnapshot}, 
 * {@link IHeapObjectArgument},
 * {@link IContentObject}, 
 * {@link IContextObjectSet}, 
 * int or int[] tagged with {@link Advice.HEAP_OBJECT}.
 * Typical arguments to be supplied by the user of the query include {@link Pattern}, boolean flags, int.
 * Implementations of this interface need to be
 * registered using the <code>org.eclipse.mat.api.query</code> extension point.
 */
public interface IQuery
{
    /**
     * The execute method is called after all arguments have been injected into
     * the query instance.
     * Typical results are {@link TextResult}, {@link CompositeResult}, {@link SectionSpec} etc. 
     * 
     * @param listener
     *            Monitor to report progress and check for cancellation.
     * @return The result of the query.
     */
    IResult execute(IProgressListener listener) throws Exception;
}

/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG, IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - improved javadoc
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
 * <ul>
 * <li>{@link org.eclipse.mat.snapshot.ISnapshot}</li> 
 * <li>{@link org.eclipse.mat.snapshot.query.IHeapObjectArgument}</li>
 * <li>{@link IContextObject}</li>
 * <li>{@link IContextObjectSet}</li>
 * <li>int or int[] tagged with {@link Advice#HEAP_OBJECT}.</li>
 * </ul>
 * Typical arguments to be supplied by the user of the query include
 * <ul>
 * <li>{@link Pattern}</li>
 * <li>boolean flags</li>
 * <li>int parm</li>
 * <li>File file optionally tagged with tagged with {@link Advice#DIRECTORY} or  {@link Advice#SAVE}.</li>
 * </ul>
 * The implementation can be tagged with the following annotations to control the placement
 * and help in the query menus.
 * <ul>
 * <li>{@link org.eclipse.mat.query.annotations.Name}</li>
 * <li>{@link org.eclipse.mat.query.annotations.Category}</li>
 * <li>{@link org.eclipse.mat.query.annotations.CommandName}</li>
 * <li>{@link org.eclipse.mat.query.annotations.Icon}</li>
 * <li>{@link org.eclipse.mat.query.annotations.Help}</li>
 * <li>{@link org.eclipse.mat.query.annotations.HelpUrl}</li>
 * <li>{@link org.eclipse.mat.query.annotations.Menu}</li>
 * </ul>
 * 
 * Implementations of this interface need to be
 * registered using the <code>org.eclipse.mat.report.query</code> extension point.
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

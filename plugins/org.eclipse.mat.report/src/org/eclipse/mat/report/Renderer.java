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
package org.eclipse.mat.report;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.eclipse.mat.query.IResult;

/**
 * Annotates a renderer describing what it accepts and it generates.
 * Qualifies an {@link IOutputter}.
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface Renderer
{
    /**
     * What the renderer accepts.
     * @return an array of acceptable classes
     */
    Class<? extends IResult>[] result() default IResult.class;

    /**
     * What the renderer generates.
     * @return for example "html" or "csv"
     */
    String target();
}

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
package org.eclipse.mat.query.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates the name of the query as displayed in the query menu or query browser.
 * The text can be replaced by translatable text in an annotations.properties file in the same package with the key
 * <pre>
 *{@code
<SimpleClassName>.name = My first query
<SimpleClassName>.name = 4|My second query
}
 * </pre>
 * where {@code <SimpleClassName>} is the name of the class of the query without the package name.
 * The name is optionally preceded by a number indicating the menu order with a vertical bar separator.
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface Name
{
    String value();
}

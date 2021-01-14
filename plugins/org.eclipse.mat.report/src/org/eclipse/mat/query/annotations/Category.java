/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
 * Annotates the category to which the query belongs.
 * The category is used to specify the menu section for a query.
 * Cascaded menus can be given by separating the names with a slash.
 * The text can be replaced by translatable text in an annotations.properties file in the same package with the key
 * and example value as here:
 * <pre>
 *{@code
<SimpleClassName>.category = Java Basics/References
<SimpleClassName2>.category = Java Basics
}
 * </pre>
 * where {@code <SimpleClassName>} is the name of the class of the query without the package name.
 * The category name is optionally preceded by a number indicating the menu order with a vertical bar separator.
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface Category
{
    /**
     * A report which does not appear on the query menu or in the search queries.
     */
    public static final String HIDDEN = "__hidden__"; //$NON-NLS-1$

    String value();
}

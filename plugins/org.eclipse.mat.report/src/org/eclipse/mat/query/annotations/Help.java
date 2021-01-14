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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a help message to the argument and/or query.
 * The text can be replaced by translatable text in an annotations.properties file in the same package with the key
 * <pre>
 *{@code
<SimpleClassName>.help =
}
 * </pre>
 * where {@code <SimpleClassName>} is the name of the class of the query without the package name.
 */
@Target( { TYPE, FIELD })
@Retention(RUNTIME)
public @interface Help
{
    String value();
}

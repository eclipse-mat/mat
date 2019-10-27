/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.extension;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.eclipse.mat.query.IQuery;

/**
 * Used to tag {@link IClassSpecificNameResolver} and {@link IRequestDetailsResolver}
 * resolvers with the name of the class that they handle.
 * Can be used as follows:
 * {@code @Subject("com.example.class1") }
 * See {@link Subjects} for multiple class names.
 * <p>Experimental: can also be used to tag {@link IQuery} queries.
 * These queries are only offered in the drop-down menu
 * from the task bar when the class named by {@link #value()}
 * is present in the snapshot. They are also not offered in the
 * pop-up context menu if the objects selected do not include
 * at least one object of type named by {@link #value()}.
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface Subject
{
    String value();
}

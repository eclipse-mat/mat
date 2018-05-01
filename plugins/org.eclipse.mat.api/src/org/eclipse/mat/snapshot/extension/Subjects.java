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

/**
 * Used to tag resolvers with the names of classes that they handle.
 * Can be used as follows:
 * {@code @Subjects({"com.example.class1", "com.example.Class2"}) }
 * or
 * {@code @Subjects("com.example.class1") }
 * See {@link Subject} for a single class name, though this annotation can also be used for a single class name.
 * <p>Experimental: can also be used to tag queries which only make sense when at least one the classes
 * is present in the snapshot.
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface Subjects
{
    String[] value();
}

/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
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
 * Annotates a query which has multiple menu items.
 * annotations.properties replaces this annotation
 * <pre>
 *{@code
<classname>.menu.<nn>.category = 
<classname>.menu.<nn>.label = 
}
 * </pre>
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface Menu
{
    /**
     * Annotates a menu item for a particular sub-query.
     */
    public @interface Entry
    {
        String category() default "";

        String icon() default "";

        String label() default "";

        String help() default "";

        String helpUrl() default "";

        String options() default "";
    }

    Entry[] value();
}

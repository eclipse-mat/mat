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
 * Annotates a query which has multiple menu items for a single query class.
 * annotations.properties replaces this annotation
 * <pre>
 *{@code
<SimpleClassName>.menu.<nn>.category = Java Basics
<SimpleClassName>.menu.<nn>.label = 1|Special query
<SimpleClassName>.menu.<nn>.help =
}
 * </pre>
 * where {@code <SimpleClassName>} is the name of the class of the query without the package name
 * and {@literal <nn>} is a 0-based sequence number to distinguish menu entries.
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface Menu
{
    /**
     * Annotates a {@link Menu} item for a particular sub-query.
     */
    public @interface Entry
    {
        /**
         * The category for the menu item, optionally preceded by a number indicating the menu order with a vertical bar separator.
         * Overrides the {@link Category} for the query class.
         * @return the menu category
         */
        String category() default "";

        /**
         * The icon path for the menu item. Overrides the {@link Icon} for the query class.
         * @return the icon path
         */
        String icon() default "";

        /**
         * The label for the menu item, optionally preceded by a number indicating the menu order with a vertical bar separator.
         * The equivalent of the {@link Name} for an ordinary query.
         * @return
         */
        String label() default "";

        /**
         * The help for the menu item. Overrides the help for the query class.
         * @return the help
         */
        String help() default "";

        /**
         * The help URL for the menu item. Overrides the help URL for the query class.
         * @return the help URL
         */
        String helpUrl() default "";

        /**
         * The specific options for the query for this menu item. Overrides default 
         * argument options for the query class.
         * @return the specific options
         */
        String options() default "";
    }

    Entry[] value();
}

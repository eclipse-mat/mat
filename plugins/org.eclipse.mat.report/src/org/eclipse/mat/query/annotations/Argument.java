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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.eclipse.mat.query.IQuery;

/**
 * Annotates a member variable to be an query argument.
 * Memory Analyzer queries implementing {@link IQuery} use @Argument
 * to annotate query arguments.
 * Heap dump providers using implementations of {@link org.eclipse.mat.snapshot.acquire.IHeapDumpProvider} 
 * and subclasses of {@link org.eclipse.mat.snapshot.acquire.VmInfo} can use @Argument to annotate extra arguments.
 */
@Target( { FIELD })
@Retention(RUNTIME)
public @interface Argument
{
    /**
     * Optional advice for the query argument that is needed if the declaring
     * type does not give enough evidence how to convert or validate this
     * argument.
     */
    enum Advice
    {
        /**
         * By default, the conversion and validation rules are applied that
         * result from the declared type.
         */
        NONE,
        /**
         * Indicates that the (primitive) Integer or List / Array of Integers
         * shall represent heap objects.
         */
        HEAP_OBJECT,
        /**
         * Indicates that the argument of type
         * {@link org.eclipse.mat.snapshot.ISnapshot} relates to a snapshot
         * other than the current one.
         */
        SECONDARY_SNAPSHOT,
        /**
         * Indicates that the argument of type java.util.Pattern specifies a
         * class name pattern. Therefore the appropriate smart fixing is
         * applied.
         */
        CLASS_NAME_PATTERN,
        /**
         * Used with an argument of type File this should indicate that 
         * the parameter represents a directory. The default for File arguments
         * is a file.
         * @since 1.0
         */
        DIRECTORY,
        /**
         * Used with an argument of type File this should indicate that 
         * the parameter represents a file to be created or written to. The default for File arguments
         * is a file to be opened.
         * @since 1.0
         */
        SAVE
    }

    /**
     * The name of the flag, used for query arguments table and for specifying command line arguments.
     * The default, "", means use the name of the argument field. {@link #UNFLAGGED} or {@value #UNFLAGGED} means for the command line 
     * query no flag should be specified before the argument.
     */
    String flag() default "";

    /**
     * A constant for the {@link #flag()} annotation parameter to show that for a command line query no flag should be specified before the argument. For a query dialog the field name without
     * a leading dash is used as the argument name.
     * @since 1.0
     */
    static final String UNFLAGGED = "none"; //$NON-NLS-1$

    /**
     * Indicates whether the argument is mandatory (default)
     */
    boolean isMandatory() default true;

    /**
     * If needed, the type of the argument.
     */
    Advice advice() default Argument.Advice.NONE;

}

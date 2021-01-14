/*******************************************************************************
 * Copyright (c) 2010,2011 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - documentation
 *******************************************************************************/
package org.eclipse.mat.query.annotations.descriptors;

import java.lang.reflect.Field;

import org.eclipse.mat.query.annotations.Argument;

/**
 * A descriptor for fields annotated with the annotation @Argument.
 * @since 1.0
 *
 */
public interface IArgumentDescriptor
{

	/**
	 * Check if the annotated field is an array or a list
	 * @return true if the annotated field is an array or a list
	 */
	public boolean isMultiple();

	/**
	 * Check if the annotated field is a boolean or Boolean
	 * @return true if the annotated field is a boolean or Boolean
	 */
	public boolean isBoolean();

	/**
	 * Get the default value of the field
	 * @return the default value
	 */
	public Object getDefaultValue();

	/**
	 * Get the annotated field
	 * @return the field
	 */
	public Field getField();

	/**
	 * Get the flag which is used in the command line to introduce the argument.
	 * See  {@link org.eclipse.mat.query.annotations.Argument#flag}.
	 * @return the flag
	 */
	public String getFlag();

	/**
	 * Check if the annotated field is an array
	 * @return true if the annotated field is an array
	 */
	public boolean isArray();

	/**
	 * Check if the annotated field is a List
	 * @return true if the annotated field is a List
	 */
	public boolean isList();

	/**
	 * Check if the annotated field is an Enum
	 * @return true if the annotated field is an Enum
	 */
	public boolean isEnum();

	/**
	 * Check if the annotated field is a mandatory parameter
	 * @return true if the annotated field is a mandatory parameter
	 */
	public boolean isMandatory();

	/**
	 * Get the name of the parameter, for example the field name of the argument in its class.
	 * @return the name
	 */
	public String getName();


	/**
	 * Get the type of the annotated field
	 * @return the class of the field
	 */
	public Class<?> getType();

	/**
	 * Get any help on the field, for example provided by the annotation {@link org.eclipse.mat.query.annotations.Help}
	 * @return the help string
	 */
	public String getHelp();

	/**
	 * Get the {@link org.eclipse.mat.query.annotations.Argument.Advice} provided with the annotation
	 * @return the Advice
	 */
	public Argument.Advice getAdvice();

}

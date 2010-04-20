/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.query.annotations.descriptors;

import java.lang.reflect.Field;

import org.eclipse.mat.query.annotations.Argument;

/**
 * A descriptor for fields annotated with the annotation @Argument
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
	 * Get the flag
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
	 * Get the name of the parameter
	 * @return the name
	 */
	public String getName();


	/**
	 * Get the type of the annotated field
	 * @return the class of the field
	 */
	public Class<?> getType();

	/**
	 * Get any help on the field provided by the annotation @Help
	 * @return the help string
	 */
	public String getHelp();

	/**
	 * Get the {@link Advice} provided with the annotation
	 * @return the Advice
	 */
	public Argument.Advice getAdvice();

}
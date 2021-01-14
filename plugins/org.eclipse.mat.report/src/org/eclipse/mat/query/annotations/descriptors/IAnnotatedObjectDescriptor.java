/*******************************************************************************
 * Copyright (c) 2010,2011 SAP AG and IBM Corporation..
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

import java.net.URL;
import java.util.List;
import java.util.Locale;

import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;

/**
 * 
 * A descriptor which allows to inspect an annotated object, e.g. a IQuery
 * @since 1.0
 *
 */
public interface IAnnotatedObjectDescriptor
{

	/**
	 * Get the usage information, for example provided by the annotation {@link org.eclipse.mat.query.annotations.Usage}, or
	 * by a combination of the {@link #getIdentifier()} and {@link ArgumentDescriptor#appendUsage}.
	 * @param context used to fill in some arguments leaving usage to explain the remainder
	 * @return the usage information for that query
	 */
	public String getUsage(IQueryContext context);
	

	/**
	 * Get the Icon representing the annotated object, for example provided by the annotation {@link org.eclipse.mat.query.annotations.Icon}.
	 * @return the Icon as a URL
	 */
	public URL getIcon();

	/**
	 * Get the identifier for the annotated object, for example provided by the annotation {@link org.eclipse.mat.query.annotations.CommandName}
	 * or {@link #getName}.
	 * @return the identifier
	 */
	public String getIdentifier();

	/**
	 * Get the name, for example provided by the annotation {@link org.eclipse.mat.query.annotations.Name}.
	 * @return the name
	 */
	public String getName();

	/**
	 * Get the help String, for example provided by the annotation {@link org.eclipse.mat.query.annotations.Help}.
	 * @return the help
	 */
	public String getHelp();

	/**
	 * Get the help URL, for example provided by the annotation {@link org.eclipse.mat.query.annotations.HelpUrl}.
	 * @return the help URL
	 */
	public String getHelpUrl();

	/**
	 * Get the help locale
	 * @return the locale
	 */
	public Locale getHelpLocale();

	/**
	 * Get descriptors for the fields annotated by the annotation {@link org.eclipse.mat.query.annotations.Argument}.
	 * TODO Should this have been IArgumentDescriptor ?
	 * @return the list of annotated arguments, see {@link ArgumentDescriptor}
	 */
	public List<ArgumentDescriptor> getArguments();

	/**
	 * Check if the object has provided some help via annotations.
	 * @return true if the object or arguments were annotated with {@link org.eclipse.mat.query.annotations.Help}.
	 */
	public boolean isHelpAvailable();

}

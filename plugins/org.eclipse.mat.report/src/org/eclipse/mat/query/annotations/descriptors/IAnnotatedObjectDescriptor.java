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

import java.net.URL;
import java.util.List;
import java.util.Locale;

import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;

/**
 * 
 * A descriptor which allows to inspect an annotated object, e.g. a IQuery
 *
 */
public interface IAnnotatedObjectDescriptor
{

	/**
	 * 
	 * @param context
	 * @return
	 */
	public String getUsage(IQueryContext context);
	

	/**
	 * Get the Icon provided by the annotation @Icon
	 * @return the Icon
	 */
	public URL getIcon();

	/**
	 * Get the identifier for the annotated object 
	 * @return the identifier
	 */
	public String getIdentifier();

	/**
	 * Get the name provided by the annotation @Name
	 * @return the name
	 */
	public String getName();

	/**
	 * Get the help String provided by the annotation @Help
	 * @return the help
	 */
	public String getHelp();

	/**
	 * Get the help URL provided by the annotation @HelpURL
	 * @return the help URL
	 */
	public String getHelpUrl();

	/**
	 * Get the help locale
	 * @return the locale
	 */
	public Locale getHelpLocale();

	/**
	 * Get descriptors for the fields annotated by the annotation @Argument
	 * @return the list of annotated arguments, see {@link ArgumentDescriptor}
	 */
	public List<ArgumentDescriptor> getArguments();

	/**
	 * Check if the object has provided some help via annotations
	 * @return true if the object was annotated with help
	 */
	public boolean isHelpAvailable();

}
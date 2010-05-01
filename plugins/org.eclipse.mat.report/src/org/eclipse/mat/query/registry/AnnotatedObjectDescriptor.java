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
package org.eclipse.mat.query.registry;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;

public class AnnotatedObjectDescriptor implements IAnnotatedObjectDescriptor
{
	protected final String identifier;
	protected String name;
	protected String usage;
	protected final String help;
	protected final String helpUrl;
	protected final Locale helpLocale;
	protected final URL icon;

	protected final List<ArgumentDescriptor> arguments;

	public AnnotatedObjectDescriptor(String identifier, String name, String usage, URL icon, String help, String helpUrl, Locale helpLocale)
	{
		super();
		this.identifier = identifier;
		this.name = name;
		this.usage = usage;
		this.icon = icon;
		this.help = help;
		this.helpUrl = helpUrl;
		this.helpLocale = helpLocale;
		this.arguments = new ArrayList<ArgumentDescriptor>();
	}

	public synchronized String getUsage(IQueryContext context)
	{
		if (usage != null) return usage;
		else if (context == null)
		{
			return usage = identifier;
		}
		else
		{

			StringBuilder buf = new StringBuilder(256);

			buf.append(identifier);

			for (ArgumentDescriptor param : arguments)
			{
				if (context.available(param.getType(), param.getAdvice())) continue;

				param.appendUsage(buf);
			}

			return usage = buf.toString();
		}
	}

	public URL getIcon()
	{
		return icon;
	}

	public void setUsage(String usage)
	{
		this.usage = usage;
	}

	public String getIdentifier()
	{
		return identifier;
	}

	public String getName()
	{
		return name;
	}

	public String getHelp()
	{
		return help;
	}

	public String getHelpUrl()
	{
		return helpUrl;
	}

	public Locale getHelpLocale()
	{
		return helpLocale;
	}

	public List<ArgumentDescriptor> getArguments()
	{
		return arguments;
	}

	public boolean isHelpAvailable()
	{
		if (help != null) return true;

		for (ArgumentDescriptor arg : arguments)
		{
			if (arg.getHelp() != null) return true;
		}

		return false;
	}

	public void addParameter(ArgumentDescriptor descriptor)
	{
		arguments.add(descriptor);
	}

}

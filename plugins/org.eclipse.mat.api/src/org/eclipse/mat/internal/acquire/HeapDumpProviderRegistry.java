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
package org.eclipse.mat.internal.acquire;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Usage;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.snapshot.acquire.IHeapDumpProvider;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.RegistryReader;

public class HeapDumpProviderRegistry extends RegistryReader<IHeapDumpProvider>
{
	private final Map<String, HeapDumpProviderDescriptor> providersByIdentifier = new HashMap<String, HeapDumpProviderDescriptor>();
	private final Map<String, HeapDumpProviderDescriptor> providersByClass = new HashMap<String, HeapDumpProviderDescriptor>();

	private static final HeapDumpProviderRegistry instance = new HeapDumpProviderRegistry();

	private HeapDumpProviderRegistry()
	{
		init(MATPlugin.getDefault().getExtensionTracker(), MATPlugin.PLUGIN_ID + ".heapDumpProvider"); //$NON-NLS-1$
	}

	public static HeapDumpProviderRegistry instance()
	{
		return instance;
	}

	@Override
	protected IHeapDumpProvider createDelegate(IConfigurationElement configElement) throws CoreException
	{
		try
		{
			IHeapDumpProvider dumpProvider = (IHeapDumpProvider) configElement.createExecutableExtension("impl"); //$NON-NLS-1$
			HeapDumpProviderDescriptor descriptor = registerProvider(dumpProvider);

			if (MATPlugin.getDefault().isDebugging()) MATPlugin.log(IStatus.INFO, MessageUtil.format("IHeapDumpProvider '{0}' registered.", descriptor)); //$NON-NLS-1$

			return descriptor != null ? dumpProvider : null;
		}
		catch (SnapshotException e)
		{
			throw new CoreException(new Status(IStatus.ERROR, MATPlugin.PLUGIN_ID, MessageUtil.format(
					"Error registering query: {0}", configElement.getAttribute("impl")), e)); //$NON-NLS-1$
		}
	}

	@Override
	protected void removeDelegate(IHeapDumpProvider delegate)
	{
		for (HeapDumpProviderDescriptor descriptor : providersByIdentifier.values())
		{
			if (descriptor.getSubject() == delegate.getClass())
			{
				providersByIdentifier.remove(descriptor.getIdentifier());
				providersByClass.remove(descriptor.getSubject().getName().toLowerCase(Locale.ENGLISH));
				break;
			}
		}
	}

	public synchronized Collection<HeapDumpProviderDescriptor> getHeapDumpProviders()
	{
		return Collections.unmodifiableCollection(providersByIdentifier.values());
	}

	public synchronized HeapDumpProviderDescriptor getHeapDumpProvider(String name)
	{
		HeapDumpProviderDescriptor descriptor = providersByIdentifier.get(name);
		return descriptor != null ? descriptor : providersByClass.get(name);
	}

	public synchronized HeapDumpProviderDescriptor getHeapDumpProvider(Class<? extends IHeapDumpProvider> providerClass)
	{
		return providersByClass.get(providerClass.getName().toLowerCase(Locale.ENGLISH));
	}

	private final synchronized HeapDumpProviderDescriptor registerProvider(IHeapDumpProvider provider) throws SnapshotException
	{
		Class<? extends IHeapDumpProvider> providerClass = provider.getClass();

		String key = providerClass.getSimpleName();
		ResourceBundle i18n = getBundle(providerClass);

		Name n = providerClass.getAnnotation(Name.class);
		String name = translate(i18n, key + ".name", n != null ? n.value() : providerClass.getSimpleName()); //$NON-NLS-1$

		String identifier = getIdentifier(provider);

		// do NOT overwrite command names
		if (providersByIdentifier.containsKey(identifier))
			throw new SnapshotException(MessageUtil.format("Query name ''{0}'' is already bound to {1}!", identifier, providersByIdentifier.get(identifier)
					.getSubject().getName()));

		Usage u = providerClass.getAnnotation(Usage.class);
		String usage = u != null ? u.value() : null;

		Help h = providerClass.getAnnotation(Help.class);
		String help = translate(i18n, key + ".help", h != null ? h.value() : null); //$NON-NLS-1$
		Locale helpLoc = i18n.getLocale();

		HelpUrl hu = providerClass.getAnnotation(HelpUrl.class);
		String helpUrl = hu != null ? hu.value() : null;

		HeapDumpProviderDescriptor descriptor = new HeapDumpProviderDescriptor(identifier, name, usage, help, helpUrl, helpLoc, provider);

		Class<?> clazz = providerClass;
		while (!clazz.equals(Object.class))
		{
			addArguments(provider, clazz, descriptor, i18n);
			clazz = clazz.getSuperclass();
		}

		providersByIdentifier.put(identifier, descriptor);
		providersByClass.put(provider.getClass().getName().toLowerCase(Locale.ENGLISH), descriptor);
		return descriptor;
	}

	private ResourceBundle getBundle(Class<? extends IHeapDumpProvider> providerClass)
	{
		try
		{
			return ResourceBundle.getBundle(providerClass.getPackage().getName() + ".annotations", //$NON-NLS-1$
					Locale.getDefault(), providerClass.getClassLoader());
		}
		catch (MissingResourceException e)
		{
			return new ResourceBundle() {
				@Override
				protected Object handleGetObject(String key)
				{
					return null;
				}

				@Override
				public Enumeration<String> getKeys()
				{
					return null;
				}

				@Override
				public Locale getLocale()
				{
					// All the standard providers should have annotations, so a
					// missing annotation is for a user supplied
					// provider, so guess it is in the default locale
					return Locale.getDefault();
				}
			};
		}
	}

	private String translate(ResourceBundle i18n, String key, String defaultValue)
	{
		try
		{
			return i18n.getString(key);
		}
		catch (MissingResourceException e)
		{
			return defaultValue;
		}
	}

	private String getIdentifier(IHeapDumpProvider provider)
	{
		Class<? extends IHeapDumpProvider> queryClass = provider.getClass();

		Name n = queryClass.getAnnotation(Name.class);
		String name = n != null ? n.value() : queryClass.getSimpleName();

		CommandName cn = queryClass.getAnnotation(CommandName.class);
		return (cn != null ? cn.value() : name).toLowerCase(Locale.ENGLISH).replace(' ', '_');
	}

	private void addArguments(IHeapDumpProvider provider, Class<?> clazz, HeapDumpProviderDescriptor descriptor, ResourceBundle i18n) throws SnapshotException
	{
		Field[] fields = clazz.getDeclaredFields();

		for (Field field : fields)
		{
			try
			{
				Argument argument = field.getAnnotation(Argument.class);

				if (argument != null)
				{
					ArgumentDescriptor argDescriptor = fromAnnotation(clazz, argument, field, field.get(provider));

					// add help (if available)
					Help h = field.getAnnotation(Help.class);
					String help = translate(i18n, clazz.getSimpleName() + "." + argDescriptor.getName() + ".help", //$NON-NLS-1$//$NON-NLS-2$
							h != null ? h.value() : null);
					if (help != null) argDescriptor.setHelp(help);

					descriptor.addParameter(argDescriptor);
				}
			}
			catch (SnapshotException e)
			{
				throw e;
			}
			catch (IllegalAccessException e)
			{
				String msg = "Unable to access argument ''{0}'' of class ''{1}''. Make sure the attribute is PUBLIC.";
				throw new SnapshotException(MessageUtil.format(msg, field.getName(), clazz.getName()), e);
			}
			catch (Exception e)
			{
				throw new SnapshotException(MessageUtil.format("Error get argument ''{0}'' of class ''{1}''", field.getName(), clazz.getName()), e);
			}
		}
	}

	private ArgumentDescriptor fromAnnotation(Class<?> clazz, Argument annotation, Field field, Object defaultValue) throws SnapshotException
	{
		ArgumentDescriptor d = new ArgumentDescriptor();
		d.setMandatory(annotation.isMandatory());
		d.setName(field.getName());

		String flag = annotation.flag();
		if (flag.length() == 0) flag = field.getName().toLowerCase(Locale.ENGLISH);
		if ("none".equals(flag)) //$NON-NLS-1$
			flag = null;
		d.setFlag(flag);

		d.setField(field);

		d.setArray(field.getType().isArray());
		d.setList(List.class.isAssignableFrom(field.getType()));

		// set type of the argument
		if (d.isArray())
		{
			d.setType(field.getType().getComponentType());
		}
		else if (d.isList())
		{
			Type type = field.getGenericType();
			if (type instanceof ParameterizedType)
			{
				Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
				d.setType((Class<?>) typeArguments[0]);
			}
		}
		else
		{
			d.setType(field.getType());
		}

		// validate the advice
		Argument.Advice advice = annotation.advice();

		if (advice == Argument.Advice.CLASS_NAME_PATTERN && !Pattern.class.isAssignableFrom(d.getType()))
		{
			String msg = MessageUtil.format("Field {0} of {1} has advice {2} but is not of type {3}.", field.getName(), clazz.getName(),
					Argument.Advice.CLASS_NAME_PATTERN, Pattern.class.getName());
			throw new SnapshotException(msg);
		}

		if (advice != Argument.Advice.NONE) d.setAdvice(advice);

		// set the default value
		if (d.isArray() && defaultValue != null)
		{
			// internally, all multiple values have their values held as arrays
			// therefore we convert the array once and for all
			int size = Array.getLength(defaultValue);
			List<Object> l = new ArrayList<Object>(size);
			for (int ii = 0; ii < size; ii++)
			{
				l.add(Array.get(defaultValue, ii));
			}
			d.setDefaultValue(Collections.unmodifiableList(l));
		}
		else
		{
			d.setDefaultValue(defaultValue);
		}

		return d;
	}
}

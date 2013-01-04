/*******************************************************************************
 * Copyright (c) 2008, 2013 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - localization of icons
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Usage;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.report.internal.ReportPlugin;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.RegistryReader;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

public class QueryRegistry extends RegistryReader<IQuery>
{
    private final Map<String, QueryDescriptor> commandsByIdentifier = new HashMap<String, QueryDescriptor>();
    private final Map<String, QueryDescriptor> commandsByClass = new HashMap<String, QueryDescriptor>();

    private CategoryDescriptor rootCategory;

    private static final QueryRegistry instance = new QueryRegistry();

    public static QueryRegistry instance()
    {
        return instance;
    }

    public QueryRegistry()
    {
        init(ReportPlugin.getDefault().getExtensionTracker(), ReportPlugin.PLUGIN_ID + ".query"); //$NON-NLS-1$
    }

    @Override
    protected IQuery createDelegate(IConfigurationElement configElement) throws CoreException
    {
        try
        {
            IQuery query = (IQuery) configElement.createExecutableExtension("impl"); //$NON-NLS-1$
            QueryDescriptor descriptor = registerQuery(query);

            if (ReportPlugin.getDefault().isDebugging())
                ReportPlugin.log(IStatus.INFO, MessageUtil.format(Messages.QueryRegistry_Msg_QueryRegistered,
                                descriptor));

            rootCategory = null;
            return descriptor != null ? query : null;
        }
        catch (SnapshotException e)
        {
            throw new CoreException(new Status(IStatus.ERROR, ReportPlugin.PLUGIN_ID, MessageUtil.format(
                            Messages.QueryRegistry_Error_Registering, configElement.getAttribute("impl")), e)); //$NON-NLS-1$
        }
    }

    @Override
    protected void removeDelegate(IQuery delegate)
    {
        for (QueryDescriptor descriptor : commandsByIdentifier.values())
        {
            if (descriptor.getCommandType() == delegate.getClass())
            {
                commandsByIdentifier.remove(descriptor.getIdentifier());
                commandsByClass.remove(descriptor.getCommandType().getName().toLowerCase(Locale.ENGLISH));
                rootCategory = null;
                break;
            }
        }
    }

    public synchronized Collection<QueryDescriptor> getQueries()
    {
        return Collections.unmodifiableCollection(commandsByIdentifier.values());
    }

    public List<QueryDescriptor> getQueries(Pattern pattern)
    {
        List<QueryDescriptor> answer = new ArrayList<QueryDescriptor>();

        for (Map.Entry<String, QueryDescriptor> entry : commandsByIdentifier.entrySet())
        {
            if (pattern.matcher(entry.getKey()).matches())
                answer.add(entry.getValue());
        }

        return answer;
    }

    public synchronized CategoryDescriptor getRootCategory()
    {
        if (rootCategory == null)
        {
            LinkedList<QueryDescriptor> stack = new LinkedList<QueryDescriptor>();
            stack.addAll(commandsByIdentifier.values());

            rootCategory = new CategoryDescriptor("<root>"); //$NON-NLS-1$
            while (!stack.isEmpty())
            {
                QueryDescriptor descriptor = stack.removeFirst();
                stack.addAll(descriptor.getMenuEntries());

                String category = descriptor.getCategory();
                if (Category.HIDDEN.equals(category))
                    continue;

                if (category == null)
                {
                    rootCategory.add(descriptor);
                }
                else
                {
                    CategoryDescriptor entry = rootCategory.resolve(category);
                    entry.add(descriptor);
                }
            }
        }

        return rootCategory;
    }

    public synchronized QueryDescriptor getQuery(String name)
    {
        QueryDescriptor descriptor = commandsByIdentifier.get(name);
        return descriptor != null ? descriptor : commandsByClass.get(name);
    }

    public synchronized QueryDescriptor getQuery(Class<? extends IQuery> query)
    {
        return commandsByClass.get(query.getName().toLowerCase(Locale.ENGLISH));
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    private String getIdentifier(IQuery query)
    {
        Class<? extends IQuery> queryClass = query.getClass();

        Name n = queryClass.getAnnotation(Name.class);
        String name = n != null ? n.value() : queryClass.getSimpleName();

        CommandName cn = queryClass.getAnnotation(CommandName.class);
        return (cn != null ? cn.value() : name).toLowerCase(Locale.ENGLISH).replace(' ', '_');
    }

    private final synchronized QueryDescriptor registerQuery(IQuery query) throws SnapshotException
    {
        Class<? extends IQuery> queryClass = query.getClass();

        String key = queryClass.getSimpleName();
        ResourceBundle i18n = getBundle(queryClass);

        Name n = queryClass.getAnnotation(Name.class);
        String name = translate(i18n, key + ".name", n != null ? n.value() : queryClass.getSimpleName()); //$NON-NLS-1$

        String identifier = getIdentifier(query);

        // do NOT overwrite command names
        if (commandsByIdentifier.containsKey(identifier))
            throw new SnapshotException(MessageUtil.format(Messages.QueryRegistry_Error_NameBound, identifier,
                            commandsByIdentifier.get(identifier).getCommandType().getName()));

        Category c = queryClass.getAnnotation(Category.class);
        String category = translate(i18n, key + ".category", c != null ? c.value() : null); //$NON-NLS-1$

        Usage u = queryClass.getAnnotation(Usage.class);
        String usage = u != null ? u.value() : null;

        Help h = queryClass.getAnnotation(Help.class);
        String help = translate(i18n, key + ".help", h != null ? h.value() : null); //$NON-NLS-1$
        Locale helpLoc = i18n.getLocale();

        HelpUrl hu = queryClass.getAnnotation(HelpUrl.class);
        String helpUrl = hu != null ? hu.value() : null;

        /*
         * $nl$ and annotations.properties .icon substitution added with 1.3
         * This allows
         * @Icon("$nl$/MANIFEST.MF/icons/myicon.gif")
         * also this which is safe (ignored) for < V1.3
         * myquery.icon = $nl$/MANIFEST.MF/icons/myicon.gif
         */
        Icon i = queryClass.getAnnotation(Icon.class);
        String iconPath = translate(i18n, key + ".icon", i != null ? i.value() : null); //$NON-NLS-1$
        URL icon;
        if (iconPath != null)
        {
            icon = findIcon(queryClass, iconPath);
        }
        else
        {
            icon = null;
        }

        QueryDescriptor descriptor = new QueryDescriptor(identifier, name, category, queryClass, usage, icon, help,
                        helpUrl, helpLoc);

        Class<?> clazz = queryClass;
        while (!clazz.equals(Object.class))
        {
            addArguments(query, clazz, descriptor, i18n);
            clazz = clazz.getSuperclass();
        }

        readMenuEntries(queryClass, descriptor, i18n);

        commandsByIdentifier.put(identifier, descriptor);
        commandsByClass.put(query.getClass().getName().toLowerCase(Locale.ENGLISH), descriptor);
        return descriptor;
    }

    private URL findIcon(Class<? extends IQuery> queryClass, String iconPath)
    {
        URL icon = null;
        ClassLoader cl = queryClass.getClassLoader();
        if (cl instanceof BundleReference)
        {
            // Try FileLocator to allow $arg$ substitutions
            BundleReference br = (BundleReference) cl;
            Bundle bb = br.getBundle();
            icon = FileLocator.find(bb, new Path(iconPath), null);
        }
        if (icon == null)
        {
            // Use old way
            String v = iconPath;
            v = v.replace("$nl$", ""); //$NON-NLS-1$//$NON-NLS-2$
            v = v.replace("$ws$", ""); //$NON-NLS-1$//$NON-NLS-2$
            v = v.replace("$os$", ""); //$NON-NLS-1$//$NON-NLS-2$
            icon = queryClass.getResource(v);
        }
        return icon;
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

    private ResourceBundle getBundle(Class<? extends IQuery> queryClass)
    {
        try
        {
            return ResourceBundle.getBundle(queryClass.getPackage().getName() + ".annotations", //$NON-NLS-1$
                            Locale.getDefault(), queryClass.getClassLoader());
        }
        catch (MissingResourceException e)
        {
            return new ResourceBundle()
            {
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
                    // All the standard queries should have annotations, so a missing annotation
                    // is for a user supplied query, so guess it is in the default locale.
                    return Locale.getDefault();
                }
            };
        }
    }

    private void readMenuEntries(Class<? extends IQuery> queryClass, QueryDescriptor descriptor, ResourceBundle i18n)
    {
        Menu menu = queryClass.getAnnotation(Menu.class);
        if (menu == null || menu.value() == null || menu.value().length == 0)
            return;

        String key = queryClass.getSimpleName() + ".menu."; //$NON-NLS-1$

        int index = 0;
        for (Menu.Entry entry : menu.value())
        {
            String label = translate(i18n, key + index + ".label", entry.label()); //$NON-NLS-1$
            if (label == null || label.length() == 0)
                label = MessageUtil.format(Messages.QueryRegistry_MissingLabel, queryClass.getName(),
                                String.valueOf(index + 1));

            String category = translate(i18n, key + index + ".category", entry.category()); //$NON-NLS-1$
            if (category == null || category.length() == 0)
                category = descriptor.getCategory();

            String help = translate(i18n, key + index + ".help", entry.help()); //$NON-NLS-1$
            if (help == null || help.length() == 0)
                help = descriptor.getHelp();

            String helpUrl = entry.helpUrl();
            if (helpUrl.length() == 0)
                helpUrl = descriptor.getHelpUrl();

            URL icon = descriptor.getIcon();
            String i = translate(i18n, key + index + ".icon", entry.icon()); //$NON-NLS-1$
            if (i.length() > 0)
                icon = findIcon(queryClass, i);

            String options = entry.options();

            if (i18n != null)
            {}

            descriptor.addMenuEntry(label, category, help, helpUrl, icon, options);
            index++;
        }
    }

    private void addArguments(IQuery query, Class<?> clazz, QueryDescriptor descriptor, ResourceBundle i18n)
                    throws SnapshotException
    {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields)
        {
            try
            {
                Argument argument = field.getAnnotation(Argument.class);

                if (argument != null)
                {
                    ArgumentDescriptor argDescriptor = fromAnnotation(clazz, argument, field, field.get(query));

                    // add help (if available)
                    Help h = field.getAnnotation(Help.class);
                    String help = translate(i18n, clazz.getSimpleName() + "." + argDescriptor.getName() + ".help", //$NON-NLS-1$//$NON-NLS-2$
                                    h != null ? h.value() : null);
                    if (help != null)
                        argDescriptor.setHelp(help);

                    descriptor.addParameter(argDescriptor);
                }
            }
            catch (SnapshotException e)
            {
                throw e;
            }
            catch (IllegalAccessException e)
            {
                String msg = Messages.QueryRegistry_Error_Inaccessible;
                throw new SnapshotException(MessageUtil.format(msg, field.getName(), clazz.getName()), e);
            }
            catch (Exception e)
            {
                throw new SnapshotException(MessageUtil.format(Messages.QueryRegistry_Error_Argument, field.getName(),
                                clazz.getName()), e);
            }
        }
    }

    private ArgumentDescriptor fromAnnotation(Class<?> clazz, Argument annotation, Field field, Object defaultValue)
                    throws SnapshotException
    {
        ArgumentDescriptor d = new ArgumentDescriptor();
        d.setMandatory(annotation.isMandatory());
        d.setName(field.getName());

        String flag = annotation.flag();
        if (flag.equals(Argument.UNFLAGGED))
            flag = null;
        else if (flag.length() == 0)
            flag = field.getName().toLowerCase(Locale.ENGLISH);
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
            String msg = MessageUtil.format(Messages.QueryRegistry_Error_Advice, field.getName(), clazz.getName(),
                            Argument.Advice.CLASS_NAME_PATTERN, Pattern.class.getName());
            throw new SnapshotException(msg);
        }

        if (advice != Argument.Advice.NONE)
            d.setAdvice(advice);

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

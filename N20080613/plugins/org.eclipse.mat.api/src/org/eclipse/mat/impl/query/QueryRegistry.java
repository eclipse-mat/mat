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
package org.eclipse.mat.impl.query;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.ApiPlugin;
import org.eclipse.mat.impl.registry.RegistryReader;
import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Usage;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;


public class QueryRegistry extends RegistryReader<IQuery>
{
    private static final List<Class<?>> SUPPORTED_TYPES = Arrays.asList(new Class<?>[] { boolean.class, Boolean.class,
                    String.class, Integer.class, int.class, Double.class, double.class, Long.class, long.class,
                    ISnapshot.class, IObject.class, Pattern.class, IHeapObjectArgument.class, File.class });

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
        init(ApiPlugin.getDefault().getExtensionTracker(), ApiPlugin.PLUGIN_ID + ".query");
    }

    @Override
    protected IQuery createDelegate(IConfigurationElement configElement) throws CoreException
    {
        try
        {
            IQuery query = (IQuery) configElement.createExecutableExtension("impl");
            QueryDescriptor descriptor = registerQuery(query);
            rootCategory = null;
            return descriptor != null ? query : null;
        }
        catch (SnapshotException e)
        {
            throw new CoreException(new Status(IStatus.ERROR, ApiPlugin.PLUGIN_ID, MessageFormat.format(
                            "Error registering query: {0}", configElement.getAttribute("impl")), e));
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
                commandsByClass.remove(descriptor.getCommandType().getName().toLowerCase());
                rootCategory = null;
                break;
            }
        }
    }

    public synchronized Collection<QueryDescriptor> getQueries()
    {
        return Collections.unmodifiableCollection(commandsByIdentifier.values());
    }

    public synchronized CategoryDescriptor getRootCategory()
    {
        if (rootCategory == null)
        {
            LinkedList<QueryDescriptor> stack = new LinkedList<QueryDescriptor>();
            stack.addAll(commandsByIdentifier.values());

            rootCategory = new CategoryDescriptor("<root>");
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
        return commandsByClass.get(query.getName().toLowerCase());
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
        return (cn != null ? cn.value() : name).toLowerCase().replace(' ', '_');
    }

    private final synchronized QueryDescriptor registerQuery(IQuery query) throws SnapshotException
    {
        Class<? extends IQuery> queryClass = query.getClass();

        Name n = queryClass.getAnnotation(Name.class);
        String name = n != null ? n.value() : queryClass.getSimpleName();

        String identifier = getIdentifier(query);

        // do NOT overwrite command names
        if (commandsByIdentifier.containsKey(identifier))
            throw new SnapshotException(MessageFormat.format("Query name ''{0}'' is already bound to {1}!", identifier,
                            commandsByIdentifier.get(identifier).getCommandType().getName()));

        Category c = queryClass.getAnnotation(Category.class);
        String category = c != null ? c.value() : null;

        Usage u = queryClass.getAnnotation(Usage.class);
        String usage = u != null ? u.value() : null;

        Help h = queryClass.getAnnotation(Help.class);
        String help = h != null ? h.value() : null;

        Icon i = queryClass.getAnnotation(Icon.class);
        URL icon = i != null ? queryClass.getResource(i.value()) : null;

        QueryDescriptor descriptor = new QueryDescriptor(identifier, name, category, queryClass, usage, icon, help);

        Class<?> clazz = queryClass;
        while (!clazz.equals(Object.class))
        {
            addArguments(query, clazz, descriptor);
            clazz = clazz.getSuperclass();
        }

        readMenuEntries(queryClass, query, descriptor);

        commandsByIdentifier.put(identifier, descriptor);
        commandsByClass.put(query.getClass().getName().toLowerCase(), descriptor);
        return descriptor;
    }

    private void readMenuEntries(Class<? extends IQuery> queryClass, IQuery query, QueryDescriptor descriptor)
    {
        Menu menu = queryClass.getAnnotation(Menu.class);
        if (menu == null || menu.value() == null || menu.value().length == 0)
            return;

        for (Menu.Entry entry : menu.value())
        {
            String label = entry.label();

            String category = entry.category();
            if (category.length() == 0)
                category = descriptor.getCategory();

            String help = entry.help();
            if (help.length() == 0)
                help = descriptor.getHelp();

            URL icon = descriptor.getIcon();
            String i = entry.icon();
            if (i.length() > 0)
                icon = queryClass.getResource(i);

            String options = entry.options();

            descriptor.addMenuEntry(label, category, help, icon, options);
        }
    }

    private void addArguments(IQuery query, Class<?> clazz, QueryDescriptor descriptor) throws SnapshotException
    {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields)
        {
            try
            {
                Argument argument = field.getAnnotation(Argument.class);

                if (argument != null)
                {
                    if (!isSupportedType(field))
                    {
                        String msg = MessageFormat.format("Type {0} for argument ''{1}'' of class {2} not supported.\n"
                                        + " Choose one of {3}. Or use an array of list of one of these types.", field
                                        .getType().getName(), field.getName(), clazz.getName(), SUPPORTED_TYPES);
                        throw new SnapshotException(msg);
                    }

                    ArgumentDescriptor argDescriptor = fromAnnotation(clazz, argument, field, field.get(query));

                    // check: if it is a heap object, the type of the argument
                    // must be int, Integer or IObject
                    if (argDescriptor.isHeapObject())
                    {
                        Class<?> type = argDescriptor.getType();
                        if (type != int.class && type != Integer.class && !IObject.class.isAssignableFrom(type)
                                        && !IHeapObjectArgument.class.isAssignableFrom(type))
                        {
                            String txt = "Heap object argument ''{1}'' of class {2} is of unsupported type {0}.\n "
                                            + "Only Integer, int, IObject and IHeapObjectArgument are supported as heap objects.";
                            String msg = MessageFormat.format(txt, field.getType().getName(), field.getName(), clazz
                                            .getName());
                            throw new SnapshotException(msg);
                        }
                    }

                    boolean isSnapshotArgument = ISnapshot.class.isAssignableFrom(argDescriptor.getType());

                    // check: all but one snapshot argument must be of type
                    // secondary snapshot
                    if (isSnapshotArgument && argDescriptor.getAdvice() != Argument.Advice.SECONDARY_SNAPSHOT)
                    {
                        if (descriptor.getPrimarySnapshotArgument() != null)
                        {
                            String txt = "Snapshot argument ''{0}'' of class {1} is must be annotated with @Argument(advice = Argument.Advice.SECONDARY_SNAPSHOT).\n "
                                            + "At most one snapshot argument without annotation is allowed per query";
                            String msg = MessageFormat.format(txt, field.getName(), clazz.getName());
                            throw new SnapshotException(msg);
                        }

                        argDescriptor.setPrimarySnapshot(true);
                        descriptor.setPrimarySnapshotArgument(argDescriptor);
                    }

                    // check: only one unflagged mandatory argument allowed
                    if (!isSnapshotArgument && argDescriptor.getFlag() == null)
                    {
                        if (descriptor.getUnflaggedArgument() != null)
                        {
                            String txt = "Snapshot argument ''{0}'' of class {1} is must be annotated with @Argument( flag = \"flag\" ).\n "
                                            + "At most one unflagged mandatory argument is allowed per query";
                            String msg = MessageFormat.format(txt, field.getName(), clazz.getName());
                            throw new SnapshotException(msg);
                        }

                        descriptor.setUnflaggedArgument(argDescriptor);
                    }

                    // add help (if available)
                    Help help = field.getAnnotation(Help.class);
                    if (help != null)
                        argDescriptor.setHelp(help.value());

                    descriptor.addParamter(argDescriptor);
                }
            }
            catch (SnapshotException e)
            {
                throw e;
            }
            catch (IllegalAccessException e)
            {
                String msg = "Unable to access argument ''{0}'' of class ''{1}''. Make sure the attribute is PUBLIC.";
                throw new SnapshotException(MessageFormat.format(msg, field.getName(), clazz.getName()), e);
            }
            catch (Exception e)
            {
                throw new SnapshotException(MessageFormat.format("Error get argument ''{0}'' of class ''{1}''", field
                                .getName(), clazz.getName()), e);
            }
        }
    }

    private boolean isSupportedType(Field field)
    {
        Class<?> fieldType = field.getType();

        // check for arrays
        if (fieldType.isArray())
            fieldType = fieldType.getComponentType();

        // supported via List and a given generic type
        if (List.class.isAssignableFrom(fieldType))
        {
            Type type = field.getGenericType();
            if (type instanceof ParameterizedType)
            {
                Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
                if (typeArguments.length == 1)
                {
                    fieldType = (Class<?>) typeArguments[0];
                }
                else
                {
                    return false;
                }
            }
        }

        // directly supported
        if (SUPPORTED_TYPES.contains(fieldType))
            return true;

        // check if it is an enumeration
        if (fieldType.isEnum())
            return true;

        return false;
    }

    private ArgumentDescriptor fromAnnotation(Class<?> clazz, Argument annotation, Field field, Object defaultValue)
                    throws SnapshotException
    {
        ArgumentDescriptor d = new ArgumentDescriptor();
        d.setMandatory(annotation.isMandatory());
        d.setName(field.getName());

        String flag = annotation.flag();
        if (flag.length() == 0)
            flag = field.getName().toLowerCase();
        if ("none".equals(flag))
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

        boolean isNativeHeapObject = IObject.class.isAssignableFrom(d.getType())
                        || IHeapObjectArgument.class.isAssignableFrom(d.getType());
        boolean isIntegerHeapObject = advice == Argument.Advice.HEAP_OBJECT
                        && (int.class.isAssignableFrom(d.getType()) || Integer.class.isAssignableFrom(d.getType()));

        if (isNativeHeapObject || isIntegerHeapObject)
        {
            d.setHeapObject(true);
            advice = Argument.Advice.HEAP_OBJECT;
        }
        else if (advice == Argument.Advice.HEAP_OBJECT)
        {
            String pattern = "Field {0} of {1} has advice {2} but is not one of the object types (int, Integer, IObject, IHeapObjectArgument).";
            String msg = MessageFormat.format(pattern, field.getName(), clazz.getName(), Argument.Advice.HEAP_OBJECT);
            throw new SnapshotException(msg);
        }

        if (advice == Argument.Advice.CLASS_NAME_PATTERN && !Pattern.class.isAssignableFrom(d.getType()))
        {
            String msg = MessageFormat.format("Field {0} of {1} has advice {2} but is not of type {3}.", field
                            .getName(), clazz.getName(), Argument.Advice.CLASS_NAME_PATTERN, Pattern.class.getName());
            throw new SnapshotException(msg);
        }

        if (advice == Argument.Advice.SECONDARY_SNAPSHOT && !ISnapshot.class.isAssignableFrom(d.getType()))
        {
            String msg = MessageFormat.format("Field {0} of {1} has advice {2} but is not of type {3}.", field
                            .getName(), clazz.getName(), Argument.Advice.SECONDARY_SNAPSHOT, ISnapshot.class.getName());
            throw new SnapshotException(msg);
        }

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

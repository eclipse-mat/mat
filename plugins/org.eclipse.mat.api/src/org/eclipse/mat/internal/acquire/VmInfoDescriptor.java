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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.registry.AnnotatedObjectDescriptor;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.snapshot.acquire.VmInfo;
import org.eclipse.mat.util.MessageUtil;

/**
 * @noextend This class is not intended to be subclassed by clients.
 * @author ktsvetkov
 *
 */
public class VmInfoDescriptor extends AnnotatedObjectDescriptor
{
	protected final VmInfo vmInfo;

    public VmInfoDescriptor(String identifier, String name, String usage, String help, String helpUrl,
                    Locale helpLocale, VmInfo vmInfo)
    {
        super(identifier, name, usage, null, help, helpUrl, helpLocale);
        this.vmInfo = vmInfo;
    }
	
	public VmInfo getVmInfo()
    {
        return vmInfo;
    }

    public static final VmInfoDescriptor createDescriptor(VmInfo vmInfo) throws SnapshotException
    {
        Class<? extends VmInfo> vmInfoClass = vmInfo.getClass();

        ResourceBundle i18n = getBundle(vmInfoClass);
        Locale helpLoc = i18n.getLocale();

        Name n = vmInfoClass.getAnnotation(Name.class);
        String name = translate(i18n, vmInfoClass.getSimpleName() + ".name", //$NON-NLS-1$
                        n != null ? n.value() : vmInfoClass.getSimpleName());

        Help h = vmInfoClass.getAnnotation(Help.class);
        String help = translate(i18n, vmInfoClass.getSimpleName() + ".help", //$NON-NLS-1$
                        h != null ? h.value() : null);

        VmInfoDescriptor descriptor = new VmInfoDescriptor(vmInfoClass.getSimpleName(), name, null, help, null,
                        helpLoc, vmInfo);

        Class<?> clazz = vmInfo.getClass();
        while (!clazz.equals(Object.class))
        {
            addArguments(vmInfo, clazz, descriptor, i18n);
            clazz = clazz.getSuperclass();
        }

        return descriptor;
    }

    private static ResourceBundle getBundle(Class<? extends VmInfo> providerClass)
    {
        try
        {
            return ResourceBundle.getBundle(providerClass.getPackage().getName() + ".annotations", //$NON-NLS-1$
                            Locale.getDefault(), providerClass.getClassLoader());
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
                    // All the standard providers should have annotations, so a
                    // missing annotation is for a user supplied
                    // provider, so guess it is in the default locale
                    return Locale.getDefault();
                }
            };
        }
    }

    private static void addArguments(VmInfo provider, Class<?> clazz, VmInfoDescriptor descriptor, ResourceBundle i18n)
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
                    ArgumentDescriptor argDescriptor = fromAnnotation(clazz, argument, field, field.get(provider));

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
                String msg = Messages.VmInfoDescriptor_UnableToAccessArgumentErrorMsg;
                throw new SnapshotException(MessageUtil.format(msg, field.getName(), clazz.getName()), e);
            }
            catch (Exception e)
            {
                throw new SnapshotException(MessageUtil.format(Messages.VmInfoDescriptor_ErrorGettingArgumentErrorMsg, field
                                .getName(), clazz.getName()), e);
            }
        }
    }

    private static ArgumentDescriptor fromAnnotation(Class<?> clazz, Argument annotation, Field field,
                    Object defaultValue) throws SnapshotException
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
            String msg = MessageUtil.format(Messages.VmInfoDescriptor_WrongTypeErrorMsg, field.getName(),
                            clazz.getName(), Argument.Advice.CLASS_NAME_PATTERN, Pattern.class.getName());
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

    private static String translate(ResourceBundle i18n, String key, String defaultValue)
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

}

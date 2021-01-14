/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - better error message
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;

public class Converters
{
    /* package */interface IConverter<O>
    {
        String toString(O object);

        O toObject(String string, Argument.Advice advice);
    }

    private static final Map<Class<?>, IConverter<?>> converters = new HashMap<Class<?>, IConverter<?>>();

    static
    {
        register(String.class, new StringFormat());
        register(Boolean.class, new BooleanFormat());
        register(boolean.class, new BooleanFormat());
        register(Integer.class, new IntegerFormat());
        register(int.class, new IntegerFormat());
        register(Long.class, new LongFormat());
        register(long.class, new LongFormat());
        register(Double.class, new DoubleFormat());
        register(double.class, new DoubleFormat());
        register(Float.class, new FloatFormat());
        register(float.class, new FloatFormat());
        register(Short.class, new ShortFormat());
        register(short.class, new ShortFormat());

        register(Pattern.class, new PatternFormat());
        register(File.class, new FileConverter());
    }

    private static void register(Class<?> clazz, IConverter<?> converter)
    {
        converters.put(clazz, converter);
    }

    @SuppressWarnings("unchecked")
    /* package */static IConverter<Object> getConverter(Class<?> clazz)
    {
        IConverter<Object> converter = (IConverter<Object>) converters.get(clazz);
        if (converter == null && clazz.isEnum())
            converters.put(clazz, converter = new EnumConverter(clazz));
        return converter;
    }

    public static String convertAndEscape(Class<?> type, Object value)
    {
        String result;

        IConverter<Object> converter = getConverter(type);
        if (converter != null)
            result = converter.toString(value);
        else
            result = String.valueOf(value);

        boolean hasQuote = result.indexOf('"') >= 0;
        boolean hasSpace = result.indexOf(' ') >= 0;
        for (int ii = 0; !hasSpace && ii < result.length(); ii++)
        {
            if (Character.isWhitespace(result.charAt(ii)))
                hasSpace = true;
        }
        boolean hasBackslash = result.indexOf("\\\\") >= 0 || result.indexOf("\\\"") >= 0; //$NON-NLS-1$ //$NON-NLS-2$

        if (!hasQuote && !hasSpace && !hasBackslash)
            return result;

        StringBuilder buf = new StringBuilder(result.length() * 110 / 100);
        if (hasSpace)
            buf.append("\""); //$NON-NLS-1$

        for (int ii = 0; ii < result.length(); ii++)
        {

            if (hasQuote && result.charAt(ii) == '"')
                buf.append("\\"); //$NON-NLS-1$

            if (hasBackslash && ii + 1 < result.length() && result.charAt(ii) == '\\'
                            && (result.charAt(ii + 1) == '\\' || result.charAt(ii + 1) == '"'))
                buf.append("\\"); //$NON-NLS-1$                

            buf.append(result.charAt(ii));
        }

        if (hasSpace)
            buf.append("\""); //$NON-NLS-1$

        return buf.toString();
    }

    // //////////////////////////////////////////////////////////////
    // format implementations
    // //////////////////////////////////////////////////////////////

    private static final class EnumConverter implements IConverter<Object>
    {
        Object[] values;
        String[] names;

        public EnumConverter(Class<?> enumClass)
        {
            this.values = enumClass.getEnumConstants();

            try
            {
                Method nameMethod = enumClass.getMethod("name"); //$NON-NLS-1$
                this.names = new String[this.values.length];

                for (int ii = 0; ii < this.values.length; ii++)
                {
                    this.names[ii] = (String) nameMethod.invoke(this.values[ii]);
                }
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException(e);
            }
            catch (NoSuchMethodException e)
            {
                throw new RuntimeException(e);
            }
        }

        public Object toObject(String string, Advice advice)
        {
            for (int ii = 0; ii < names.length; ii++)
            {
                if (names[ii].equals(string))
                    return values[ii];
            }

            throw new IllegalArgumentException(MessageUtil.format(Messages.Converters_Error_InvalidEnumValue, Arrays
                            .toString(names), string));
        }

        public String toString(Object object)
        {
            for (int ii = 0; ii < values.length; ii++)
            {
                if (values[ii] == object)
                    return names[ii];
            }

            return null;
        }
    }

    private static final class StringFormat implements IConverter<String>
    {
        public String toObject(String string, Advice advice)
        {
            return string;
        }

        public String toString(String object)
        {
            return object;
        }
    }

    private static final class IntegerFormat implements IConverter<Integer>
    {
        public Integer toObject(String string, Advice advice)
        {
            return Integer.parseInt(string);
        }

        public String toString(Integer object)
        {
            return String.valueOf(object);
        }
    }

    private static final class DoubleFormat implements IConverter<Double>
    {
        public Double toObject(String string, Advice advice)
        {
            return Double.parseDouble(string);
        }

        public String toString(Double object)
        {
            return String.valueOf(object);
        }
    }

    private static final class FloatFormat implements IConverter<Float>
    {
        public Float toObject(String string, Advice advice)
        {
            return Float.parseFloat(string);
        }

        public String toString(Float object)
        {
            return String.valueOf(object);
        }
    }

    private static final class ShortFormat implements IConverter<Short>
    {
        public Short toObject(String string, Advice advice)
        {
            return Short.parseShort(string);
        }

        public String toString(Short object)
        {
            return String.valueOf(object);
        }
    }

    private static final class LongFormat implements IConverter<Long>
    {
        public Long toObject(String string, Advice advice)
        {
            return Long.parseLong(string);
        }

        public String toString(Long object)
        {
            return String.valueOf(object);
        }
    }

    private static final class BooleanFormat implements IConverter<Boolean>
    {
        public Boolean toObject(String string, Advice advice)
        {
            return Boolean.parseBoolean(string);
        }

        public String toString(Boolean object)
        {
            return object.toString();
        }
    }

    private static final class PatternFormat implements IConverter<Pattern>
    {
        public Pattern toObject(String pattern, Advice advice)
        {
            if (advice == Argument.Advice.CLASS_NAME_PATTERN)
                pattern = PatternUtil.smartFix(pattern, false);

            return Pattern.compile(pattern);
        }

        public String toString(Pattern object)
        {
            return object.pattern();
        }
    }

    private static class FileConverter implements IConverter<File>
    {

        public File toObject(String path, Advice advice)
        {
            return new File(path);
        }

        public String toString(File file)
        {
            return file.getAbsolutePath();
        }

    }

}

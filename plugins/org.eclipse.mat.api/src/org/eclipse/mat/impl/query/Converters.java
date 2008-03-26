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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.PatternUtil;


public class Converters
{
    private static final Map<Class<?>, IConverter<?>> converters = new HashMap<Class<?>, IConverter<?>>();

    static
    {
        register(Integer.class, new IntegerFormat());
        register(int.class, new IntegerFormat());
        register(Double.class, new DoubleFormat());
        register(double.class, new DoubleFormat());
        register(Long.class, new LongFormat());
        register(long.class, new LongFormat());
        register(Boolean.class, new BooleanFormat());
        register(boolean.class, new BooleanFormat());
        register(String.class, new StringFormat());
        register(Pattern.class, new PatternFormat());
        register(ISnapshot.class, new SnapshotFormat());
        register(File.class, new FileConverter());
    }

    public static void register(Class<?> clazz, IConverter<?> converter)
    {
        converters.put(clazz, converter);
    }

    @SuppressWarnings("unchecked")
    public static IConverter<Object> getConverter(Class<?> clazz)
    {
        IConverter<Object> converter = (IConverter<Object>) converters.get(clazz);
        if (converter == null && clazz.isEnum())
            converters.put(clazz, converter = new EnumConverter(clazz));
        return converter;
    }

    public static String convertAndEscape(Class<?> type, Object value)
    {
        String result;

        IConverter<Object> converter = (IConverter<Object>) getConverter(type);
        if (converter != null)
            result = converter.toString(value);
        else
            result = String.valueOf(value);

        boolean hasQuote = result.indexOf('"') >= 0;
        boolean hasSpace = result.indexOf(' ') >= 0;

        if (!hasQuote && !hasSpace)
            return result;

        StringBuilder buf = new StringBuilder(result.length() * 110 / 100);
        for (int ii = 0; ii < result.length(); ii++)
        {
            if (hasSpace && ii == 0)
                buf.append("\"");

            if (hasQuote && result.charAt(ii) == '"')
                buf.append("\\");

            buf.append(result.charAt(ii));
        }

        if (hasSpace)
            buf.append("\"");

        return buf.toString();
    }

    // //////////////////////////////////////////////////////////////
    // format implementations
    // //////////////////////////////////////////////////////////////

    static final class EnumConverter implements IConverter<Object>
    {
        Object[] values;
        String[] names;

        public EnumConverter(Class<?> enumClass)
        {
            this.values = enumClass.getEnumConstants();

            try
            {
                Method nameMethod = enumClass.getMethod("name");
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

            throw new RuntimeException(MessageFormat.format("Must be one of {0}", Arrays.toString(names)));
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

    static final class StringFormat implements IConverter<String>
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

    static final class IntegerFormat implements IConverter<Integer>
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

    static final class DoubleFormat implements IConverter<Double>
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

    static final class LongFormat implements IConverter<Long>
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

    static final class BooleanFormat implements IConverter<Boolean>
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

    static final class PatternFormat implements IConverter<Pattern>
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

    static final class SnapshotFormat implements IConverter<SnapshotArgument>
    {
        public SnapshotArgument toObject(String string, Advice advice)
        {
            return new SnapshotArgument(string);
        }

        public String toString(SnapshotArgument object)
        {
            return object.getFilename();
        }
    }

    static class FileConverter implements IConverter<File>
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

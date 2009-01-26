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
package org.eclipse.mat.query.registry;

import java.lang.reflect.Field;
import java.util.List;

import org.eclipse.mat.query.annotations.Argument;

public class ArgumentDescriptor
{
    private boolean isMandatory;
    private String name;
    private String flag;
    private String help;

    private Field field;
    private boolean isArray;
    private boolean isList;
    private Class<?> type;
    private Argument.Advice advice;

    private Object defaultValue;

    public boolean isMultiple()
    {
        return isArray || isList;
    }

    public boolean isBoolean()
    {
        return type == Boolean.class || type == boolean.class;
    }

    public Object getDefaultValue()
    {
        return defaultValue;
    }

    void setDefaultValue(Object defaultValue)
    {
        this.defaultValue = defaultValue;
    }

    public Field getField()
    {
        return field;
    }

    void setField(Field field)
    {
        this.field = field;
    }

    public String getFlag()
    {
        return flag;
    }

    void setFlag(String flag)
    {
        this.flag = flag;
    }

    public boolean isArray()
    {
        return isArray;
    }

    void setArray(boolean isArray)
    {
        this.isArray = isArray;
    }

    public boolean isList()
    {
        return isList;
    }

    void setList(boolean isList)
    {
        this.isList = isList;
    }

    public boolean isEnum()
    {
        return type.isEnum();
    }

    public boolean isMandatory()
    {
        return isMandatory;
    }

    void setMandatory(boolean isMandatory)
    {
        this.isMandatory = isMandatory;
    }

    public String getName()
    {
        return name;
    }

    void setName(String name)
    {
        this.name = name;
    }

    public Class<?> getType()
    {
        return type;
    }

    void setType(Class<?> type)
    {
        this.type = type;
    }

    public String getHelp()
    {
        return help;
    }

    void setHelp(String help)
    {
        this.help = help;
    }

    public Argument.Advice getAdvice()
    {
        return advice;
    }

    /* package */void setAdvice(Argument.Advice advice)
    {
        this.advice = advice;
    }

    @SuppressWarnings("nls")
    @Override
    public String toString()
    {
        return new StringBuilder(256).append(name).append("(isRequired=").append(isMandatory).append(",flag=").append(
                        flag).append(",type=").append(type.getName()).append(")").toString();
    }

    @SuppressWarnings("nls")
    void appendUsage(StringBuilder buf)
    {
        buf.append(" ");
        if (!isMandatory())
            buf.append("[");

        if (flag != null)
            buf.append("-").append(flag);

        if (isMultiple())
        {
            if (flag != null)
                buf.append(" ");

            buf.append("<").append(name).append("0 .. N>");
        }
        else if (!isBoolean())
        {
            if (flag != null)
                buf.append(" ");
            buf.append("<").append(name).append(">");
        }

        if (!isMandatory())
            buf.append("]");
    }

    @SuppressWarnings("nls")
    void appendUsage(StringBuilder buf, Object value)
    {
        if (value instanceof ArgumentFactory)
        {
            if (flag != null)
                buf.append("-").append(flag).append(" ");
            ((ArgumentFactory) value).appendUsage(buf);
            return;
        }

        if (isMultiple() && (value == null || ((List<?>) value).isEmpty()))
            return;

        buf.append(" ");

        if (isBoolean())
        {
            if (value != null && ((Boolean) value).booleanValue())
                buf.append("-").append(flag);
        }
        else
        {
            if (flag != null)
                buf.append("-").append(flag).append(" ");

            if (value == null)
            {
                buf.append("\"\"");
            }
            else if (isMultiple())
            {
                List<?> values = (List<?>) value;
                for (Object v : values)
                {
                    if (v == null)
                    {
                        buf.append("\"\"");
                    }
                    else if (v instanceof ArgumentFactory)
                    {
                        ((ArgumentFactory) v).appendUsage(buf);
                        buf.append(" ");
                    }
                    else
                    {
                        buf.append(Converters.convertAndEscape(type, v)).append(" ");
                    }
                }
            }
            else
            {
                buf.append(Converters.convertAndEscape(type, value));
            }
        }
    }

}

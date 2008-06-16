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

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.snapshot.SnapshotException;


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

    private boolean isHeapObject;
    private boolean isPrimarySnapshot;

    private Object defaultValue;

    public Object stringToValue(String value) throws SnapshotException
    {
        try
        {
            if (isHeapObject)
            {
                return CommandLine.parseHeapObjectArgument(value);
            }
            else
            {
                IConverter<Object> cnv = Converters.getConverter(type);

                if (cnv == null)
                    throw new SnapshotException(MessageFormat.format(
                                    "No converter registered for type ''{0}'' of argument ''{1}''", type.getName(),
                                    name));

                return cnv.toObject(value, this.advice);
            }
        }
        catch (RuntimeException e)
        {
            String description = this.flag != null ? this.flag : this.name;
            throw new SnapshotException(MessageFormat.format("Error converting argument ''{0}'': {1}", description, e
                            .getMessage()), e);
        }
    }

    public String valueToString(Object value) throws SnapshotException
    {
        if (isHeapObject)
        {
            return String.valueOf(value);
        }
        else
        {
            IConverter<Object> cnv = Converters.getConverter(type);

            if (cnv == null)
                throw new SnapshotException(MessageFormat.format(
                                "No converter registered for type {0} of parameter {1}", type.getName(), name));

            return cnv.toString(value);
        }
    }

    public boolean isMultiple()
    {
        return isArray || isList;
    }

    public boolean isBoolean()
    {
        return type == Boolean.class || type == boolean.class;
    }

    public boolean isMultipleHeapObjects()
    {
        return isHeapObject && (isArray || isList || IHeapObjectArgument.class.isAssignableFrom(type));
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

    boolean isArray()
    {
        return isArray;
    }

    void setArray(boolean isArray)
    {
        this.isArray = isArray;
    }

    boolean isList()
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

    public boolean isHeapObject()
    {
        return isHeapObject;
    }

    void setHeapObject(boolean isHeapObject)
    {
        this.isHeapObject = isHeapObject;
    }

    public boolean isPrimarySnapshot()
    {
        return isPrimarySnapshot;
    }

    void setPrimarySnapshot(boolean isPrimarySnapshot)
    {
        this.isPrimarySnapshot = isPrimarySnapshot;
    }

    public Argument.Advice getAdvice()
    {
        return advice;
    }

    /* package */void setAdvice(Argument.Advice advice)
    {
        this.advice = advice;
    }

    @Override
    public String toString()
    {
        return new StringBuilder(256).append(name).append("(isRequired=").append(isMandatory).append(",flag=").append(
                        flag).append(",type=").append(type.getName()).append(")").toString();
    }

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

    void appendUsage(StringBuilder buf, Object value)
    {
        if (isMultiple() && !isHeapObject() && (value == null || ((List<?>) value).isEmpty()))
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
            else if (isHeapObject())
            {
                buf.append(value.toString());
            }
            else if (isMultiple())
            {
                List<?> values = (List<?>) value;
                for (Object v : values)
                    buf.append(v == null ? "\"\"" : Converters.convertAndEscape(type, v)).append(" ");
            }
            else
            {
                buf.append(Converters.convertAndEscape(type, value));
            }
        }
    }

}

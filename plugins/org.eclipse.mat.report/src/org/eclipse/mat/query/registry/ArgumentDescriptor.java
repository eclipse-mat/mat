/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - documentation
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import java.lang.reflect.Field;
import java.util.List;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.descriptors.IArgumentDescriptor;

/**
 * Provides details about an argument to be injected into a query or heap dump provider.
 */
public class ArgumentDescriptor implements IArgumentDescriptor
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

	public void setDefaultValue(Object defaultValue)
	{
		this.defaultValue = defaultValue;
	}

	public Field getField()
	{
		return field;
	}

	public void setField(Field field)
	{
		this.field = field;
	}

	public String getFlag()
	{
		return flag;
	}

	public void setFlag(String flag)
	{
		this.flag = flag;
	}

	public boolean isArray()
	{
		return isArray;
	}

	public void setArray(boolean isArray)
	{
		this.isArray = isArray;
	}

	public boolean isList()
	{
		return isList;
	}

	public void setList(boolean isList)
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

	public void setMandatory(boolean isMandatory)
	{
		this.isMandatory = isMandatory;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Class<?> getType()
	{
		return type;
	}

	public void setType(Class<?> type)
	{
		this.type = type;
	}

	public String getHelp()
	{
		return help;
	}

	public void setHelp(String help)
	{
		this.help = help;
	}

	public Argument.Advice getAdvice()
	{
		return advice;
	}

	public void setAdvice(Argument.Advice advice)
	{
		this.advice = advice;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString()
	{
		return new StringBuilder(256).append(name).append("(isRequired=").append(isMandatory).append(",flag=").append(flag).append(",type=").append(
				type.getName()).append(")").toString();
	}

	/**
	 * Adds usage information for the argument to the buffer.
	 * @param buf
	 */
	@SuppressWarnings("nls")
	void appendUsage(StringBuilder buf)
	{
		buf.append(" ");
		if (!isMandatory()) buf.append("[");

		if (flag != null) buf.append("-").append(flag);

		if (isMultiple())
		{
			if (flag != null) buf.append(" ");

			buf.append("<").append(name).append("0 .. N>");
		}
		else if (!isBoolean())
		{
			if (flag != null) buf.append(" ");
			if (isEnum())
			{
				boolean first = true;
				for (Enum<?> o : type.asSubclass(Enum.class).getEnumConstants())
				{
					if (first)
					{
						first = false;
					}
					else
					{
						buf.append("|");
					}
					buf.append(o.name());
				}
			}
			else
			{
				buf.append("<").append(name).append(">");
			}
		}

		if (!isMandatory()) buf.append("]");
	}

	/**
	 * Builds a description of how the argument can be set with the value from the command line.
	 * @param buf
	 * @param value
	 */
	@SuppressWarnings("nls")
	void appendUsage(StringBuilder buf, Object value)
	{
		if (value instanceof ArgumentFactory)
		{
			buf.append(" ");
			if (flag != null) buf.append("-").append(flag).append(" ");
			((ArgumentFactory) value).appendUsage(buf);
			return;
		}

		if (isMultiple() && (value == null || ((List<?>) value).isEmpty())) return;

		buf.append(" ");

		if (isBoolean())
		{
			if (value != null && ((Boolean) value).booleanValue()) buf.append("-").append(flag);
		}
		else
		{
			if (flag != null) buf.append("-").append(flag).append(" ");

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

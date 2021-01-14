/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.query.results;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;

/**
 * A list of items such as properties as a result table. Compare to
 * {@link PropertyResult} which extracts and displays from a single object.
 * 
 * Example: 
 * <pre>
 * <code>
 *    int[] objectIds = myClass.getObjectIds();
 *    List&lt;NameValuePair&gt; pairs = new ArrayList&lt;NameValuePair&gt;();
 *     
 *    // loop over all instances and take the value of the field name and the field value
 *    for (int id : objectIds)
 *    {
 *        IObject myObject = snapshot.getObject(id);
 *        String name = ((IObject) myObject.resolveValue(&quot;name&quot;)).getClassSpecificName();
 *        String value = ((IObject) myObject.resolveValue(&quot;value&quot;)).getClassSpecificName();

 *        pairs.add(new NameValuePair(name, value));
 *    }
 *    
 *    // the NameValuePair is a bean with two properties - name and value
 *    // the returned result will be a table with two columns - one for each of these properties
 *    return new ListResult(NameValuePair.class, pairs, "name", "value")
 * </code>
 * </pre>
 * The column names are derived from the property names.
 * If the names are required to be internationalized then a BeanInfo can be provided for the
 * type which provides a display name for the property descriptor.
 */
public class ListResult implements IResultTable, IIconProvider
{
	/**
	 * Converts a row to the needed value from the row
	 */
	public interface ValueProvider
	{
		/**
		 * Extracts the value from the row
		 * 
		 * @param row
		 *            the row
		 * @return the value
		 */
		Object getValueFor(Object row);
	}

	private List<?> subjects;
	private List<Column> columns;
	private List<ValueProvider> providers;

	/**
	 * Construct a displayable list from a List.
	 * 
	 * @param <L>
	 *            type name of items in the list
	 * @param type
	 *            class of items in the list
	 * @param subjects
	 *            the list
	 * @param properties
	 *            the field names (or Java Bean properties) to be extracted from
	 *            the list entries
	 */
	public <L> ListResult(Class<? extends L> type, List<L> subjects, String... properties)
	{
		this.subjects = subjects;
		this.columns = new ArrayList<Column>();
		this.providers = new ArrayList<ValueProvider>();

		setup(type, properties);
	}

	private void setup(Class<?> type, String... properties)
	{
		try
		{
			Map<String, PropertyDescriptor> name2prop = new HashMap<String, PropertyDescriptor>();

			BeanInfo info = Introspector.getBeanInfo(type);
			for (PropertyDescriptor d : info.getPropertyDescriptors())
			{
				Method readMethod = d.getReadMethod();
				if (readMethod == null) continue;

				name2prop.put(d.getName(), d);
			}

			if (properties == null || properties.length == 0) properties = name2prop.keySet().toArray(new String[name2prop.size()]);

			for (String property : properties)
			{
				PropertyDescriptor d = name2prop.get(property);

				if (d != null)
				{
                    String columnName = d.getDisplayName();
                    if (columnName.equals(d.getName()))
                        columnName = fixName(d.getName());
					columns.add(new Column(columnName, d.getPropertyType()));
					providers.add(new MethodValueProvider(d.getReadMethod()));
				}
			}
		}
		catch (IntrospectionException e)
		{
			throw new RuntimeException(e);
		}
	}

	private String fixName(String name)
	{
		StringBuilder buf = new StringBuilder(name.length() + 10);

		for (int ii = 0; ii < name.length(); ii++)
		{
			char ch = name.charAt(ii);

			if (ii == 0) buf.append(Character.toUpperCase(ch));
			else if (Character.isUpperCase(ch)) buf.append(' ').append(ch);
			else buf.append(ch);
		}

		return buf.toString();
	}

	public void addColumn(Column column, ValueProvider valueProvider)
	{
		this.columns.add(column);
		this.providers.add(valueProvider);
	}

	public ResultMetaData getResultMetaData()
	{
		return null;
	}

	public final Column[] getColumns()
	{
		return columns.toArray(new Column[columns.size()]);
	}

	public final int getRowCount()
	{
		return subjects.size();
	}

	public final Object getRow(int rowId)
	{
		return subjects.get(rowId);
	}

	public final Object getColumnValue(Object row, int columnIndex)
	{
		return providers.get(columnIndex).getValueFor(row);
	}

	public URL getIcon(Object row)
	{
		return null;
	}

	public IContextObject getContext(Object row)
	{
		return null;
	}

	// //////////////////////////////////////////////////////////////
	// internal classes
	// //////////////////////////////////////////////////////////////

	private static class MethodValueProvider implements ValueProvider
	{
		private Method readMethod;

		public MethodValueProvider(Method readMethod)
		{
			this.readMethod = readMethod;
		}

		public Object getValueFor(Object row)
		{
			try
			{
				return readMethod.invoke(row, (Object[]) null);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}

	}
}

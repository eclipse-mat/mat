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

public class ListResult implements IResultTable, IIconProvider
{
    public interface ValueProvider
    {
        Object getValueFor(Object row);
    }

    private List<?> subjects;
    private List<Column> columns;
    private List<ValueProvider> providers;

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
                if (readMethod == null)
                    continue;

                name2prop.put(d.getName(), d);
            }

            if (properties == null || properties.length == 0)
                properties = name2prop.keySet().toArray(new String[0]);

            for (String property : properties)
            {
                PropertyDescriptor d = name2prop.get(property);

                if (d != null)
                {
                    columns.add(new Column(fixName(d.getName()), d.getPropertyType()));
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

            if (ii == 0)
                buf.append(Character.toUpperCase(ch));
            else if (Character.isUpperCase(ch))
                buf.append(' ').append(ch);
            else
                buf.append(ch);
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
        return columns.toArray(new Column[0]);
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

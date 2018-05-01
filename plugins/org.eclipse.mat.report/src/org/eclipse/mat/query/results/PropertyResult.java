/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query.results;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import com.ibm.icu.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.report.internal.Messages;

/**
 * Extract properties from an object and display as a result table.
 * Compare to {@link ListResult} which extracts and displays from a list of objects.
 * The column names are derived from the property names.
 * If the names are required to be internationalized then a BeanInfo can be provided for the
 * subject class which provides a display name for the property descriptor.
 */
public class PropertyResult implements IResultTable, IIconProvider
{
    private static class Pair
    {
        private String name;
        private String value;

        Pair(String name, String value)
        {
            this.name = name;
            this.value = value;
        }

        public String getName()
        {
            return name;
        }

        public String getValue()
        {
            return value;
        }
    }

    private List<Pair> rows;

    /**
     * Generate a result from a single object
     * @param <L> The type of PropertyResult
     * @param subject the object
     * @param properties the field names or Java Bean properties to extract, or null or none to extract them all.
     */
    public <L> PropertyResult(Object subject, String... properties)
    {
        this.rows = new ArrayList<Pair>();

        try
        {
            Map<String, PropertyDescriptor> name2prop = new HashMap<String, PropertyDescriptor>();

            BeanInfo info = Introspector.getBeanInfo(subject.getClass());
            for (PropertyDescriptor d : info.getPropertyDescriptors())
            {
                Method readMethod = d.getReadMethod();
                if (readMethod == null)
                    continue;

                name2prop.put(d.getName(), d);
            }

            if (properties == null || properties.length == 0)
                properties = name2prop.keySet().toArray(new String[name2prop.size()]);

            for (String property : properties)
            {
                PropertyDescriptor d = name2prop.get(property);

                if (d != null)
                {
                    String columnName = d.getDisplayName();
                    if (columnName.equals(d.getName()))
                        columnName = fixName(d.getName());
                    Object v = d.getReadMethod().invoke(subject, (Object[]) null);
                    Pair p = new Pair(columnName, fixValue(v));
                    rows.add(p);
                }
            }
        }
        catch (IntrospectionException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalArgumentException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
    }

    private String fixValue(Object v)
    {
        if (v == null)
            return null;

        if (v instanceof Number)
            return NumberFormat.getNumberInstance().format(v);

        return String.valueOf(v);
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

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public final Column[] getColumns()
    {
        return new Column[] { new Column(Messages.PropertyResult_Column_Name, String.class),
                        new Column(Messages.PropertyResult_Column_Value, String.class) };
    }

    public final int getRowCount()
    {
        return rows.size();
    }

    public final Object getRow(int rowId)
    {
        return rows.get(rowId);
    }

    public final Object getColumnValue(Object row, int columnIndex)
    {
        Pair pair = (Pair) row;
        switch (columnIndex)
        {
            case 0:
                return pair.getName();
            case 1:
                return pair.getValue();
        }
        return null;
    }

    public URL getIcon(Object row)
    {
        return null;
    }

    public IContextObject getContext(Object row)
    {
        return null;
    }

}

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
package org.eclipse.mat.parser.internal.oql.compiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.collect.IteratorLong;

public class Query
{
    public static class SelectItem
    {
        String name;
        Expression expression;

        public SelectItem(String name, Expression expression)
        {
            this.name = name;
            this.expression = expression;
        }

        public Expression getExpression()
        {
            return expression;
        }

        public void setExpression(Expression expression)
        {
            this.expression = expression;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

    }

    public static class SelectClause
    {
        boolean isDistinct;
        boolean isRetainedSet;
        boolean asObjects;
        List<SelectItem> selectList;

        public boolean isDistinct()
        {
            return isDistinct;
        }

        public void setDistinct(boolean isDistinct)
        {
            this.isDistinct = isDistinct;
        }

        public boolean isRetainedSet()
        {
            return isRetainedSet;
        }

        public void setRetainedSet(boolean isRetainedSet)
        {
            this.isRetainedSet = isRetainedSet;
        }

        public boolean isAsObjects()
        {
            return asObjects;
        }

        public void setAsObjects(boolean asObjects)
        {
            this.asObjects = asObjects;
        }

        public List<SelectItem> getSelectList()
        {
            return selectList;
        }

        public void setSelectList(List<SelectItem> selectList)
        {
            this.selectList = selectList;
        }

    }

    public static class FromClause
    {
        boolean includeObjects;
        boolean includeSubClasses;
        String alias;

        // the five ways to define the source
        Query subSelect;
        ArrayLong objectAddresses;
        ArrayInt objectIds;
        String className;
        String classNamePattern;
        Expression call;

        public ArrayLong getObjectAddresses()
        {
            return objectAddresses;
        }

        public void addObjectAddress(long objectAddress)
        {
            if (this.objectAddresses == null)
                this.objectAddresses = new ArrayLong();
            this.objectAddresses.add(objectAddress);
        }

        public ArrayInt getObjectIds()
        {
            return objectIds;
        }

        public void addObjectId(int objectId)
        {
            if (this.objectIds == null)
                this.objectIds = new ArrayInt();
            this.objectIds.add(objectId);
        }

        public String getClassName()
        {
            return className;
        }

        public void setClassName(String className)
        {
            this.className = className;
        }

        public String getClassNamePattern()
        {
            return classNamePattern;
        }

        public void setClassNamePattern(String classNamePattern)
        {
            this.classNamePattern = classNamePattern;
        }

        public Expression getCall()
        {
            return call;
        }

        public void setCall(Expression call)
        {
            this.call = call;
        }

        public String getAlias()
        {
            return alias;
        }

        public void setAlias(String alias)
        {
            this.alias = alias;
        }

        public boolean includeSubClasses()
        {
            return includeSubClasses;
        }

        public void setIncludeSubClasses(boolean includeSubClasses)
        {
            this.includeSubClasses = includeSubClasses;
        }

        public boolean includeObjects()
        {
            return includeObjects;
        }

        public void setIncludeObjects(boolean includeObjects)
        {
            this.includeObjects = includeObjects;
        }

        public Query getSubSelect()
        {
            return subSelect;
        }

        public void setSubSelect(Query subSelect)
        {
            this.subSelect = subSelect;
        }

        @Override
        public String toString()
        {
            StringBuilder buf = new StringBuilder(128);

            if (includeObjects)
                buf.append("OBJECTS ");//$NON-NLS-1$

            if (includeSubClasses)
                buf.append("INSTANCEOF ");//$NON-NLS-1$

            if (subSelect != null)
            {
                buf.append("( ").append(subSelect).append(" )");//$NON-NLS-1$//$NON-NLS-2$
            }
            else if (call != null)
            {
                buf.append(call);
            }
            else if (className != null)
            {
                buf.append(className);
            }
            else if (classNamePattern != null)
            {
                buf.append("\"").append(classNamePattern).append("\"");//$NON-NLS-1$//$NON-NLS-2$
            }
            else if (objectIds != null)
            {
                for (IteratorInt ee = objectIds.iterator(); ee.hasNext();)
                {
                    buf.append(ee.next());
                    if (ee.hasNext())
                        buf.append(",");//$NON-NLS-1$
                }
            }
            else
            {
                for (IteratorLong ee = objectAddresses.iterator(); ee.hasNext();)
                {
                    buf.append("0x").append(Long.toHexString(ee.next()));//$NON-NLS-1$
                    if (ee.hasNext())
                        buf.append(",");//$NON-NLS-1$
                }
            }

            if (alias != null)
                buf.append(" ").append(alias);//$NON-NLS-1$

            return buf.toString();
        }

    }

    private SelectClause selectClause;
    private FromClause fromClause;
    private Expression whereClause;
    private List<Query> unionQueries;

    public SelectClause getSelectClause()
    {
        return selectClause;
    }

    public void setSelectClause(SelectClause selectClause)
    {
        this.selectClause = selectClause;
    }

    public FromClause getFromClause()
    {
        return fromClause;
    }

    public void setFromClause(FromClause fromClause)
    {
        this.fromClause = fromClause;
    }

    public Expression getWhereClause()
    {
        return whereClause;
    }

    public void setWhereClause(Expression whereClause)
    {
        this.whereClause = whereClause;
    }

    public void addUnionQuery(Query query)
    {
        if (unionQueries == null)
            unionQueries = new ArrayList<Query>();

        unionQueries.add(query);
    }

    public List<Query> getUnionQueries()
    {
        return unionQueries;
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(256);

        // select clause
        buf.append("SELECT ");//$NON-NLS-1$

        if (selectClause.isDistinct())
            buf.append("DISTINCT ");//$NON-NLS-1$

        if (selectClause.isRetainedSet())
            buf.append("AS RETAINED SET ");//$NON-NLS-1$

        if (selectClause.getSelectList().isEmpty())
        {
            buf.append("* ");//$NON-NLS-1$
        }
        else
        {
            for (Iterator<SelectItem> iter = selectClause.getSelectList().iterator(); iter.hasNext();)
            {
                SelectItem column = iter.next();
                buf.append(column.getExpression());

                if (column.getName() != null)
                {
                    buf.append(" AS ");//$NON-NLS-1$
                    buf.append(column.getName());
                }

                if (iter.hasNext())
                    buf.append(", ");//$NON-NLS-1$
                else
                    buf.append(" ");//$NON-NLS-1$
            }
        }

        // from clause
        buf.append("FROM ");//$NON-NLS-1$
        buf.append(fromClause).append(" ");//$NON-NLS-1$

        // where clause
        if (whereClause != null)
            buf.append("WHERE ").append(whereClause);//$NON-NLS-1$

        if (unionQueries != null)
        {
            for (Query q : unionQueries)
            {
                buf.append(" UNION ( ").append(q).append(" )");//$NON-NLS-1$//$NON-NLS-2$
            }
        }

        return buf.toString();
    }
}

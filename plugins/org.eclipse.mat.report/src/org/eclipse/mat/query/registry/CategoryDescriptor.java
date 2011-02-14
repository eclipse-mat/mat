/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.mat.report.internal.Messages;

public final class CategoryDescriptor
{
    private CategoryDescriptor parent;
    private final String name;
    private final int sortOrder;
    private final List<Object> children;

    private boolean isSorted = false;

    /* package */CategoryDescriptor(String identifier)
    {
        if (identifier == null)
        {
            this.name = Messages.CategoryDescriptor_Label_NoCategory;
            this.sortOrder = Integer.MAX_VALUE;
        }
        else
        {
            int p = identifier.indexOf('|');
            String name1 = p >= 0 ? identifier.substring(p + 1) : identifier;
            int sortOrder = 100;
            int end = p;
            // Babel pseudo-translation gives mat123456:101|Java Basics
            // Skip over trailing garbage introduced by pseudo-translation
            while (end > 0 && !Character.isDigit(identifier.charAt(end - 1))) --end;
            // Extract the longest number - useful for pseudo-translation
            for (int start = 0; start < end; ++start)
            {
                try
                {
                    sortOrder = Integer.parseInt(identifier.substring(start, end));
                    // Add pseudo-translation prefix to the name
                    name1 = identifier.substring(0, start) + name1;
                    break;
                }
                catch (NumberFormatException e)
                {}
            }
            this.name = name1;
            this.sortOrder = sortOrder;
        }

        this.children = new ArrayList<Object>();
    }

    public String getName()
    {
        return name;
    }

    public String getFullName()
    {
        if (parent == null)
            return null;

        String prefix = parent.getFullName();
        return prefix != null ? prefix + " / " + getName() : getName(); //$NON-NLS-1$
    }

    public List<Object> getChildren()
    {
        sort();
        return new ArrayList<Object>(children);
    }

    public List<CategoryDescriptor> getSubCategories()
    {
        sort();
        List<CategoryDescriptor> answer = new ArrayList<CategoryDescriptor>();
        for (Object child : children)
        {
            if (child instanceof CategoryDescriptor)
                answer.add((CategoryDescriptor) child);
        }
        return answer;
    }

    public List<QueryDescriptor> getQueries()
    {
        sort();
        List<QueryDescriptor> answer = new ArrayList<QueryDescriptor>();
        for (Object child : children)
        {
            if (child instanceof QueryDescriptor)
                answer.add((QueryDescriptor) child);
        }
        return answer;
    }

    private void sort()
    {
        if (!isSorted)
        {
            Collections.sort(children, new Comparator<Object>()
            {
                public int compare(Object o1, Object o2)
                {
                    boolean isCat1 = o1 instanceof CategoryDescriptor;
                    boolean isCat2 = o2 instanceof CategoryDescriptor;

                    int s1 = isCat1 ? ((CategoryDescriptor) o1).sortOrder : ((QueryDescriptor) o1).sortOrder;
                    int s2 = isCat2 ? ((CategoryDescriptor) o2).sortOrder : ((QueryDescriptor) o2).sortOrder;

                    if (s1 < s2)
                        return -1;
                    else if (s1 > s2)
                        return 1;

                    if (isCat1 ^ isCat2)
                        return isCat1 ? -1 : 1;

                    String name1 = isCat1 ? ((CategoryDescriptor) o1).name : ((QueryDescriptor) o1).getName();
                    String name2 = isCat2 ? ((CategoryDescriptor) o2).name : ((QueryDescriptor) o2).getName();

                    return name1.compareTo(name2);
                }
            });
            isSorted = true;
        }
    }

    public void add(QueryDescriptor descriptor)
    {
        children.add(descriptor);
        isSorted = false;
    }

    public void add(CategoryDescriptor category)
    {
        children.add(category);
        isSorted = false;
    }

    public CategoryDescriptor resolve(String name)
    {
        int slash = name.indexOf('/');
        String subIdentifier = slash < 0 ? name : name.substring(0, slash);

        int pipe = subIdentifier.indexOf('|');
        String subName = pipe >= 0 ? subIdentifier.substring(pipe + 1) : subIdentifier;

        CategoryDescriptor subCat = null;
        for (Object child : children)
        {
            if (!(child instanceof CategoryDescriptor))
                continue;

            CategoryDescriptor c = (CategoryDescriptor) child;

            if (subName.equals(c.getName()))
            {
                subCat = c;
                break;
            }
        }

        if (subCat == null)
        {
            subCat = new CategoryDescriptor(subIdentifier);
            subCat.parent = this;
            children.add(subCat);
        }

        return slash < 0 ? subCat : subCat.resolve(name.substring(slash + 1));
    }

    @Override
    public String toString()
    {
        return getName();
    }
}

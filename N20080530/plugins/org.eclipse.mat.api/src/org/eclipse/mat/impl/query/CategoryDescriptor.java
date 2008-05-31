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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CategoryDescriptor implements Comparable<CategoryDescriptor>
{
    CategoryDescriptor parent;
    private final String name;
    private final int sortOrder;
    private final List<CategoryDescriptor> subCategories;
    private final List<QueryDescriptor> queries;
    private boolean isSorted = false;

    /* package */CategoryDescriptor(String identifier)
    {
        if (identifier == null)
        {
            this.name = "<uncategorized>";
            this.sortOrder = Integer.MAX_VALUE;
        }
        else
        {
            int p = identifier.indexOf('|');
            this.name = p >= 0 ? identifier.substring(p + 1) : identifier;
            this.sortOrder = p >= 0 ? Integer.parseInt(identifier.substring(0, p)) : Integer.MAX_VALUE;
        }

        this.subCategories = new ArrayList<CategoryDescriptor>();
        this.queries = new ArrayList<QueryDescriptor>();
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
        return prefix != null ? prefix + " / " + getName() : getName();
    }

    public List<CategoryDescriptor> getSubCategories()
    {
        sort();
        return Collections.unmodifiableList(subCategories);
    }

    public List<QueryDescriptor> getQueries()
    {
        sort();
        return Collections.unmodifiableList(queries);
    }

    private void sort()
    {
        if (!isSorted)
        {
            Collections.sort(queries);
            Collections.sort(subCategories);
            isSorted = true;
        }
    }

    public void add(QueryDescriptor descriptor)
    {
        queries.add(descriptor);
        isSorted = false;
    }

    public void add(CategoryDescriptor category)
    {
        subCategories.add(category);
        isSorted = false;
    }

    public CategoryDescriptor resolve(String name)
    {
        int slash = name.indexOf('/');
        String subIdentifier = slash < 0 ? name : name.substring(0, slash);
        
        int pipe = subIdentifier.indexOf('|');
        String subName = pipe >= 0 ? subIdentifier.substring(pipe + 1) : subIdentifier;

        CategoryDescriptor subCat = null;
        for (CategoryDescriptor c : subCategories)
        {
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
            subCategories.add(subCat);
        }

        return slash < 0 ? subCat : subCat.resolve(name.substring(slash + 1));
    }

    public int compareTo(CategoryDescriptor o)
    {
        if (sortOrder < o.sortOrder)
            return -1;
        else if (sortOrder > o.sortOrder)
            return 1;
        else
            return name.compareTo(o.name);
    }

    @Override
    public String toString()
    {
        return getName();
    }
}

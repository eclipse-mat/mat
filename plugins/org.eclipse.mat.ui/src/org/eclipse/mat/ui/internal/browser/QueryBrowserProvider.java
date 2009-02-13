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
package org.eclipse.mat.ui.internal.browser;

import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.mat.ui.internal.browser.QueryBrowserPopup.Element;

public abstract class QueryBrowserProvider
{
    protected QueryBrowserPopup.Element[] sortedElements;

    public abstract String getName();

    public abstract QueryBrowserPopup.Element[] getElements();

    public QueryBrowserPopup.Element[] getElementsSorted()
    {
        if (sortedElements == null)
        {
            sortedElements = getElements();
            Arrays.sort(sortedElements, new Comparator<Element>()
            {
                public int compare(Element o1, Element o2)
                {
                    return o1.getLabel().compareTo(o2.getLabel());
                }
            });
        }
        return sortedElements;
    }

}

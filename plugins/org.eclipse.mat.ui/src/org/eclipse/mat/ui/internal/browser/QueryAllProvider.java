/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.browser;

import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.browser.QueryBrowserPopup.Element;

/**
 * Empty provider used to generate the 'All queries' action item
 * @author ajohnson
 *
 */
public class QueryAllProvider extends QueryBrowserProvider
{
    public static String ALL = Messages.QueryAllProvider_AllQueries;

    @Override
    public Element[] getElements()
    {
        return new Element[0];
    }

    @Override
    public String getName()
    {
        return ALL;
    }

}

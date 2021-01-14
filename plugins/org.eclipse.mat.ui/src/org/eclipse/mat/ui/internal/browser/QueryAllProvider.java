/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
    public static final String ALL = Messages.QueryAllProvider_AllQueries;

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

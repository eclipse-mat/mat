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
package org.eclipse.mat.util;


public final class HTMLUtils
{

    public static String escapeText(String text)
    {
        final StringBuilder result = new StringBuilder(text.length() * 120 / 100);
        
        for (int ii = 0; ii < text.length(); ii++)
        {
            char ch = text.charAt(ii);
            if (ch == '<')
                result.append("&lt;");
            else if (ch == '>')
                result.append("&gt;");
            else if (ch == '&')
                result.append("&amp;");
            else
                result.append(ch);
        }
        
        return result.toString();
    }

    private HTMLUtils()
    {}
}

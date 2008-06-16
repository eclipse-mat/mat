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

import org.eclipse.mat.impl.query.IndividualObjectUrl;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.ResultMetaData;

/**
 * This result is rendered as simple text. Any object addresses will be rendered
 * as links and allow the user to execute queries on the objects.
 */
public class TextResult implements IResult
{
    private boolean isHtml;
    private String text;

    public TextResult(String text)
    {
        this(text, false);
    }

    public TextResult(String text, boolean isHtml)
    {
        this.isHtml = isHtml;
        this.text = text;
    }

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public String getText()
    {
        return text;
    }

    /**
     * Converts the text into HTML including links to the heap objects.
     */
    @SuppressWarnings("fallthrough")
    public String getHtml()
    {
        if (isHtml || text == null)
        {
            return text;
        }
        else
        {
            StringBuilder htmlBuilder = new StringBuilder("<html><body><pre>"); //$NON-NLS-1$
            
            int len = text.length();
            for (int ii = 0; ii < len; ii++)
            {
                char c = text.charAt(ii);
                switch (c)
                {
                    case '&':
                        htmlBuilder.append("&amp;"); //$NON-NLS-1$
                        break;
                    case '<':
                        htmlBuilder.append("&lt;"); //$NON-NLS-1$
                        break;
                    case '>':
                        htmlBuilder.append("&gt;"); //$NON-NLS-1$
                        break;
                    case '0':
                        
                        // TODO include checks for end of line!!!
                        if (ii + 1 < len && text.charAt(ii + 1) == 'x')
                        {
                            // pattern "0x\p{XDigit}*" is recognized as an
                            // object
                            // address

                            int jj = 0;
                            while (ii + 2 + jj + 1 < len && "0123456789abcdefABCDEF".indexOf(text.charAt(ii + 2 + jj)) != -1)
                                jj++;

                            if (jj > 0)
                            {
                                htmlBuilder.append(new IndividualObjectUrl(text.substring(ii, ii + 2 + jj))
                                                .toHtml());
                                ii += 2 + jj;
                                break;
                            }
                        }
                    default:
                        htmlBuilder.append(c);
                }
            }
            htmlBuilder.append("</pre></body></html>"); //$NON-NLS-1$

            return htmlBuilder.toString();
        }
    }
}

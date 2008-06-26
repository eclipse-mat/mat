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

    public boolean isHtml()
    {
        return isHtml;
    }

}

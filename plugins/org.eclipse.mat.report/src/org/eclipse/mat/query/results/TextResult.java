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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.DetailResultProvider;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.util.IProgressListener;

/**
 * This result is rendered as simple text. Any object addresses will be rendered
 * as links and allow the user to execute queries on the objects.
 * 
 * @noextend
 */
public class TextResult implements IResult
{
    private ResultMetaData metaData;
    private LinkedResults links;
    private boolean isHtml;
    private String text;

    public TextResult()
    {
        this("", true);
    }

    public TextResult(String text)
    {
        this(text, false);
    }

    public TextResult(String text, boolean isHtml)
    {
        this.text = text;
        this.isHtml = isHtml;
    }

    public ResultMetaData getResultMetaData()
    {
        return metaData;
    }

    public void setText(String text)
    {
        this.text = text;
    }

    public String getText()
    {
        return text;
    }

    public boolean isHtml()
    {
        return isHtml;
    }

    /**
     * @return url
     */
    public String linkTo(String label, IResult result)
    {
        if (metaData == null)
        {
            links = new LinkedResults();
            metaData = new ResultMetaData.Builder() //
                            .addDetailResult(links) //
                            .build();
        }

        StringBuilder buf = new StringBuilder();
        buf.append("<a href=\"");
        buf.append(links.add(result));
        buf.append("\">");
        buf.append(label);
        buf.append("</a>");

        return buf.toString();
    }

    // //////////////////////////////////////////////////////////////
    // internal
    // //////////////////////////////////////////////////////////////

    private static class LinkedResults extends DetailResultProvider
    {
        private List<IResult> results = new ArrayList<IResult>();

        public LinkedResults()
        {
            super("Links");
        }

        public String add(IResult result)
        {
            results.add(result);
            return QueryObjectLink.forDetailResult(this, String.valueOf(results.size() - 1));
        }

        @Override
        public IResult getResult(Object row, IProgressListener listener) throws SnapshotException
        {
            try
            {
                int index = Integer.parseInt((String) row);
                return results.get(index);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        @Override
        public boolean hasResult(Object row)
        {
            try
            {
                int index = Integer.parseInt((String) row);
                return index >= 0 && index < results.size();
            }
            catch (NumberFormatException e)
            {
                return false;
            }
        }

    }
}

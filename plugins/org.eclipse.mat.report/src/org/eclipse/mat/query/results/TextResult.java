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
package org.eclipse.mat.query.results;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.DetailResultProvider;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.IProgressListener;

/**
 * This result is rendered as text.
 * The input can be plain text, or HTML.
 * Any object addresses can be rendered
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

    /**
     * A simple text result as HTML
     */
    public TextResult()
    {
        this("", true); //$NON-NLS-1$
    }

    /**
     * A simple text result, using the supplied text, as plain text.
     * @param text
     */
    public TextResult(String text)
    {
        this(text, false);
    }

    /**
     * Creates a section to hold some simple text.
     * @param text the contents of the report
     * @param isHtml whether it is in HTML
     */
    public TextResult(String text, boolean isHtml)
    {
        this.text = text;
        this.isHtml = isHtml;
    }

    /**
     * Get the metadata for fine-tuning the display of this result.
     * @return null
     */
    public ResultMetaData getResultMetaData()
    {
        return metaData;
    }

    /**
     * Set the text for the text result.
     * @param text the contents of the report
     */
    public void setText(String text)
    {
        this.text = text;
    }

    /**
     * Get the text
     * @return the text of the report
     */
    public String getText()
    {
        return text;
    }

    /**
     * Whether it is HTML
     * @return true if HTML
     */
    public boolean isHtml()
    {
        return isHtml;
    }

    /**
     * Generate a link to another report, and save the referenced report too.
     * @return the URL as a String
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
        buf.append("<a href=\""); //$NON-NLS-1$
        buf.append(links.add(result));
        buf.append("\">"); //$NON-NLS-1$
        buf.append(label);
        buf.append("</a>"); //$NON-NLS-1$

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
            super(Messages.TextResult_Label_Links);
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

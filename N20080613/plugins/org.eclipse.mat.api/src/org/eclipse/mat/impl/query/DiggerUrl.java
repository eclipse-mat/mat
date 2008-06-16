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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Pattern;

public class DiggerUrl
{
    private final static String AHREF_START = "<a href=\""; //$NON-NLS-1$
    private final static String AHREF_END = "\">"; //$NON-NLS-1$
    private final static String AHREF_CLOSING_TAG = "</a>"; //$NON-NLS-1$

    private final static String PROTOCOL = "digger://"; //$NON-NLS-1$

    private final static String ENC = "UTF-8"; //$NON-NLS-1$

    private final static String ANY = ".+"; //$NON-NLS-1$
    private final static Pattern URL_PATTERN = Pattern.compile(PROTOCOL + ANY + '/' + ANY); //$NON-NLS-1$

    protected String type;
    protected String content;
    protected String label;

    DiggerUrl()
    {}

    DiggerUrl(String content)
    {
        this.content = content;
    }

    DiggerUrl(String type, String content, String label)
    {
        this.type = type;
        this.content = content;
        this.label = label;
    }

    DiggerUrl(String type, String content)
    {
        this.type = type;
        this.content = content;
    }

    public String getType()
    {
        return type;
    }

    public String getContent()
    {
        return content;
    }

    public static final String forQuery(String query)
    {
        try
        {
            return PROTOCOL + "query/" + URLEncoder.encode(query, ENC);
        }
        catch (UnsupportedEncodingException ignore)
        {
            // $JL-EXC$
            // never thrown as the UTF-8 is supported by all VMs
            return PROTOCOL + "query/" + query;
        }
    }

    public static final DiggerUrl forResult(String result, String label)
    {
        return new DiggerUrl("result", result, label);
    }

    /**
     * @return null if the URL does not present a proper Digger URL
     */
    public static DiggerUrl parse(String url)
    {
        if (!URL_PATTERN.matcher(url).matches())
            return null;

        int typeStart = PROTOCOL.length();
        int typeEnd = url.indexOf('/', typeStart);
        String type = url.substring(typeStart, typeEnd);

        int contentStart = typeEnd + 1;
        int contentEnd = url.indexOf('?', contentStart);
        if (contentEnd == -1)
            contentEnd = url.length();
        String content = url.substring(contentStart, contentEnd);

        try
        {
            content = URLDecoder.decode(content, ENC);
        }
        catch (UnsupportedEncodingException ignore)
        {
            // $JL-EXC$
            // would be thrown only if the second parameter of URLDecoder.decode
            // were not a name of a known and supported encoding
        }

        if (type.equals(IndividualObjectUrl.TYPE))
            return new IndividualObjectUrl(content);
        else
            return new DiggerUrl(type, content);
    }

    public String toHtml()
    {
        String encodedContent = null;
        try
        {
            encodedContent = URLEncoder.encode(content, ENC);
        }
        catch (UnsupportedEncodingException ignore)
        {
            // $JL-EXC$
            // would be thrown only if the second parameter of URLEncoder.encode
            // were not a name of a known and supported encoding
        }

        StringBuilder b = new StringBuilder(AHREF_START);
        b.append(PROTOCOL);
        b.append(type);
        b.append('/');
        b.append(encodedContent);
        b.append(AHREF_END);
        b.append(label);
        b.append(AHREF_CLOSING_TAG);

        return b.toString();
    }

}

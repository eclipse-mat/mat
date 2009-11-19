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
package org.eclipse.mat.query.registry;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.mat.query.DetailResultProvider;

public final class QueryObjectLink
{
    public enum Type
    {
        OBJECT, QUERY, DETAIL_RESULT;
    }

    public final static String PROTOCOL = "mat://"; //$NON-NLS-1$

    private final static String ENC = "UTF-8"; //$NON-NLS-1$

    protected Type type;
    protected String target;

    public QueryObjectLink(Type type, String target)
    {
        this.type = type;
        this.target = target;
    }

    public Type getType()
    {
        return type;
    }

    public String getTarget()
    {
        return target;
    }

    public String getURL()
    {
        return forType(type, target);
    }

    public static final String forQuery(String query)
    {
        return forType(Type.QUERY, query);
    }

    public static final String forObject(String identifier)
    {
        return forType(Type.OBJECT, identifier);
    }

    public static final String forDetailResult(DetailResultProvider provider, String identifier)
    {
        return forType(Type.DETAIL_RESULT, provider.getLabel() + "/" + identifier); //$NON-NLS-1$
    }

    public static final String forType(Type type, String target)
    {
        try
        {
            return PROTOCOL + type.name().toLowerCase(Locale.ENGLISH) + "/" + URLEncoder.encode(target, ENC); //$NON-NLS-1$
        }
        catch (UnsupportedEncodingException ignore)
        {
            // never thrown as the UTF-8 is supported by all VMs
            return PROTOCOL + type.name().toLowerCase(Locale.ENGLISH) + "/" + target; //$NON-NLS-1$
        }
    }

    private final static Pattern URL_PATTERN = Pattern.compile(PROTOCOL + "([^/]*)/(.*)"); //$NON-NLS-1$

    /**
     * @return null if the URL does not present a proper Memory Analyzer Object
     *         Link URL
     */
    public static QueryObjectLink parse(String url)
    {
        Matcher matcher = URL_PATTERN.matcher(url);

        if (!matcher.matches())
            return null;

        Type type;
        try
        {
            type = Type.valueOf(matcher.group(1).toUpperCase(Locale.ENGLISH));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }

        String target = matcher.group(2);
        try
        {
            target = URLDecoder.decode(target, ENC);
        }
        catch (UnsupportedEncodingException ignore)
        {}

        return new QueryObjectLink(type, target);
    }

}

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
package org.eclipse.mat.report;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.ResultMetaData;

/**
 * A container for combining results.
 */
public class Spec implements IResult
{
    private String name;
    private String template;
    private Map<String, String> params = new HashMap<String, String>();

    /* package */Spec()
    {}

    /* package */Spec(String name)
    {
        this.name = name;
    }

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getTemplate()
    {
        return template;
    }

    public void setTemplate(String template)
    {
        this.template = template;
    }

    public Map<String, String> getParams()
    {
        return params;
    }

    public void putAll(Map<String, String> map)
    {
        this.params.putAll(map);
    }

    public void set(String key, String value)
    {
        this.params.put(key, value);
    }

    /**
     * Merge with another Spec.
     * Combine the parameters and choose the other name if this has none.
     * @param other the other Spec
     */
    public void merge(Spec other)
    {
        if (this.name == null)
            this.name = other.name;

        for (Map.Entry<String, String> entry : other.params.entrySet())
        {
            if (!params.containsKey(entry.getKey()))
                params.put(entry.getKey(), entry.getValue());
        }
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
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

    /**
     * Get the entire set of parameters.
     * @return the parameters
     */
    public Map<String, String> getParams()
    {
        return params;
    }

    /**
     * Add an entire map of a parameter names and values.
     * @param map a map of names and associated values
     */
    public void putAll(Map<String, String> map)
    {
        this.params.putAll(map);
    }

    /**
     * Set a parameter to control the formatting of a report
     * @param key a {@link org.eclipse.mat.report.Params} value
     * @param value the value which controls an aspect of the report
     */
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

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
package org.eclipse.mat.report.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Parameters
{
    private static final String NONE = "<NONE>";

    public abstract Parameters shallow();

    public abstract String get(String key, String defaultValue);

    public String get(String key)
    {
        return get(key, null);
    }

    public String expand(String value)
    {
        if (value == null)
            return null;
        
        while (true)
        {
            int p1 = value.indexOf("${");
            if (p1 < 0)
                return value;

            int p2 = value.indexOf('}', p1);
            if (p2 < 0)
                return value;

            value = value.substring(0, p1) + get(value.substring(p1 + 2, p2)) + value.substring(p2 + 1);
        }
    }

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////

    public int getInt(String key, int defaultValue)
    {
        String value = get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public boolean getBoolean(String key, boolean defaultValue)
    {
        String value = get(key);
        return value == null ? defaultValue : "true".equals(value.toLowerCase());
    }
    
    public String[] getStringArray(String key)
    {
        String value = get(key);
        if (value == null)
            return null;
        
        List<String> values = new ArrayList<String>();
        
        int c = 0;
        int p = value.indexOf(',', c);
        
        while (p >= 0 && c < value.length())
        {
            String v = value.substring(c, p);
            
            if (v.length() > 0)
                values.add(v);
            c = p + 1;
            p = value.indexOf(',', c);
        }
        
        if (c < value.length())
            values.add(value.substring(c));
        
        return values.toArray(new String[0]);
    }

    // //////////////////////////////////////////////////////////////
    // default implementations
    // //////////////////////////////////////////////////////////////

    public static class Deep extends Parameters
    {
        private Parameters parent;
        private Map<String, String> base;

        private Map<String, String> materialized;

        public Deep(Parameters parent, Map<String, String> map)
        {
            this.parent = parent;
            this.base = map;

            this.materialized = new HashMap<String, String>();
        }

        public Deep(Map<String, String> map)
        {
            this(null, map);
        }

        public String get(String key, String defaultValue)
        {
            String value = materialized.get(key);
            if (value == null)
            {
                value = base.get(key);
                if (value != null)
                {
                    value = expand(value);
                }
                else
                {
                    if (parent != null)
                        value = parent.get(key);
                }

                if (value == null)
                    value = NONE;

                materialized.put(key, value);
            }

            return value == NONE ? defaultValue : value;
        }

        @Override
        public Parameters shallow()
        {
            return new Parameters()
            {
                @Override
                public String get(String key, String defaultValue)
                {
                    String value = base.get(key);
                    if (value == null)
                        return defaultValue;

                    return expand(value);
                }

                @Override
                public Parameters shallow()
                {
                    return this;
                }
            };

        }
    }
}

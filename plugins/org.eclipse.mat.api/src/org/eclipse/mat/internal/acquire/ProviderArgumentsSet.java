/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.acquire;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.query.registry.ArgumentDescriptor;

public class ProviderArgumentsSet
{
	private HeapDumpProviderDescriptor providerDescriptor;
    private Map<ArgumentDescriptor, Object> values;
	public ProviderArgumentsSet(HeapDumpProviderDescriptor providerDescriptor)
	{
		this.providerDescriptor = providerDescriptor;
		this.values = new HashMap<ArgumentDescriptor, Object>();
	}

    public void setArgumentValue(ArgumentDescriptor arg, Object value)
    {
        values.put(arg, value);
    }
    
    public boolean isExecutable()
    {
        // all mandatory parameters must be set
        for (ArgumentDescriptor parameter : providerDescriptor.getArguments())
        {
            if (parameter.isMandatory() && !values.containsKey(parameter) && parameter.getDefaultValue() == null)
                return false;
        }

        return true;
    }
    
    public void removeArgumentValue(ArgumentDescriptor arg)
    {
        values.remove(arg);
    }
    
    public Object getArgumentValue(ArgumentDescriptor desc)
    {
        return values.get(desc);
    }

	public HeapDumpProviderDescriptor getProviderDescriptor()
	{
		return providerDescriptor;
	}

	public Map<ArgumentDescriptor, Object> getValues()
	{
		return values;
	}
	
	
    
}

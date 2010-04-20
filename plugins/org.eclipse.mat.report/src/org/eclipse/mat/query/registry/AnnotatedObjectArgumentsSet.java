/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;


public class AnnotatedObjectArgumentsSet
{
	private IAnnotatedObjectDescriptor descriptor;
    private Map<ArgumentDescriptor, Object> values;
	public AnnotatedObjectArgumentsSet(IAnnotatedObjectDescriptor descriptor)
	{
		this.descriptor = descriptor;
		this.values = new HashMap<ArgumentDescriptor, Object>();
	}

    public void setArgumentValue(ArgumentDescriptor arg, Object value)
    {
        values.put(arg, value);
    }
    
    public boolean isExecutable()
    {
        // all mandatory parameters must be set
        for (ArgumentDescriptor parameter : descriptor.getArguments())
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

	public IAnnotatedObjectDescriptor getDescriptor()
	{
		return descriptor;
	}

	public Map<ArgumentDescriptor, Object> getValues()
	{
		return values;
	}
	
	
    
}

/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

import org.eclipse.mat.internal.Messages;

/**
 * Provides information about the column names for a {@link HistogramRecord}.
 * Not intended to be directly used by clients.
 * @noextend This class is not intended to be subclassed by clients.
 * @since 1.1
 */
public class HistogramRecordBeanInfo extends SimpleBeanInfo
{
	/**
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public HistogramRecordBeanInfo()
	{
	}
	
    /**
     * Gets the property descriptors for the fields of the {@link HistogramRecord}.
     * The system bean code will call this once and cache the result. When bean info is
     * needed the data will be copied to a bean info to return to the user.
     * The returned results implement the {@link PropertyDescriptor#getDisplayName} method
     * so that for RAP the language can be dynamically chosen. If a basic property descriptor
     * was used and initialized using {@link PropertyDescriptor#setDisplayName} then only
     * one language could be used. 
     */
    public PropertyDescriptor[] getPropertyDescriptors()
    {
        PropertyDescriptor ret[];
        try
        {
            final PropertyDescriptor propertyDescriptor1 = new PropertyDescriptor("label", HistogramRecord.class) //$NON-NLS-1$
            {
                public String getDisplayName()
                {
                    return Messages.HistogramRecordBeanInfo_Label;
                }
            };
            final PropertyDescriptor propertyDescriptor2 = new PropertyDescriptor("numberOfObjects", HistogramRecord.class) //$NON-NLS-1$
            {
                public String getDisplayName()
                {
                    return Messages.HistogramRecordBeanInfo_NumberOfObjects;
                }
            };
            final PropertyDescriptor propertyDescriptor3 = new PropertyDescriptor("usedHeapSize", HistogramRecord.class) //$NON-NLS-1$
            {
                public String getDisplayName()
                {
                    return Messages.HistogramRecordBeanInfo_UsedHeapSize;
                }
            };
            final PropertyDescriptor propertyDescriptor4 = new PropertyDescriptor("retainedHeapSize", HistogramRecord.class) //$NON-NLS-1$
            {
                public String getDisplayName()
                {
                    return Messages.HistogramRecordBeanInfo_RetainedHeapSize;
                }
            };
            ret = new PropertyDescriptor[] { propertyDescriptor1, propertyDescriptor2, propertyDescriptor3, propertyDescriptor4 };
        }
        catch (IntrospectionException e)
        {
            // Log this error
            ret = null;
        }
        return ret;
    }
}

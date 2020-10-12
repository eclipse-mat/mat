/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.dtfj.bridge;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.Platform;

import com.ibm.dtfj.image.ImageFactory;

/**
 * IExecutableExtensionFactory that creates the ImageFactory.
 */
public class DelegatedImageFactory implements IExecutableExtensionFactory, IExecutableExtension
{
    private final boolean isDebuggingClassloading = "true"
                    .equals(Platform.getDebugOption("org.eclipse.mat.dtfj.bridge/classloading/debug"));
    protected ImageFactory underlyingImageFactory;
    private String implementationClass;

    @Override
    public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
                    throws CoreException
    {
        if (isDebuggingClassloading)
        {
            System.out.println(this + " " + propertyName + " = " + data);
        }
        if ("action".equals(propertyName))
        {
            implementationClass = (String) data;
        }
    }

    @Override
    public Object create() throws CoreException
    {
        try
        {
            if (isDebuggingClassloading)
            {
                System.out.println(this + " creating CustomClassLoader with " + implementationClass);
            }
            CustomClassLoader ccl = new CustomClassLoader(this.getClass().getClassLoader());
            Class<?> imageFactory = ccl.loadClass(implementationClass);
            return (ImageFactory) imageFactory.newInstance();
        }
        catch (Throwable t)
        {
            throw new RuntimeException(t);
        }
    }
}

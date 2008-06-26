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
package org.eclipse.mat.hprof.internal;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.mat.hprof.extension.IParsingEnhancer;
import org.eclipse.mat.hprof.extension.IRuntimeEnhancer;
import org.eclipse.mat.util.RegistryReader;


public class EnhancerRegistry extends RegistryReader<EnhancerRegistry.Enhancer>
{
    public static class Enhancer
    {
        IConfigurationElement configElement;

        public Enhancer(IConfigurationElement configElement)
        {
            this.configElement = configElement;
        }

        public IParsingEnhancer parser()
        {
            try
            {
                return (IParsingEnhancer) configElement.createExecutableExtension("parser");
            }
            catch (CoreException e)
            {
                Logger.getLogger(getClass().getName()).log(
                                Level.SEVERE,
                                MessageFormat.format("Error creating parser for {0}", configElement
                                                .getAttribute("parser")), e);
                return null;
            }
        }

        public IRuntimeEnhancer runtime()
        {
            try
            {
                return (IRuntimeEnhancer) configElement.createExecutableExtension("runtime");
            }
            catch (CoreException e)
            {
                Logger.getLogger(getClass().getName()).log(
                                Level.SEVERE,
                                MessageFormat.format("Error creating runtime for {0}", configElement
                                                .getAttribute("runtime")), e);
                return null;
            }
        }

    }

    private static final EnhancerRegistry instance = new EnhancerRegistry();

    public static EnhancerRegistry instance()
    {
        return instance;
    }

    private EnhancerRegistry()
    {
        init(null, "org.eclipse.mat.hprof.enhancer");
    }

    @Override
    protected Enhancer createDelegate(IConfigurationElement configElement) throws CoreException
    {
        return new Enhancer(configElement);
    }

    @Override
    protected void removeDelegate(Enhancer delegate)
    {}

}

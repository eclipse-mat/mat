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
package org.eclipse.mat.parser.internal.util;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.mat.impl.registry.RegistryReader;
import org.eclipse.mat.parser.ParserPlugin;


public class ParserRegistry extends RegistryReader<ParserRegistry.Parser>
{
    public static final String INDEX_BUILDER = "indexBuilder";
    public static final String OBJECT_READER = "objectReader";

    public class Parser
    {
        private IConfigurationElement configElement;
        private Pattern pattern;

        public Parser(IConfigurationElement configElement, Pattern pattern)
        {
            this.configElement = configElement;
            this.pattern = pattern;
        }

        public Pattern getPattern()
        {
            return pattern;
        }

        public String getUniqueIdentifier()
        {
            IExtension extension = (IExtension) configElement.getParent();
            return extension.getUniqueIdentifier();
        }

        @SuppressWarnings("unchecked")
        public <I> I create(Class<I> type, String attribute)
        {
            try
            {
                return (I) configElement.createExecutableExtension(attribute);
            }
            catch (CoreException e)
            {
                Logger.getLogger(getClass().getName()).log(
                                Level.SEVERE,
                                MessageFormat.format("Error while creating {0} ''{1}''", type.getSimpleName(),
                                                configElement.getAttribute("addonBuilder")), e);
                return null;
            }
        }
    }

    public ParserRegistry()
    {
        init(ParserPlugin.getDefault().getExtensionTracker(), ParserPlugin.PLUGIN_ID + ".parser");
    }

    @Override
    public Parser createDelegate(IConfigurationElement configElement)
    {
        String regexp = configElement.getAttribute("fileNamePattern");
        if (regexp == null || regexp.length() == 0)
            return null;

        try
        {
            Pattern p = Pattern.compile(regexp);
            return new Parser(configElement, p);
        }
        catch (PatternSyntaxException e)
        {
            Logger.getLogger(getClass().getName()).log(
                            Level.SEVERE,
                            MessageFormat.format("Error compiling file name pattern of extension {0}", configElement
                                            .getNamespaceIdentifier()), e);
            return null;
        }
    }

    @Override
    protected void removeDelegate(Parser delegate)
    {}

    public Parser lookupParser(String uniqueIdentifier)
    {
        for (Parser p : delegates())
            if (uniqueIdentifier.equals(p.getUniqueIdentifier()))
                return p;
        return null;
    }

    public Parser matchParser(String fileName)
    {
        for (Parser p : delegates())
            if (p.pattern.matcher(fileName).matches())
                return p;
        return null;
    }

}

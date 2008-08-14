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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.mat.parser.internal.ParserPlugin;
import org.eclipse.mat.snapshot.SnapshotFormat;
import org.eclipse.mat.util.RegistryReader;
import org.eclipse.mat.util.SimpleStringTokenizer;

public class ParserRegistry extends RegistryReader<ParserRegistry.Parser>
{
    public static final String INDEX_BUILDER = "indexBuilder";
    public static final String OBJECT_READER = "objectReader";

    public class Parser
    {
        private String id;
        private IConfigurationElement configElement;
        private SnapshotFormat snapshotFormat;
        private Pattern[] pattern;

        private Parser(IConfigurationElement configElement, SnapshotFormat snapshotFormat, Pattern[] pattern)
        {
            this.id = ((IExtension) configElement.getParent()).getSimpleIdentifier();
            this.configElement = configElement;
            this.snapshotFormat = snapshotFormat;
            this.pattern = pattern;
        }
        
        public String getId()
        {
            return id;
        }

        public String getUniqueIdentifier()
        {
            IExtension extension = (IExtension) configElement.getParent();
            return extension.getUniqueIdentifier();
        }

        public SnapshotFormat getSnapshotFormat()
        {
            return snapshotFormat;
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

    public ParserRegistry(IExtensionTracker tracker)
    {
        init(tracker, ParserPlugin.PLUGIN_ID + ".parser");
    }

    @Override
    public Parser createDelegate(IConfigurationElement configElement)
    {
        String fileExtensions = configElement.getAttribute("fileExtension");
        if (fileExtensions == null || fileExtensions.length() == 0)
            return null;

        try
        {
            String[] extensions = SimpleStringTokenizer.split(fileExtensions, ',');
            Pattern[] patterns = new Pattern[extensions.length];
            for (int ii = 0; ii < extensions.length; ii++)
                patterns[ii] = Pattern.compile("(.*\\.)((?i)" + extensions[ii] + ")(\\.[0-9]*)?");

            SnapshotFormat snapshotFormat = new SnapshotFormat(configElement.getAttribute("name"), extensions);
            return new Parser(configElement, snapshotFormat, patterns);
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

    public List<Parser> matchParser(String fileName)
    {
        List<Parser> answer = new ArrayList<Parser>();
        for (Parser p : delegates())
            for (Pattern regex : p.pattern)
                if (regex.matcher(fileName).matches())
                    answer.add(p);
        return answer;
    }

}

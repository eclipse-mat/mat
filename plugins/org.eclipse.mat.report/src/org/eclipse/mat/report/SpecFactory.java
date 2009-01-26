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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.report.internal.ReportPlugin;
import org.eclipse.mat.util.RegistryReader;
import org.osgi.framework.Bundle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;


public final class SpecFactory extends RegistryReader<SpecFactory.Report>
{
    public class Report
    {
        String name;
        String description;
        IConfigurationElement configElement;

        public Report(IConfigurationElement configElement)
        {
            this.configElement = configElement;
            this.name = configElement.getAttribute("name"); //$NON-NLS-1$
            this.description = configElement.getAttribute("description"); //$NON-NLS-1$
        }

        public String getExtensionIdentifier()
        {
            return configElement.getNamespaceIdentifier() + ":" + configElement.getAttribute("id"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        protected URL getURL()
        {
            Bundle bundle = Platform.getBundle(configElement.getContributor().getName());
            if (bundle == null)
                return null;

            return bundle.getResource(configElement.getAttribute("file")); //$NON-NLS-1$
        }

        public String getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }
    }

    private static final SpecFactory instance = new SpecFactory();

    public static final SpecFactory instance()
    {
        return instance;
    }

    private SpecFactory()
    {
        init(ReportPlugin.getDefault().getExtensionTracker(), ReportPlugin.PLUGIN_ID + ".report"); //$NON-NLS-1$
    }

    @Override
    protected Report createDelegate(IConfigurationElement configElement) throws CoreException
    {
        return new Report(configElement);
    }

    @Override
    protected void removeDelegate(Report delegate)
    {}

    public Spec create(Report report) throws IOException
    {
        URL url = report.getURL();
        if (url == null)
            return null;

        InputStream in = null;
        try
        {
            in = url.openStream();
            return read(in);
        }
        finally
        {
            if (in != null)
                in.close();
        }
    }

    public Spec create(String extensionIdentifier) throws IOException
    {
        for (Report report : delegates())
        {
            if (extensionIdentifier.equals(report.getExtensionIdentifier()))
                return create(report);
        }
        return null;
    }

    public Spec create(File specFile) throws IOException
    {
        FileInputStream in = new FileInputStream(specFile);

        try
        {
            return read(in);
        }
        finally
        {
            in.close();
        }
    }

    public void resolve(Spec master) throws IOException
    {
        if (master.getTemplate() != null)
        {
            String template = master.getTemplate();
            Spec other = create(template);
            if (other != null)
            {
                resolve(other);
                master.merge(other);
            }
            else
            {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                                MessageFormat.format(Messages.SpecFactory_Error_MissingTemplate, template));
            }
        }

        if (master instanceof SectionSpec)
        {
            for (Spec child : ((SectionSpec) master).getChildren())
                resolve(child);
        }
    }

    // //////////////////////////////////////////////////////////////
    // XML reading
    // //////////////////////////////////////////////////////////////

    private static final Spec read(InputStream input) throws IOException
    {
        try
        {
            SpecHandler handler = new SpecHandler();
            XMLReader saxXmlReader = XMLReaderFactory.createXMLReader();
            saxXmlReader.setContentHandler(handler);
            saxXmlReader.setErrorHandler(handler);
            saxXmlReader.parse(new InputSource(input));
            return handler.getSpec();
        }
        catch (SAXException e)
        {
            IOException ioe = new IOException();
            ioe.initCause(e);
            throw ioe;
        }
    }

    private static class SpecHandler extends DefaultHandler
    {
        private LinkedList<Spec> stack;
        private StringBuilder buf;

        private SpecHandler()
        {
            stack = new LinkedList<Spec>();
            stack.add(new SectionSpec("root")); //$NON-NLS-1$
        }

        @SuppressWarnings("nls")
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            if ("section".equals(localName))
            {
                SectionSpec spec = new SectionSpec(attributes.getValue("name"));
                ((SectionSpec) stack.getLast()).add(spec);
                stack.add(spec);
            }
            else if ("query".equals(localName))
            {
                QuerySpec spec = new QuerySpec(attributes.getValue("name"));
                ((SectionSpec) stack.getLast()).add(spec);
                stack.add(spec);
            }
            else if ("param".equals(localName))
            {
                stack.getLast().set(attributes.getValue("key"), attributes.getValue("value"));
            }
            else if ("template".equals(localName))
            {
                buf = new StringBuilder();
            }
            else if ("command".equals(localName))
            {
                buf = new StringBuilder();
            }
        }

        @SuppressWarnings("nls")
        @Override
        public void endElement(String uri, String localName, String name) throws SAXException
        {
            if ("section".equals(localName))
            {
                stack.removeLast();
            }
            else if ("query".equals(localName))
            {
                stack.removeLast();
            }
            else if ("template".equals(localName))
            {
                stack.getLast().setTemplate(buf.toString());
                buf = null;
            }
            else if ("command".equals(localName))
            {
                ((QuerySpec) stack.getLast()).setCommand(buf.toString());
                buf = null;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException
        {
            if (buf != null)
                buf.append(ch, start, length);
        }

        public Spec getSpec()
        {
            return ((SectionSpec) stack.getFirst()).getChildren().get(0);
        }
    }
}

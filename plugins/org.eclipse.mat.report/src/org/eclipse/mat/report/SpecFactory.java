/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - improved translation of reports
 *******************************************************************************/
package org.eclipse.mat.report;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.report.internal.ReportPlugin;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.RegistryReader;
import org.osgi.framework.Bundle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Builds a full report based on an xml report definition, which could 
 * specify several queries to be run.
 */
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
            return read(in, Platform.getBundle(report.configElement.getNamespaceIdentifier()), url);
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
            return read(in, null, specFile);
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
                                MessageUtil.format(Messages.SpecFactory_Error_MissingTemplate, template));
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

    private static final Spec read(InputStream input, Bundle bundle, Object source) throws IOException
    {
        try
        {
            SpecHandler handler = new SpecHandler(bundle, source);
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            // Add schema validation
            URL url = ReportPlugin.getDefault().getBundle().getResource("schema/report.xsd"); //$NON-NLS-1$
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(url);
            parserFactory.setSchema(schema);
            SAXParser parser = parserFactory.newSAXParser();
            XMLReader saxXmlReader =  parser.getXMLReader();
            // Old way is deprecated
            //saxXmlReader = XMLReaderFactory.createXMLReader();
            saxXmlReader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            saxXmlReader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
            saxXmlReader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
            saxXmlReader.setContentHandler(handler);
            saxXmlReader.setErrorHandler(handler);
            saxXmlReader.parse(new InputSource(input));
            return handler.getSpec();
        }
        catch (SAXException e)
        {
            IOException ioe = new IOException(source.toString());
            ioe.initCause(e);
            throw ioe;
        }
        catch (ParserConfigurationException e)
        {
            IOException ioe = new IOException();
            ioe.initCause(e);
            throw ioe;
        }
    }

    private static class SpecHandler extends DefaultHandler
    {
        private Bundle bundle;
        private LinkedList<Spec> stack;
        private StringBuilder buf;
        private Object source;

        private SpecHandler(Bundle bundle, Object source)
        {
            this.bundle = bundle;
            this.stack = new LinkedList<Spec>();
            this.stack.add(new SectionSpec("root")); //$NON-NLS-1$
            this.source = source;
        }

        @SuppressWarnings("nls")
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            if ("section".equals(localName))
            {
                String n = attributes.getValue("name");
                n = translate(n);

                SectionSpec spec = new SectionSpec(n);
                ((SectionSpec) stack.getLast()).add(spec);
                stack.add(spec);
            }
            else if ("query".equals(localName))
            {
                String n = attributes.getValue("name");
                n = translate(n);

                QuerySpec spec = new QuerySpec(n);
                ((SectionSpec) stack.getLast()).add(spec);
                stack.add(spec);
            }
            else if ("param".equals(localName))
            {
                String value = attributes.getValue("value");
                value = translate(value);
                stack.getLast().set(attributes.getValue("key"), value);
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

        /**
         * Some values are translatable using % prefix and a translation
         * in the plugin.properties file.
         * %key.part default value
         * 'key.part' is looked up, if missing then
         * 'default value' is returned (without quotes).
         * @param n
         * @return the translated string, or the default if present, or the key
         */
        private String translate(String n)
        {
            if (n != null && n.length() > 0 && n.charAt(0) == '%')
                n = Platform.getResourceString(bundle, n);
            return n;
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
        public void warning(SAXParseException e)
        {
            // Just log the warning
            Logger.getLogger(getClass().getName()).log(Level.INFO, MessageUtil.format(Messages.SpecFactory_ReportDefinitionWarning, getSpec().getName(), source), e);
        }

        @Override
        public void error(SAXParseException e)
        {
            // Just log the error
            Logger.getLogger(getClass().getName()).log(Level.WARNING, MessageUtil.format(Messages.SpecFactory_ReportDefinitionError, getSpec().getName(), source), e);
        }

        @Override
        public void fatalError(SAXParseException e)
        {
            // Just log the fatal error - an exception will be thrown later
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, MessageUtil.format(Messages.SpecFactory_ReportDefinitionSevereError, getSpec().getName(), source), e);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException
        {
            if (buf != null)
                buf.append(ch, start, length);
        }

        public Spec getSpec()
        {
            List<Spec>children = ((SectionSpec) stack.getFirst()).getChildren();
            if (children.isEmpty())
                return stack.getFirst();
            return children.get(0);
        }
    }
}

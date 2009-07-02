/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.internal.registry.ExtensionRegistry;
import org.eclipse.core.runtime.ContributorFactoryOSGi;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionDelta;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.content.IContentType;
import org.osgi.framework.BundleContext;

public class InitDTFJ extends Plugin implements IRegistryChangeListener
{

    private static String xmlCodePage = "UTF-8"; //$NON-NLS-1$
    private static final String DTFJ_NAMESPACE = "com.ibm.dtfj.api"; //$NON-NLS-1$
    private static final String DTFJ_IMAGEFACTORY = "imagefactory"; //$NON-NLS-1$
    private static final String MAT_PARSER = "org.eclipse.mat.parser.parser"; //$NON-NLS-1$

    /**
     * Start the bundle - find DTFJ implementations and convert to parsers.
     * Register listener for new DTFJ implementations.
     */
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        IExtensionRegistry reg = Platform.getExtensionRegistry();
        reg.addRegistryChangeListener(this, DTFJ_NAMESPACE);
        registerFileExtensions();
    }

    /**
     * Stop the bundle, deregister parsers associated with DTFJ. Deregister
     * listener for new DTFJ implementations.
     */
    public void stop(BundleContext context) throws Exception
    {
        IExtensionRegistry reg = Platform.getExtensionRegistry();
        removalAllExtensions();
        reg.removeRegistryChangeListener(this);
        super.stop(context);
    }

    /**
     * DTFJ implementation added/removed.
     */
    public void registryChanged(IRegistryChangeEvent event)
    {
        IExtensionRegistry reg = Platform.getExtensionRegistry();
        IContributor cont = getContributor(reg);
        // Find the standard Eclipse content types extension point
        IExtensionPoint contentPoint = contentExtensionPoint(reg);

        for (IExtensionDelta delta : event.getExtensionDeltas(DTFJ_NAMESPACE, DTFJ_IMAGEFACTORY))
        {
            IExtension dtfjExtension = delta.getExtension();

            switch (delta.getKind())
            {
                case IExtensionDelta.ADDED:
                    try
                    {
                        contributeParserExtension(reg, cont, contentPoint, dtfjExtension);
                    }
                    catch (UnsupportedEncodingException e2)
                    {}
                    break;
                case IExtensionDelta.REMOVED:
                    removeParserExtension(reg, cont, dtfjExtension);
                    break;
            }
        }
    }

    /**
     * Dynamically remove a plugin from the system
     * 
     * @param reg
     * @param cont
     * @param dtfjExtension
     */
    private void removeParserExtension(IExtensionRegistry reg, IContributor cont, IExtension dtfjExtension)
    {
        for (IConfigurationElement el : dtfjExtension.getConfigurationElements())
        {
            if (el.getName().equals("factory")) //$NON-NLS-1$
            {
                String id = el.getAttribute("id"); //$NON-NLS-1$
                String fullid = cont.getName() + "." + id; //$NON-NLS-1$
                IExtension extension = reg.getExtension(MAT_PARSER, fullid); //$NON-NLS-1$
                Object token = ((ExtensionRegistry) reg).getTemporaryUserToken();
                reg.removeExtension(extension, token);
            }
        }
    }

    /**
     * Remove all the DTFJ parsers.
     */
    private void removalAllExtensions()
    {
        IExtensionRegistry reg = Platform.getExtensionRegistry();
        IContributor cont = getContributor(reg);

        IExtensionPoint dtfjPoint = dtfjExtensionPoint(reg);

        if (dtfjPoint != null)
        {
            // Look through each DTFJ implementation
            for (IExtension ex : dtfjPoint.getExtensions())
            {
                removeParserExtension(reg, cont, ex);
            }
        }
    }

    /**
     * Convert DTFJ extensions into MAT parser extensions
     */
    void registerFileExtensions()
    {
        IExtensionRegistry reg = Platform.getExtensionRegistry();

        try
        {
            IContributor cont = getContributor(reg);

            IExtensionPoint dtfjPoint = dtfjExtensionPoint(reg);

            // Find the standard Eclipse content types extension point
            IExtensionPoint contentPoint = contentExtensionPoint(reg);

            if (dtfjPoint != null)
            {
                // Look through each DTFJ implementation
                for (IExtension ex : dtfjPoint.getExtensions())
                {
                    contributeParserExtension(reg, cont, contentPoint, ex);
                }
            }
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
    }

    private IExtensionPoint contentExtensionPoint(IExtensionRegistry reg)
    {
        return reg.getExtensionPoint("org.eclipse.core.contenttype", "contentTypes"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private IExtensionPoint dtfjExtensionPoint(IExtensionRegistry reg)
    {
        // Find the DTFJ extension point
        IExtensionPoint dtfjPoint = reg.getExtensionPoint(DTFJ_NAMESPACE, DTFJ_IMAGEFACTORY);
        return dtfjPoint;
    }

    /**
     * Helper method - find the contributor for this bundle.
     * 
     * @param reg
     * @return
     */
    private IContributor getContributor(IExtensionRegistry reg)
    {
        // Find a predefined DTFJ Adapter Plugin extension - use this to set
        // the provider for the new plugin
        IExtension es = reg.getExtension("org.eclipse.ui.startup", "org.eclipse.mat.dtfj.dtfj"); //$NON-NLS-1$ //$NON-NLS-2$

        // Second go at finding an extension for this bundle - the first
        // might fail if there is no UI.
        if (es == null)
        {
            es = reg.getExtension("org.eclipse.core.contenttype.contentTypes", "com.ibm.dtfj.base"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        IContributor cont;
        if (es != null)
        {
            cont = es.getContributor();
        }
        else
        {
            cont = ContributorFactoryOSGi.createContributor(getBundle());
        }
        return cont;
    }

    /**
     * Convert a DTFJ image factory to a parser extension.
     * 
     * @param reg
     * @param cont
     * @param contentPoint
     * @param dtfjExtension
     * @throws UnsupportedEncodingException
     */
    private void contributeParserExtension(IExtensionRegistry reg, IContributor cont, IExtensionPoint contentPoint,
                    IExtension dtfjExtension) throws UnsupportedEncodingException
    {
        for (IConfigurationElement el : dtfjExtension.getConfigurationElements())
        {
            if (el.getName().equals("factory")) //$NON-NLS-1$
            {
                // List of possible file types
                Set<String> done = new HashSet<String>();

                // Generate a plugin XML for each factory
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                OutputStreamWriter oo = new OutputStreamWriter(bos, xmlCodePage);
                PrintWriter ow = new PrintWriter(oo);

                // Later used as $heapFormat so we can tell which
                // DTFJ to use for a dump
                String id = el.getAttribute("id"); //$NON-NLS-1$
                String name = el.getAttribute("label"); //$NON-NLS-1$

                ow.println("<?xml version=\"1.0\" encoding=\"" + xmlCodePage + "\"?>"); //$NON-NLS-1$ //$NON-NLS-2$
                ow.println("<?eclipse version=\"3.2\"?>"); //$NON-NLS-1$
                ow.println("<plugin>"); //$NON-NLS-1$
                ow.println("<extension"); //$NON-NLS-1$
                ow.println("id=\"" + id + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                ow.println("name=\"" + name + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                ow.println("point=\"" + MAT_PARSER + "\">"); //$NON-NLS-1$ //$NON-NLS-2$

                for (IConfigurationElement el2 : el.getChildren())
                {
                    String ref = el2.getAttribute("dump-type"); //$NON-NLS-1$
                    if (ref != null && done.add(ref))
                    {
                        genParser(ref, contentPoint, ow);
                    }

                    ref = el2.getAttribute("meta-type"); //$NON-NLS-1$
                    if (ref != null && done.add(ref))
                    {
                        genParser(ref, contentPoint, ow);
                    }
                }
                ow.println("</extension>"); //$NON-NLS-1$
                ow.println("</plugin>"); //$NON-NLS-1$
                ow.flush();
                ow.close();
                // System.out.println("XML " + bos.toString());
                ByteArrayInputStream is = new ByteArrayInputStream(bos.toByteArray());
                // Dynamically add a plugin into the system
                Object token = ((ExtensionRegistry) reg).getTemporaryUserToken();
                reg.addContribution(is, cont, false, Messages.InitDTFJ_DynamicDtfj, null, token);
            }
        }
    }

    /**
     * Extract the description and the file extensions of a content type.
     * 
     * @param ref
     * @param point2
     * @param ow
     */
    private static void genParser(String ref, IExtensionPoint point2, PrintWriter ow)
    {

        IContentType ct = Platform.getContentTypeManager().getContentType(ref);
        if (ct == null)
        {
            // System.out.println("Missing content-type "+ref);
            return;
        }

        // Generate parser references for each content subtype
        for (IContentType ct1 : Platform.getContentTypeManager().getAllContentTypes())
        {
            if (ct1.isKindOf(ct))
                genParser(ct1, ow);
        }
    }

    private static void genParser(IContentType ct, PrintWriter ow)
    {
        String label = ct.getName();
        if (label != null)
        {
            String s[] = ct.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
            if (s.length > 0)
            {
                // Build a comma separated extensions list
                String exts = null;
                for (String s1 : s)
                {
                    if (exts == null)
                        exts = s1;
                    else
                        exts += "," + s1; //$NON-NLS-1$
                }
                // MAT copes with comma separated list of extensions from the
                // content-type list
                ow.println("<parser"); //$NON-NLS-1$
                ow.println("name=\"" + label + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                ow.println("fileExtension=\"" + exts + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                // Important - do not reference the actual DTFJIndexBuilder
                // class etc. otherwise this
                // has to load the MAT interfaces and can start MAT before it is
                // required.
                ow.println("indexBuilder=\"org.eclipse.mat.dtfj.DTFJIndexBuilder\""); //$NON-NLS-1$
                ow.println("objectReader=\"org.eclipse.mat.dtfj.DTFJHeapObjectReader\">"); //$NON-NLS-1$
                ow.println("</parser>"); //$NON-NLS-1$
            }
        }
    }
}

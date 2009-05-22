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
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.content.IContentType;
import org.osgi.framework.BundleContext;

public class InitDTFJ extends Plugin
{

    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        registerFileExtensions();
    }

    /**
     * Convert DTFJ extensions into MAT parser extensions
     */
    void registerFileExtensions()
    {
        IExtensionRegistry reg = Platform.getExtensionRegistry();
        String cp = "UTF-8"; //$NON-NLS-1$

        try
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

            // Find the DTFJ extension point
            IExtensionPoint dtfjPoint = reg.getExtensionPoint("com.ibm.dtfj.api", "imagefactory"); //$NON-NLS-1$ //$NON-NLS-2$

            // Find the standard Eclipse content types extension point
            IExtensionPoint contentPoint = reg.getExtensionPoint("org.eclipse.core.contenttype", "contentTypes"); //$NON-NLS-1$ //$NON-NLS-2$

            // List of possible file extensions
            Set<String> done = new HashSet<String>();

            if (dtfjPoint != null)
            {
                // Look through each DTFJ implementation
                for (IExtension ex : dtfjPoint.getExtensions())
                {
                    for (IConfigurationElement el : ex.getConfigurationElements())
                    {
                        if (el.getName().equals("factory")) //$NON-NLS-1$
                        {
                            // Generate a plugin XML for each factory
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            OutputStreamWriter oo = new OutputStreamWriter(bos, cp);
                            PrintWriter ow = new PrintWriter(oo);

                            // Later used as $heapFormat so we can tell which
                            // DTFJ to use for a dump
                            String id = el.getAttribute("id"); //$NON-NLS-1$
                            String name = el.getAttribute("label"); //$NON-NLS-1$

                            ow.println("<?xml version=\"1.0\" encoding=\"" + cp + "\"?>"); //$NON-NLS-1$ //$NON-NLS-2$
                            ow.println("<?eclipse version=\"3.2\"?>"); //$NON-NLS-1$
                            ow.println("<plugin>"); //$NON-NLS-1$
                            ow.println("<extension"); //$NON-NLS-1$
                            ow.println("id=\"" + id + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                            ow.println("name=\"" + name + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                            ow.println("point=\"org.eclipse.mat.parser.parser\">"); //$NON-NLS-1$

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
                            // System.out.println("XML "+bos.toString());
                            ByteArrayInputStream is = new ByteArrayInputStream(bos.toByteArray());
                            // Dynamically add a plugin into the system
                            Object token = ((ExtensionRegistry) reg).getTemporaryUserToken();
                            reg.addContribution(is, cont, false, Messages.InitDTFJ_DynamicDtfj, null, token);
                        }
                    }
                }
            }
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
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
                ow.println("indexBuilder=\"" + DTFJIndexBuilder.class.getName() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                ow.println("objectReader=\"" + DTFJHeapObjectReader.class.getName() + "\">"); //$NON-NLS-1$ //$NON-NLS-2$
                ow.println("</parser>"); //$NON-NLS-1$
            }
        }
    }
}

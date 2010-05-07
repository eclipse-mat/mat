/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.report;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.report.internal.ReportPlugin;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.RegistryReader;

/**
 * Holds all the renderers.
 */
public class RendererRegistry extends RegistryReader<IOutputter>
{
    private static final RendererRegistry instance = new RendererRegistry();

    private Map<String, Map<Class<? extends IResult>, IOutputter>> theMap = new HashMap<String, Map<Class<? extends IResult>, IOutputter>>();

    public static RendererRegistry instance()
    {
        return instance;
    }

    private RendererRegistry()
    {
        init(ReportPlugin.getDefault().getExtensionTracker(), ReportPlugin.PLUGIN_ID + ".renderer"); //$NON-NLS-1$
    }

    @Override
    protected synchronized IOutputter createDelegate(IConfigurationElement configElement) throws CoreException
    {
        IOutputter subject = (IOutputter) configElement.createExecutableExtension("impl"); //$NON-NLS-1$

        Renderer annotation = subject.getClass().getAnnotation(Renderer.class);
        if (annotation == null)
        {
            ReportPlugin.log(new RuntimeException(MessageUtil.format(Messages.RendererRegistry_Error_MissingAnnotation,
                            subject.getClass().getName())));
            return null;
        }

        String format = annotation.target();

        Map<Class<? extends IResult>, IOutputter> theFormatMap = theMap.get(format);
        if (theFormatMap == null)
            theMap.put(format, theFormatMap = new HashMap<Class<? extends IResult>, IOutputter>());

        Class<? extends IResult>[] r = annotation.result();
        for (Class<? extends IResult> type : r)
            theFormatMap.put(type, subject);

        return subject;
    }

    @Override
    protected synchronized void removeDelegate(IOutputter delegate)
    {
        for (Map<Class<? extends IResult>, IOutputter> formatMap : theMap.values())
        {
            for (Iterator<Map.Entry<Class<? extends IResult>, IOutputter>> iter = formatMap.entrySet().iterator(); iter
                            .hasNext();)
            {
                Map.Entry<Class<? extends IResult>, IOutputter> entry = iter.next();
                if (delegate == entry.getValue())
                    iter.remove();
            }
        }
    }

    public synchronized IOutputter match(String format, Class<? extends IResult> type)
    {
        Class<?> clazz = type;

        Map<Class<? extends IResult>, IOutputter> formatMap = theMap.get(format);
        if (formatMap == null)
            return null;

        while (clazz != null && clazz != Object.class)
        {
            IOutputter outputter = formatMap.get(clazz);
            if (outputter != null)
            {
                formatMap.put(type, outputter);
                return outputter;
            }

            LinkedList<Class<?>> interf = new LinkedList<Class<?>>();
            for (Class<?> itf : clazz.getInterfaces())
                interf.add(itf);

            while (!interf.isEmpty())
            {
                Class<?> current = interf.removeFirst();
                outputter = formatMap.get(current);
                if (outputter != null)
                {
                    formatMap.put(type, outputter);
                    return outputter;
                }

                for (Class<?> itf : current.getInterfaces())
                    interf.add(itf);
            }

            clazz = clazz.getSuperclass();
        }

        return null;
    }
}

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
package org.eclipse.mat.ui.editor;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.util.RegistryReader;

public class EditorPaneRegistry extends RegistryReader<PaneConfiguration>
{
    private static final EditorPaneRegistry INSTANCE = new EditorPaneRegistry();

    public static EditorPaneRegistry instance()
    {
        return INSTANCE;
    }

    private Map<String, PaneConfiguration> panesById = new HashMap<String, PaneConfiguration>();
    private Map<String, PaneConfiguration> panesByType = new HashMap<String, PaneConfiguration>();

    private EditorPaneRegistry()
    {
        init(MemoryAnalyserPlugin.getDefault().getExtensionTracker(), MemoryAnalyserPlugin.PLUGIN_ID + ".editorPanes"); //$NON-NLS-1$
    }

    @Override
    protected synchronized PaneConfiguration createDelegate(IConfigurationElement configElement) throws CoreException
    {
        PaneConfiguration cfg = new PaneConfiguration(configElement.getAttribute("id"), configElement);//$NON-NLS-1$
        panesById.put(cfg.getId(), cfg);

        for (IConfigurationElement child : configElement.getChildren())
            panesByType.put(child.getAttribute("type"), cfg);//$NON-NLS-1$

        return cfg;
    }

    @Override
    protected synchronized void removeDelegate(PaneConfiguration delegate)
    {
        PaneConfiguration cfg = panesById.get(delegate.getId());
        if (cfg == delegate)
            panesById.remove(delegate.getId());
    }

    public PaneConfiguration forPane(String paneId)
    {
        return panesById.get(paneId);
    }

    public AbstractEditorPane createNewPane(String paneId) throws CoreException
    {
        PaneConfiguration editor = forPane(paneId);
        return editor != null ? editor.build() : null;
    }

    /**
     * Find the appropriate editor pane for the result,
     * ignoring ones associated with the ignore class.
     * Searches all subclasses and interfaces.
     * @param subject
     * @param ignore
     * @return
     */
    public AbstractEditorPane createNewPane(IResult subject, Class<?> ignore)
    {
        try
        {
            String ignoreClassName = ignore != null ? ignore.getName() : "";//$NON-NLS-1$

            Class<?> clazz = subject.getClass();

            while (clazz != null && clazz != Object.class)
            {
                PaneConfiguration paneConfig = panesByType.get(clazz.getName());
                if (paneConfig != null && !ignoreClassName.equals(paneConfig.getClassName()))
                    return paneConfig.build();

                LinkedList<Class<?>> interf = new LinkedList<Class<?>>();
                for (Class<?> itf : clazz.getInterfaces())
                    interf.add(itf);

                while (!interf.isEmpty())
                {
                    Class<?> current = interf.removeFirst();
                    paneConfig = panesByType.get(current.getName());
                    if (paneConfig != null && !ignoreClassName.equals(paneConfig.getClassName()))
                        return paneConfig.build();

                    for (Class<?> itf : current.getInterfaces())
                        interf.add(itf);
                }

                clazz = clazz.getSuperclass();
            }

            return null;
        }
        catch (CoreException e)
        {
            throw new RuntimeException(e);
        }
    }

}

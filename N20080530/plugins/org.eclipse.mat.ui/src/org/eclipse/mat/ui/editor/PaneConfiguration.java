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
package org.eclipse.mat.ui.editor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.MultiPaneEditor.Constants;


public class PaneConfiguration
{
    private volatile static Map<String, PaneConfiguration> PANES;

    private final String id;
    private final IConfigurationElement confElement;
    private final int sequenceNr;

    public PaneConfiguration(String id, IConfigurationElement confElement, int sequenceNr)
    {
        this.id = id;
        this.confElement = confElement;
        this.sequenceNr = sequenceNr;
    }

    public String getId()
    {
        return id;
    }

    public IConfigurationElement getConfElement()
    {
        return confElement;
    }

    public int getSequenceNr()
    {
        return sequenceNr;
    }

    public AbstractEditorPane build() throws CoreException
    {
        AbstractEditorPane part = (AbstractEditorPane) confElement.createExecutableExtension(Constants.ATT_CLASS);
        part.setConfiguration(this);
        return part;
    }

    public static Collection<PaneConfiguration> panes()
    {
        setup();
        return PANES.values();
    }

    public static PaneConfiguration forPane(String paneId)
    {
        setup();
        return PANES.get(paneId);
    }

    public static AbstractEditorPane createNewPane(String paneId) throws CoreException
    {
        PaneConfiguration editor = forPane(paneId);
        return editor != null ? editor.build() : null;
    }

    private static void setup()
    {
        if (PANES == null)
        {
            synchronized (PaneConfiguration.class)
            {
                if (PANES == null)
                {
                    PANES = new HashMap<String, PaneConfiguration>();

                    IExtensionRegistry registry = Platform.getExtensionRegistry();
                    IExtensionPoint point = registry.getExtensionPoint(MemoryAnalyserPlugin.PLUGIN_ID + ".editorPanes"); //$NON-NLS-1$
                    if (point != null)
                    {
                        IExtension[] extensions = point.getExtensions();
                        for (int i = 0; i < extensions.length; i++)
                        {
                            IConfigurationElement confElements[] = extensions[i].getConfigurationElements();
                            for (int jj = 0; jj < confElements.length; jj++)
                            {
                                String sequenceNrStr = confElements[jj].getAttribute(Constants.ATT_SEQUENCE_NR);
                                int sequenceNr = sequenceNrStr != null && sequenceNrStr.length() > 0 ? Integer
                                                .valueOf(sequenceNrStr) : Integer.MAX_VALUE;
                                PaneConfiguration cfg = new PaneConfiguration(confElements[jj]
                                                .getAttribute(Constants.ATT_ID), confElements[jj], sequenceNr);
                                PANES.put(cfg.id, cfg);
                            }
                        }
                    }
                }
            }
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.editor;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;

public class PaneConfiguration
{
    private final String id;
    private final String className;
    private final IConfigurationElement confElement;

    /* package */PaneConfiguration(String id, IConfigurationElement configElement)
    {
        this.id = id;
        this.className = configElement.getAttribute("class");//$NON-NLS-1$
        this.confElement = configElement;
    }

    public String getId()
    {
        return id;
    }

    public String getClassName()
    {
        return className;
    }

    public IConfigurationElement getConfElement()
    {
        return confElement;
    }

    public AbstractEditorPane build() throws CoreException
    {
        AbstractEditorPane part = (AbstractEditorPane) confElement.createExecutableExtension("class");//$NON-NLS-1$
        part.setConfiguration(this);
        return part;
    }

}

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
package org.eclipse.mat.ui.rcp.actions;

import java.io.PrintWriter;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.intro.config.IIntroContentProvider;
import org.eclipse.ui.intro.config.IIntroContentProviderSite;
import org.osgi.framework.Bundle;

public class VersionIntroContentProvider implements IIntroContentProvider
{

    public void createContent(String id, PrintWriter out)
    {
        final String buildId = System.getProperty("mat.buildId", "Unknown Build"); //$NON-NLS-1$ //$NON-NLS-2$
        out.print(buildId);
        
        Bundle bundle = Platform.getBundle("org.eclipse.mat.api");
        if (bundle != null)
        {
            out.print(" (API ");
            out.print(bundle.getHeaders().get("Bundle-Version"));
            out.print(")");
        }
    }

    public void createContent(String id, Composite parent, FormToolkit toolkit)
    {}

    public void dispose()
    {}

    public void init(IIntroContentProviderSite site)
    {}

}

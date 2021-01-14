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
        Bundle bundle = Platform.getBundle("org.eclipse.mat.api");//$NON-NLS-1$
        if (bundle != null)
        {
            out.print("(API "); //$NON-NLS-1$
            out.print(bundle.getHeaders().get("Bundle-Version")); //$NON-NLS-1$
            out.print(")");//$NON-NLS-1$
        }
    }

    public void createContent(String id, Composite parent, FormToolkit toolkit)
    {}

    public void dispose()
    {}

    public void init(IIntroContentProviderSite site)
    {}

}

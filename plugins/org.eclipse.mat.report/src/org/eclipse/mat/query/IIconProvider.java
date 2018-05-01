/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

import java.net.URL;

/**
 * Interface to provide icon representation for rows of a
 * {@link IStructuredResult}.
 * <p>
 * See {@link org.eclipse.mat.query.annotations.Icon} 
 * {@link org.eclipse.mat.ui.snapshot.ImageHelper} for well-known icons. To add custom icons, place the GIF
 * file in your class path and return the resource URL:
 * <p>Example</p>
 * 
 * <pre>
 * private static final URL SCA = SCAQuery.class.getResource(&quot;/META-INF/icons/sca.gif&quot;);
 * 
 * public URL getIcon(Object row)
 * {
 *     if (row instanceof SCA)
 *         return SCA;
 *     return null;
 * }
 * </pre>
 */
public interface IIconProvider
{
    URL getIcon(Object row);

    public static final IIconProvider EMPTY = new IIconProvider()
    {
        public URL getIcon(Object row)
        {
            return null;
        }
    };
}

/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - move to new package
 *******************************************************************************/
package org.eclipse.mat.hprof.describer;

/* package */public enum Version
{
    JDK12BETA3("JAVA PROFILE 1.0"), //$NON-NLS-1$
    JDK12BETA4("JAVA PROFILE 1.0.1"), //$NON-NLS-1$
    JDK6("JAVA PROFILE 1.0.2");//$NON-NLS-1$

    private String label;

    private Version(String label)
    {
        this.label = label;
    }

    public static final Version byLabel(String label)
    {
        for (Version v : Version.values())
        {
            if (v.label.equals(label))
                return v;
        }
        return null;
    }

    public String getLabel()
    {
        return label;
    }
}

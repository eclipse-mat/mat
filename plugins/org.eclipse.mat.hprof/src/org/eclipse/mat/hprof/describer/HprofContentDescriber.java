/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - move to new package
 *******************************************************************************/
package org.eclipse.mat.hprof.describer;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.content.IContentDescriber;
import org.eclipse.core.runtime.content.IContentDescription;

public class HprofContentDescriber implements IContentDescriber
{
    private static final QualifiedName[] QUALIFIED_NAMES = new QualifiedName[] { new QualifiedName("java-heap-dump", //$NON-NLS-1$
                    "hprof") }; //$NON-NLS-1$

    public int describe(InputStream contents, IContentDescription description) throws IOException
    {
        return readVersion(contents) != null ? VALID : INVALID;
    }

    public QualifiedName[] getSupportedOptions()
    {
        return QUALIFIED_NAMES;
    }

    /*
     * Used for content describers, don't throw exceptions.
     */
    /* protected */static Version readVersion(InputStream in) throws IOException
    {
        StringBuilder version = new StringBuilder();

        int bytesRead = 0;
        while (bytesRead < 20)
        {
            byte b = (byte) in.read();
            bytesRead++;

            if (b != 0)
            {
                version.append((char) b);
            }
            else
            {
                Version answer = Version.byLabel(version.toString());
                if (answer == null)
                {
                    return null;
                }

                if (answer == Version.JDK12BETA3) // not supported by MAT
                    return null;
                return answer;
            }
        }

        return null;
    }
}

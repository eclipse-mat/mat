/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.content.IContentDescriber;
import org.eclipse.core.runtime.content.IContentDescription;

public class HprofGZIPContentDescriber implements IContentDescriber
{
    private static final QualifiedName[] QUALIFIED_NAMES = new QualifiedName[] { new QualifiedName("java-heap-dump", //$NON-NLS-1$
                    "hprof-gzip") }; //$NON-NLS-1$

    public int describe(InputStream contents, IContentDescription description) throws IOException
    {
        return AbstractParser.readVersion(new GZIPInputStream(contents)) != null ? VALID : INVALID;
    }

    public QualifiedName[] getSupportedOptions()
    {
        return QUALIFIED_NAMES;
    }

}

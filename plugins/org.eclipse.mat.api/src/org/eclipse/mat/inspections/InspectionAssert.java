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
package org.eclipse.mat.inspections;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Locale;
import java.util.StringTokenizer;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.MessageUtil;

public class InspectionAssert
{

    public static void heapFormatIsNot(ISnapshot snapshot, String... heapFormat)
    {
        Serializable currentHeapFormat = snapshot.getSnapshotInfo().getProperty("$heapFormat"); //$NON-NLS-1$

        for (String f : heapFormat)
        {
            if (f.equals(currentHeapFormat))
            {
                StringWriter sw = new StringWriter();
                new Throwable().printStackTrace(new PrintWriter(sw));
                StringTokenizer st = new StringTokenizer(sw.toString(), "\n"); //$NON-NLS-1$
                st.nextToken(); // error message
                st.nextToken(); // our own stack

                String reason = st.nextToken();
                String className = reason.substring(4, reason.lastIndexOf('.', reason.lastIndexOf('(')));
                QueryDescriptor descriptor = QueryRegistry.instance().getQuery(className.toLowerCase(Locale.ENGLISH));

                String name = descriptor == null ? className : descriptor.getName();

                throw new UnsupportedOperationException(MessageUtil.format(Messages.InspectionAssert_NotSupported,
                                currentHeapFormat, name));
            }
        }
    }

}

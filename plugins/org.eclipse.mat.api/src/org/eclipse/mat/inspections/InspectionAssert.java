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
import java.util.StringTokenizer;

import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.snapshot.ISnapshot;

public class InspectionAssert
{

    public static void heapFormatIsNot(ISnapshot snapshot, String... heapFormat)
    {
        Serializable currentHeapFormat = snapshot.getSnapshotInfo().getProperty("$heapFormat");

        for (String f : heapFormat)
        {
            if (f.equals(currentHeapFormat))
            {
                StringWriter sw = new StringWriter();
                new Throwable().printStackTrace(new PrintWriter(sw));
                StringTokenizer st = new StringTokenizer(sw.toString(), "\n");
                st.nextToken(); // error message
                st.nextToken(); // our own stack

                String reason = st.nextToken();
                String className = reason.substring(4, reason.lastIndexOf('.', reason.lastIndexOf('(')));
                QueryDescriptor descriptor = QueryRegistry.instance().getQuery(className.toLowerCase());

                String name = descriptor == null ? className : descriptor.getName();

                throw new UnsupportedOperationException(String.format(
                                "Dump format '%s' does not support inspection '%s'.", currentHeapFormat, name));
            }
        }
    }

}

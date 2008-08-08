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
package org.eclipse.mat.tests.regression.query;

import java.util.List;

public class List2String
{
    List<Long> path;

    List2String(List<Long> addresses)
    {
        this.path = addresses;
    }

    public String getPath()
    {
        StringBuilder buffer = new StringBuilder();
        int counter = 0;
        for (Long address : path)
        {
            buffer.append("0x").append(Long.toHexString(address));
            if (counter != path.size() - 1)
                buffer.append(", ");
            counter = counter + 1;
        }
        return buffer.toString();
    }
}
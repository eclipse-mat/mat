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

package org.eclipse.mat.ui.snapshot.actions;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

/* package */final class ExportInfo
{
    private final IPrimitiveArray charArray;
    private final int offset;
    private final int count;

    private ExportInfo(IPrimitiveArray charArray, int offset, int count)
    {
        this.charArray = charArray;
        this.offset = offset;
        this.count = count;
    }

    public IPrimitiveArray getCharArray()
    {
        return charArray;
    }

    public int getOffset()
    {
        return offset;
    }

    public int getCount()
    {
        return count;
    }

    // //////////////////////////////////////////////////////////////
    // factory method
    // //////////////////////////////////////////////////////////////

    public static ExportInfo of(IObject object) throws SnapshotException
    {
        Integer offset = 0;
        Integer count = 0;
        IPrimitiveArray charArray = null;

        if ("java.lang.String".equals(object.getClazz().getName()))
        {
            offset = (Integer) object.resolveValue("offset");
            count = (Integer) object.resolveValue("count");
            charArray = (IPrimitiveArray) object.resolveValue("value");
        }
        else if ("char[]".equals(object.getClazz().getName()))
        {
            charArray = (IPrimitiveArray) object;
            count = charArray.getLength();
        }
        else if (object.getClazz().doesExtend("java.lang.AbstractStringBuilder"))
        {
            count = (Integer) object.resolveValue("count");
            charArray = (IPrimitiveArray) object.resolveValue("value");
        }
        else
        {
            return null;
        }

        if (offset == null || count == null || charArray == null)
            return null;

        return new ExportInfo(charArray, offset, count);
    }

}

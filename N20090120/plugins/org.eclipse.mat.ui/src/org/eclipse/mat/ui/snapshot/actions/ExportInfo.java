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

        if ("java.lang.String".equals(object.getClazz().getName()))//$NON-NLS-1$
        {
            offset = (Integer) object.resolveValue("offset");//$NON-NLS-1$
            count = (Integer) object.resolveValue("count");//$NON-NLS-1$
            charArray = (IPrimitiveArray) object.resolveValue("value");//$NON-NLS-1$
        }
        else if ("char[]".equals(object.getClazz().getName()))//$NON-NLS-1$
        {
            charArray = (IPrimitiveArray) object;
            count = charArray.getLength();
        }
        else if (object.getClazz().doesExtend("java.lang.AbstractStringBuilder"))//$NON-NLS-1$
        {
            count = (Integer) object.resolveValue("count");//$NON-NLS-1$
            charArray = (IPrimitiveArray) object.resolveValue("value");//$NON-NLS-1$
        }
        else if (object.getClazz().doesExtend("java.io.StringWriter"))//$NON-NLS-1$
        {
            count = (Integer) object.resolveValue("buf.count");//$NON-NLS-1$
            charArray = (IPrimitiveArray) object.resolveValue("buf.value");//$NON-NLS-1$
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

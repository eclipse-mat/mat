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
package org.eclipse.mat.snapshot.model;

import org.eclipse.mat.SnapshotException;

public class PrettyPrinter
{
    public static String objectAsString(IObject stringObject, int limit) throws SnapshotException
    {
        int count = (Integer) stringObject.resolveValue("count");
        if (count == 0)
            return "";

        IPrimitiveArray charArray = (IPrimitiveArray) stringObject.resolveValue("value");
        if (charArray == null)
            return null;

        int offset = (Integer) stringObject.resolveValue("offset");

        return arrayAsString(charArray, offset, count, limit);
    }

    public static String arrayAsString(IPrimitiveArray charArray, int offset, int count, int limit)
    {
        if (charArray.getType() != IObject.Type.CHAR)
            return null;

        int length = charArray.getLength();
        int elementSize = IPrimitiveArray.ELEMENT_SIZE[IObject.Type.CHAR];

        int contentToRead = count <= limit ? count : limit;
        if (contentToRead > length - offset)
            contentToRead = length - offset;

        byte[] value;
        if (offset == 0 && length == contentToRead)
        {
            value = (byte[]) charArray.getContent();
        }
        else
        {
            // read part of the content only. The result is not cached
            contentToRead *= elementSize;
            value = (byte[]) charArray.getContent(elementSize * offset, contentToRead);
        }

        StringBuilder result = new StringBuilder(value.length >> 1);
        for (int ii = 0; ii < value.length;)
        {
            char val = (char) (((value[ii] & 0xff) << 8) + (value[ii + 1] & 0xff));
            if (val >= 32 && val < 127)
                result.append(val);
            else
                result.append("\\u").append(String.format("%04x", 0xFFFF & val));
            ii += elementSize;
        }
        if (limit < count)
            result.append("...");
        return result.toString();
    }
}

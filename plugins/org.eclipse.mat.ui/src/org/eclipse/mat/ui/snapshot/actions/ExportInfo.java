/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - updates for Java 9
 *******************************************************************************/

package org.eclipse.mat.ui.snapshot.actions;

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

/* package */final class ExportInfo
{
    private final IPrimitiveArray charArray;
    private final int offset;
    private final int count;
    private enum Mode { CHAR, LATIN1, UTF16BIG, UTF16LITTLE };
    private final Mode m; 

    private ExportInfo(IPrimitiveArray charArray, int offset, int count, Mode m)
    {
        this.charArray = charArray;
        this.offset = offset;
        this.count = count;
        this.m = m;
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
    
    public char[] getChars(int offset, int read)
    {
        char[] array;
        if (m == Mode.LATIN1)
        {
            byte[] bytes = (byte[]) charArray.getValueArray(offset, read);
            array = new char[bytes.length];
            for (int i = 0; i < bytes.length; ++i)
            {
                array[i] = (char)bytes[i];
            }
            return array;
        }
        else if (m == Mode.UTF16LITTLE)
        {
            byte[] bytes = (byte[]) charArray.getValueArray(offset * 2, read * 2);
            array = new char[bytes.length / 2];
            for (int i = 0; i < bytes.length; i += 2)
            {
                array[i / 2] = (char)(bytes[i] & 0xff | (bytes[i+1] & 0xff) << 8);  
            }
            return array;
        }
        else if (m == Mode.UTF16BIG)
        {
            byte[] bytes = (byte[]) charArray.getValueArray(offset * 2, read * 2);
            array = new char[bytes.length / 2];
            for (int i = 0; i < bytes.length; i += 2)
            {
                array[i / 2] = (char)(bytes[i+1] & 0xff | (bytes[i] & 0xff) << 8);  
            }
            return array;
        }
        else
        {
            array = (char[]) charArray.getValueArray(offset, read);
        }
        return array;
    }
    
    public int getLength()
    {
        if (m == Mode.UTF16BIG || m == Mode.UTF16LITTLE)
        {
            return charArray.getLength() / 2;
        }
        else
        {
            return charArray.getLength();
        }
            
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
            if (offset == null)
            {
                // Java 7
                offset = 0;
            }
            if (count == null && charArray != null)
            {
                // Java 7
                count = charArray.getLength() - offset;
            }
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

        Mode m;
        if (charArray.getType() == IObject.Type.CHAR)
        {
            m = Mode.CHAR;
        }
        else if (charArray.getType() == IObject.Type.BYTE)
        {
            Object o = object.resolveValue("coder");//$NON-NLS-1$
            if (o instanceof Byte && ((Byte)o).byteValue() != 0)
            {
                Collection<IClass>clss = object.getSnapshot().getClassesByName("java.lang.StringUTF16", false);//$NON-NLS-1$
                if (clss != null && !clss.isEmpty())
                {
                    IClass str = clss.iterator().next();
                    Object shift = str.resolveValue("HI_BYTE_SHIFT");//$NON-NLS-1$
                    if (shift instanceof Integer && (Integer)shift == 8)
                    {
                        m = Mode.UTF16BIG;
                    }
                    else
                    {
                        m = Mode.UTF16LITTLE;
                    }
                }
                else
                {
                    m = Mode.UTF16LITTLE;
                }
                // Allow for two bytes per char
                count = Math.min(charArray.getLength() / 2 - offset, count);
            }
            else
            {
                m = Mode.LATIN1;
            }
        }
        else
        {
            return null;
        }
        return new ExportInfo(charArray, offset, count, m);
    }

}

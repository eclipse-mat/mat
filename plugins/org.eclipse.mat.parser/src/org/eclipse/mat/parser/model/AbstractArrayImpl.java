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
package org.eclipse.mat.parser.model;

import java.io.IOException;
import java.lang.ref.SoftReference;

import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IArray;


public abstract class AbstractArrayImpl extends AbstractObjectImpl implements IArray
{
    private static final long serialVersionUID = 1L;

    public static class ArrayContentDescriptor
    {
        boolean isPrimitive;
        long position;
        int arraySize;
        int elementSize;

        public ArrayContentDescriptor(boolean isPrimitive, long position, int elementSize, int arraySize)
        {
            this.isPrimitive = isPrimitive;
            this.position = position;
            this.elementSize = elementSize;
            this.arraySize = arraySize;
        }

        public boolean isPrimitive()
        {
            return isPrimitive;
        }

        public long getPosition()
        {
            return position;
        }

        public int getArraySize()
        {
            return arraySize;
        }

        public int getElementSize()
        {
            return elementSize;
        }

    }

    protected int length;
    private Object content;
    private transient SoftReference<Object> lazyReadContent;

    public AbstractArrayImpl(int objectId, long address, ClassImpl classInstance, int length, Object content)
    {
        super(objectId, address, classInstance);
        this.length = length;
        this.content = content;
    }

    public int getLength()
    {
        return length;
    }

    public void setLength(int i)
    {
        length = i;
    }

    public Object getContent()
    {
        // content might be lazy loaded
        if (content instanceof ArrayContentDescriptor)
        {
            synchronized (this)
            {
                Object result = null;
                if (lazyReadContent != null)
                    result = lazyReadContent.get();

                if (result == null)
                {
                    try
                    {
                        ArrayContentDescriptor descriptor = (ArrayContentDescriptor) content;
                        result = source.getHeapObjectReader().read(descriptor);
                        lazyReadContent = new SoftReference<Object>(result);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                return result;
            }
        }

        return content;
    }

    public Object getContent(int offset, int length)
    {
        try
        {
            if (content instanceof ArrayContentDescriptor)
            {
                Object soft = null;
                if (lazyReadContent != null)
                    soft = lazyReadContent.get();

                if (soft != null)
                {
                    byte[] answer = new byte[length];
                    System.arraycopy(soft, offset, answer, 0, answer.length);
                    return answer;
                }
                else
                {
                    return source.getHeapObjectReader().read((ArrayContentDescriptor) content, offset, length);
                }
            }
            else
            {
                byte[] answer = new byte[length];
                System.arraycopy(content, offset, answer, 0, answer.length);
                return answer;
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected StringBuffer appendFields(StringBuffer buf)
    {
        return super.appendFields(buf).append(";length=").append(length);
    }

    protected Field internalGetField(String name)
    {
        return null;
    }

    public String getTechnicalName()
    {
        StringBuilder builder = new StringBuilder(256);
        String name = getClazz().getName();

        int p = name.indexOf('[');
        if (p < 0)
            builder.append(name);
        else
            builder.append(name.subSequence(0, p + 1)).append(getLength()).append(name.substring(p + 1));

        builder.append(" @ 0x");
        builder.append(Long.toHexString(getObjectAddress()));
        return builder.toString();
    }

}

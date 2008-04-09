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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.PseudoReference;

public class PrimitiveArrayImpl extends AbstractArrayImpl implements IPrimitiveArray
{
    private static final long serialVersionUID = 1L;

    private int type;

    public PrimitiveArrayImpl(int objectId, long address, ClassImpl classInstance, int length, int type, Object content)
    {
        super(objectId, address, classInstance, length, content);
        this.type = type;
    }

    public int getType()
    {
        return type;
    }
    
    public Object getValueAt(int index)
    {
        byte[] data = (byte[]) getContent(index * ELEMENT_SIZE[type], ELEMENT_SIZE[type]);
        return asObject(data, 0);
    }

    private Object asObject(byte[] data, int offset)
    {
        switch (type)
        {
            case Type.BOOLEAN:
                return data[offset] != 0;
            case Type.CHAR:
                return readChar(data, offset);
            case Type.FLOAT:
                return readFloat(data, offset);
            case Type.DOUBLE:
                return readDouble(data, offset);
            case Type.BYTE:
                return data[offset];
            case Type.SHORT:
                return readShort(data, offset);
            case Type.INT:
                return readInt(data, offset);
            case Type.LONG:
                return readLong(data, offset);
        }
        return null;
    }
    
    @Override
    public ArrayLong getReferences()
    {
        ArrayLong references = new ArrayLong(1);
        references.add(classInstance.getObjectAddress());
        return references;
    }

    public List<NamedReference> getOutboundReferences()
    {
        List<NamedReference> references = new ArrayList<NamedReference>(1);
        references.add(new PseudoReference(source, classInstance.getObjectAddress(), "<class>"));
        return references;
    }

    @Override
    protected StringBuffer appendFields(StringBuffer buf)
    {
        return super.appendFields(buf).append(";size=").append(getUsedHeapSize());
    }

    @Override
    public int getUsedHeapSize()
    {
        return doGetUsedHeapSize(classInstance, length, type);
    }

    public static int doGetUsedHeapSize(ClassImpl clazz, int length, int type)
    {
        return alignUpTo8(2 * clazz.getHeapSizePerInstance() + 4 + length * ELEMENT_SIZE[type]);
    }

    private short readShort(byte[] data, int offset)
    {
        return (short) ((data[offset] << 8) + data[offset + 1]);
    }

    private char readChar(byte[] data, int offset)
    {
        return (char) ((data[offset] << 8) + data[offset + 1]);
    }

    private int readInt(byte[] data, int offset)
    {
        int ch1 = data[offset] & 0xff;
        int ch2 = data[offset + 1] & 0xff;
        int ch3 = data[offset + 2] & 0xff;
        int ch4 = data[offset + 3] & 0xff;
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    private float readFloat(byte[] data, int offset)
    {
        return Float.intBitsToFloat(readInt(data, offset));
    }

    private long readLong(byte[] data, int offset)
    {
        return (((long) data[offset] << 56) + //
                        ((long) (data[offset + 1] & 255) << 48) + //
                        ((long) (data[offset + 2] & 255) << 40) + //
                        ((long) (data[offset + 3] & 255) << 32) + //
                        ((long) (data[offset + 4] & 255) << 24) + //
                        ((data[offset + 5] & 255) << 16) + //
                        ((data[offset + 6] & 255) << 8) + //
        ((data[offset + 7] & 255) << 0));
    }

    private double readDouble(byte[] data, int offset)
    {
        return Double.longBitsToDouble(readLong(data, offset));
    }

}

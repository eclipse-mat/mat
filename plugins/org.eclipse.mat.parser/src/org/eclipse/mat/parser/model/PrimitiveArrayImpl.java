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
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.PseudoReference;


public class PrimitiveArrayImpl extends AbstractArrayImpl implements IPrimitiveArray
{
    private static final long serialVersionUID = 1L;

    public interface Type
    {
        int T_BOOLEAN = 4;
        int T_CHAR = 5;
        int T_FLOAT = 6;
        int T_DOUBLE = 7;
        int T_BYTE = 8;
        int T_SHORT = 9;
        int T_INT = 10;
        int T_LONG = 11;
    }

    private static final byte[] SIGNATURES = { -1, -1, -1, -1, (byte) 'Z', (byte) 'C', (byte) 'F', (byte) 'D',
                    (byte) 'B', (byte) 'S', (byte) 'I', (byte) 'J' };
    private static final int[] ELEMENT_SIZE = { -1, -1, -1, -1, 1, 2, 4, 8, 1, 2, 4, 8 };
    private static final String[] TYPE = { null, null, null, null, "boolean[]", "char[]", "float[]", "double[]",
                    "byte[]", "short[]", "int[]", "long[]" };

    public static String determineReadableName(char c)
    {
        for (int ii = 0; ii < SIGNATURES.length; ii++)
        {
            if (SIGNATURES[ii] == (byte) c)
                return TYPE[ii];
        }
        return "unknown[]";
    }

    private int type;

    public PrimitiveArrayImpl(int objectId, long address, ClassImpl classInstance, int length, int type, Object content)
    {
        super(objectId, address, classInstance, length, content);
        this.type = type;
    }

    public int getUsedHeapSize()
    {
        return doGetUsedHeapSize(classInstance, length, type);
    }

    public static int doGetUsedHeapSize(ClassImpl clazz, int length, int type)
    {
        return alignUpTo8(2 * clazz.getHeapSizePerInstance() + 4 + length * ELEMENT_SIZE[type]);
    }

    @Override
    protected StringBuffer appendFields(StringBuffer buf)
    {
        return super.appendFields(buf).append(";size=").append(getUsedHeapSize());
    }

    public int getType()
    {
        return type;
    }

    public ArrayLong getReferences()
    {
        ArrayLong answer = new ArrayLong(1);
        answer.add(classInstance.getObjectAddress());
        return answer;
    }

    public List<NamedReference> getOutboundReferences()
    {
        List<NamedReference> answer = new ArrayList<NamedReference>(1);
        answer.add(new PseudoReference(source, classInstance.getObjectAddress(), "<class>"));
        return answer;
    }

    public Field getField(int index)
    {
        Object v = null;

        byte[] value = readBytesAt(index);

        switch (SIGNATURES[type])
        {
            case 'C':
            {
                char val = charAt(0, value);
                v = Character.valueOf(val);
                break;
            }
            case 'Z':
            {
                boolean val = booleanAt(0, value);
                v = Boolean.valueOf(val);
                break;
            }
            case 'B':
            {
                int val = 0xFF & byteAt(0, value);
                v = "0x" + Integer.toString(val, 16);
                break;
            }
            case 'S':
            {
                short val = shortAt(0, value);
                v = Short.valueOf(val);
                break;
            }
            case 'I':
            {
                int val = intAt(0, value);
                v = Integer.valueOf(val);
                break;
            }
            case 'J':
            { // long
                long val = longAt(0, value);
                v = Long.valueOf(val);
                break;
            }
            case 'F':
            {
                float val = floatAt(0, value);
                v = Float.valueOf(val);
                break;
            }
            case 'D':
            { // double
                double val = doubleAt(0, value);
                v = Double.valueOf(val);
                break;
            }
            default:
            {
                throw new RuntimeException("unknown primitive type?");
            }
        }

        return new Field("[" + index + "]", String.valueOf((char) SIGNATURES[type]), v);
    }

    public String valueString(int limit)
    {
        return valueString(limit, 0, length);
    }

    public String valueString(int limit, int offset, int count)
    {
        StringBuilder result;

        int contentToRead = count <= limit ? count : limit;
        if (contentToRead > length - offset)
            contentToRead = length - offset;
        
        byte[] value;
        if (offset == 0 && length == contentToRead)
        {
            /* if possible use getContent() -> no array copy, and the content is cached */
            value = (byte[]) getContent(); 
        }
        else
        {
            /* read part of the content only. The result is not cached */
            contentToRead *= ELEMENT_SIZE[type];
            value = (byte[]) getContent(ELEMENT_SIZE[type] * offset, contentToRead);
        }
        
        if (SIGNATURES[type] == 'C')
        {
            result = new StringBuilder(value.length >> 1);

            for (int i = 0; i < value.length;)
            {
                char val = charAt(i, value);
                if (val >= 32 && val < 127)
                {
                    result.append(val);
                }
                else
                {
                    result.append("\\u").append(Integer.toString(0xFFFF & val, 16));
                }
                i += 2;
            }
            if (limit < count)
                result.append("...");
        }
        else
        {
            result = new StringBuilder("{");
            int num = 0;
            for (int i = 0; i < value.length;)
            {
                if (num > 0)
                {
                    result.append(", ");
                }
                if (num >= limit)
                {
                    result.append("... ");
                    break;
                }
                num++;
                switch (SIGNATURES[type])
                {
                    case 'Z':
                    {
                        boolean val = booleanAt(i, value);
                        if (val)
                        {
                            result.append("true");
                        }
                        else
                        {
                            result.append("false");
                        }
                        i++;
                        break;
                    }
                    case 'B':
                    {
                        int val = 0xFF & byteAt(i, value);
                        result.append("0x" + Integer.toString(val, 16));
                        i++;
                        break;
                    }
                    case 'S':
                    {
                        short val = shortAt(i, value);
                        i += 2;
                        result.append("" + val);
                        break;
                    }
                    case 'I':
                    {
                        int val = intAt(i, value);
                        i += 4;
                        result.append("" + val);
                        break;
                    }
                    case 'J':
                    { // long
                        long val = longAt(i, value);
                        result.append("" + val);
                        i += 8;
                        break;
                    }
                    case 'F':
                    {
                        float val = floatAt(i, value);
                        result.append("" + val);
                        i += 4;
                        break;
                    }
                    case 'D':
                    { // double
                        double val = doubleAt(i, value);
                        result.append("" + val);
                        i += 8;
                        break;
                    }
                    default:
                    {
                        throw new RuntimeException("unknown primitive type?");
                    }
                }
            }
            result.append("}");
        }
        return result.toString();
    }

    // //////////////////////////////////////////////////////////////
    // private methods
    // //////////////////////////////////////////////////////////////

    private byte[] readBytesAt(int index)
    {
        return (byte[]) this.getContent(index * ELEMENT_SIZE[type], ELEMENT_SIZE[type]);
    }

    private boolean booleanAt(int index, byte[] value)
    {
        return (value[index] & 0xFF) == 0 ? false : true;
    }

    private byte byteAt(int index, byte[] value)
    {
        return (byte) (value[index] & 0xFF);
    }

    private char charAt(int index, byte[] value)
    {
        int b1 = ((int) value[index++]) & 0xff;
        int b2 = ((int) value[index]) & 0xff;
        return (char) ((b1 << 8) + b2);
    }

    private short shortAt(int index, byte[] value)
    {
        int b1 = ((int) value[index++]) & 0xff;
        int b2 = ((int) value[index]) & 0xff;
        return (short) ((b1 << 8) + b2);
    }

    private int intAt(int index, byte[] value)
    {
        int b1 = ((int) value[index++]) & 0xff;
        int b2 = ((int) value[index++]) & 0xff;
        int b3 = ((int) value[index++]) & 0xff;
        int b4 = ((int) value[index]) & 0xff;
        return ((b1 << 24) + (b2 << 16) + (b3 << 8) + (b4 << 0));
    }

    private long longAt(int index, byte[] value)
    {
        long val = 0;
        for (int j = 0; j < 8; j++)
        {
            val = val << 8;
            int b = ((int) value[index++]) & 0xff;
            val |= b;
        }
        return val;
    }

    private float floatAt(int index, byte[] value)
    {
        int val = intAt(index, value);
        return Float.intBitsToFloat(val);
    }

    private double doubleAt(int index, byte[] value)
    {
        long val = longAt(index, value);
        return Double.longBitsToDouble(val);
    }

}

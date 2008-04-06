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

    public Field getField(int index)
    {
        byte[] data = (byte[]) getContent(index * ELEMENT_SIZE[type], ELEMENT_SIZE[type]);
        Object v = asObject(data, 0);
        return new Field("[" + index + "]", String.valueOf((char) SIGNATURES[type]), v);
    }

    private Object asObject(byte[] data, int offset)
    {
        switch (type)
        {
            case Type.BOOLEAN:
            case 'Z':
                return data[offset] != 0;
            case Type.BYTE:
            case 'B':
                return data[offset];
            case Type.SHORT:
            case 'S':
                return readShort(data, offset);
            case Type.CHAR:
            case 'C':
                return readChar(data, offset);
            case Type.INT:
            case 'I':
                return readInt(data, offset);
            case Type.FLOAT:
            case 'F':
                return readFloat(data, offset);
            case Type.LONG:
            case 'J':
                return readLong(data, offset);
            case Type.DOUBLE:
            case 'D':
                return readDouble(data, offset);
        }
        return null;
    }
    
    public String valueString(int limit, int offset, int count)
    {
        int numOfElement = count <= limit ? count : limit;
        if (numOfElement > length - offset)
            numOfElement = length - offset;

        byte[] data = null;
        if (offset == 0 && length == numOfElement)
        {
            // if possible use getContent() -> no array copy, and the content is
            // cached
            data = (byte[]) getContent();
        }
        else
        {
            // read part of the content only. The result is not cached
            data = (byte[]) getContent(offset * ELEMENT_SIZE[type], numOfElement * ELEMENT_SIZE[type]);
        }

        if (type == Type.CHAR)
        {
            StringBuilder answer = new StringBuilder(numOfElement * 110 / 100);

            for (int ii = 0; ii < numOfElement; ii++)
            {
                char val = readChar(data, ii * 2);
                if (val >= 32 && val < 127)
                    answer.append(val);
                else
                    answer.append("\\u").append(Integer.toString(0xFFFF & val, 16));
            }
            if (limit < count)
                answer.append("...");
            return answer.toString();
        }
        else
        {
            StringBuilder answer = new StringBuilder("{");

            if (offset > 0)
                answer.append("...");

            for (int ii = 0; ii < numOfElement; ii++)
            {
                if (ii > 0)
                    answer.append(",");
                
                answer.append(asObject(data, ii * ELEMENT_SIZE[type]));
            }
            
            if (numOfElement - offset < length)
                answer.append("...");
            
            answer.append("{");
            
            return answer.toString();
        }
    }

    public String valueString(int limit)
    {
        return valueString(limit, 0, length);
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

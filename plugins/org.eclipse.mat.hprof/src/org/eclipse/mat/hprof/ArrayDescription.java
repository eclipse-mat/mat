package org.eclipse.mat.hprof;

import java.lang.ref.SoftReference;

/* package */class ArrayDescription
{
    /* package */static class Offline extends ArrayDescription
    {
        boolean isPrimitive;
        long position;
        int arraySize;
        int elementSize;
        
        SoftReference<Object> lazyReadContent = new SoftReference<Object>(null);

        public Offline(boolean isPrimitive, long position, int elementSize, int arraySize)
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

        public Object getLazyReadContent()
        {
            return lazyReadContent.get();
        }

        public void setLazyReadContent(Object content)
        {
            this.lazyReadContent = new SoftReference<Object>(content);
        }
        
        
    }

    /* package */static class Raw extends ArrayDescription
    {
        byte[] content;

        public Raw(byte[] content)
        {
            this.content = content;
        }

        public byte[] getContent()
        {
            return content;
        }
    }
}

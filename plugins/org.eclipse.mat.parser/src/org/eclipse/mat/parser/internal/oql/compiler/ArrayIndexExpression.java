/*******************************************************************************
 * Copyright (c) 2012, 2017 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import java.lang.ref.SoftReference;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedCollection;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;

class ArrayIndexExpression extends Expression
{
    private static class PrimitiveArraySubList extends AbstractList<Object>
    {
        private final IPrimitiveArray array;
        private final int i1;
        private final int i2;

        private PrimitiveArraySubList(IPrimitiveArray array, int i1, int i2)
        {
            this.array = array;
            this.i1 = i1;
            this.i2 = i2;
        }

        @Override
        public Object get(int index)
        {
            if (index < 0 || index >= size()) throw new IndexOutOfBoundsException(Integer.toString(index));
            return array.getValueAt(i1 + index);
        }

        @Override
        public int size()
        {
            return i2 - i1;
        }
    }

    private static class ArraySubList extends AbstractList<Object>
    {
        private final Object subject;
        private final int i1;
        private final int i2;

        private ArraySubList(Object subject, int i1, int i2)
        {
            this.subject = subject;
            this.i1 = i1;
            this.i2 = i2;
        }

        @Override
        public Object get(int index)
        {
            if (index < 0 || index >= size()) throw new IndexOutOfBoundsException(Integer.toString(index));
            return Array.get(subject, i1 + index);
        }

        @Override
        public int size()
        {
            return i2 - i1;
        }
    }

    private static class ObjectArraySubList extends AbstractList<IObject>
    {
        private final IObjectArray array;
        private final int i1;
        private final int i2;

        public ObjectArraySubList(IObjectArray array, int i1, int i2)
        {
            this.array = array;
            this.i1 = i1;
            this.i2 = i2;
        }
        
        @Override
        public IObject get(int index)
        {
            if (index < 0 || index >= size()) throw new IndexOutOfBoundsException(Integer.toString(index));
            long addr = array.getReferenceArray(i1 + index, 1)[0];
            if (addr == 0)
                return null;
            ISnapshot snapshot = array.getSnapshot();
            try
            {
                int objectId = snapshot.mapAddressToId(addr);
                return snapshot.getObject(objectId);
            }
            catch (SnapshotException e)
            {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int size()
        {
            return i2 - i1;
        }
        
    }
    
    private static class CollectionObjectSubList extends AbstractList<IObject>
    {
        private final ExtractedCollection coll;
        private final int i1;
        private final int i2;
        /** Cache extraction of the objects in the collection */
        SoftReference<int[]> sr = null;

        public CollectionObjectSubList(ExtractedCollection coll, int i1, int i2)
        {
            this.coll = coll;
            this.i1 = i1;
            this.i2 = i2;
            
        }
        
        @Override
        public IObject get(int index)
        {
            if (index < 0 || index >= size())
                throw new IndexOutOfBoundsException(Integer.toString(index));
            int objs[];
            if (!(sr != null && (objs = sr.get()) != null))
            {
                objs = coll.extractEntryIds();
                sr = new SoftReference<int[]>(objs);
            }
            if (i1 + index >= i2)
            { 
                throw new IllegalArgumentException(index + " >= " + (i2 - i1) +" " + coll.getTechnicalName()); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (i1 + index >= objs.length)
            { 
                /*
                 * Currently the CollectionsExtractor only returns non-null entries, so the number of entries
                 * can be less than the size, so pad at the end with nulls.
                 */
                return null;
            }
            int objectId = objs[i1 + index];
            try
            {
                return coll.getSnapshot().getObject(objectId);
            }
            catch (SnapshotException e)
            {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int size()
        {
            return i2 - i1;
        }
        
    }
    
    List<Expression> parameters;

    public ArrayIndexExpression(List<Expression> parameters)
    {
        this.parameters = parameters;
    }

    @Override
    public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
    {
        final Object subject = ctx.getSubject();
        if (subject == null)
            return null;

        // compute arguments
        Object[] arguments = new Object[parameters.size()];
        for (int ii = 0; ii < arguments.length; ii++)
            arguments[ii] = parameters.get(ii).compute(ctx);

        Object indexObj = arguments[0];
        int index = evalIndex(indexObj);
        boolean range = arguments.length > 1;
        int index2 = index;
        if (range)
            index2 = evalIndex(arguments[1]);

        if (subject.getClass().isArray())
        {
            int len = Array.getLength(subject);
            if (range)
            {
                final int i1 = normalize(index, len, 0), i2 = normalize(index2, len, 1);
                return new ArraySubList(subject, i1, i2);
            }
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            return Array.get(subject, index);
        }
        else if (subject instanceof List)
        {
            List<?> l = (List<?>) subject;
            int len = l.size();
            if (range)
            {
                final int i1 = normalize(index, len, 0), i2 = normalize(index2, len, 1);
                return ((List<?>) subject).subList(i1, i2);
            }
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            return ((List<?>) subject).get(index);
        }
        else if (subject instanceof IPrimitiveArray)
        {
            final IPrimitiveArray array = (IPrimitiveArray) subject;
            int len = array.getLength();
            if (range) {
                final int i1 = normalize(index, len, 0), i2 = normalize(index2, len, 1);
                return new PrimitiveArraySubList(array, i1, i2);
            }
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            return array.getValueAt(index);
        }
        else if (subject instanceof IObjectArray)
        {
            final IObjectArray array = (IObjectArray) subject;
            int len = array.getLength();
            if (range) {
                final int i1 = normalize(index, len, 0), i2 = normalize(index2, len, 1);
                return new ObjectArraySubList(array, i1, i2);
            }
            if (index >= len)
                return null;
            if (index < 0)
                return null;
            long addr = array.getReferenceArray(index, 1)[0];
            if (addr == 0)
                return null;
            ISnapshot snapshot = array.getSnapshot();
            int objectId = snapshot.mapAddressToId(addr);
            return snapshot.getObject(objectId);
        }
        else if (subject instanceof IObject)
        {
            IObject obj = (IObject) subject;
            ExtractedCollection coll = CollectionExtractionUtils.extractList(obj);
            if (coll != null && coll.hasExtractableContents())
            {
                Object objlen = coll.size();
                if (objlen != null)
                {
                    int len = (Integer)objlen;
                    if (range)
                    {
                        final int i1 = normalize(index, len, 0), i2 = normalize(index2, len, 1);
                        return new CollectionObjectSubList(coll, i1, i2);
                    }
                    if (index >= len)
                        return null;
                    if (index < 0)
                        return null;
                    ISnapshot snapshot = coll.getSnapshot();
                    int objectId = coll.extractEntryIds()[index];
                    coll.getSnapshot().getObject(objectId);
                    return snapshot.getObject(objectId);
                }
            }
        }
        // Show the array, which is wrong, and the index for context
        throw new IllegalArgumentException(subject + toString() + ": " + subject.getClass()); //$NON-NLS-1$
    }

    private int evalIndex(Object indexObj)
    {
        int index;
        if (indexObj instanceof Integer || indexObj instanceof Short || indexObj instanceof Byte)
        {
            index = ((Number) indexObj).intValue();
        }
        else
        {
            // Don't show the array to show that just the index is wrong
            throw new IllegalArgumentException(toString()+": "+(indexObj != null ? indexObj.getClass() : null)); //$NON-NLS-1$
        }
        return index;
    }

    /**
     * Allow indexing from end of array
     * @param i
     * @param s
     * @param a
     * @return
     * [0][1:-1] -> [0,0)
     * [1][1:-1] -> [0,1)
     * [2][1:-1] -> [1,2)
     */
    private int normalize(int i, int s, int a) {
        if (i < 0)
            i += s;
        i += a;
        if (i > s) i = s;
        if (i < 0) i = 0; 
        return i;
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        for (Expression element : this.parameters)
        {
            boolean isContextDependent = element.isContextDependent(ctx);

            if (isContextDependent)
                return true;
        }

        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(256);

        buf.append("[");//$NON-NLS-1$

        for (Iterator<Expression> iter = this.parameters.iterator(); iter.hasNext();)
        {
            Expression element = iter.next();
            buf.append(element);

            if (iter.hasNext())
                buf.append(":");//$NON-NLS-1$
        }

        buf.append("]");//$NON-NLS-1$

        return buf.toString();
    }

}

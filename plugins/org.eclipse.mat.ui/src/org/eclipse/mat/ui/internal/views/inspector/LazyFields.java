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
package org.eclipse.mat.ui.internal.views.inspector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;

/* package */abstract class LazyFields<O extends IObject>
{
    private WeakReference<ISnapshot> snapshot;
    private WeakReference<O> array;

    private int objectId;

    protected List<Object> cache = new ArrayList<Object>();

    public LazyFields(O object)
    {
        if (object != null)
        {
            this.snapshot = new WeakReference<ISnapshot>(object.getSnapshot());
            this.array = new WeakReference<O>(object);

            this.objectId = object.getObjectId();
        }
    }

    public final List<?> getElements(int limit)
    {
        if (cache.size() >= limit || cache.size() == getSize())
            return cache;

        O array = getObject();

        for (int ii = cache.size(); ii < limit && ii < getSize(); ii++)
            cache.add(createElement(array, ii));

        return cache;
    }

    @SuppressWarnings("unchecked")
    private final O getObject()
    {
        O object = this.array.get();
        if (object == null)
        {
            ISnapshot snapshot = this.snapshot.get();
            if (snapshot == null)
                throw new RuntimeException("Error Reading arrays details: snapshot not available anymore.");

            try
            {
                object = (O) snapshot.getObject(objectId);
                this.array = new WeakReference<O>(object);
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }
        return object;
    }

    protected abstract Object createElement(O array, int index);

    public abstract int getSize();

    // //////////////////////////////////////////////////////////////
    // object specific implementations
    // //////////////////////////////////////////////////////////////

    /* package */static final LazyFields<IObject> EMPTY = new LazyFields<IObject>(null)
    {
        @Override
        protected Object createElement(IObject array, int index)
        {
            return null;
        }

        @Override
        public int getSize()
        {
            return 0;
        }
    };

    /* package */static class Class extends LazyFields<IClass>
    {
        public Class(IClass object)
        {
            super(object);
            fixObjectReferences(object.getSnapshot(), cache, object.getStaticFields(), true);
        }

        @Override
        protected Object createElement(IClass array, int index)
        {
            return null;
        }

        @Override
        public int getSize()
        {
            return cache.size();
        }
    }

    /* package */static class Instance extends LazyFields<IInstance>
    {
        public Instance(IInstance object)
        {
            super(object);
            fixObjectReferences(object.getSnapshot(), cache, object.getClazz().getStaticFields(), true);
            fixObjectReferences(object.getSnapshot(), cache, object.getFields(), false);
        }

        @Override
        protected Object createElement(IInstance array, int index)
        {
            return null;
        }

        @Override
        public int getSize()
        {
            return cache.size();
        }
    }

    /* package */static class PrimitiveArray extends LazyFields<IPrimitiveArray>
    {
        private int length;

        public PrimitiveArray(IPrimitiveArray array)
        {
            super(array);
            this.length = array.getLength();
        }

        public int getSize()
        {
            return length;
        }

        @Override
        protected Object createElement(IPrimitiveArray array, int index)
        {
            Field field = new Field("[" + index + "]", array.getType(), array.getValueAt(index));
            return new FieldNode(field, false);
        }
    }

    /* package */static class ObjectArray extends LazyFields<IObjectArray>
    {
        private int length;

        public ObjectArray(IObjectArray array)
        {
            super(array);
            this.length = array.getLength();
        }

        public int getSize()
        {
            return length;
        }

        @Override
        protected Object createElement(IObjectArray array, int index)
        {
            long refs[] = (long[]) array.getContent();

            if (refs[index] != 0)
            {
                NamedReference ref = new NamedReference(array.getSnapshot(), refs[index], "[" + index + "]");
                return new NamedReferenceNode(ref, false);
            }
            else
            {
                Field f = new Field("[" + index + "]", IObject.Type.OBJECT, "null");
                return new FieldNode(f, false);
            }
        }
    }

    // //////////////////////////////////////////////////////////////
    // private helpers
    // //////////////////////////////////////////////////////////////

    protected static void fixObjectReferences(ISnapshot snapshot, List<Object> appendTo, List<?> fields,
                    boolean areStatics)
    {
        for (int ii = 0; ii < fields.size(); ii++)
        {
            Field field = (Field) fields.get(ii);

            if (field instanceof Field && field.getValue() instanceof ObjectReference)
            {
                ObjectReference ref = (ObjectReference) field.getValue();
                if (ref != null)
                {
                    appendTo.add(new NamedReferenceNode(new NamedReference(snapshot, ref.getObjectAddress(), field
                                    .getName()), areStatics));
                }
                else
                {
                    Field f = new Field(field.getName(), field.getType(), "null");
                    appendTo.add(new FieldNode(f, areStatics));
                }
            }
            else
            {
                appendTo.add(new FieldNode(field, areStatics));
            }
        }
    }
}

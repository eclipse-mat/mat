/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.views.inspector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.ui.Messages;

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
                throw new RuntimeException(Messages.LazyFields_ErrorReadingArrayDetails);

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
        public Class(IClass object, boolean showPseudoStatics, boolean showSubclasses)
        {
            super(object);
            do
            {
                fixObjectReferences(object.getSnapshot(), cache, object.getStaticFields(), true, showPseudoStatics);
            }
            while (showSubclasses && (object = object.getSuperClass()) != null);
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
            fixObjectReferences(object.getSnapshot(), cache, object.getFields(), false, false);
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
            Field field = new Field("[" + index + "]", array.getType(), array.getValueAt(index)); //$NON-NLS-1$//$NON-NLS-2$
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
            long refs[] = array.getReferenceArray(index, 1);

            if (refs[0] != 0)
            {
                NamedReference ref = new NamedReference(array.getSnapshot(), refs[0], "[" + index + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                return new NamedReferenceNode(ref, false);
            }
            else
            {
                Field f = new Field("[" + index + "]", IObject.Type.OBJECT, "null");//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                return new FieldNode(f, false);
            }
        }
    }

    // //////////////////////////////////////////////////////////////
    // private helpers
    // //////////////////////////////////////////////////////////////

    protected static void fixObjectReferences(ISnapshot snapshot, List<Object> appendTo, List<Field> fields,
                    boolean areStatics, boolean showPseudoStatics)
    {
        for (int ii = 0; ii < fields.size(); ii++)
        {
            Field field = fields.get(ii);

            // Do we want to show pseudo static fields?
            if (areStatics && (field.getName().startsWith("<") != showPseudoStatics)) continue;  //$NON-NLS-1$
            
            if (field.getValue() instanceof ObjectReference)
            {
                ObjectReference ref = (ObjectReference) field.getValue();
                if (ref != null)
                {
                    appendTo.add(new NamedReferenceNode(new NamedReference(snapshot, ref.getObjectAddress(), field
                                    .getName()), areStatics));
                }
                else
                {
                    Field f = new Field(field.getName(), field.getType(), "null"); //$NON-NLS-1$
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

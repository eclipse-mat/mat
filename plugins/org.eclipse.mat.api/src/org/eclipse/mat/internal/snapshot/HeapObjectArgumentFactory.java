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

package org.eclipse.mat.internal.snapshot;

import java.lang.reflect.Array;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentFactory;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

public abstract class HeapObjectArgumentFactory implements ArgumentFactory
{
    public static final ArgumentFactory build(ISnapshot snapshot, int[] objectIds)
    {
        return new AsIntegerImpl(snapshot, objectIds);
    }

    public static final ArgumentFactory build(ISnapshot snapshot, IHeapObjectArgument argument)
    {
        return new AsObjectArgumentImpl(snapshot, argument, "[objects]");
    }

    protected final ISnapshot snapshot;

    protected HeapObjectArgumentFactory(ISnapshot snapshot)
    {
        this.snapshot = snapshot;
    }

    public Object build(ArgumentDescriptor argument) throws SnapshotException
    {
        try
        {
            // resolving of object ids is deferred
            if (IHeapObjectArgument.class.isAssignableFrom(argument.getType()))
            {
                IHeapObjectArgument value = asObjectArgument();

                if (argument.isArray())
                {
                    Object array = Array.newInstance(argument.getType(), 1);
                    Array.set(array, 0, value);
                    return array;
                }
                else if (argument.isList())
                {
                    List<IHeapObjectArgument> list = new ArrayList<IHeapObjectArgument>(1);
                    list.add(value);
                    return list;
                }
                else
                {
                    return value;
                }
            }
            else
            {
                int[] objectIds = asIntegerArray();
                return assignObjectIds(argument, snapshot, objectIds);
            }
        }
        catch (SnapshotException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new SnapshotException(MessageFormat.format("{0}: Error setting heap objects to field ''{1}''", e
                            .getClass().getCanonicalName(), argument.getName()), e);
        }
    }

    private Object assignObjectIds(ArgumentDescriptor argument, ISnapshot snapshot, int[] objectIds)
                    throws IllegalArgumentException, SnapshotException
    {
        if (argument.isArray())
        {
            if (argument.getType() == int.class)
            {
                return objectIds;
            }
            else if (argument.getType() == Integer.class)
            {
                Object array = Array.newInstance(argument.getType(), objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    Array.set(array, ii, Integer.valueOf(objectIds[ii]));

                return array;
            }
            else if (IObject.class.isAssignableFrom(argument.getType()))
            {
                Object array = Array.newInstance(argument.getType(), objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    Array.set(array, ii, snapshot.getObject(objectIds[ii]));

                return array;
            }
            else
            {
                throw new SnapshotException();
            }
        }
        else if (argument.isList())
        {
            if (argument.getType() == Integer.class)
            {
                List<Integer> list = new ArrayList<Integer>(objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    list.add(objectIds[ii]);

                return list;
            }
            else if (IObject.class.isAssignableFrom(argument.getType()))
            {
                List<IObject> list = new ArrayList<IObject>(objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    list.add(snapshot.getObject(objectIds[ii]));

                return list;
            }
            else
            {
                throw new SnapshotException(MessageFormat.format("Type ''{0}'' of argument ''{1}'' not supported.",
                                argument.getType().getName(), argument.getName()));
            }
        }
        else
        {
            if (objectIds.length != 1)
                throw new SnapshotException(MessageFormat.format(
                                "Argument ''{0}'' does not allow to assign multiple objects", argument.getName()));

            if (argument.getType() == int.class)
            {
                return objectIds[0];
            }
            else if (argument.getType() == Integer.class)
            {
                return objectIds[0];
            }
            else if (IObject.class.isAssignableFrom(argument.getType()))
            {
                return snapshot.getObject(objectIds[0]);
            }
            else
            {
                throw new SnapshotException(MessageFormat.format("Type ''{0}'' of argument ''{1}'' not supported.",
                                argument.getType().getName(), argument.getName()));
            }
        }
    }

    protected abstract int[] asIntegerArray() throws SnapshotException;

    protected abstract IHeapObjectArgument asObjectArgument();

    // //////////////////////////////////////////////////////////////
    // used for IHeapObjectArgument
    // //////////////////////////////////////////////////////////////

    private static class AsObjectArgumentImpl extends HeapObjectArgumentFactory
    {
        private final IHeapObjectArgument hoa;
        private final String label;

        public AsObjectArgumentImpl(ISnapshot snapshot, IHeapObjectArgument hoa, String label)
        {
            super(snapshot);
            this.hoa = hoa;
            this.label = label;
        }

        public void appendUsage(StringBuilder buf)
        {
            buf.append(label);
        }

        @Override
        protected IHeapObjectArgument asObjectArgument()
        {
            return hoa;
        }

        @Override
        protected int[] asIntegerArray() throws SnapshotException
        {
            return hoa.getIds(new VoidProgressListener());
        }

    }

    // //////////////////////////////////////////////////////////////
    // used for integer arrays
    // //////////////////////////////////////////////////////////////

    private static class AsIntegerImpl extends HeapObjectArgumentFactory
    {
        private final int[] objectIds;

        public AsIntegerImpl(ISnapshot snapshot, int[] objectIds)
        {
            super(snapshot);
            this.objectIds = objectIds;
        }

        public void appendUsage(StringBuilder buf)
        {
            buf.append("[objects]");
        }

        @Override
        protected IHeapObjectArgument asObjectArgument()
        {
            return new IHeapObjectArgument()
            {

                public int[] getIds(IProgressListener listener) throws SnapshotException
                {
                    return objectIds;
                }

                public String getLabel()
                {
                    return "Objects";
                }

                public Iterator<int[]> iterator()
                {
                    return new Iterator<int[]>()
                    {
                        boolean done = false;

                        public boolean hasNext()
                        {
                            return !done;
                        }

                        public int[] next()
                        {
                            done = true;
                            return objectIds;
                        }

                        public void remove()
                        {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            };
        }

        @Override
        protected int[] asIntegerArray()
        {
            return objectIds;
        }
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - lazy loading of length
 *******************************************************************************/
package org.eclipse.mat.parser.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.model.PseudoReference;
import org.eclipse.mat.util.MessageUtil;

/**
 * Implementation of a Java object array.
 * @noextend
 */
public class ObjectArrayImpl extends AbstractArrayImpl implements IObjectArray
{
    private static final long serialVersionUID = 2L;

    /**
     * Constructs an array of objects.
     * @param objectId the object id of the array
     * @param address the actual address
     * @param classInstance the type of the array
     * @param length the length of the array in elements
     */
    public ObjectArrayImpl(int objectId, long address, ClassImpl classInstance, int length)
    {
        super(objectId, address, classInstance, length);
    }

    @Override
    public long getUsedHeapSize()
    {
        try {
            return getSnapshot().getHeapSize(getObjectId());
        } catch (SnapshotException e) {
            return doGetUsedHeapSize(classInstance, getLength());
        }
    }

    /**
     * Calculates the size of an object array
     * @param clazz the type
     * @param length the length in elements
     * @return the size in bytes
     * @since 1.0
     */
    public static long doGetUsedHeapSize(ClassImpl clazz, int length)
    {
        return alignUpTo8(2 * clazz.getHeapSizePerInstance() + 4 + length * (long)clazz.getHeapSizePerInstance());
    }

    public long[] getReferenceArray()
    {
        try
        {
            return source.getHeapObjectReader().readObjectArrayContent(this, 0, getLength());
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public long[] getReferenceArray(int offset, int length)
    {
        try
        {
            return source.getHeapObjectReader().readObjectArrayContent(this, offset, length);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public ArrayLong getReferences()
    {
        ArrayLong answer = new ArrayLong(getLength() + 1);

        answer.add(classInstance.getObjectAddress());

        long refs[] = getReferenceArray();
        for (int i = 0; i < refs.length; i++)
        {
            if (refs[i] != 0)
            {
                answer.add(refs[i]);
            }
        }

        return answer;
    }

    protected Field internalGetField(String name)
    {
        if (name.charAt(0) != '[' || name.charAt(name.length() - 1) != ']')
            return null;

        try
        {
            int index = Integer.parseInt(name.substring(1, name.length() - 1));
            if (index < 0 || index > getLength())
                throw new IndexOutOfBoundsException(MessageUtil.format(Messages.ObjectArrayImpl_forArray, index,
                                getTechnicalName()));

            long[] references = source.getHeapObjectReader().readObjectArrayContent(this, index, 1);
            return new Field(name, IObject.Type.OBJECT, new ObjectReference(source, references[0]));
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<NamedReference> getOutboundReferences()
    {
        List<NamedReference> answer = new ArrayList<NamedReference>(getLength() + 1);

        answer.add(new PseudoReference(source, classInstance.getObjectAddress(), "<class>"));//$NON-NLS-1$

        long refs[] = getReferenceArray();
        for (int i = 0; i < refs.length; i++)
        {
            if (refs[i] != 0)
            {
                StringBuilder builder = new StringBuilder();
                builder.append('[').append(i).append(']');

                answer.add(new NamedReference(source, refs[i], builder.toString()));
            }
        }

        return answer;
    }

}

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
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.PseudoReference;

/**
 * @noextend
 */
public class ObjectArrayImpl extends AbstractArrayImpl implements IObjectArray
{
    private static final long serialVersionUID = 1L;

    public ObjectArrayImpl(int objectId, long address, ClassImpl classInstance, int length, Object content)
    {
        super(objectId, address, classInstance, length, content);
    }

    @Override
    public int getUsedHeapSize()
    {
        return doGetUsedHeapSize(classInstance, length);
    }

    public static int doGetUsedHeapSize(ClassImpl clazz, int length)
    {
        return alignUpTo8(2 * clazz.getHeapSizePerInstance() + 4 + length * clazz.getHeapSizePerInstance());
    }

    public ArrayLong getReferences()
    {
        ArrayLong answer = new ArrayLong(getLength() + 1);

        answer.add(classInstance.getObjectAddress());

        long refs[] = (long[]) getContent();
        for (int i = 0; i < refs.length; i++)
        {
            if (refs[i] != 0)
            {
                answer.add(refs[i]);
            }
        }

        return answer;
    }

    public List<NamedReference> getOutboundReferences()
    {
        List<NamedReference> answer = new ArrayList<NamedReference>(getLength() + 1);

        answer.add(new PseudoReference(source, classInstance.getObjectAddress(), "<class>"));

        long refs[] = (long[]) getContent();
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

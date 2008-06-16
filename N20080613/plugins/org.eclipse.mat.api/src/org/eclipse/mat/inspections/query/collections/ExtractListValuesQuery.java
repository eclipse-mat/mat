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
package org.eclipse.mat.inspections.query.collections;

import java.text.MessageFormat;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.query.ObjectListQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.PseudoReference;
import org.eclipse.mat.util.IProgressListener;


@Name("Extract List Values")
@CommandName("extract_list_values")
@Category("Java Collections")
@Help("List elements of a LinkedList, ArrayList or Vector object.")
public class ExtractListValuesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IObject list;

    public IResult execute(IProgressListener listener) throws Exception
    {
        if (list.getClazz().doesExtend("java.util.LinkedList"))
        {
            return extractLinkedList(listener);
        }
        else if (list.getClazz().doesExtend("java.util.ArrayList"))
        {
            return extractArrayList(listener, "size");
        }
        else if (list.getClazz().doesExtend("java.util.Vector")) { return extractArrayList(listener, "elementCount"); }

        throw new IllegalArgumentException(MessageFormat.format("Not a ArrayList, Vector or LinkedList: {0}", list
                        .getDisplayName()));
    }

    private IResult extractArrayList(IProgressListener listener, String sizeAttribute) throws SnapshotException
    {
        int size = (Integer) list.resolveValue(sizeAttribute);

        if (size == 0)
            return new ObjectListQuery.OutboundObjects(snapshot, new int[0]);

        String taskMsg = MessageFormat.format("Collecting {0} element(s) of {1}", new Object[] { size,
                        list.getTechnicalName() });

        listener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();

        IObjectArray elementData = (IObjectArray) list.resolveValue("elementData");
        for (NamedReference ref : elementData.getOutboundReferences())
        {
            if (ref instanceof PseudoReference)
                continue;

            result.add(ref.getObjectId());
            listener.worked(1);
        }

        listener.done();

        return new ObjectListQuery.OutboundObjects(snapshot, result.toArray());
    }

    private IResult extractLinkedList(IProgressListener listener) throws Exception
    {
        int size = (Integer) list.resolveValue("size");

        if (size == 0)
            return new ObjectListQuery.OutboundObjects(snapshot, new int[0]);

        String taskMsg = MessageFormat.format("collecting {0} element(s) of {1}", new Object[] { size,
                        list.getTechnicalName() });
        listener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();

        IObject header = (IObject) list.resolveValue("header");
        IObject current = (IObject) header.resolveValue("next");

        while (header != current)
        {
            IObject ref = (IObject) current.resolveValue("element");
            if (ref != null)
                result.add(ref.getObjectId());

            current = (IObject) current.resolveValue("next");
            listener.worked(1);
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }

        listener.done();

        return new ObjectListQuery.OutboundObjects(snapshot, result.toArray());
    }

}

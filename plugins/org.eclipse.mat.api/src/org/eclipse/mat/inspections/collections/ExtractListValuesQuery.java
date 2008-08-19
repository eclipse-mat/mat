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
package org.eclipse.mat.inspections.collections;

import java.text.MessageFormat;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.PseudoReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
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
        InspectionAssert.heapFormatIsNot(snapshot, "phd");

        CollectionUtil.Info info = CollectionUtil.getInfo(list);

        if (info != null && !info.isMap())
        {
            if (info.hasBackingArray())
                return extractArrayList(info, listener);
            else if (list.getClazz().doesExtend("java.util.LinkedList"))
                return extractLinkedList(info, listener);
        }

        throw new IllegalArgumentException(MessageFormat.format("Not a (well-known) list: {0}", list.getDisplayName()));
    }

    private IResult extractArrayList(CollectionUtil.Info info, IProgressListener listener) throws SnapshotException
    {
        int size = info.getSize(list);

        if (size == 0)
            return new ObjectListResult.Outbound(snapshot, new int[0]);

        String taskMsg = MessageFormat.format("Collecting {0} element(s) of {1}", size, list.getTechnicalName());

        listener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();

        IObjectArray elementData = info.getBackingArray(list);
        for (NamedReference ref : elementData.getOutboundReferences())
        {
            if (ref instanceof PseudoReference)
                continue;

            result.add(ref.getObjectId());
            listener.worked(1);
        }

        listener.done();

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }

    private IResult extractLinkedList(CollectionUtil.Info info, IProgressListener listener) throws Exception
    {
        int size = info.getSize(list);

        if (size == 0)
            return new ObjectListResult.Outbound(snapshot, new int[0]);

        String taskMsg = MessageFormat.format("collecting {0} element(s) of {1}", new Object[] { size,
                        list.getTechnicalName() });
        listener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();

        IObject header = (IObject) list.resolveValue("header");
        if (header == null)
            header = (IObject) list.resolveValue("voidLink"); // IBM VM
        IObject current = (IObject) header.resolveValue("next");

        while (header != current)
        {
            IObject ref = (IObject) current.resolveValue("element");
            if (ref == null)
                ref = (IObject) current.resolveValue("data"); // IBM VM
            if (ref != null)
                result.add(ref.getObjectId());

            current = (IObject) current.resolveValue("next");
            listener.worked(1);
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }

        listener.done();

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.PseudoReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("extract_list_values")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
public class ExtractListValuesQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IObject list;

    public IResult execute(IProgressListener listener) throws Exception
    {
        CollectionUtil.Info info = CollectionUtil.getInfo(list);

        if (info != null && !info.isMap())
        {
            if (info.hasBackingArray())
                return extractArrayList(info, listener);
            else if (list.getClazz().doesExtend("java.util.LinkedList")) //$NON-NLS-1$
                return extractLinkedList(info, listener);
        }

        throw new IllegalArgumentException(MessageUtil.format(Messages.ExtractListValuesQuery_NotAWellKnownList, list
                        .getDisplayName()));
    }

    private IResult extractArrayList(CollectionUtil.Info info, IProgressListener listener) throws SnapshotException
    {
        int size = info.getSize(list);

        if (size == 0)
            return new ObjectListResult.Outbound(snapshot, new int[0]);

        String taskMsg = MessageUtil.format(Messages.ExtractListValuesQuery_CollectingElements, size, list
                        .getTechnicalName());

        listener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();

        IObjectArray elementData = info.getBackingArray(list);
        if (elementData != null)
        {
            for (NamedReference ref : elementData.getOutboundReferences())
            {
                if (ref instanceof PseudoReference)
                    continue;

                result.add(ref.getObjectId());
                listener.worked(1);
                // If the user cancels then just return what we have got so far
                if (listener.isCanceled())
                    break;
            }
        }

        listener.done();

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }

    private IResult extractLinkedList(CollectionUtil.Info info, IProgressListener listener) throws Exception
    {
        int size = info.getSize(list);

        if (size == 0)
            return new ObjectListResult.Outbound(snapshot, new int[0]);

        String taskMsg = MessageUtil.format(Messages.ExtractListValuesQuery_CollectingElements, size, list
                        .getTechnicalName());
        listener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();
        int loopingLimit = size;

        IObject header = (IObject) list.resolveValue("header"); //LinkedList$Header //$NON-NLS-1$
        if (header == null)
            header = (IObject) list.resolveValue("voidLink"); //LinkedList$Link IBM VM //$NON-NLS-1$
        if (header == null)
            header = (IObject) list.resolveValue("first"); //LinkedList$Node Java 7 //$NON-NLS-1$
        if (header == null)
        {
            // Look for the only object field
            header = info.resolveNextFields(list);
        }
        if (header == null)
            return null;

        IObject previous = header;
        IObject current = header;
        
        if (!current.getClazz().getName().equals("java.util.LinkedList$Node")) //$NON-NLS-1$
        {
            // Skip over header link for pre Java 7 implementations
            current = (IObject) header.resolveValue("next"); //$NON-NLS-1$;
            if (current == null)
            {
                // Try without using field names
                final ISnapshot snapshot = header.getSnapshot();
                for (int i : snapshot.getOutboundReferentIds(header.getObjectId()))
                {
                    IObject o = snapshot.getObject(i);
                    // Exclude the class
                    if (i != header.getClazz().getObjectId())
                    {
                        if (o.getClazz().equals(header.getClazz()))
                        {
                            // same type as header, so possible next field
                            // don't care whether we get current or previous - just circle the wrong way
                            current = o;
                            break;
                        }
                    }
                }
            }
        }
        else
        {
            header = null;
        }

        while (current != null && current != header && loopingLimit-- > 0)
        {
            // Find the element
            IObject ref = (IObject) current.resolveValue("element"); //$NON-NLS-1$
            if (ref == null)
                ref = (IObject) current.resolveValue("data"); // IBM VM //$NON-NLS-1$
            if (ref == null)
                ref = (IObject) current.resolveValue("item"); // Java 7 //$NON-NLS-1$

            // Find the next link
            IObject next = (IObject) current.resolveValue("next"); //$NON-NLS-1$
            
            if (next == null)
            {
                // Try without using field names
                final ISnapshot snapshot = current.getSnapshot();
                for (int i : snapshot.getOutboundReferentIds(current.getObjectId()))
                {
                    IObject o = snapshot.getObject(i);
                    // Exclude the previous field and the class
                    if (i != previous.getObjectId() && i != current.getClazz().getObjectId())
                    {
                        if (o.getClazz().equals(current.getClazz()))
                        {
                            // same type as current, so possible next field
                            if (next != null)
                            {
                                // Uncertain, so give up
                                next = null;
                                break;
                            }
                            next = o;
                        }
                        else
                        {
                            // possible element
                            if (ref == null)
                                ref = o;
                        }
                    }
                }
            }
            
            if (ref != null)
                result.add(ref.getObjectId());
            previous = current;
            current = next;
            listener.worked(1);
            // If the user cancels then just return what we have got so far
            if (listener.isCanceled())
                break;
        }

        listener.done();

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }

}

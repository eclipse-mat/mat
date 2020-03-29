/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Chris Grindstaff 
 *    Andrew Johnson (IBM Corporation) - help
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@Subject("char[]")
@CommandName("waste_in_char_arrays")
@Icon("/META-INF/icons/waste.gif")
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/component_report.html#ref_inspections_component_report__strings")
public class WasteInCharArraysQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument
    public int minimumWaste = 50;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD"); //$NON-NLS-1$
        ArrayInt result = new ArrayInt();

        Collection<IClass> classes = snapshot.getClassesByName("char[]", false); //$NON-NLS-1$
        if (classes != null)
            for (IClass clasz : classes)
            {
                int[] objectIds = clasz.getObjectIds();

                listener.beginTask(Messages.WasteInCharArraysQuery_CheckingCharArrays, objectIds.length / 10);

                for (int ii = 0; ii < objectIds.length; ii++)
                {
                    if (ii % 10 == 0)
                    {
                        if (listener.isCanceled())
                            throw new IProgressListener.OperationCanceledException();
                        listener.worked(1);
                    }

                    int id = objectIds[ii];

                    // ignore if object is too small anyways
                    IArray array = (IArray) snapshot.getObject(id);
                    int length = array.getLength();
                    if (length < minimumWaste)
                        continue;

                    if (hasWaste(id, length))
                        result.add(id);

                }
            }

        return new ObjectListResult.Inbound(snapshot, result.toArray());
    }

    private boolean hasWaste(int charArrayId, int length) throws SnapshotException
    {
        int[] inbounds = snapshot.getInboundRefererIds(charArrayId);

        List<Fragment> fragments = null;

        for (int inbound : inbounds)
        {
            // if inbounds contain other types than strings,
            // then waste is not defined
            if (!isString(inbound))
                return false;

            IObject string = snapshot.getObject(inbound);
            Integer offset = (Integer) string.resolveValue("offset"); //$NON-NLS-1$
            // E.g. JDK7u6
            if (offset == null)
                offset = 0;
            Integer count = (Integer) string.resolveValue("count"); //$NON-NLS-1$
            // E.g. JDK7u6
            if (count == null)
                count = length - offset;
            // string length already uses enough of the char[]
            if (length - count < minimumWaste)
                return false;

            // if there is only one string (and enough waste), include it
            if (inbounds.length == 1)
                return true;

            if (fragments == null)
            {
                fragments = new ArrayList<Fragment>();
                fragments.add(new Fragment(offset, offset + count));
            }
            else
            {
                boolean isMerged = false;

                for (Fragment fragment : fragments)
                {
                    if (offset >= fragment.start && offset <= fragment.end)
                    {
                        fragment.end = Math.max(offset + count, fragment.end);
                        isMerged = true;
                        break;
                    }
                    else if (offset + count >= fragment.start && offset + count <= fragment.end)
                    {
                        fragment.start = offset;
                        fragment.end = Math.max(offset + count, fragment.end);
                        isMerged = true;
                        break;
                    }
                }

                if (!isMerged)
                    fragments.add(new Fragment(offset, offset + count));
            }
        }

        if (fragments != null)
        {
            for (Fragment ff : fragments)
                length -= (ff.end - ff.start);
        }

        return length > minimumWaste;
    }

    private boolean isString(int inbound) throws SnapshotException
    {
        IClass clazz = snapshot.getClassOf(inbound);
        return "java.lang.String".equals(clazz.getName()); //$NON-NLS-1$
    }

    private static class Fragment
    {
        int start;
        int end;

        public Fragment(int start, int end)
        {
            this.start = start;
            this.end = end;
        }

    }
}

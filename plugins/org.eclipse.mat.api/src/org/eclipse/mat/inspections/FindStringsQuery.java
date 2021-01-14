/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("find_strings")
@Icon("/META-INF/icons/find_strings.gif")
@Subject("java.lang.String")
public class FindStringsQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    // Experimental default heap argument
    @Argument(flag = Argument.UNFLAGGED, isMandatory = false)
    public IHeapObjectArgument objects = new IHeapObjectArgument() {
        public Iterator<int[]> iterator()
        {
            return null;
        }

        public int[] getIds(IProgressListener listener) throws SnapshotException
        {
            return null;
        }

        public String getLabel()
        {
            return "java.lang.String"; //$NON-NLS-1$
        }
    };

    @Argument
    public Pattern pattern;

    public IResult execute(IProgressListener listener) throws Exception
    {
        ArrayInt result = new ArrayInt();

        Collection<IClass> classes = snapshot.getClassesByName("java.lang.String", false); //$NON-NLS-1$
        if (objects == null)
        {
            if (classes != null)
                ClassesLoop: for (IClass clasz : classes)
                {
                    int[] objectIds = clasz.getObjectIds();

                    listener.beginTask(Messages.FindStringsQuery_SearchingStrings, objectIds.length);

                    for (int id : objectIds)
                    {
                        if (listener.isCanceled())
                            break ClassesLoop;

                        String value = snapshot.getObject(id).getClassSpecificName();
                        if (value != null && pattern.matcher(value).matches())
                            result.add(id);

                        listener.worked(1);
                    }

                    listener.done();
                }
        }
        else
        {
            if (classes != null && !classes.isEmpty())
            {
                IClass javaLangString = classes.iterator().next();
                HeapObjectsTracker hot = new HeapObjectsTracker(objects);

                listener.beginTask(Messages.FindStringsQuery_SearchingStrings, hot.totalWork());

                ObjectsLoop: for (Iterator<int[]> it = objects.iterator(); it.hasNext();)
                {
                    int objectIds[] = it.next();
                    hot.beginBlock(objectIds, !it.hasNext());
                    for (int id : objectIds)
                    {
                        if (listener.isCanceled())
                            break ObjectsLoop;

                        if (snapshot.isArray(id) || snapshot.isClass(id) || snapshot.isClassLoader(id))
                        {
                            listener.worked(hot.work());
                            continue;
                        }

                        IObject instance = snapshot.getObject(id);
                        // if (!classes.contains(instance.getClazz()))
                        if (!javaLangString.equals(instance.getClazz()))
                        {
                            listener.worked(hot.work());
                            continue;
                        }

                        String value = instance.getClassSpecificName();
                        if (value != null && pattern.matcher(value).matches())
                        {
                            result.add(id);
                        }

                        listener.worked(hot.work());
                    }
                    listener.worked(hot.endBlock());
                }

                listener.done();
            }
        }

        if (listener.isCanceled() && result.isEmpty())
            throw new IProgressListener.OperationCanceledException();

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }
}

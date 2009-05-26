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
package org.eclipse.mat.inspections;

import java.util.regex.Pattern;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("find_strings")
public class FindStringsQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false)
    public IHeapObjectArgument objects;

    @Argument
    public Pattern pattern;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.FindStringsQuery_SearchingStrings);

        ArrayInt result = new ArrayInt();

        if (objects == null)
        {
            ClassesLoop: for (IClass clasz : snapshot.getClassesByName("java.lang.String", false)) //$NON-NLS-1$
            {
                int[] objectIds = clasz.getObjectIds();

                for (int id : objectIds)
                {
                    if (listener.isCanceled())
                        break ClassesLoop;

                    String value = snapshot.getObject(id).getClassSpecificName();
                    if (value != null && pattern.matcher(value).matches())
                        result.add(id);
                }
            }
        }
        else
        {
            IClass javaLangString = snapshot.getClassesByName("java.lang.String", false).iterator().next(); //$NON-NLS-1$

            ObjectsLoop: for (int[] objectIds : objects)
            {
                for (int id : objectIds)
                {
                    if (listener.isCanceled())
                        break ObjectsLoop;

                    if (snapshot.isArray(id) || snapshot.isClass(id) || snapshot.isClassLoader(id))
                        continue;

                    IObject instance = snapshot.getObject(id);
                    if (!javaLangString.equals(instance.getClazz()))
                        continue;

                    String value = instance.getClassSpecificName();
                    if (pattern.matcher(value).matches())
                    {
                        result.add(id);
                    }
                }
            }
        }

        if (listener.isCanceled() && result.isEmpty())
            throw new IProgressListener.OperationCanceledException();

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }

}

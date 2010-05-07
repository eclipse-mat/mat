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
package org.eclipse.mat.jdt;

import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;

@CommandName("open_source_file")
public class OpenSourceFileQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument
    public IContextObject subject;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int objectId = subject.getObjectId();
        if (objectId < 0 && subject instanceof IContextObjectSet)
        {
            IContextObjectSet set = (IContextObjectSet) subject;
            int[] objectIds = set.getObjectIds();
            if (objectIds.length > 0)
                objectId = objectIds[0];
        }

        if (objectId < 0)
            throw new IProgressListener.OperationCanceledException();

        IObject obj = snapshot.getObject(objectId);

        String className = obj instanceof IClass ? ((IClass) obj).getName() : obj.getClazz().getName();
        new OpenSourceFileJob(className).schedule();
        throw new IProgressListener.OperationCanceledException();
    }

}

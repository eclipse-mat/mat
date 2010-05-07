/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.finalizer;

import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@CommandName("finalizer_thread_locals")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/finalizer.gif")
public class FinalizerThreadLocalsQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int[] finalizerThreadObjects = FinalizerThreadQuery.getFinalizerThreads(snapshot);

        SetInt result = new SetInt();

        for (int finalizerThreadObject : finalizerThreadObjects)
        {	
        	Field localsField = ((IInstance) snapshot.getObject(finalizerThreadObject)).getField("threadLocals"); //$NON-NLS-1$
            if (localsField != null)
            {
                ObjectReference ref = (ObjectReference) localsField.getValue();
                if (ref != null)
                {
                    // TODO Don't add the thread locals object, but the pairs of
                    // referent and value stored in the thread locals
                    result.add(ref.getObjectId());
                }
            }
        }

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }
}

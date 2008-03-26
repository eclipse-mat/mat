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
package org.eclipse.mat.inspections.query.finalizer;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.test.QuerySpec;
import org.eclipse.mat.test.SectionSpec;
import org.eclipse.mat.util.IProgressListener;

@Name("Finalizer Overview")
@Category("Java Basics")
@Icon("/META-INF/icons/finalizer.gif")
@Help("Finalizer Overview.\n\n" //
                + "Finalizers are executed when the internal garbage collection frees the objects. "
                + "Due to the lack of control over the finalizer execution, it is recommended to "
                + "avoid finalizers. Long running tasks in the finalizer can block garbage "
                + "collection, because the memory can only be freed after the finalize method finished."
                + "This query shows the the finalizer currently processed, the finalizer queue, "
                + "the demon finalizer thread and the thread local variables.")
public class FinalizerQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SectionSpec spec = new SectionSpec("Finalizers");

        spec.add(new QuerySpec(FinalizerInProcessingQuery.class));
        spec.add(new QuerySpec(FinalizerQueueQuery.class));
        spec.add(new QuerySpec(FinalizerThreadQuery.class));
        spec.add(new QuerySpec(FinalizerThreadLocalsQuery.class));

        return spec;
    }
}

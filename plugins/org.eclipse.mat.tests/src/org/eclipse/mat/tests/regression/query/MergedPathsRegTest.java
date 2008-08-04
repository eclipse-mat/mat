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
package org.eclipse.mat.tests.regression.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.ListResult;
import org.eclipse.mat.snapshot.IMultiplePathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.util.IProgressListener;

@Name("Merged Paths from GC Roots")
@CommandName("merged_paths_reg_test")
@Category(Category.HIDDEN)
public class MergedPathsRegTest implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Collection<IClass> classes = snapshot.getClassesByName("java.util.HashMap", false); //$NON-NLS-1$
        IClass systemClass = (IClass) classes.iterator().next();
        int[] objects = systemClass.getObjectIds();

        // calculate the shortest path for each object
        IMultiplePathsFromGCRootsComputer computer = snapshot.getMultiplePathsFromGCRoots(objects, null);

        Object[] paths = computer.getAllPaths(listener);

        List<int[]> result = new ArrayList<int[]>(paths.length);

        for (int i = 0; i < paths.length; i++)
            result.add((int[]) paths[i]);

        List<List2String> stringResult = new ArrayList<List2String>(paths.length);
        for (int[] path : result)
        {
            List<Long> addresses = new ArrayList<Long>(path.length);
            for (int i = 0; i < path.length; i++)
            {
                addresses.add(snapshot.mapIdToAddress(path[i]));
            }
            stringResult.add(new List2String(addresses));

        }
        return new ListResult(List2String.class, stringResult, "path");
    }

}

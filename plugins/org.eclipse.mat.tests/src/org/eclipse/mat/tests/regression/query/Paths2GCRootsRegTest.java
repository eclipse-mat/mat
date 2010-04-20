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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.mat.internal.snapshot.inspections.Path2GCRootsQuery;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.snapshot.IPathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@Name("Paths from GC Roots")
@CommandName("path2gc_reg_test")
@Category(Category.HIDDEN)
public class Paths2GCRootsRegTest extends Path2GCRootsQuery
{
    // other arguments are the same as in superclass
    @Argument(isMandatory = false)
    public int numberOfPaths = 100;

    public IResult execute(IProgressListener listener) throws Exception
    {
        // get 1st HashMap object from the list of HashMaps sorted ascending
        RefinedResultBuilder builder = SnapshotQuery.parse("list_objects java.util.HashMap", snapshot) //
                        .refine(listener);
        builder.setSortOrder(0, Column.SortDirection.ASC);
        IResultTree tree = (IResultTree) builder.build();
        object = tree.getContext(tree.getElements().get(0)).getObjectId();

        // convert excludes into the required format
        Map<IClass, Set<String>> excludeMap = convert(snapshot, excludes);

        // create result tree
        IPathsFromGCRootsComputer computer = snapshot.getPathsFromGCRoots(object, excludeMap);

        List<String> stringResult = new ArrayList<String>(numberOfPaths);
        for (int i = 0; i < numberOfPaths; i++)
        {
            int[] path = computer.getNextShortestPath();
            if (path == null)
            {
                computer = null;
                break;
            }

            StringBuilder buffer = new StringBuilder(path.length * 20);
            for (int j = 0; j < path.length; j++)
            {
                if (j > 0)
                    buffer.append(", ");

                buffer.append("0x").append(Long.toHexString(snapshot.mapIdToAddress(path[j])));
            }
            stringResult.add(buffer.toString());

        }

        return new StringResult(stringResult);
    }

}

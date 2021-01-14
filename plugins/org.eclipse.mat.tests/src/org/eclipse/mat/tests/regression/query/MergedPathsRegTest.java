/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
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

    @Argument(isMandatory = false)
    public int numberOfPaths = 1000;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Collection<IClass> classes = snapshot.getClassesByName("java.util.HashMap", false); //$NON-NLS-1$
        if (classes == null || classes.isEmpty())
            throw new RuntimeException("Missing java.util.HashMap class or objects");
        IClass hashMapClass = classes.iterator().next();
        int[] objects = hashMapClass.getObjectIds();

        // calculate the shortest path for each object
        IMultiplePathsFromGCRootsComputer computer = snapshot.getMultiplePathsFromGCRoots(objects, null);

        Object[] paths = computer.getAllPaths(listener);

        numberOfPaths = Math.min(paths.length, numberOfPaths);

        List<String> stringResult = new ArrayList<String>(numberOfPaths);
        for (int i = 0; i < numberOfPaths; i++)
        {
            int[] path = (int[]) paths[i];

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

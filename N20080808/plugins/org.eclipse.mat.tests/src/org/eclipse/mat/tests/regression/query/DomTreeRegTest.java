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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Stack;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayUtils;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

@Name("Dominator Tree (binary)")
@Category(Category.HIDDEN)
@CommandName("dom_tree_binary")
public class DomTreeRegTest implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        FileOutputStream out = null;
        DataOutputStream dout = null;
        try
        {
            File domTreeBinary = new File(snapshot.getSnapshotInfo().getPrefix() + "Dom_tree.bin");
            out = new FileOutputStream(domTreeBinary);
            dout = new DataOutputStream(out);

            write(dout);
            dout.flush();
            dout.close();

            return new DisplayFileResult(domTreeBinary);
        }
        finally
        {
            if (out != null)
                out.close();
            if (dout != null)
                dout.close();

        }
    }

    private void write(DataOutputStream out) throws IOException, SnapshotException
    {
        Stack<Integer> stack = new Stack<Integer>();
        stack.add(-1);
        while (!stack.isEmpty())
        {
            int objectId = stack.pop();

            int[] objectIds = snapshot.getImmediateDominatedIds(objectId);
            long[] addresses = new long[objectIds.length];
            for (int i = 0; i < objectIds.length; i++)
                addresses[i] = snapshot.mapIdToAddress(objectIds[i]);

            ArrayUtils.sortDesc(addresses, objectIds);
            for (int i = 0; i < addresses.length; i++)
                out.writeLong(addresses[i]);

            addresses = null;

            for (int i = objectIds.length - 1; i >= 0; i--)
                stack.add(objectIds[i]);
        }

    }

}

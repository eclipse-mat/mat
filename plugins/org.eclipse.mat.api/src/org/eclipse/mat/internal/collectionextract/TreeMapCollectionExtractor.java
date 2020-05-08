/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import java.util.Stack;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class TreeMapCollectionExtractor extends MapCollectionExtractorBase
{
    protected final String sizeField;

    public TreeMapCollectionExtractor(String sizeField, String keyField, String valueField)
    {
        super(keyField, valueField);
        if (sizeField == null)
            throw new IllegalArgumentException();
        this.sizeField = sizeField;
    }

    public boolean hasExtractableArray()
    {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException
    {
        throw new IllegalArgumentException();
    }

    public boolean hasExtractableContents()
    {
        return true;
    }

    public int[] extractEntryIds(IObject treeMap) throws SnapshotException
    {
        ArrayInt result = new ArrayInt();
        String rootf = "root"; //$NON-NLS-1$
        // For TreeSet
        int dot = sizeField.lastIndexOf("."); //$NON-NLS-1$
        if (dot > 0)
        {
            rootf = sizeField.substring(0, dot + 1) + rootf;
        }
        IObject root = (IObject) treeMap.resolveValue(rootf);
        if (root == null)
            return new int[0];

        Stack<IObject> stack = new Stack<IObject>();
        stack.push(root);

        IObject current = root;
        SetInt visited = new SetInt();

        /* traverse the TreeMap entries in-order */
        while (stack.size() > 0)
        {
            current = stack.peek();

            /* go left */
            IObject left = (IObject) current.resolveValue("left"); //$NON-NLS-1$
            if (left != null && !visited.contains(left.getObjectId()))
            {
                stack.push(left);
            }
            else
            {
                /* process node */
                result.add(current.getObjectId());
                visited.add(current.getObjectId());
                stack.pop();

                /* go right */
                IObject right = (IObject) current.resolveValue("right"); //$NON-NLS-1$
                if (right != null && !visited.contains(right.getObjectId()))
                {
                    stack.push(right);
                }
            }
        }

        return result.toArray();
    }

    public boolean hasSize()
    {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException
    {
        return ExtractionUtils.toInteger(coll.resolveValue(sizeField));
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException
    {
        return getSize(coll);
    }

    public boolean hasCollisionRatio()
    {
        return false;
    }

    public Double getCollisionRatio(IObject collection)
    {
        return null;
    }

    public boolean hasFillRatio()
    {
        return false;
    }

    public Double getFillRatio(IObject coll) throws SnapshotException
    {
        return null;
    }
}

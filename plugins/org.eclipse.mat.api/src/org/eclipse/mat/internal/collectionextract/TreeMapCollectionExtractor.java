/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
        boolean noFieldNames = root == null;
        if (root == null)
        {
            if (dot > 0)
            {
                root = ExtractionUtils.followOnlyOutgoingReferencesExceptLast(rootf, treeMap);
                if (root != null)
                    root = ExtractionUtils.followOnlyNonArrayOutgoingReference(root);
            }
            else
            {
                root = ExtractionUtils.followOnlyNonArrayOutgoingReference(treeMap);
            }
            if (root == null || !root.getClazz().getName().endsWith("$Entry")) //$NON-NLS-1$
                return new int[0];
        }

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
                else if (noFieldNames)
                {
                    // All nodes of same type as root
                    for (int o : current.getSnapshot().getOutboundReferentIds(current.getObjectId()))
                    {
                        if (current.getSnapshot().getClassOf(o).equals(root.getClazz()))
                        {
                            if (!visited.contains(o))
                                stack.push(current.getSnapshot().getObject(o));
                        }
                    }
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
        Integer ret = ExtractionUtils.toInteger(coll.resolveValue(sizeField));
        if (ret != null)
            return ret;
        int e[] = this.extractEntryIds(coll);
        if (e != null)
            return e.length;
        return null;
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

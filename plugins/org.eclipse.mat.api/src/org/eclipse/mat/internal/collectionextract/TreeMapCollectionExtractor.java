package org.eclipse.mat.internal.collectionextract;

import java.util.Stack;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class TreeMapCollectionExtractor extends MapCollectionExtractorBase {
    public TreeMapCollectionExtractor(String sizeField,
            String keyField, String valueField) {
        super(sizeField, keyField, valueField);
    }

    public boolean hasExtractableArray() {
        return false;
    }

    public IObjectArray extractEntries(IObject coll) throws SnapshotException {
        throw new IllegalArgumentException();
    }

    public boolean hasExtractableContents() {
        return true;
    }

    public int[] extractEntryIds(IObject treeMap) throws SnapshotException {
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

    public boolean hasSize() {
        return (sizeField != null);
    }

    public Integer getNumberOfNotNullElements(IObject coll) throws SnapshotException {
        return getSize(coll);
    }

    public boolean hasCollisionRatio() {
        return false;
    }

    public Double getCollisionRatio(IObject collection) {
        return 0.0;
    }

	public boolean hasFillRatio() {
		return false;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		return null;
	}
}

package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.MessageUtil;

public abstract class HashedMapCollectionExtractorBase extends MapCollectionExtractorBase {
    protected final String arrayField;

    public HashedMapCollectionExtractorBase(String sizeField, String arrayField,
            String keyField, String valueField) {
        super(sizeField, keyField, valueField);
        this.arrayField = arrayField;
    }

    public boolean hasSize() {
        return true;
    }

    public Integer getSize(IObject coll) throws SnapshotException {
		// fast path
        Integer ret = super.getSize(coll);
        if (ret != null)
            return ret;

        if (hasExtractableContents()) {
            return getMapSize(coll, extractEntryIds(coll));
        } else {
            // LinkedList
            IObject header = resolveNextFields(coll);
            if (header != null)
            {
                ISnapshot snapshot = coll.getSnapshot();
                return getMapSize(coll, snapshot.getOutboundReferentIds(header.getObjectId()));
            } else {
                return null;
            }
        }
    }

    public boolean hasFillRatio() {
		return true;
	}

	public Double getFillRatio(IObject coll) throws SnapshotException {
		Integer size = getSize(coll);
		Integer cap = getCapacity(coll);
		if (size != null && cap != null)
			return size.doubleValue() / cap.doubleValue();
		else
			return 1.0; // FIXME: default to this? old code did
	}

	public boolean hasCollisionRatio() {
        return true;
    }

    public Double getCollisionRatio(IObject coll) throws SnapshotException {
        Integer size = getSize(coll);
        if (size == null || size <= 0) {
            return 0d;
        } else {
            return (double) (size - getNumberOfNotNullElements(coll)) / (double) size;
        }
    }

    protected IObjectArray extractBackingArray(IObject coll) throws SnapshotException {
        final Object obj = coll.resolveValue(arrayField);
        IObjectArray ret = null;
        if (obj instanceof IObjectArray)
        {
            return (IObjectArray) obj;
        }
        else if (obj instanceof IObject)
        {
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                    coll.getTechnicalName(), ((IObject) obj).getTechnicalName());
            throw new SnapshotException(msg);
        }
        else if (obj != null)
        {
            String msg = MessageUtil.format(Messages.CollectionUtil_BadBackingArray, arrayField,
                    coll.getTechnicalName(), obj.toString());
            throw new SnapshotException(msg);
        }
        IObject next = resolveNextFields(coll);
        if (next == null)
            return null;
        // Look for the only object array field
        final ISnapshot snapshot = next.getSnapshot();
        for (int i : snapshot.getOutboundReferentIds(next.getObjectId()))
        {
            if (snapshot.isArray(i))
            {
                IObject o = snapshot.getObject(i);
                if (o instanceof IObjectArray)
                {
                    // Have we already found a possible return type?
                    // If so, things are uncertain and so give up.
                    if (ret != null)
                        return null;
                    ret = (IObjectArray) o;
                }
            }
        }
        return ret;
    }

    private int getMapSize(IObject collection, int[] objects) throws SnapshotException {
        // Maps have chained buckets in case of clashes
        // LinkedMaps have additional chains to maintain ordering
        int count = 0;
        ISnapshot snapshot = collection.getSnapshot();
        // Avoid visiting nodes twice
        BitField seen = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
        // Used for alternative nodes if there is a choice
        ArrayInt extra = new ArrayInt();
        // Eliminate the LinkedHashMap header node
        //seen.set(array.getObjectId());
        // Walk over whole array, or all outbounds of header
        for (int i : objects)
        {
            // Ignore classes, outbounds we have seen, and plain Objects (which can't be buckets e.g. ConcurrentSkipListMap)
            if (!snapshot.isClass(i) && !seen.get(i) && !snapshot.getClassOf(i).getName().equals("java.lang.Object")) //$NON-NLS-1$
            {
                // Found a new outbound
                // Look at the reachable nodes from this one, remember this
                extra.clear();
                extra.add(i);
                seen.set(i);
                for (int k = 0; k < extra.size(); ++k)
                {
                    for (int j = extra.get(k); j >= 0;)
                    {
                        ++count;
                        j = resolveNextSameField(snapshot, j, seen, extra);
                    }
                }
            }
        }
        return count;
    }

    /**
     * Get the only object field from the object which is of the same type
     * as the source
     *
     * @param sourceId
     * @param seen
     *            whether seen yet
     * @param extra
     *            extra ones to do
     * @return the next node to search, null if none found
     * @throws SnapshotException
     */
    int resolveNextSameField(ISnapshot snapshot, int sourceId,
    		BitField seen, ArrayInt extra) throws SnapshotException {
    	int ret = -1;
    	IClass c1 = snapshot.getClassOf(sourceId);
    	for (int i : snapshot.getOutboundReferentIds(sourceId))
    	{
    		if (!snapshot.isArray(i) && !snapshot.isClass(i))
    		{
    			IClass c2 = snapshot.getClassOf(i);
    			if (c1.equals(c2) && !seen.get(i))
    			{
    				seen.set(i);
    				if (ret == -1)
    				{
    					ret = i;
    				}
    				else
    				{
    					extra.add(i);
    				}
    			}
    		}
    	}
    	return ret;
    }

    protected IObject resolveNextFields(IObject collection) throws SnapshotException {
        int j = arrayField.lastIndexOf('.');
        if (j >= 0)
        {
            Object ret = collection.resolveValue(arrayField.substring(0, j));
            if (ret instanceof IObject)
            {
                return (IObject) ret;
            }
        }
        // Find out how many fields to chain through to find the array
        IObject next = collection;
        // Don't do the last as that is the array field
        for (int i = arrayField.indexOf('.'); i >= 0 && next != null; i = arrayField.indexOf('.', i + 1))
        {
            next = resolveNextField(next);
        }
        return next;
    }

    /**
     * Get the only object field from the object Used for finding the
     * HashMap from the HashSet
     *
     * @param source
     * @return null if non or duplicates found
     * @throws SnapshotException
     */
    private IInstance resolveNextField(IObject source) throws SnapshotException {
        final ISnapshot snapshot = source.getSnapshot();
        IInstance ret = null;
        for (int i : snapshot.getOutboundReferentIds(source.getObjectId()))
        {
            if (!snapshot.isArray(i) && !snapshot.isClass(i))
            {
                IObject o = snapshot.getObject(i);
                if (o instanceof IInstance)
                {
                    if (ret != null)
                    {
                        ret = null;
                        break;
                    }
                    ret = (IInstance) o;
                }
            }
        }
        return ret;
    }

}
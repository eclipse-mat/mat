package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ExtractionUtils {
    public static Integer toInteger(Object i) {
        if (i instanceof Number)
            return ((Number)i).intValue();
        else
            return null;
    }

    public static int getNumberOfNotNullArrayElements(IObjectArray arrayObject) {
        // Fast path using referentIds for arrays with same number of outbounds
        // (+class id) as length
        // or no outbounds other than the class
        ISnapshot snapshot = arrayObject.getSnapshot();
        try
        {
            final int[] outs = snapshot.getOutboundReferentIds(arrayObject.getObjectId());
            if (outs.length == 1 || outs.length == arrayObject.getLength() + 1) { return outs.length - 1; }
        }
        catch (SnapshotException e)
        {}

        return getNumberOfNotNullArrayElements(arrayObject.getReferenceArray());
    }

    public static int getNumberOfNotNullArrayElements(long[] addresses) {
        int result = 0;
        for (int i = 0; i < addresses.length; i++)
        {
            if (addresses[i] != 0)
                result++;
        }
        return result;
    }

    public static int getNumberOfNotNullArrayElements(int[] ids) {
        int result = 0;
        for (int i = 0; i < ids.length; i++)
        {
            if (ids[i] != 0)
                result++;
        }
        return result;
    }

    public static int[] referenceArrayToIds(ISnapshot snapshot, long[] referenceArray) throws SnapshotException {
    	ArrayInt arr = new ArrayInt();
        for (int i = 0; i < referenceArray.length; i++) {
        	if (referenceArray[i] != 0)
        		arr.add(snapshot.mapAddressToId(referenceArray[i]));
        }
        return arr.toArray();

    }
}

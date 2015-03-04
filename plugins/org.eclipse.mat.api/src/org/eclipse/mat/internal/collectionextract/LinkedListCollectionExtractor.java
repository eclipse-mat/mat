package org.eclipse.mat.internal.collectionextract;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;

public class LinkedListCollectionExtractor extends FieldSizedCollectionExtractor {
    private final String leadField;

    public LinkedListCollectionExtractor(String sizeField, String leadField) {
        super(sizeField);
        this.leadField = leadField;
    }

    @Override
    public boolean hasExtractableContents() {
        return true;
    }

    @Override
    public int[] extractEntryIds(IObject list) throws SnapshotException {
        IProgressListener listener = new VoidProgressListener();
        int size = getSize(list);

        String taskMsg = MessageUtil.format(Messages.ExtractListValuesQuery_CollectingElements, size, list
                        .getTechnicalName());
        listener.beginTask(taskMsg, size);

        ArrayInt result = new ArrayInt();
        int loopingLimit = size;

        IObject header = (IObject) list.resolveValue("header"); //LinkedList$Header //$NON-NLS-1$
        if (header == null)
            header = (IObject) list.resolveValue("voidLink"); //LinkedList$Link IBM VM //$NON-NLS-1$
        if (header == null)
            header = (IObject) list.resolveValue("first"); //LinkedList$Node Java 7 //$NON-NLS-1$
        if (header == null)
        {
            // Look for the only object field
            header = resolveNextFields(list);
        }
        if (header == null)
            return null;

        IObject previous = header;
        IObject current = header;

        if (!current.getClazz().getName().equals("java.util.LinkedList$Node")) //$NON-NLS-1$
        {
            // Skip over header link for pre Java 7 implementations
            current = (IObject) header.resolveValue("next"); //$NON-NLS-1$;
            if (current == null)
            {
                // Try without using field names
                final ISnapshot snapshot = header.getSnapshot();
                for (int i : snapshot.getOutboundReferentIds(header.getObjectId()))
                {
                    IObject o = snapshot.getObject(i);
                    // Exclude the class
                    if (i != header.getClazz().getObjectId())
                    {
                        if (o.getClazz().equals(header.getClazz()))
                        {
                            // same type as header, so possible next field
                            // don't care whether we get current or previous - just circle the wrong way
                            current = o;
                            break;
                        }
                    }
                }
            }
        }
        else
        {
            header = null;
        }

        while (current != null && current != header && loopingLimit-- > 0)
        {
            // Find the element
            IObject ref = (IObject) current.resolveValue("element"); //$NON-NLS-1$
            if (ref == null)
                ref = (IObject) current.resolveValue("data"); // IBM VM //$NON-NLS-1$
            if (ref == null)
                ref = (IObject) current.resolveValue("item"); // Java 7 //$NON-NLS-1$

            // Find the next link
            IObject next = (IObject) current.resolveValue("next"); //$NON-NLS-1$

            if (next == null)
            {
                // Try without using field names
                final ISnapshot snapshot = current.getSnapshot();
                for (int i : snapshot.getOutboundReferentIds(current.getObjectId()))
                {
                    IObject o = snapshot.getObject(i);
                    // Exclude the previous field and the class
                    if (i != previous.getObjectId() && i != current.getClazz().getObjectId())
                    {
                        if (o.getClazz().equals(current.getClazz()))
                        {
                            // same type as current, so possible next field
                            if (next != null)
                            {
                                // Uncertain, so give up
                                next = null;
                                break;
                            }
                            next = o;
                        }
                        else
                        {
                            // possible element
                            if (ref == null)
                                ref = o;
                        }
                    }
                }
            }

            if (ref != null)
                result.add(ref.getObjectId());
            previous = current;
            current = next;
            listener.worked(1);
            // If the user cancels then just return what we have got so far
            if (listener.isCanceled())
                break;
        }

        listener.done();

        return result.toArray();
    }

    protected IObject resolveNextFields(IObject collection) throws SnapshotException
    {
        int j = leadField.lastIndexOf('.');
        if (j >= 0)
        {
            Object ret = collection.resolveValue(leadField.substring(0, j));
            if (ret instanceof IObject)
            {
                return (IObject) ret;
            }
        }
        // Find out how many fields to chain through to find the array
        IObject next = collection;
        // Don't do the last as that is the array field
        for (int i = leadField.indexOf('.'); i >= 0 && next != null; i = leadField.indexOf('.', i + 1))
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
    private IInstance resolveNextField(IObject source) throws SnapshotException
    {
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

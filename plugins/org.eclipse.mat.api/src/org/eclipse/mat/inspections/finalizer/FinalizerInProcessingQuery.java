// Goals:
//
// 1.) Histogram on all objects with finalize() method
//
// 2A.) Histogram on all objects not ready for finalization
// 2B.) Histogram on all objects ready for finalization (including pending) +
// Overall Retained Size/Set
// Maybe instead (References distributed per state):
// 2A.) Active: queue = ReferenceQueue with which instance is registered, or
// ReferenceQueue.NULL if it was not registered with a queue; next = null.
// 2B.) Pending: queue = ReferenceQueue with which instance is registered;
// next = Following instance in queue, or this if at end of list.
// 2C.) Enqueued: queue = ReferenceQueue.ENQUEUED; next = Following instance
// in queue, or this if at end of list.
// 2D.) Inactive: queue = ReferenceQueue.NULL; next = this.
//
// Finalizer specific:
// 3.) Object currently in finalization (possibly hanging)
// 4.) Histogram on all objects which are already finalized but which are
// retained by unfinalized objects (possible illegal dependency)
//
// Weak specific:
// 5.) Weak referents only

package org.eclipse.mat.inspections.finalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@Name("Finalizer In Processing")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/finalizer.gif")
@Help("Extract object currently processed by Finalizer Thread.\n\n"
                + "Finalizers are executed when the internal garbage collection frees the objects. "
                + "Due to the lack of control over the finalizer execution, it is recommended to "
                + "avoid finalizers. Long running tasks in the finalizer can block garbage "
                + "collection, because the memory can only be freed after the finalize method finished."
                + "This query shows the currently processed object by the Finalizer Thread if any."
                + "Be aware that there could be many reasons for this object to be currently processed:"
                + "a.) it could be blocking, b.) it could be long running, or c.) it could be ok,"
                + "but the queue was or is still full (please use our finalizer queue query to check).")
public class FinalizerInProcessingQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Collection<IClass> finalizerThreadClasses = snapshot.getClassesByName(
                        "java.lang.ref.Finalizer$FinalizerThread", false);
        if (finalizerThreadClasses == null)
            throw new Exception("Class java.lang.ref.Finalizer$FinalizerThread not found in heap dump.");
        if (finalizerThreadClasses.size() != 1)
            throw new Exception("Error: Snapshot contains multiple java.lang.ref.Finalizer$FinalizerThread classes.");

        int[] finalizerThreadObjects = finalizerThreadClasses.iterator().next().getObjectIds();
        if (finalizerThreadObjects == null)
            throw new Exception("Instance of class java.lang.ref.Finalizer$FinalizerThread not found in heap dump.");
        if (finalizerThreadObjects.length != 1)
            throw new Exception(
                            "Error: Snapshot contains multiple instances of java.lang.ref.Finalizer$FinalizerThread class.");

        SetInt result = new SetInt();

        IObject finalizerThreadObject = snapshot.getObject(finalizerThreadObjects[0]);
        IObject[] localVars = getLocalVarsForThread(finalizerThreadObject);
        for (IObject object : localVars)
        {
            if ("java.lang.ref.Finalizer".equals(object.getClazz().getName()))
            {
                ObjectReference ref = (ObjectReference) ((IInstance) object).getField("referent").getValue();
                if (ref != null)
                {
                    result.add(ref.getObjectId());
                }
            }
        }

        return new ObjectListResult.Outbound(snapshot, result.toArray());
    }

    private static IObject[] getLocalVarsForThread(IObject thread) throws SnapshotException
    {
        List<NamedReference> refs = thread.getOutboundReferences();
        List<IObject> result = new ArrayList<IObject>();
        for (NamedReference ref : refs)
        {
            if (ref instanceof ThreadToLocalReference)
                result.add(ref.getObject());
        }
        return result.toArray(new IObject[result.size()]);
    }
}

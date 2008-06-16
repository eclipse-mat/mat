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

package org.eclipse.mat.inspections.query.finalizer;

import java.util.Collection;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.ObjectListResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;


@Name("Finalizer Queue")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/finalizer.gif")
@Help("Extract objects in the Finalizer Queue.\n\n"
                + "Finalizers are executed when the internal garbage collection frees the objects. "
                + "Due to the lack of control over the finalizer execution, it is recommended to "
                + "avoid finalizers. Long running tasks in the finalizer can block garbage "
                + "collection, because the memory can only be freed after the finalize method finished."
                + "This query shows the objects ready for finalization in their processing order."
                + "Be aware that there could be many reasons for a full finalizer queue:"
                + "a.) the currently processed object could be blocking or long running"
                + "(please use our finalizer in processing query to check) or the application"
                + "made use of too many objects with finalize() which are queueing up in memory.")
public class FinalizerQueueQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        Collection<IClass> finalizerClasses = snapshot.getClassesByName("java.lang.ref.Finalizer", false);
        if (finalizerClasses == null)
            throw new Exception("Class java.lang.ref.Finalizer not found in heap dump.");
        if (finalizerClasses.size() != 1)
            throw new Exception("Error: Snapshot contains multiple java.lang.ref.Finalizer classes.");

        ArrayInt result = new ArrayInt();

        // Extracting objects ready for finalization from queue
        IClass finalizerClass = finalizerClasses.iterator().next();
        IObject queue = (IObject)finalizerClass.resolveValue("queue");
        
        if (queue == null)
            return null;
        
        
        IInstance item = (IInstance) queue.resolveValue("head");
        int length = ((Long) queue.resolveValue("queueLength")).intValue();
        int threshold = length / 100;
        int worked = 0;
        listener.beginTask("Extracting objects ready for finalization from queue...", length);

        while (item != null)
        {
            if (listener.isCanceled()) { throw new IProgressListener.OperationCanceledException(); }

            ObjectReference ref = (ObjectReference) item.getField("referent").getValue();
            if (ref != null)
            {
                result.add(ref.getObjectId());
            }

            IInstance next = (IInstance) item.resolveValue("next");
            if (next == item)
            {
                next = null;
            }
            item = next;

            if (++worked >= threshold)
            {
                listener.worked(worked);
                worked = 0;
            }
        }

        listener.done();

        return new ObjectListResult("Ready for Finalizer Thread", result.toArray());
    }
}

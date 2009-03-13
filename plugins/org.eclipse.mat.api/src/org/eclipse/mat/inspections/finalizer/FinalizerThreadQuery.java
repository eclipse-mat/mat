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

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.CommonNameResolver;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@Name("Finalizer Thread")
@Category(Category.HIDDEN)
@Icon("/META-INF/icons/finalizer.gif")
@Help("Extract Finalizer Threads.\n\n"
                + "Finalizers are executed when the internal garbage collection frees the objects. "
                + "Due to the lack of control over the finalizer execution, it is recommended to "
                + "avoid finalizers. Long running tasks in the finalizer can block garbage "
                + "collection, because the memory can only be freed after the finalize method finished."
                + "This query shows the daemon thread or threads which are performing the object finalizations.")
public class FinalizerThreadQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;
  
    public IResult execute(IProgressListener listener) throws Exception
    {
        int finalizerThreadObjects[] = getFinalizerThreads(snapshot);
        return new ObjectListResult.Outbound(snapshot, finalizerThreadObjects);
    }

    static int[] getFinalizerThreads(ISnapshot snapshot) throws Exception
    {
        Collection<IClass> finalizerThreadClasses = snapshot.getClassesByName(
                        "java.lang.ref.Finalizer$FinalizerThread", false);
        if (finalizerThreadClasses == null)
        {
        	// IBM finalizer thread
            return getFinalizerThreads2(snapshot, "Finalizer thread");
        } 
        else
        {
            // Two sorts of finalizer threads
            int a[] = getFinalizerThreads1(snapshot);
            // Created by System.runFinalization()
            int b[] = getFinalizerThreads2(snapshot, "Secondary finalizer");
            int ret[] = new int[a.length+b.length];
            System.arraycopy(a, 0, ret, 0, a.length);
            System.arraycopy(b, 0, ret, a.length, b.length);
            return ret;
        }
    }
    
    private static int[] getFinalizerThreads1(ISnapshot snapshot) throws SnapshotException, Exception
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
        return finalizerThreadObjects;
    }
    
    private static int[] getFinalizerThreads2(ISnapshot snapshot, String finalizerThreadName) throws Exception
    {        
        Collection<IClass> finalizerThreadClasses = snapshot.getClassesByName(
                        "java.lang.Thread", false);
        if (finalizerThreadClasses == null)
            throw new Exception("Class java.lang.Thread not found in heap dump.");
        if (finalizerThreadClasses.size() != 1)
            throw new Exception("Error: Snapshot contains multiple java.lang.Thread classes.");

        int[] finalizerThreadObjects = finalizerThreadClasses.iterator().next().getObjectIds();
        if (finalizerThreadObjects == null)
            throw new Exception("Instance of class java.lang.Thread not found in heap dump.");
        int finalizerThreadObjectsLength = 0;
        for (int objectId : finalizerThreadObjects)
        {
            IObject o = snapshot.getObject(objectId);
            CommonNameResolver.ThreadResolver t = new CommonNameResolver.ThreadResolver();
            String name = t.resolve(o);
            if (name != null && name.equals(finalizerThreadName))
            {
                finalizerThreadObjects[finalizerThreadObjectsLength++] = objectId;
            }
        }
        int ret[] = new int[finalizerThreadObjectsLength];
        System.arraycopy(finalizerThreadObjects, 0, ret, 0, finalizerThreadObjectsLength);
        return ret;
    }

}

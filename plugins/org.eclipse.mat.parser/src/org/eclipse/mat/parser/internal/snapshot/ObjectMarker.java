/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - improved multithreading using local stacks
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.QueueInt;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.ParserPlugin;
import org.eclipse.mat.parser.internal.util.IntStack;
import org.eclipse.mat.snapshot.ExcludedReferencesDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.IProgressListener.Severity;

public class ObjectMarker
{
    int[] roots;
    boolean[] bits;
    IIndexReader.IOne2ManyIndex outbound;
    long outboundMem;
    IProgressListener progressListener;
    private static final boolean DEBUG = Platform.inDebugMode() && ParserPlugin.getDefault().isDebugging();
    private static final boolean USELOCAL = !(Platform.inDebugMode() && ParserPlugin.getDefault().isDebugging() && 
                    Boolean.parseBoolean(Platform.getDebugOption("org.eclipse.mat.parser/debug/oldMarker"))); //$NON-NLS-1$
    private static final int MIN_LOCALITY = 1000000;

    public ObjectMarker(int[] roots, boolean[] bits, IIndexReader.IOne2ManyIndex outbound,
                    IProgressListener progressListener)
    {
        this(roots, bits, outbound, 0, progressListener);
    }
    
    public ObjectMarker(int[] roots, boolean[] bits, IIndexReader.IOne2ManyIndex outbound,
                    long outboundLength, IProgressListener progressListener)
    {
        this.roots = roots;
        this.bits = bits;
        this.outbound = outbound;
        this.outboundMem = outboundLength > 0 ? outboundLength : outbound.size() * 30L;
        this.progressListener = progressListener;
    }

    public int markSingleThreaded() throws IProgressListener.OperationCanceledException
    {
        int count = 0;
        int size = 0;
        int[] data = new int[10 * 1024]; // start with 10k
        int rootsToProcess = 0;

        for (int rootId : roots)
        {
            if (!bits[rootId])
            {
                /* start stack.push() */
                if (size == data.length)
                {
                    int[] newArr = new int[data.length << 1];
                    System.arraycopy(data, 0, newArr, 0, data.length);
                    data = newArr;
                }
                data[size++] = rootId;
                /* end stack.push() */

                bits[rootId] = true;
                count++;

                rootsToProcess++;
            }
        }

        progressListener.beginTask(Messages.ObjectMarker_MarkingObjects, rootsToProcess);

        int current;

        while (size > 0)
        {
            /* start stack.pop() */
            current = data[--size];

            if (size <= rootsToProcess)
            {
                rootsToProcess--;
                progressListener.worked(1);
                if (progressListener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }

            for (int child : outbound.get(current))
            {
                if (!bits[child])
                {
                    // stack.push(child);
                    /* start stack.push() */
                    if (size == data.length)
                    {
                        int[] newArr = new int[data.length << 1];
                        System.arraycopy(data, 0, newArr, 0, data.length);
                        data = newArr;
                    }
                    data[size++] = child;
                    /* end stack.push() */

                    bits[child] = true;
                    count++;
                }

            }
        }

        progressListener.done();

        return count;
    }

    public int markSingleThreaded(ExcludedReferencesDescriptor[] excludeSets, ISnapshot snapshot)
                    throws SnapshotException, IProgressListener.OperationCanceledException
    {
        /*
         * prepare the exclude stuff
         */
        BitField excludeObjectsBF = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
        for (ExcludedReferencesDescriptor set : excludeSets)
        {
            for (int k : set.getObjectIds())
            {
                excludeObjectsBF.set(k);
            }
        }

        int count = 0; // # of processed objects in the stack
        int rootsToProcess = 0; // counter to report progress

        /* a stack of int structure */
        int size = 0; // # of elements in the stack
        int[] data = new int[10 * 1024]; // data for the stack - start with 10k

        /* first put all "roots" in the stack, and mark them as processed */
        for (int rootId : roots)
        {
            if (!bits[rootId])
            {
                /* start stack.push() */
                if (size == data.length)
                {
                    int[] newArr = new int[data.length << 1];
                    System.arraycopy(data, 0, newArr, 0, data.length);
                    data = newArr;
                }
                data[size++] = rootId;
                /* end stack.push() */

                bits[rootId] = true; // mark the object
                count++;

                rootsToProcess++;
            }
        }

        /* now do the real marking */
        progressListener.beginTask(Messages.ObjectMarker_MarkingObjects, rootsToProcess);

        int current;

        while (size > 0) // loop until there are elements in the stack
        {
            /* do a stack.pop() */
            current = data[--size];

            /* report progress if one of the roots is processed */
            if (size <= rootsToProcess)
            {
                rootsToProcess--;
                progressListener.worked(1);
                if (progressListener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }

            for (int child : outbound.get(current))
            {
                if (!bits[child]) // already visited?
                {
                    if (!refersOnlyThroughExcluded(current, child, excludeSets, excludeObjectsBF, snapshot))
                    {
                        /* start stack.push() */
                        if (size == data.length)
                        {
                            int[] newArr = new int[data.length << 1];
                            System.arraycopy(data, 0, newArr, 0, data.length);
                            data = newArr;
                        }
                        data[size++] = child;
                        /* end stack.push() */

                        bits[child] = true; // mark the object
                        count++;
                    }
                }
            }
        }

        progressListener.done();

        return count;
    }

    /**
     * A stack accessible by multiple threads
     * @author ajohnson
     *
     */
    static class MultiThreadedRootStack extends IntStack
    {
        private int waitingThreads;
        private int totalThreads;
        private int waits; // Debug
        private long waitsduration; // Debug
        static final int RESERVED_WAITING = 50;
        static final int RESERVED_RUNNING = 15;
        int totalWork;
        int worked; // ticks done so far
        int pushed; // items pushed to the stack
        int lastDone; // items processed at last tick

        MultiThreadedRootStack(int n)
        {
            super(n);
            totalWork = n;
            pushed = n;
        }

        /**
         * Note that this thread is using this stack.
         */
        synchronized void linkThread()
        {
            ++totalThreads;
        }

        /**
         * Note that this thread is no longer using the stack.
         */
        synchronized void unlinkThread()
        {
            --totalThreads;
            if (waitingThreads >= totalThreads)
            {
                // Everyone else is waiting, so all must finish
                notifyAll();
                if (DEBUG && totalThreads == 0) System.out.println("Total waits "+waits+" "+waitsduration+"ms");  //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            }
        }

        /**
         * Calculate the number of ticks worked.
         * The total ticks is just the initial stack size
         * However, more items can be pushed later, so we calculate the 
         * items processed since last time, the total to do, and the 
         * remaining ticks.
         * Must have the lock on this stack.
         * @return new ticks worked
         */
        int worked()
        {
            int done = pushed - size();
            int newDone = done - lastDone;
            int ticksLeft = totalWork - worked;
            if (newDone > 0)
            {
                int k = ticksLeft * newDone / (newDone + size());
                // Make sure we don't report progress too often
                if (k < totalWork / 1000)
                    k = 0;
                if (k > 0)
                {
                    worked += k;
                    lastDone = done;
                }
                return k;
            }
            else
            {
                return 0;
            }
        }

        /**
         * When the stack is empty wait for another thread to
         * put something back onto the stack.
         * Must have lock on this stack.
         * @return object id 
         * -1 if all threads are waiting, so everything is done
         */
        int waitAndPop()
        {
            waitingThreads++;
            long t = System.currentTimeMillis();
            waits++;
            try
            {
                while (waitingThreads < totalThreads && size() == 0)
                {
                    wait();
                }
            }
            catch (InterruptedException e)
            {
                return -1;
            }
            long t2 = System.currentTimeMillis();
            waitsduration += (t2-t);
            if (DEBUG && t2 - t > 10) System.out.println("Slow wait "+(t2-t)+"ms "+Thread.currentThread()+" "+size());  //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            if (waitingThreads >= totalThreads)
            {
                // Everyone is waiting, so all must finish
                waitingThreads--;
                return -1;
            }
            waitingThreads--;
            return pop();
        }

        /**
         * Push an item onto the stack if another thread is waiting
         * for something to be added and there aren't already 
         * enough items on the stack for all the waiting threads.
         * @param z
         * @return true if the item has been pushed
         * false if the item has not been pushed and should be 
         * dealt with by the current thread
         */
        boolean pushIfWaiting(int z)
        {
            /*
             * Push up to RESERVED_WAITING = 20 items per waiting thread, 
             * RESERVED_RUNNING = 5 per non-waiting other threads,
             * so the threads have a chance of finding work waiting.
             * May require tuning.
             */
            if (waitingThreads * RESERVED_WAITING + 
                (totalThreads - waitingThreads - 1) * RESERVED_RUNNING > size())
            {
                push(z);
                ++pushed;
                if (waitingThreads > 0)
                {
                    // All or one?
                    if (size() > 1)
                        notifyAll();
                    else
                        notify();
                }
                return true;
            }
            return false;
        }
    }

    public void markMultiThreaded(int numberOfThreads) throws InterruptedException
    {
        MultiThreadedRootStack rootsStack = new MultiThreadedRootStack(roots.length);

        for (int rootId : roots)
        {
            if (!bits[rootId])
            {
                rootsStack.push(rootId);
                bits[rootId] = true;
            }
        }

        long l = System.currentTimeMillis();
        if (DEBUG) System.out.println("Starting threads "+(new Date()));  //$NON-NLS-1$
        progressListener.beginTask(Messages.ObjectMarker_MarkingObjects, rootsStack.size());

        // Heuristic guess as to a reasonable local range for thread to search
        int n = bits.length;
        Runtime runtime = Runtime.getRuntime();
        // This free memory calculation is very approximate - we do a GC to get a better estimate
        long maxFree = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
        if (maxFree < outboundMem)
        {
            runtime.gc();
            maxFree = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
        }
        /*
         *  Guess as to how many objects with outbound refs we can support @ 30 bytes per ref.
         *  A better estimate would use the size of the outbound refs file.
         */
        int n1 = (int)Math.min(bits.length, bits.length * maxFree / outboundMem);
        // guess as to size for each thread so we don't use all of the memory - allow for overlaps
        int m = (int)((1.0 - Math.pow((double)(n - n1)/n, 1.0/numberOfThreads)) * n);
        // now impose some reasonable limits
        int locality = Math.min(n, Math.max(MIN_LOCALITY, m));
        if (DEBUG) System.out.println("maxFree="+maxFree+" outbound mem="+outboundMem+" n="+n+" n1="+n1+" m="+m+" locality="+locality);  //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$ //$NON-NLS-6$

        // create and start as much marker threads as specified
        DfsThread[] dfsthreads = new DfsThread[numberOfThreads];
        Thread[] threads = new Thread[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++)
        {
            DfsThread dfsthread = USELOCAL ? new LocalDfsThread(rootsStack, locality) :
                                  new DfsThread(rootsStack);
            dfsthreads[i] = dfsthread;
            Thread thread = new Thread(dfsthread, "ObjectMarkerThread-" + (i + 1));//$NON-NLS-1$
            thread.start();
            threads[i] = thread;
        }

        // wait for all the threads to finish
        
        int failed = 0;
        Throwable failure = null;
        for (int i = 0; i < numberOfThreads; i++)
        {
            threads[i].join();
            if (!dfsthreads[i].completed)
            {
                ++failed;
                if (failure == null)
                {
                    failure = dfsthreads[i].failure;
                }
            }
        }

        if (progressListener.isCanceled())
            return;

        if (failed > 0)
        {
            throw new RuntimeException(MessageUtil.format(Messages.ObjectMarker_ErrorMarkingObjectsSeeLog, failed), failure);
        }

        progressListener.done();
        if (DEBUG) System.out.println("Took "+(System.currentTimeMillis() - l)+"ms"); //$NON-NLS-1$//$NON-NLS-2$
    }

    public class DfsThread implements Runnable
    {

        boolean completed; // normal completion
        int size = 0;
        int[] data = new int[10 * 1024]; // start with 10k
        IntStack rootsStack;
        int current = -1;
        Throwable failure; // Exception/Error

        public DfsThread(IntStack roots)
        {
            this.rootsStack = roots;
        }

        public void run()
        {
            try
            {
                while (true)
                {
                    synchronized (rootsStack)
                    {
                        progressListener.worked(1);
                        if (progressListener.isCanceled())
                            return;

                        if (rootsStack.size() > 0) // still some roots are not
                            // processed
                        {
                            data[0] = rootsStack.pop();
                            size = 1;
                        }
                        else
                            // the work is done
                        {
                            break;
                        }
                    }

                    while (size > 0)
                    {
                        /* start stack.pop() */
                        current = data[--size];
                        /* end stack.pop */

                        for (int child : outbound.get(current))
                        {
                            /*
                             * No synchronization here. It costs a lot of
                             * performance It is possible that some bits are marked
                             * more than once, but this is not a problem
                             */
                            if (!bits[child])
                            {
                                bits[child] = true;
                                // stack.push(child);
                                /* start stack.push() */
                                if (size == data.length)
                                {
                                    int[] newArr = new int[data.length << 1];
                                    System.arraycopy(data, 0, newArr, 0, data.length);
                                    data = newArr;
                                }
                                data[size++] = child;
                                /* end stack.push() */
                            }
                        }
                    } // end of processing one GC root
                }
                completed = true;
            } 
            catch (RuntimeException e)
            {
                progressListener.sendUserMessage(Severity.ERROR, Messages.ObjectMarker_ErrorMarkingObjects, e);
                failure = e;
            }
            catch (Error e)
            {
                progressListener.sendUserMessage(Severity.ERROR, Messages.ObjectMarker_ErrorMarkingObjects, e);
                failure = e;
            }
        }
    }

    /**
     * Depth first search thread - with locality.
     * Have a local stack for objects close to the current object.
     * Have a local queue for remaining objects.
     * Use the global stack for excess objects or when local stack and queue are empty.
     */
    public class LocalDfsThread extends DfsThread
    {
        private static final int RESERVED = MultiThreadedRootStack.RESERVED_WAITING - MultiThreadedRootStack.RESERVED_RUNNING;
        static final int MAXSTACK = 100 * 1024;
        /** How many times to loop before checking the global stack */
        private static final int CHECKCOUNT = 10;
        /** How many times to loop on outbounds before checking the global stack */
        private static final int CHECKCOUNT2 = 200;
        int localRange;
        final int localRangeLimit;
        SoftReference<int[]> sr;
        double scaleUp = 0.005;
        MultiThreadedRootStack rootsStack;
        QueueInt queue = new QueueInt(1024);

        public LocalDfsThread(MultiThreadedRootStack roots)
        {
            this(roots, 1000000);
        }
        public LocalDfsThread(MultiThreadedRootStack roots, int range)
        {
            super(roots);
            rootsStack = roots;
            localRange = localRangeLimit = range;
        }
        int localBase;
        void initLocalStack(int val) {
            data[0] = val;
            size = 1;
            localBase = calcBase(data[0]);
        }
        void fillStack() {
            // data array must be at least length 2
            int originalQueueSize = queue.size();
            // Look at every item in the queue
            for (int i = 0; i < originalQueueSize; ++i)
            {
                int z = queue.get();
                if (inRange(z))
                {
                    // In range, so put on the local stack
                    data[size++] = z;
                    if (size == data.length)
                        break;
                }
                else
                {
                    // Requeue
                    queue.put(z);
                }
            }
        }
        public void run()
        {
            rootsStack.linkThread();
            try
            {
                boolean check = false;
                int checkCount = 0;
                while (true)
                {
                    // Pull some work off the global work stack
                    int d;
                    int work;
                    synchronized (rootsStack)
                    {

                        if (rootsStack.size() > 0) // still some roots are not
                            // processed
                        {
                            d = rootsStack.pop();
                        }
                        else
                        {
                            d = rootsStack.waitAndPop();
                            if (d == -1)
                                break;
                        }
                        work = rootsStack.worked(); 
                    }
                    if (work > 0)
                        progressListener.worked(work);
                    queue.put(d);

                    while (queue.size() > 0) 
                    {
                        if (progressListener.isCanceled())
                            return;
                        initLocalStack(queue.get());
                        fillStack();

                        // Process the local stack and queue
                        while (size > 0)
                        {
                            /* start stack.pop() */
                            current = data[--size];
                            /* end stack.pop */

                            // See if other threads need work
                            if (check || checkCount++ >= CHECKCOUNT)
                            {
                                checkCount = 0;
                                check = fillRootsStack();
                            }

                            // Examine each outbound reference
                            for (int child : outbound.get(current))
                            {
                                /*
                                 * No synchronization here. It costs a lot of
                                 * performance It is possible that some bits are
                                 * marked more than once, but this is not a
                                 * problem
                                 */
                                if (!bits[child])
                                {
                                    bits[child] = true;
                                    // See if we have enough work and other threads need more work. 
                                    if (queue.size() + size > RESERVED && (check || checkCount++ >= CHECKCOUNT2))
                                    {
                                        checkCount = 0;
                                        synchronized(rootsStack)
                                        {
                                            check = rootsStack.pushIfWaiting(child);
                                            /*
                                             * Other threads might still need more,
                                             * so transfer more from the queue which is non-local
                                             */
                                        }
                                        if (check)
                                        {
                                            check = fillRootsStack();
                                            continue;
                                        }
                                    }
                                    if (size == 0)
                                    {
                                        // We have emptied the stack, so reset
                                        // the base and refill
                                        initLocalStack(child);
                                        fillStack();
                                    }
                                    else if (inRange(child))
                                    {
                                        // In range
                                        if (size < data.length)
                                        {
                                            data[size++] = child;
                                        }
                                        else if (size < MAXSTACK)
                                        {
                                            // Grow the local stack
                                            int[] newArr = new int[Math.min(Math.max(0, data.length << 1), MAXSTACK)];
                                            System.arraycopy(data, 0, newArr, 0, data.length);
                                            data = newArr;
                                            data[size++] = child;
                                        }
                                        else
                                        {
                                            // Local queue
                                            queue.put(child);
                                        }
                                    }
                                    else
                                    {
                                        // Local queue
                                        queue.put(child);
                                    }
                                    /* end stack.push() */
                                }
                            }
                        } // end of processing one GC root
                    }
                }
                completed = true;
            }
            catch (RuntimeException e)
            {
                progressListener.sendUserMessage(Severity.ERROR, Messages.ObjectMarker_ErrorMarkingObjects, e);
                failure = e;
            }
            catch (OutOfMemoryError e)
            {
                failure = e;
                if (emptyLocalStacks())
                {
                    progressListener.sendUserMessage(Severity.WARNING, Messages.ObjectMarker_WarningMarkingObjects, e);
                    // Mark as done as the remaining threads can continue to handle the remaining work
                    completed = true;
                }
                else
                {
                    progressListener.sendUserMessage(Severity.ERROR, Messages.ObjectMarker_ErrorMarkingObjects, e);
                }
            }
            catch (Error e)
            {
                progressListener.sendUserMessage(Severity.ERROR, Messages.ObjectMarker_ErrorMarkingObjects, e);
                failure = e;
            }
            finally
            {
                rootsStack.unlinkThread();
            }
        }

        /**
         * Fill the root stack with some of the local items
         * if other threads are waiting and there are still
         * enough items on the local stack/queue.
         * @return
         */
        private boolean fillRootsStack()
        {
            boolean check;
            check = true;
            if (queue.size() > 0 && queue.size() + size > RESERVED)
            {
                int fromQueue;
                synchronized (rootsStack)
                {
                    do
                    {
                        fromQueue = queue.get();
                        check = rootsStack.pushIfWaiting(fromQueue);
                    }
                    while (check && queue.size() > 0 && queue.size() + size > RESERVED);
                }
                if (!check)
                    queue.put(fromQueue);
            }
            else if (size > RESERVED)
            {
                synchronized (rootsStack)
                {
                    do
                    {
                        /* Leave current unaffected */
                        /* start stack.pop() */
                        int item = data[--size];
                        check = rootsStack.pushIfWaiting(item);
                        if (!check)
                            /* start stack.push() */
                            data[size++] = item;
                    }
                    while (check && size > RESERVED);
                }
            }
            return check;
        }

        /**
         * Empty the local stacks to the global stack.
         * @return If this was successful.
         */
        boolean emptyLocalStacks()
        {
            synchronized (rootsStack)
            {
                if (DEBUG) System.out.println("emptyLocalStacks "+rootsStack.totalThreads+" "+current+" "+size+" "+queue.size()); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
                // Only can requeue if another thread is still running
                if (rootsStack.totalThreads >= 2)
                {
                    if (current != -1)
                    {
                        rootsStack.push(current);
                        current = -1;
                    }
                    while (size > 0)
                    {
                        rootsStack.push(data[--size]);
                    }
                    while (queue.size() > 0)
                    {
                        rootsStack.push(queue.get());
                    }
                }
            }
            if (DEBUG) System.out.println("emptyLocalStacks "+current+" "+size+" "+queue.size()); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            return current == -1 && size == 0 && queue.size() == 0;
        }

        /**
         * Is the objectId in range for the local stack?
         * @param val
         * @return
         */
        private boolean inRange(int val)
        {
            return val >= localBase && val - localBase < localRange;
        }
        /**
         * Select a suitable base for the local stack.
         * This is 1/4 range below and 3/4 range around the candidate item.
         * @param v
         * @return
         */
        private int calcBase(int v)
        {
            calcRange();
            return Math.max(Math.min(v + (localRange * 3 >>> 2), bits.length) - localRange, 0);
        }
        /**
         * Heuristic to vary size of range depending on GC pressure.
         * Slowly increase the range if no GC pressure.
         * Cleared soft reference means we are running out of space, so reduce the range.
         */
        private void calcRange()
        {
            if (sr == null)
            {
                if (DEBUG) System.out.println("Set local range="+localRange); //$NON-NLS-1$
                // set trigger
                sr = new SoftReference<int[]>(new int[1024]);
            }
            else if (sr.get() != null)
            {
                if (localRange < bits.length && scaleUp > 0.0)
                {
                    // Increase slowly
                    localRange = Math.min((int)(localRange * (1.0 + scaleUp)), bits.length);
                    if (DEBUG) System.out.println("Increased local range="+localRange+" "+scaleUp); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            else if (localRange == localRangeLimit && scaleUp == 0.0)
            {
                // Already at minimum
            }
            else
            {
                // decrease more rapidly
                localRange = Math.max((int)(localRange * 0.9), localRangeLimit);
                // and don't increase as fast
                scaleUp *= 0.5f;
                // if it is too small, stop the scaling
                if (scaleUp * localRange < 1.0)
                    scaleUp = 0.0;
                if (DEBUG) System.out.println("Decreased local range="+localRange+" "+scaleUp); //$NON-NLS-1$ //$NON-NLS-2$
                // reset trigger
                sr = new SoftReference<int[]>(new int[1024]);
            }
        }
    }

    private boolean refersOnlyThroughExcluded(int referrerId, int referentId,
                    ExcludedReferencesDescriptor[] excludeSets, BitField excludeObjectsBF, ISnapshot snapshot)
                    throws SnapshotException
    {
        if (!excludeObjectsBF.get(referrerId))
            return false;

        Set<String> excludeFields = null;
        for (ExcludedReferencesDescriptor set : excludeSets)
        {
            if (set.contains(referrerId))
            {
                excludeFields = set.getFields();
                break;
            }
        }
        if (excludeFields == null)
            return true; // treat null as all fields

        IObject referrerObject = snapshot.getObject(referrerId);
        long referentAddr = snapshot.mapIdToAddress(referentId);

        List<NamedReference> refs = referrerObject.getOutboundReferences();
        for (NamedReference reference : refs)
        {
            if (referentAddr == reference.getObjectAddress() && !excludeFields.contains(reference.getName())) { return false; }
        }
        return true;
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2024 SAP AG, IBM Corporation and others.
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
 *    Jason Koch (Netflix, Inc) - switch implementation to use FJ Pool
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.ConcurrentBitField;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.snapshot.ExcludedReferencesDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;

public class ObjectMarker implements IObjectMarker
{
    int[] roots;
    boolean[] bits;
    IIndexReader.IOne2ManyIndex outbound;
    IProgressListener progressListener;

    // The ForkJoinPool RecursiveAction will recurse, but creating the recursion object is non-zero
    // compared with marking a boolean[] entry. So we recurse N levels inline, then fork.
    // The less levels of inlining, the more overhead from creating the recursion tasks, but the
    // more parallelism. The more levels of inlining, the less parallelism but the less overhead
    // from creating the recursion tasks.

    // Measurements of performance on a sample 33GB heap on Apple M2 show not a huge amount of
    // variation, so we just pick four as some sort of middle ground.
    // inline = 0: 1.8 sec
    // inline = 1: 1.7 sec
    // inline = 2: 1.7 sec
    // inline = 3: 1.9 sec
    // inline = 4: 1.7 sec
    // inline = 6: 1.9 sec
    // inline = 10: 2.0 sec
    // inline = 50: 3.0 sec
    // (previous impl, for reference, 5.1 sec)

    final int LEVELS_RUN_INLINE = 4;

    public ObjectMarker(int[] roots, boolean[] bits, IIndexReader.IOne2ManyIndex outbound,
                    IProgressListener progressListener)
    {
        this(roots, bits, outbound, 0, progressListener);
    }

    public ObjectMarker(int[] roots, boolean[] bits, IIndexReader.IOne2ManyIndex outbound, long outboundLength,
                    IProgressListener progressListener)
    {
        this.roots = roots;
        this.bits = bits;
        this.outbound = outbound;
        this.progressListener = progressListener;
    }

    @Override
    public int markSingleThreaded()
    {
        try
        {
            return (int) markMultiThreadedInner(1);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void markMultiThreaded(int threads) throws InterruptedException
    {
        markMultiThreadedInner(threads);
    }

    public long markMultiThreadedInner(int threads) throws InterruptedException
    {
        ConcurrentBitField bitField = new ConcurrentBitField(bits);

        int[] claimedRoots = IntStream.of(roots)
                .filter(r -> !bits[r])
                .peek(bitField::set)
                .toArray();

        progressListener.beginTask(Messages.ObjectMarker_MarkingObjects, claimedRoots.length);

        LongAdder count = new LongAdder();
        count.add(claimedRoots.length);

        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            ObjectMarkerRootCC root = new ObjectMarkerRootCC(null, claimedRoots, bitField, LEVELS_RUN_INLINE, count);
            // blocks until all work is done
            pool.invoke(root);
        }
        finally {
            pool.shutdown();
            while (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS))
            {
                // wait until completion
            }
        }

        bitField.intoBooleanArrayNonAtomic(bits);
        progressListener.done();

        return count.sum();
    }

    final class ObjectMarkerRootCC extends CountedCompleter<Void> {
        final int[] roots;
        final ConcurrentBitField visited;
        final int levelsInline;
        final LongAdder count;

        ObjectMarkerRootCC(CountedCompleter<?> parent, int[] roots, ConcurrentBitField visited, int levelsInline, LongAdder count) {
            super(parent);
            this.roots = roots;
            this.visited = visited;
            this.levelsInline = levelsInline;
            this.count = count;
        }

        @Override
        public void compute() {
            if (progressListener.isCanceled()) {
                tryComplete();
                return;
            }

            // fork one CC per root
            addToPendingCount(roots.length);
            for (int r : roots) {
                new ObjectMarkerCC(this, r, visited, levelsInline, true, count).fork();
            }

            // completes when children complete
            tryComplete();
        }
    }

    final class ObjectMarkerCC extends CountedCompleter<Void> {
        final int position;
        final ConcurrentBitField visited;
        final int levelsLeft;
        final boolean topLevel;
        final LongAdder count;

        ObjectMarkerCC(
                CountedCompleter<?> parent,
                int position,
                ConcurrentBitField visited,
                int levelsLeft,
                boolean topLevel,
                LongAdder count
        ) {
            super(parent);
            this.position = position;
            this.visited = visited;
            this.levelsLeft = levelsLeft;
            this.topLevel = topLevel;
            this.count = count;
        }

        @Override
        public void compute() {
            if (progressListener.isCanceled()) {
                tryComplete();
                return;
            }

            final int[] process = outbound.get(position);

            if (levelsLeft > 0) {
                // inline traversal
                for (int r : process) {
                    if (!visited.compareAndSet(r, false, true)) continue;
                    count.increment();

                    new ObjectMarkerCC(
                            this, r, visited, levelsLeft - 1, false, count
                    ).compute();
                }
                onDone();
                tryComplete();
                return;
            }

            // fork boundary
            int forks = 0;
            for (int r : process) {
                if (!visited.compareAndSet(r, false, true)) continue;
                count.increment();

                forks++;
                new ObjectMarkerCC(
                        this, r, visited, 0, false, count
                ).fork();
            }

            if (forks == 0) {
                onDone();
                tryComplete();
            } else {
                addToPendingCount(forks);
                // children will call tryComplete()
            }
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            onDone();
        }

        private void onDone() {
            if (topLevel) {
                synchronized (progressListener) {
                    progressListener.worked(1);
                }
            }
        }
    }

    @Override
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

        // Used for performance
        List<NamedReference>refCache = new ArrayList<NamedReference>();
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

            refCache.clear();
            for (int child : outbound.get(current))
            {
                if (!bits[child]) // already visited?
                {
                    if (!refersOnlyThroughExcluded(current, child, excludeSets, excludeObjectsBF, refCache, snapshot))
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
                        if (count % 10000 == 0 && progressListener.isCanceled())
                            throw new IProgressListener.OperationCanceledException();
                    }
                }
            }
        }

        progressListener.done();

        return count;
    }

    private boolean refersOnlyThroughExcluded(int referrerId, int referentId,
                    ExcludedReferencesDescriptor[] excludeSets, BitField excludeObjectsBF,
                    List<NamedReference> refCache, ISnapshot snapshot)
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

        // Only bother doing the sort if there are several entries
        final int minsort = 10;
        boolean sorted;
        if (refCache.isEmpty())
        {
            List<NamedReference> refs = referrerObject.getOutboundReferences();
            refCache.addAll(refs);
            sorted = refCache.size() >= minsort;
            if (sorted)
            {
                refCache.sort(CompObjectReference.INSTANCE);
            }
        }
        else
        {
            sorted = refCache.size() >= minsort;
        }
        int idx;
        if (sorted)
        {
            /*
             * If there are duplicate addresses then this will find one,
             * but to find all must scan forwards and backwards.
             */
            idx = Collections.binarySearch(refCache, new ObjectReference(snapshot, referentAddr),
                            CompObjectReference.INSTANCE);
            if (idx < 0)
                return true;
            // Find the first
            while (idx > 0 && refCache.get(idx - 1).getObjectAddress() == referentAddr)
                --idx;
        }
        else
        {
            // Linear search of all
            idx = 0;
        }

        // Search forwards for the referent
        for (int i = idx; i < refCache.size(); ++i)
        {
            NamedReference reference = refCache.get(i);
            if (referentAddr == reference.getObjectAddress())
            {
                if (!excludeFields.contains(reference.getName()))
                {
                    return false;
                }
            }
            else if (sorted)
            {
                // No more references with this address
                break;
            }
        }
        return true;
    }

    /**
     * Used for sorting {@link ObjectReference} by address.
     */
    private static final class CompObjectReference implements Comparator<ObjectReference>
    {
        @Override
        public int compare(ObjectReference o1, ObjectReference o2)
        {
            return Long.compare(o1.getObjectAddress(), o2.getObjectAddress());
        }
        static CompObjectReference INSTANCE = new CompObjectReference();
    }
}

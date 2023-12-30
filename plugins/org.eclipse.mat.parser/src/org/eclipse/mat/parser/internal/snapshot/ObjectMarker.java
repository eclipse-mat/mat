/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

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
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.mat.util.MessageUtil;

public class ObjectMarker
{
    int[] roots;
    // TODO we can create a new BitField called ConcurrentBitField that would be 1/8th footprint
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
    
    final int LEVELS_RUN_INLINE = 6;

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

    public class FjObjectMarker extends RecursiveAction
    {
        final int position;
        final boolean[] visited;
        final boolean topLevel;

        int counter = 0;

        private FjObjectMarker(final int position, final boolean[] visited, final boolean topLevel)
        {
            // mark as soon as we are created and about to be queued
            visited[position] = true;
            this.position = position;
            this.visited = visited;
            this.topLevel = topLevel;
        }

        public void compute()
        {
            if (progressListener.isCanceled())
            { return; }

            compute(position, LEVELS_RUN_INLINE);

            // only mark progress from the top level tasks; as each root level element
            // is completed, a progress marker is updated
            if (topLevel)
            {
                progressListener.worked(1);
            }
        }

        void compute(final int outboundPosition, final int levelsLeft)
        {
            // TODO can this be an IteratorInt to avoid allocating arrays?
            //      not yet supported by IndexReader but could be in future;
            //      esp. for very large outbounds (ie: arrays)
            final int[] process = outbound.get(outboundPosition);

            if (levelsLeft == 0) {
                // at the final "level" of recursion depth, mark and fork
                for (int r : process)
                {
                    if (!visited[r])
                    {
                        visited[r] = true;
                        new FjObjectMarker(r, visited, false).fork();
                    }
                }
            } else {
                // at non-final level of depth, mark and recurse directly
                for (int r : process)
                {
                    if (!visited[r])
                    {
                        visited[r] = true;
                        compute(r, levelsLeft - 1);
                    }
                }
            }
        }
    }

    public int markSingleThreaded()
    {
        int before = countMarked();
        try
        {
            markMultiThreaded(1);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        int after = countMarked();
        return after - before;
    }

    public void markMultiThreaded(int threads) throws InterruptedException
    {
        progressListener.beginTask(Messages.ObjectMarker_MarkingObjects, roots.length);

        ForkJoinPool pool = new ForkJoinPool(threads);
        for (int r : roots) {
            pool.execute(new FjObjectMarker(r, bits, true));
        }
        pool.shutdown();
        while (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS))
        {
            // wait until completion
        }

        progressListener.done();
    }

    int countMarked()
    {
        int marked = 0;
        for (boolean b : bits)
            if (b)
                marked++;
        return marked;
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

        List<NamedReference> refs = referrerObject.getOutboundReferences();
        for (NamedReference reference : refs)
        {
            if (referentAddr == reference.getObjectAddress() && !excludeFields.contains(reference.getName()))
            { return false; }
        }
        return true;
    }

}

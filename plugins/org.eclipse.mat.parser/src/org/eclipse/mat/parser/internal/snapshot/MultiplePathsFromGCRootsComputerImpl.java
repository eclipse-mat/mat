/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.SnapshotImpl;
import org.eclipse.mat.parser.internal.util.IntStack;
import org.eclipse.mat.snapshot.IMultiplePathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.MultiplePathsFromGCRootsClassRecord;
import org.eclipse.mat.snapshot.MultiplePathsFromGCRootsRecord;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.util.IProgressListener;


public class MultiplePathsFromGCRootsComputerImpl implements IMultiplePathsFromGCRootsComputer
{

    int[] objectIds; // the initial objects
    Object[] paths; // paths for each of the objects

    // snapshot & structures needed from it
    SnapshotImpl snapshot;
    IIndexReader.IOne2ManyIndex inboundIndex;
    HashMapIntObject<?> roots;

    BitField excludeInstances;
    Map<IClass, Set<String>> excludeMap;

    boolean pathsCalculated;

    HashMapIntObject<Path> pathsCache = new HashMapIntObject<Path>(10000);

    public MultiplePathsFromGCRootsComputerImpl(int[] objectIds, Map<IClass, Set<String>> excludeMap,
                    SnapshotImpl snapshot, HashMapIntObject<?> roots) throws SnapshotException
    {
        this.snapshot = snapshot;
        this.objectIds = objectIds;
        this.roots = roots;
        this.excludeMap = excludeMap;
        inboundIndex = snapshot.getIndexManager().inbound;

        if (excludeMap != null)
        {
            initExcludeInstances();
        }
    }

    private void initExcludeInstances() throws SnapshotException
    {
        excludeInstances = new BitField(snapshot.getIndexManager().o2address().size());
        for (IClass clazz : excludeMap.keySet())
        {
            int[] objects = clazz.getObjectIds();
            for (int objId : objects)
            {
                excludeInstances.set(objId);
            }
        }
    }
    
    private void computePaths(IProgressListener progressListener) throws SnapshotException
    {
        int reportFrequency = Math.max(10, objectIds.length / 100);
        progressListener.beginTask(Messages.MultiplePathsFromGCRootsComputerImpl_FindingPaths, 100);
        
        ArrayList<int[]> pathsList = new ArrayList<int[]>();

        for (int i = 0; i < objectIds.length; i++)
        {
            int[] path = getShortestPath(objectIds[i]);
            /*
             * if there is an exclude filter, for some objects there could be no
             * path, i.e. null is expected then
             */
            if (path != null)
            {
                pathsList.add(path);
            }
            
            if (progressListener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            if (i % reportFrequency == 0)
                progressListener.worked(1);
        }
        progressListener.done();

        pathsCalculated = true;
        paths = pathsList.toArray();
    }
    

    public MultiplePathsFromGCRootsRecord[] getPathsByGCRoot(IProgressListener progressListener)
                    throws SnapshotException
    {
        if (!pathsCalculated)
        {
            computePaths(progressListener);
        }
        
        MultiplePathsFromGCRootsRecord dummy = new MultiplePathsFromGCRootsRecord(-1, -1, snapshot);
        for (int i = 0; i < paths.length; i++)
        {
            dummy.addPath((int[]) paths[i]);
        }

        return dummy.nextLevel();
    }

    public Object[] getAllPaths(IProgressListener progressListener) throws SnapshotException
    {
        if (!pathsCalculated)
        {
            computePaths(progressListener);
        }
        return paths;
    }
    
    public MultiplePathsFromGCRootsClassRecord[] getPathsGroupedByClass(boolean startFromTheGCRoots, IProgressListener progressListener) throws SnapshotException
    {
        if (!pathsCalculated)
        {
            computePaths(progressListener);
        }
        
        MultiplePathsFromGCRootsClassRecord dummy = new MultiplePathsFromGCRootsClassRecord(null, -1, startFromTheGCRoots, snapshot);
        for (int i = 0; i < paths.length; i++)
        {
            dummy.addPath((int[]) paths[i]);
        }

        return dummy.nextLevel();
    }

    private int[] getShortestPath(int objectId) throws SnapshotException
    {
        LinkedList<Path> pathFifo = new LinkedList<Path>();
        LinkedList<int[]> referrersFifo = new LinkedList<int[]>();

        Path shortestPathToGCRoot = null;
        Path shortestPathToObject = null;

        int shortestDepth = Integer.MAX_VALUE;

        SetInt visited = new SetInt(1000);

        Path currentPath = null;
        int[] currentReferrers;

        if (roots.get(objectId) != null)
        {
            return new int[] { objectId };
        }
        else
        {
            pathFifo.add(null);
            referrersFifo.add(new int[] { objectId });
        }

        // Continue with the FIFO
        while (pathFifo.size() > 0)
        {
            currentPath = pathFifo.getFirst();
            pathFifo.removeFirst();

            currentReferrers = referrersFifo.getFirst();
            referrersFifo.removeFirst();

            int currentDepth = currentPath == null ? 0 : currentPath.depth;

            for (int i = 0; i < currentReferrers.length; i++)
            {
                int referrer = currentReferrers[i];
                if (visited.contains(referrer))
                {
                    continue;
                }

                if (excludeMap != null)
                {
                    if (currentPath != null && refersOnlyThroughExcluded(referrer, currentPath.objectId))
                    {
                        continue;
                    }
                }

                int[] nextReferrers = inboundIndex.get(referrer);

                /* first chech if any of the referrers is a GC root */
                for (int j = 0; j < nextReferrers.length; j++)
                {
                    int nextReferrer = nextReferrers[j];

                    /* check if the current referrer is a GC root */
                    if (roots.get(nextReferrers[j]) != null)
                    {
                        if (excludeMap == null)
                        {
                            // found a new path
                            Path p1 = new Path(referrer, null, currentPath);
                            if (currentPath != null)
                                currentPath.prev = p1;

                            Path p = new Path(nextReferrer, null, p1);
                            p1.prev = p;
                            Path res = p;
                            int count = 1;
                            while (p != null)
                            {
                                p.depth = count++;
                                pathsCache.put(p.objectId, p);
                                Path q = p.next;
                                if (q != null)
                                    q.prev = p;
                                p = q;
                            }

                            return path2Int(res);
                        }
                        else
                        {
                            if (!refersOnlyThroughExcluded(nextReferrer, referrer))
                            {
                                // found a new strong path

                                Path p1 = new Path(referrer, null, currentPath);
                                if (currentPath != null)
                                    currentPath.prev = p1;

                                Path p = new Path(nextReferrer, null, p1);
                                p1.prev = p;
                                Path res = p;
                                int count = 1;
                                while (p != null)
                                {
                                    p.depth = count++;
                                    pathsCache.put(p.objectId, p);
                                    Path q = p.next;
                                    if (q != null)
                                        q.prev = p;
                                    p = q;
                                }

                                return path2Int(res);
                            }
                        }
                    }

                    /*
                     * nextReferrer is not a GC root. check if there's a cache
                     * entry for it
                     */
                    Path existingPath = pathsCache.get(nextReferrer);

                    if (existingPath != null)
                    {
                        /*
                         * there's already a path to this one - prevent further
                         * exploring it
                         */
                        visited.add(nextReferrer);

                        if (existingPath.depth + currentDepth < shortestDepth)
                        {
                            shortestDepth = existingPath.depth + currentDepth + 1;
                            shortestPathToGCRoot = existingPath;
                            // shortestPathToObject = currentPath;
                            shortestPathToObject = new Path(referrer, null, currentPath);
                        }
                    }

                }

                /* no GC root - update the FIFOs and continue with the next */
                visited.add(referrer);
                Path newPath = new Path(referrer, null, currentPath);
                pathFifo.addLast(newPath);
                referrersFifo.addLast(nextReferrers);
            }

            if ((shortestPathToGCRoot != null) && (currentDepth + 1 >= shortestDepth))
            {
                /*
                 * there can't be a shorter path any longer - return the one we
                 * found
                 */
                return joinPaths(shortestDepth, shortestPathToObject, shortestPathToGCRoot);
            }
        }

        // no other path different from the shortest was found
        if (shortestPathToGCRoot != null)
        {
            return joinPaths(shortestDepth, shortestPathToObject, shortestPathToGCRoot);
        }
        else
        {
            // System.out.println("WARN: Returning a NULL path for ID=" +
            // objectId + "; " + snapshot.getObject(objectId));
            return null;
        }
    }

    private int[] joinPaths(int shortestDepth, Path shortestPathToObject, Path shortestPathToGCRoot)
    {
        /*
         * we have the paths to all of the referrers and we know the shortest
         * one - build it and return it
         */
        int[] result = new int[shortestDepth];
        // save into cache the tail
        if (shortestPathToObject != null)
            shortestPathToObject.prev = shortestPathToGCRoot;

        int depthToGCRoot = shortestPathToGCRoot.depth;
        int depthToObject = shortestDepth - depthToGCRoot;
        int count = 1;

        while (shortestPathToObject != null)
        {
            shortestPathToObject.depth = depthToGCRoot + count;
            // countdown++;
            result[depthToObject - count] = shortestPathToObject.objectId;
            count++;
            pathsCache.put(shortestPathToObject.objectId, shortestPathToObject);
            // move towards the initial object
            Path next = shortestPathToObject.next;
            if (next != null)
                next.prev = shortestPathToObject;
            shortestPathToObject = next;
        }

        while (shortestPathToGCRoot != null)
        {
            try
            {
                result[depthToObject++] = shortestPathToGCRoot.objectId;
            }
            catch (ArrayIndexOutOfBoundsException e)
            {
                // $JL-EXC$
                System.out.println(depthToObject + " ");//$NON-NLS-1$
                // $JL-SYS_OUT_ERR$
            }
            // move towards the GC root
            shortestPathToGCRoot = shortestPathToGCRoot.prev;
        }

        return result;
    }

    private boolean refersOnlyThroughExcluded(int referrerId, int referentId) throws SnapshotException
    {
        if (!excludeInstances.get(referrerId))
            return false;

        IObject referrerObject = snapshot.getObject(referrerId);
        Set<String> excludeFields = excludeMap.get(referrerObject.getClazz());
        if (excludeFields == null)
            return true; // treat null as all fields

        long referentAddr = snapshot.mapIdToAddress(referentId);

        List<NamedReference> refs = referrerObject.getOutboundReferences();
        for (NamedReference reference : refs)
        {
            if (referentAddr == reference.getObjectAddress() && !excludeFields.contains(reference.getName())) { return false; }
        }
        return true;
    }

    private int[] path2Int(Path p)
    {
        IntStack s = new IntStack();
        while (p != null)
        {
            s.push(p.getObjectId());
            p = p.getNext();
        }
        int res[] = new int[s.size()];
        for (int i = 0; i < res.length; i++)
        {
            res[i] = s.pop();
        }
        return res;
    }

    class Path implements Cloneable
    {

        int objectId;
        int depth;
        Path next; // pointing in the direction of the object
        Path prev; // pointing in the direction of the GC root

        public Path(int index, Path prev, Path next)
        {
            this.objectId = index;
            this.next = next;
            this.prev = prev;
            depth = next == null ? 1 : next.depth + 1;
        }

        public Path getNext()
        {
            return next;
        }

        public int getObjectId()
        {
            return objectId;
        }

        public int getDepth()
        {
            return depth;
        }

        public boolean contains(long id)
        {
            Path p = this;
            while (p != null)
            {
                if (p.objectId == id)
                    return true;
                p = p.next;
            }
            return false;
        }

        public Path clone()
        {
            Path p = new Path(objectId, next, prev);
            return p;
        }

    }

}

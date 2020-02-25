/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.util.ObjectTreeFactory;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.snapshot.inspections.MultiplePath2GCRootsQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.IMultiplePathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.IPathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.MultiplePathsFromGCRootsClassRecord;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor;

import com.ibm.icu.text.MessageFormat;

/**
 * Extract information about objects referenced by java.lang.ref.Reference, e.g.
 * weak and soft references, and Finalizer which are also strongly held by
 * the reference, so causing a possible leak.
 */
@CommandName("reference_leak")
@Icon("/META-INF/icons/reference.gif")
public class ReferenceLeakQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument
    public IQueryContext context;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    static final String DEFAULT_REFERENT = "referent"; //$NON-NLS-1$
    @Argument(isMandatory = false)
    public String referent_attribute = DEFAULT_REFERENT;

    @Argument(isMandatory = false)
    public int maxresults = 5;

    @Argument(isMandatory = false)
    public int maxpaths = 100;

    @Argument(isMandatory = false)
    public int maxobjs = 0;

    @Argument(isMandatory = false)
    public double factor = 0.8;

    public IResult execute(IProgressListener listener) throws Exception
    {
        CompositeResult results = new CompositeResult();
        Set<String> fields = Collections.singleton(referent_attribute);
        Map<IClass, Set<String>> allExcludeMap = new HashMap<IClass, Set<String>>();
        // Add all the suspect referents
        ArrayInt ai = new ArrayInt();
        int commonpath[] = null;
        MultiplePathsFromGCRootsClassRecord dummy = new MultiplePathsFromGCRootsClassRecord(null, -1, true, snapshot);

        int nobjs = 0;
        for (int[] objs : objects)
        {
            nobjs += objs.length;
        }

        SimpleMonitor sm = new SimpleMonitor(Messages.ReferenceLeakQuery_ComputingReferentLeaks, listener, new int[] {400, 100});

        listener = sm.nextMonitor();
        listener.beginTask(Messages.ReferenceLeakQuery_ExaminingReferenceObjects, maxobjs > 0 ? maxobjs : nobjs);

        int iobjs = 0;
        int eobjs = 0;
        for (int[] objs : objects)
        {
            for (int ii = 0; ii < objs.length; ii++, iobjs++)
            {
                // Choose a random selection
                if (maxobjs > 0 && Math.random() * (nobjs - iobjs) >= (maxobjs - eobjs))
                    continue;
                ++eobjs;
                IObject o = snapshot.getObject(objs[ii]);
                if (o instanceof IInstance)
                {
                    IInstance obj = (IInstance) o;
                    ObjectReference ref = ReferenceQuery.getReferent(obj, referent_attribute);
                    if (ref != null)
                    {
                        int suspect = ref.getObjectId();
                        Map<IClass, Set<String>> excludeMap = new HashMap<IClass, Set<String>>();
                        excludeMap.put(obj.getClazz(), fields);
                        allExcludeMap.put(obj.getClazz(), fields);
                        IPathsFromGCRootsComputer path = snapshot.getPathsFromGCRoots(suspect, excludeMap);
                        int count = 0;
                        lx: for (int p1[] = path.getNextShortestPath(); p1 != null && count < maxpaths; p1 = path.getNextShortestPath(), ++count)
                        {
                            for (int ip = 0; ip < p1.length; ++ip)
                            {
                                int p = p1[ip];
                                if (listener.isCanceled())
                                    throw new IProgressListener.OperationCanceledException();
                                if (p == obj.getObjectId())
                                {
                                    // Good path and suspect
                                    ai.add(suspect);
                                    ObjectTreeFactory.TreePathBuilder builder = new ObjectTreeFactory.TreePathBuilder();
                                    builder.setIsOutgoing();
                                    builder.addBranch(p1[p1.length - 1]);
                                    for (int j = p1.length - 2; j >= 0 ; --j)
                                    {
                                        if (j + 1 == ip)
                                            builder.addSibling(suspect, true);
                                        builder.addChild(p1[j], j == ip || j == 0);
                                    }
                                    if (commonpath == null)
                                    {
                                        commonpath = new int[p1.length];
                                        for (int i = 0; i < p1.length; ++i)
                                        {
                                            commonpath[i] = p1[p1.length - 1 - i];
                                        }
                                    }
                                    else
                                    {
                                        for (int i = 0; i < commonpath.length; ++i)
                                        {
                                            if (p1.length - 1 - i < 0 || commonpath[i] != p1[p1.length - 1 - i])
                                            {
                                                commonpath = Arrays.copyOf(commonpath, i);
                                            }
                                        }
                                    }
                                    // for classes
                                    dummy.addPath(p1);

                                    // Show some example exact paths
                                    if (ai.size() <= maxresults)
                                    {
                                        IResultTree t1 = builder.build(snapshot);
                                        String title = MessageFormat.format(Messages.ReferenceLeakQuery_TwoPaths, ref.getObject().getDisplayName(), o.getDisplayName());
                                        results.addResult(title, t1);
                                    }
                                    break lx;
                                }
                            }
                        }
                    }
                }
                listener.worked(1);
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
            }
        }
        listener.done();

        listener = sm.nextMonitor();

        // Combine the results

        if (ai.size() == 0)
        {
            TextResult child = new TextResult(MessageFormat.format(Messages.ReferenceLeakQuery_NoStrongPaths, eobjs));
            results.addResult(child);
        }

        if (ai.size() == 1)
        {
            // calculate the shortest path for each object
            IMultiplePathsFromGCRootsComputer computer = snapshot.getMultiplePathsFromGCRoots(ai.toArray(), allExcludeMap);
            // force a path as sometimes the multiple paths GC root choice doesn't match paths from GC roots
            Object allpaths[] = computer.getAllPaths(listener);
            if (allpaths.length >= 1 && allpaths[0] instanceof int[])
            {
                int path1[] = (int[])allpaths[0];
                int path2[] = new int[path1.length];
                for (int i = 0; i < path1.length; ++i)
                {
                    path2[i] = path1[path1.length - 1 - i];
                }
                commonpath = path2;
            }
            IResultTree rt = MultiplePath2GCRootsQuery.create(snapshot, computer, commonpath, listener);
            results.addResult(Messages.ReferenceLeakQuery_PathToReferent, rt);
        }

        if (ai.size() > 1)
        {
            // Display the common paths by class - borrowed from LeakHunterQuery.findReferencePattern()

            // calculate the shortest path for each object
            IMultiplePathsFromGCRootsComputer computer = snapshot.getMultiplePathsFromGCRoots(ai.toArray(), allExcludeMap);

            /*
             * Using the paths from getPathsFromGCRoots doesn't always match getMultiplePathsFromGCRoots
             * so redo.
             */
            dummy = new MultiplePathsFromGCRootsClassRecord(null, -1, true, snapshot);

            Object[] allPaths = computer.getAllPaths(listener);
            for (Object path : allPaths)
                dummy.addPath((int[]) path);

            int numPaths = dummy.getCount();
            MultiplePathsFromGCRootsClassRecord[] classRecords = dummy.nextLevel();

            double threshold = numPaths * factor;
            List<IClass> referencePattern = new ArrayList<IClass>();

            Arrays.sort(classRecords, MultiplePathsFromGCRootsClassRecord.getComparatorByNumberOfReferencedObjects());
            MultiplePathsFromGCRootsClassRecord r = classRecords[0];

            while (r.getCount() > threshold)
            {
                threshold = r.getCount() * factor;
                referencePattern.add(r.getClazz());
                classRecords = r.nextLevel();
                if (classRecords == null || classRecords.length == 0)
                    break;

                Arrays.sort(classRecords, MultiplePathsFromGCRootsClassRecord.getComparatorByNumberOfReferencedObjects());
                r = classRecords[0];
            }

            /*
             * build the tree
             */
            int expandedClasses[] = new int[referencePattern.size()];
            for (int i = 0; i < referencePattern.size(); ++i)
            {
                expandedClasses[i] = referencePattern.get(i).getObjectId();
            }
            IResultTree rt = MultiplePath2GCRootsQuery.create(snapshot, computer, expandedClasses, true, listener);
            String message;
            if (eobjs < nobjs)
                message = MessageUtil.format(Messages.ReferenceLeakQuery_CommonPathsLimit, eobjs);
            else
                message = Messages.ReferenceLeakQuery_CommonPaths;
            results.addResult(message, rt);
        }

        listener.done();
        return results;
    }

}

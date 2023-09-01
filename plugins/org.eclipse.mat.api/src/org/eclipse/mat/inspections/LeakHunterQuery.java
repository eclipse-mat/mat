/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - improve progress monitor checking
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.collect.BitField;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.inspections.FindLeaksQuery.AccumulationPoint;
import org.eclipse.mat.inspections.FindLeaksQuery.ExcludesConverter;
import org.eclipse.mat.inspections.FindLeaksQuery.SuspectRecord;
import org.eclipse.mat.inspections.FindLeaksQuery.SuspectRecordGroupOfObjects;
import org.eclipse.mat.inspections.threads.ThreadInfoQuery;
import org.eclipse.mat.inspections.util.ObjectTreeFactory;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.snapshot.inspections.MultiplePath2GCRootsQuery;
import org.eclipse.mat.query.BytesFormat;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.registry.Converters;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.ListResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.ITestResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.IMultiplePathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.MultiplePathsFromGCRootsClassRecord;
import org.eclipse.mat.snapshot.MultiplePathsFromGCRootsRecord;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.extension.ITroubleTicketResolver;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.snapshot.query.PieFactory;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.snapshot.registry.TroubleTicketResolverRegistry;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

import com.ibm.icu.text.NumberFormat;

@CommandName("leakhunter")
@Icon("/META-INF/icons/leak.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/runningleaksuspectreport.html")
public class LeakHunterQuery implements IQuery
{

    static final String SYSTEM_CLASSLOADER = Messages.LeakHunterQuery_SystemClassLoader;

    // Use per-instance formatters to avoid thread safety problems
    NumberFormat percentFormatter;
    {
        // Use com.ibm.icu
        percentFormatter = NumberFormat.getPercentInstance();
        percentFormatter.setMinimumFractionDigits(2);
        percentFormatter.setMaximumFractionDigits(2);
    }
    NumberFormat numberFormatter = NumberFormat.getNumberInstance();
    BytesFormat bytesFormatter = BytesFormat.getInstance();

    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false)
    public int threshold_percent = 10;

    @Argument(isMandatory = false)
    public int max_paths = 10000;

    @Argument(isMandatory = false, advice = Advice.CLASS_NAME_PATTERN, flag = "skip")
    public Pattern skipPattern = Pattern.compile("java\\..*|javax\\..*|com\\.sun\\..*|jdk\\..*"); //$NON-NLS-1$

    @Argument(isMandatory = false)
    public List<String> excludes = Arrays.asList( //
                    new String[] { "java.lang.ref.Reference:referent", "java.lang.ref.Finalizer:unfinalized", "java.lang.Runtime:" + "<" + GCRootInfo.getTypeAsString(GCRootInfo.Type.UNFINALIZED) + ">" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

    private long totalHeap;

    private IProgressListener listener;

    public IResult execute(IProgressListener listener) throws Exception
    {
        this.listener = listener;
        totalHeap = snapshot.getSnapshotInfo().getUsedHeapSize();

        /* call find_leaks */
        listener.subTask(Messages.LeakHunterQuery_FindingProblemSuspects);
        FindLeaksQuery.SuspectsResultTable findLeaksResult = callFindLeaks(listener);
        SuspectRecord[] leakSuspects = findLeaksResult.getData();

        SectionSpec result = new SectionSpec(Messages.LeakHunterQuery_LeakHunter);
        listener.subTask(Messages.LeakHunterQuery_PreparingResults);

        if (leakSuspects.length > 0)
        {
            PieFactory pie = new PieFactory(snapshot);
            for (int num = 0; num < leakSuspects.length; num++)
            {
                SuspectRecord rec = leakSuspects[num];
                pie.addSlice(rec.suspect.getObjectId(), MessageUtil.format(Messages.LeakHunterQuery_ProblemSuspect, (num + 1)), rec.suspect.getUsedHeapSize(),
                                rec.getSuspectRetained());
            }
            result.add(new QuerySpec(Messages.LeakHunterQuery_Overview, pie.build()));

            HashMap<Integer, List<Integer>> accPoint2ProblemNr = new HashMap<Integer, List<Integer>>();
            int problemNum = 0;
            for (SuspectRecord rec : leakSuspects)
            {
                problemNum++;

                AccumulationPoint ap = rec.getAccumulationPoint();
                if (ap != null)
                {
                    List<Integer> numbers = accPoint2ProblemNr.get(ap.getObject().getObjectId());
                    if (numbers == null)
                    {
                        numbers = new ArrayList<Integer>(2);
                        accPoint2ProblemNr.put(ap.getObject().getObjectId(), numbers);
                    }
                    numbers.add(problemNum);
                }

                CompositeResult suspectDetails = getLeakSuspectDescription(rec, listener);
                suspectDetails.setStatus(ITestResult.Status.ERROR);

                QuerySpec spec = new QuerySpec(MessageUtil.format(Messages.LeakHunterQuery_ProblemSuspect, problemNum));
                spec.setResult(suspectDetails);
                spec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
                spec.set(Params.Html.IS_IMPORTANT, Boolean.TRUE.toString());
                result.add(spec);
            }

            // give hints for problems which could be related
            List<CompositeResult> hints = findCommonPathForSuspects(accPoint2ProblemNr);
            for (int k = 0; k < hints.size(); k++)
            {
                QuerySpec spec = new QuerySpec(MessageUtil.format(Messages.LeakHunterQuery_Hint, (k + 1)));
                spec.setResult(hints.get(k));
                spec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
                spec.set(Params.Html.IS_IMPORTANT, Boolean.TRUE.toString());
                result.add(spec);
            }
        }

        if (result.getChildren().size() != 0)
        {
            return result;
        }
        else
        {
            return new TextResult(Messages.LeakHunterQuery_NothingFound);
        }
    }

    FindLeaksQuery.SuspectsResultTable callFindLeaks(IProgressListener listener) throws Exception
    {
        return (FindLeaksQuery.SuspectsResultTable) SnapshotQuery.lookup("find_leaks", snapshot) //$NON-NLS-1$
                        .setArgument("threshold_percent", threshold_percent) //$NON-NLS-1$
                        .setArgument("max_paths", max_paths) //$NON-NLS-1$
                        .setArgument("excludes", excludes) //$NON-NLS-1$
                        .execute(listener);
    }

    private boolean isThreadRelated(SuspectRecord suspect) throws SnapshotException
    {
        return isThread(suspect.getSuspect().getObjectId());
    }

    private boolean isThread(int objectId) throws SnapshotException
    {
        GCRootInfo[] gcRootInfo = snapshot.getGCRootInfo(objectId);
        if (gcRootInfo != null)
        {
            for (GCRootInfo singleInfo : gcRootInfo)
            {
                if (singleInfo.getType() == GCRootInfo.Type.THREAD_OBJ) { return true; }
            }
        }
        return false;
    }

    /**
     * Is this object a pseudo-object for
     * stack frames as objects?
     */
    private boolean isStackFrame(int objectId) throws SnapshotException
    {
        if (snapshot.getClassOf(objectId).doesExtend("<method>") || //$NON-NLS-1$
                        snapshot.getClassOf(objectId).doesExtend("<stack frame>")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Is this object a local variable from the frameId?
     * @param frameId the stack frame ID, or thread ID if no stack frames as variables.
     * @param objectId the object in questions
     * @return true if a local variable
     * @throws SnapshotException
     */
    private boolean isStackFrameLocal(int frameId, int objectId) throws SnapshotException
    {
        IObject frame = snapshot.getObject(frameId);
        List<NamedReference> refs = frame.getOutboundReferences();
        for (NamedReference ref : refs)
        {
            if (ref instanceof ThreadToLocalReference)
            {
                if (ref.getObjectId() == objectId)
                    return true;
            }
        }
        return false;
    }

    private CompositeResult getLeakSuspectDescription(SuspectRecord suspect, IProgressListener listener)
                    throws SnapshotException
    {
        if (suspect instanceof SuspectRecordGroupOfObjects)
        {
            return getLeakDescriptionGroupOfObjects((SuspectRecordGroupOfObjects) suspect, listener);
        }
        else
        {
            return getLeakDescriptionSingleObject(suspect, listener);
        }
    }

    private CompositeResult getLeakDescriptionSingleObject(SuspectRecord suspect, IProgressListener listener)
                    throws SnapshotException
    {
        StringBuilder overview = new StringBuilder(256);
        TextResult overviewResult = new TextResult(); // used to create links
        // from it to other
        // results
        Set<String> keywords = new LinkedHashSet<String>();
        List<IObject> objectsForTroubleTicketInfo = new ArrayList<IObject>(2);
        int suspectId = suspect.getSuspect().getObjectId();

        /* get dominator info */
        boolean isThreadRelated = isThreadRelated(suspect);
        if (isThreadRelated) // a thread bound problem
        {
            overview.append("<p>"); //$NON-NLS-1$
            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Thread, //
                            HTMLUtils.escapeText(suspect.getSuspect().getDisplayName()), //
                            formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));
            overview.append("</p>"); //$NON-NLS-1$
        }
        else if (snapshot.isClassLoader(suspectId))
        {
            IClassLoader suspectClassloader = (IClassLoader) suspect.getSuspect();
            objectsForTroubleTicketInfo.add(suspectClassloader);

            String classloaderName = getClassLoaderName(suspectClassloader, keywords);

            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ClassLoader, //
                            classloaderName, formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));
        }
        else if (snapshot.isClass(suspectId))
        {
            String className = ((IClass) suspect.getSuspect()).getName();
            keywords.add(className);

            IClassLoader suspectClassloader = (IClassLoader) snapshot.getObject(((IClass) suspect.getSuspect())
                            .getClassLoaderId());
//            involvedClassloaders.add(suspectClassloader);
            objectsForTroubleTicketInfo.add(suspect.getSuspect());

            String classloaderName = getClassLoaderName(suspectClassloader, keywords);

            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Class, //
                            HTMLUtils.escapeText(className), classloaderName, formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));
        }
        else
        {
            String className = suspect.getSuspect().getClazz().getName();
            keywords.add(className);

            IClassLoader suspectClassloader = (IClassLoader) snapshot.getObject(suspect.getSuspect().getClazz()
                            .getClassLoaderId());
//            involvedClassloaders.add(suspectClassloader);
            objectsForTroubleTicketInfo.add(suspect.getSuspect());

            String classloaderName = getClassLoaderName(suspectClassloader, keywords);

            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Instance, //
                            HTMLUtils.escapeText(className), classloaderName, formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));

            /*
             * if the class name matches the skip pattern, try to find the first
             * referrer which does not match the pattern
             */
            if (skipPattern.matcher(className).matches() && !isThreadRelated)
            {
                int referrerId = findReferrer(suspect.getSuspect().getObjectId());
                if (referrerId != -1)
                {
                    IObject referrer = snapshot.getObject(referrerId);
                    IObject referrerClassloader = null;
                    if (snapshot.isClassLoader(referrerId))
                    {
                        referrerClassloader = referrer;
                        objectsForTroubleTicketInfo.add(referrerClassloader);
                        String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedBy,
                                        referrerClassloaderName));
                    }
                    else if (snapshot.isClass(referrerId))
                    {
                        className = ((IClass)referrer).getName();
                        keywords.add(className);
                        referrerClassloader = snapshot.getObject(((IClass) referrer).getClassLoaderId());
//                        involvedClassloaders.add(suspectClassloader);
                        objectsForTroubleTicketInfo.add(referrer);
                        String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedByClass, HTMLUtils.escapeText(className),
                                        referrerClassloaderName));
                    }
                    else
                    {
                        if (isThread(referrerId))
                        {
                            isThreadRelated = true;
                            suspectId = referrerId;
                            IObject suspectObject = snapshot.getObject(suspectId);
                            suspect = new SuspectRecord(suspectObject, suspectObject.getRetainedHeapSize(), suspect.getAccumulationPoint());
                        }
                        className = referrer.getClazz().getName();
                        keywords.add(className);
                        referrerClassloader = snapshot.getObject(referrer.getClazz().getClassLoaderId());
//                        involvedClassloaders.add(suspectClassloader);
                        objectsForTroubleTicketInfo.add(referrer);
                        String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedByInstance, HTMLUtils.escapeText(referrer
                                        .getDisplayName()), referrerClassloaderName));
                        if (isThreadRelated)
                        {
                            overview.append("<p>"); //$NON-NLS-1$
                            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Thread, //
                                            HTMLUtils.escapeText(suspect.getSuspect().getDisplayName()), //
                                            formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));
                            overview.append("</p>"); //$NON-NLS-1$
                        }
                    }
                }
            }
        }

        /* get accumulation point info */
        if (suspect.getAccumulationPoint() != null)
        {
            IObject accumulationObject = suspect.getAccumulationPoint().getObject();

            int accumulationPointId = accumulationObject.getObjectId();
            if (snapshot.isClassLoader(accumulationPointId))
            {
                IClassLoader accPointClassloader = (IClassLoader) accumulationObject;
                objectsForTroubleTicketInfo.add(accPointClassloader);

                String classloaderName = getClassLoaderName(accPointClassloader, keywords);
                overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_AccumulatedBy, classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));

            }
            else if (snapshot.isClass(accumulationPointId))
            {
                IClass clazz = (IClass) accumulationObject;
                keywords.add(clazz.getName());
                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(clazz.getClassLoaderId());
//                involvedClassloaders.add(accPointClassloader);
                objectsForTroubleTicketInfo.add(accumulationObject);

                String classloaderName = getClassLoaderName(accPointClassloader, keywords);

                overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_AccumulatedByLoadedBy, HTMLUtils.escapeText(clazz.getName()),
                                classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));
            }
            else
            {
                String className = accumulationObject.getClazz().getName();
                keywords.add(className);

                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(accumulationObject.getClazz()
                                .getClassLoaderId());
//                involvedClassloaders.add(accPointClassloader);
                objectsForTroubleTicketInfo.add(accumulationObject);

                String classloaderName = getClassLoaderName(accPointClassloader, keywords);
                overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_AccumulatedByInstance, HTMLUtils.escapeText(className),
                                classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));
            }
        }

        /* extract request information for thread related problems */
        ThreadInfoQuery.Result threadDetails = null;
        IObject threadObj = null;
        if (isThreadRelated)
        {
            threadDetails = extractThreadData(suspect, keywords, objectsForTroubleTicketInfo, overview, overviewResult);
            threadObj = suspect.getSuspect();
        }

        IObject describedObject = (suspect.getAccumulationPoint() != null) ? suspect.getAccumulationPoint().getObject()
                        : suspect.getSuspect();

        // add a path to the accumulation point
        QuerySpec qspath;
        try
        {
            IResult result = SnapshotQuery.lookup("path2gc", snapshot) //$NON-NLS-1$
                            .setArgument("object", describedObject) //$NON-NLS-1$
                            .execute(listener);
            qspath = new QuerySpec(Messages.LeakHunterQuery_ShortestPaths, result);
            StringBuilder sb = new StringBuilder("path2gc"); //$NON-NLS-1$
            sb.append(" 0x").append(Long.toHexString(describedObject.getObjectAddress())); //$NON-NLS-1$
            //addExcludes(sb);
            qspath.setCommand(sb.toString());
            // See if the end of the path is a thread
            if (!isThreadRelated && result instanceof IResultTree && result instanceof ISelectionProvider)
            {
                IResultTree tree = (IResultTree)result;
                ISelectionProvider sel = (ISelectionProvider)result;
                for (Object row : tree.getElements())
                {
                    int r[] = findEndTree(tree, sel, row, describedObject.getObjectId(), -1);
                    if (r != null)
                    {
                        if (isThread(r[0]))
                        {
                            isThreadRelated = true;
                            int accObjId = (r[2] >= 0 && isStackFrame(r[1]) && isStackFrameLocal(r[1], r[2])) ? r[2] : r[1];
                            AccumulationPoint ap = accObjId >= 0 ? new AccumulationPoint(snapshot.getObject(accObjId)) : null;
                            IObject suspectObject = snapshot.getObject(r[0]);
                            SuspectRecord suspect2 = new SuspectRecord(suspectObject, suspectObject.getRetainedHeapSize(), ap);
                            objectsForTroubleTicketInfo.add(suspect2.getSuspect());

                            overview.append("<p>"); //$NON-NLS-1$
                            if (ap != null)
                            {
                                overview.append(MessageUtil.format(Messages.LeakHunterQuery_ThreadLocalVariable,
                                                HTMLUtils.escapeText(suspect2.getSuspect().getDisplayName()),
                                                HTMLUtils.escapeText(ap.getObject().getDisplayName()),
                                                HTMLUtils.escapeText(describedObject.getDisplayName())));
                            }
                            else
                            {
                                overview.append(MessageUtil.format(Messages.LeakHunterQuery_ThreadShortestPath,
                                                HTMLUtils.escapeText(suspect2.getSuspect().getDisplayName()),
                                                HTMLUtils.escapeText(describedObject.getDisplayName())));
                            }
                            overview.append(" "); //$NON-NLS-1$
                            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Thread, //
                                            HTMLUtils.escapeText(suspect2.getSuspect().getDisplayName()), //
                                            formatRetainedHeap(suspect2.getSuspectRetained(), totalHeap)));
                            overview.append("</p>"); //$NON-NLS-1$
                            threadDetails = extractThreadData(suspect2, keywords, objectsForTroubleTicketInfo, overview, overviewResult);
                            threadObj = suspect2.getSuspect();
                            break;
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new SnapshotException(Messages.LeakHunterQuery_ErrorShortestPaths, e);
        }

        /* append keywords */
        appendKeywords(keywords, overview);

        // add CSN components data
        appendTroubleTicketInformation(objectsForTroubleTicketInfo, overview);

        /*
         * Prepare the composite result from the different pieces
         */
        CompositeResult composite = new CompositeResult();
        overviewResult.setText(overview.toString());
        composite.addResult(Messages.LeakHunterQuery_Description, overviewResult);

        // add the result of a path to the accumulation point
        composite.addResult(qspath);

        // show the acc. point in the dominator tree
        IResult objectInDominatorTree = showInDominatorTree(describedObject.getObjectId());
        QuerySpec qs = new QuerySpec(Messages.LeakHunterQuery_AccumulatedObjects, objectInDominatorTree);
        qs.setCommand("show_dominator_tree 0x" +  Long.toHexString(describedObject.getObjectAddress())); //$NON-NLS-1$
        composite.addResult(qs);

        // add histogram of dominated.
        IResult histogramOfDominated = getHistogramOfDominated(describedObject.getObjectId());
        if (histogramOfDominated != null)
        {
            qs = new QuerySpec(Messages.LeakHunterQuery_AccumulatedObjectsByClass, histogramOfDominated);
            qs.setCommand("show_dominator_tree 0x" +  Long.toHexString(describedObject.getObjectAddress()) + " -groupby BY_CLASS"); //$NON-NLS-1$//$NON-NLS-2$
            composite.addResult(qs);

            IResult result = SnapshotQuery.lookup("show_retained_set", snapshot) //$NON-NLS-1$
                            .setArgument("objects", describedObject) //$NON-NLS-1$
                            .execute(listener);
            qs = new QuerySpec(Messages.LeakHunterQuery_AllAccumulatedObjectsByClass, result);
            qs.setCommand("show_retained_set 0x" + Long.toHexString(describedObject.getObjectAddress())); //$NON-NLS-1$
            composite.addResult(qs);
        }

        if (threadDetails != null)
        {
            qs = new QuerySpec(Messages.LeakHunterQuery_ThreadDetails, threadDetails);
            qs.setCommand("thread_details 0x" + Long.toHexString(threadObj.getObjectAddress())); //$NON-NLS-1$
            composite.addResult(qs);
        }

        return composite;
    }

    private void addCommand(QuerySpec spec, String command, int suspects[])
    {
        if (suspects.length > 0)
        {
            if (suspects.length <= 30)
            {
                try
                {
                    StringBuilder sb = new StringBuilder(command);
                    for (int i : suspects)
                    {
                        sb.append(" 0x").append(Long.toHexString(snapshot.mapIdToAddress(i))); //$NON-NLS-1$
                    }
                    spec.setCommand(sb.toString());
                    return;
                }
                catch (SnapshotException e)
                {} // Ignore if problem
            }
            // Perhaps they are all the instances of a class
            try
            {
                IClass cls = snapshot.getClassOf(suspects[0]);
                if (cls.getNumberOfObjects() == suspects.length)
                {
                    //
                    int a[] = cls.getObjectIds();
                    int b[] = suspects.clone();
                    Arrays.sort(a);
                    Arrays.sort(b);
                    if (Arrays.equals(a, b))
                    {
                        Collection<IClass> cl1 = snapshot.getClassesByName(cls.getName(), false);
                        if (cl1 != null && cl1.size() == 1)
                        {
                            StringBuilder sb = new StringBuilder(command);
                            sb.append(' ');
                            sb.append(cls.getName());
                            spec.setCommand(sb.toString());
                            return;
                        }

                    }
                }
                // See if in dominator tree
            }
            catch (SnapshotException e)
            {}
        }
    }

    private CompositeResult getLeakDescriptionGroupOfObjects(SuspectRecordGroupOfObjects suspect, IProgressListener listener)
                    throws SnapshotException
    {
        StringBuilder builder = new StringBuilder(256);
        Set<String> keywords = new LinkedHashSet<String>();
        List<IObject> objectsForTroubleTicketInfo = new ArrayList<IObject>(2);


        /* get leak suspect info */
        String className = ((IClass) suspect.getSuspect()).getName();
        keywords.add(className);

        IClassLoader classloader = (IClassLoader) snapshot
                        .getObject(((IClass) suspect.getSuspect()).getClassLoaderId());
        objectsForTroubleTicketInfo.add(suspect.getSuspect());

        String classloaderName = getClassLoaderName(classloader, keywords);

        String numberOfInstances = numberFormatter.format(suspect.getSuspectInstances().length);
        builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_InstancesOccupy, numberOfInstances, HTMLUtils.escapeText(className),
                        classloaderName, formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));

        int[] suspectInstances = suspect.getSuspectInstances();
        List<IObject> bigSuspectInstances = new ArrayList<IObject>();
        for (int j = 0; j < suspectInstances.length; j++)
        {
            IObject inst = snapshot.getObject(suspectInstances[j]);
            if (inst.getRetainedHeapSize() < (totalHeap / 100))
                break;
            bigSuspectInstances.add(inst);
        }
        if (bigSuspectInstances.size() > 0)
        {
            builder.append("<p>").append(Messages.LeakHunterQuery_BiggestInstances); //$NON-NLS-1$
            builder.append("</p>"); //$NON-NLS-1$
            builder.append("<ul title=\"").append(escapeHTMLAttribute(Messages.LeakHunterQuery_BiggestInstances)).append("\">"); //$NON-NLS-1$ //$NON-NLS-2$
            for (IObject inst : bigSuspectInstances)
            {
                builder.append("<li>").append(HTMLUtils.escapeText(inst.getDisplayName())); //$NON-NLS-1$
                builder.append("&nbsp;-&nbsp;") //$NON-NLS-1$
                                .append(
                                                MessageUtil.format(Messages.LeakHunterQuery_Msg_Bytes,
                                                                formatRetainedHeap(inst.getRetainedHeapSize(),
                                                                                totalHeap)));
                builder.append("</li>"); //$NON-NLS-1$
            }
            builder.append("</ul>"); //$NON-NLS-1$
        }

        /* get accumulation point info */
        if (suspect.getAccumulationPoint() != null)
        {
            builder.append("<p>"); //$NON-NLS-1$
            int accumulationPointId = suspect.getAccumulationPoint().getObject().getObjectId();
            if (snapshot.isClassLoader(accumulationPointId))
            {
                objectsForTroubleTicketInfo.add((IClassLoader) suspect.getAccumulationPoint().getObject());
                classloaderName = getClassLoaderName(suspect.getAccumulationPoint().getObject(), keywords);
                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromClassLoader,
                               classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));
            }
            else if (snapshot.isClass(accumulationPointId))
            {
                className = ((IClass) suspect.getAccumulationPoint().getObject()).getName();
                keywords.add(className);

                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(((IClass) suspect
                                .getAccumulationPoint().getObject()).getClassLoaderId());
//                involvedClassLoaders.add(accPointClassloader);
                objectsForTroubleTicketInfo.add(suspect.getAccumulationPoint().getObject());
                classloaderName = getClassLoaderName(accPointClassloader, keywords);

                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromClass, HTMLUtils.escapeText(className),
                                classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));
            }
            else
            {
                className = suspect.getAccumulationPoint().getObject().getClazz().getName();
                keywords.add(className);

                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(suspect.getAccumulationPoint()
                                .getObject().getClazz().getClassLoaderId());
//                involvedClassLoaders.add(accPointClassloader);
                objectsForTroubleTicketInfo.add(suspect.getAccumulationPoint().getObject());

                classloaderName = getClassLoaderName(accPointClassloader, keywords);

                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromInstance, HTMLUtils.escapeText(className),
                                classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));

                boolean isThreadRelated = isThread(suspect.getAccumulationPoint().getObject().getObjectId());
                /*
                 * if the class name matches the skip pattern, try to find the first
                 * referrer which does not match the pattern
                 */
                if (skipPattern.matcher(className).matches() && !isThreadRelated)
                {
                    int referrerId = findReferrer(accumulationPointId);
                    if (referrerId != -1)
                    {
                        IObject referrer = snapshot.getObject(referrerId);
                        IObject referrerClassloader = null;
                        if (snapshot.isClassLoader(referrerId))
                        {
                            referrerClassloader = referrer;
                            objectsForTroubleTicketInfo.add(referrerClassloader);
                            String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                            builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedBy,
                                            referrerClassloaderName));
                        }
                        else if (snapshot.isClass(referrerId))
                        {
                            className = ((IClass)referrer).getName();
                            keywords.add(className);
                            referrerClassloader = snapshot.getObject(((IClass) referrer).getClassLoaderId());
//                            involvedClassloaders.add(referrerClassloader);
                            objectsForTroubleTicketInfo.add(referrer);
                            String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                            builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedByClass, HTMLUtils.escapeText(className),
                                            referrerClassloaderName));
                        }
                        else
                        {
                            SuspectRecord suspect2 = null;
                            if (isThread(referrerId))
                            {
                                isThreadRelated = true;
                                int suspectId = referrerId;
                                IObject suspectObject = snapshot.getObject(suspectId);
                                suspect2 = new SuspectRecord(suspectObject, suspectObject.getRetainedHeapSize(), suspect.getAccumulationPoint());
                            }
                            className = referrer.getClazz().getName();
                            keywords.add(className);
                            referrerClassloader = snapshot.getObject(referrer.getClazz().getClassLoaderId());
//                            involvedClassloaders.add(suspectClassloader);
                            objectsForTroubleTicketInfo.add(referrer);
                            String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                            builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedByInstance, HTMLUtils.escapeText(referrer
                                            .getDisplayName()), referrerClassloaderName));
                            if (isThreadRelated)
                            {
                                builder.append("<p>"); //$NON-NLS-1$
                                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Thread, //
                                                HTMLUtils.escapeText(suspect2.getSuspect().getDisplayName()), //
                                                formatRetainedHeap(suspect2.getSuspectRetained(), totalHeap)));
                                builder.append("</p>"); //$NON-NLS-1$
                            }
                        }
                    }
                }
            }
            builder.append("</p>"); //$NON-NLS-1$
        }
        ThreadInfoQuery.Result threadDetails = null;
        IObject threadObj = null;

        /*
         * Prepare the composite result from the different pieces
         */
        CompositeResult composite = new CompositeResult();
        TextResult overviewResult = new TextResult(); // used to create links
        // Empty result, will be filled in later
        composite.addResult(Messages.LeakHunterQuery_Description, overviewResult);

        /*
         * Show more details about the big instances
         */
        if (bigSuspectInstances.size() > 0)
        {
            PieFactory pie = new PieFactory(snapshot, totalHeap);
            int big[] = new int[bigSuspectInstances.size()];
            long totalBig = 0;
            long usedBig = 0;
            for (int i = 0; i < bigSuspectInstances.size(); ++i)
            {
                IObject iObject = bigSuspectInstances.get(i);
                big[i] = iObject.getObjectId();
                pie.addSlice(big[i], iObject.getDisplayName(), iObject.getUsedHeapSize(), iObject.getRetainedHeapSize());
                totalBig += iObject.getRetainedHeapSize();
                usedBig += iObject.getUsedHeapSize();
            }
            long totalSuspects = 0;
            long usedSuspects = 0;
            for (int s : suspectInstances)
            {
                IObject inst = snapshot.getObject(s);
                totalSuspects += inst.getRetainedHeapSize();
                usedSuspects += inst.getUsedHeapSize();
            }
            pie.addSlice(-1, Messages.LeakHunterQuery_OtherSuspectInstances, usedSuspects - usedBig, totalSuspects - totalBig);
            QuerySpec specPie = new QuerySpec(Messages.LeakHunterQuery_BiggestInstancesOverview, pie.build());
            specPie.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
            composite.addResult(specPie);

            QuerySpec spec = new QuerySpec(Messages.LeakHunterQuery_BiggestInstancesHeading,
                            new ObjectListResult.Outbound(snapshot, big));
            try
            {
                StringBuilder sb = new StringBuilder("list_objects"); //$NON-NLS-1$
                for (int i : big)
                {
                    sb.append(" 0x").append(Long.toHexString(snapshot.mapIdToAddress(i))); //$NON-NLS-1$
                }
                spec.setCommand(sb.toString());
            }
            catch (SnapshotException e)
            {} // Ignore if problem
            spec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
            composite.addResult(spec);
        }

        // add histogram of suspects and show objects they retain
        if (true)
        {
            IObject io = suspect.getSuspect();
            String oql = null;
            if (io instanceof IClass)
            {
                String cn = OQLclassName((IClass)io);
                oql = "SELECT * FROM " + cn + " s WHERE dominatorof(s) = null"; //$NON-NLS-1$ //$NON-NLS-2$
            }

            RefinedResultBuilder rbuilder = SnapshotQuery.lookup("histogram", snapshot) //$NON-NLS-1$
                            .setArgument("objects", suspectInstances) //$NON-NLS-1$
                            .refine(listener);
            rbuilder.setInlineRetainedSizeCalculation(true);
            rbuilder.addDefaultContextDerivedColumn(RetainedSizeDerivedData.PRECISE);
            IResult result = rbuilder.build();
            QuerySpec qs = new QuerySpec(Messages.LeakHunterQuery_SuspectObjectsByClass, result);
            addCommand(qs, "histogram", suspectInstances); //$NON-NLS-1$
            if (qs.getCommand() == null)
            {
                qs.setCommand("histogram " + oql +";"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            qs.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
            composite.addResult(qs);

            rbuilder = SnapshotQuery.lookup("show_retained_set", snapshot) //$NON-NLS-1$
                            .setArgument("objects", suspectInstances) //$NON-NLS-1$
                            .refine(listener);
            rbuilder.setInlineRetainedSizeCalculation(true);
            rbuilder.addDefaultContextDerivedColumn(RetainedSizeDerivedData.APPROXIMATE);
            result = rbuilder.build();
            qs = new QuerySpec(Messages.LeakHunterQuery_AllObjectsByClassRetained, result);
            addCommand(qs, "show_retained_set", suspectInstances); //$NON-NLS-1$
            if (qs.getCommand() == null)
            {
                qs.setCommand("show_retained_set " + oql +";"); //$NON-NLS-1$
            }
            qs.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
            composite.addResult(qs);
        }

        AccumulationPoint accPoint = suspect.getAccumulationPoint();
        if (accPoint != null)
        {
            QuerySpec qs = new QuerySpec(Messages.LeakHunterQuery_CommonPath, //
                                MultiplePath2GCRootsQuery.create(snapshot, suspect.getPathsComputer(), suspect.getCommonPath(), listener));
            int paths[];
            if (suspect.suspectInstances.length <= 25)
            {
                // Use all the objects
                paths = suspect.suspectInstances;
            }
            else if (suspect.getCommonPath().length > 0)
            {
                // Just use the last of the common path
                paths = new int[] { suspect.getCommonPath()[suspect.getCommonPath().length - 1] };
            }
            else
            {
                paths = new int[0];
            }
            if (paths.length > 0)
            {
                StringBuilder sb = new StringBuilder("merge_shortest_paths"); //$NON-NLS-1$
                for (int objId : paths)
                {
                    long addr = snapshot.mapIdToAddress(objId);
                    sb.append(" 0x").append(Long.toHexString(addr)); //$NON-NLS-1$
                }
                addExcludes(sb);
                qs.setCommand(sb.toString());
            }
            composite.addResult(qs);
            int path[] = suspect.getCommonPath();
            if (path.length > 0)
            {
                if (isThread(path[0]))
                {
                    AccumulationPoint ap;
                    if (path.length > 2 && isStackFrame(path[1]) && isStackFrameLocal(path[1], path[2]))
                    {
                        ap = new AccumulationPoint(snapshot.getObject(path[2]));
                    }
                    else if (path.length > 1)
                    {
                        ap = new AccumulationPoint(snapshot.getObject(path[1]));
                    }
                    else
                    {
                        ap = null;
                    }
                    IObject suspectObject = snapshot.getObject(path[0]);
                    SuspectRecord suspect2 = new SuspectRecord(suspectObject, suspectObject.getRetainedHeapSize(), ap);
                    objectsForTroubleTicketInfo.add(suspect2.getSuspect());
                    // Description
                    builder.append("<p>"); //$NON-NLS-1$
                    if (ap != null)
                    {
                        builder.append(MessageUtil.format(Messages.LeakHunterQuery_ThreadLocalVariable,
                                        HTMLUtils.escapeText(suspect2.getSuspect().getDisplayName()),
                                        HTMLUtils.escapeText(ap.getObject().getDisplayName()),
                                        HTMLUtils.escapeText(accPoint.getObject().getDisplayName())));
                    }
                    else
                    {
                        builder.append(MessageUtil.format(Messages.LeakHunterQuery_ThreadShortestPath,
                                        HTMLUtils.escapeText(suspect2.getSuspect().getDisplayName()),
                                        HTMLUtils.escapeText(accPoint.getObject().getDisplayName())));
                    }
                    builder.append(" "); //$NON-NLS-1$
                    builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Thread, //
                                    HTMLUtils.escapeText(suspect2.getSuspect().getDisplayName()), //
                                    formatRetainedHeap(suspect2.getSuspectRetained(), totalHeap)));
                    builder.append("</p>"); //$NON-NLS-1$
                    threadDetails = extractThreadData(suspect2, keywords, objectsForTroubleTicketInfo, builder, overviewResult);
                    threadObj = suspect2.getSuspect();
                    // add keywords to builder later
                    // add CSN components data to builder later
                }
            }

            // show the acc. point in the dominator tree
            IObject describedObject = accPoint.getObject();
            IResult objectInDominatorTree = showInDominatorTree(describedObject.getObjectId());
            QuerySpec qs2 = new QuerySpec(Messages.LeakHunterQuery_AccumulatedObjects, objectInDominatorTree);
            qs2.setCommand("show_dominator_tree 0x" +  Long.toHexString(describedObject.getObjectAddress())); //$NON-NLS-1$
            composite.addResult(qs2);

            // add histogram of dominated.
            IResult histogramOfDominated = getHistogramOfDominated(describedObject.getObjectId());
            if (histogramOfDominated != null)
            {
                qs = new QuerySpec(Messages.LeakHunterQuery_AccumulatedObjectsByClass, histogramOfDominated);
                qs.setCommand("show_dominator_tree 0x" +  Long.toHexString(describedObject.getObjectAddress()) + " -groupby BY_CLASS"); //$NON-NLS-1$//$NON-NLS-2$
                composite.addResult(qs);

                IResult result = SnapshotQuery.lookup("show_retained_set", snapshot) //$NON-NLS-1$
                                .setArgument("objects", describedObject) //$NON-NLS-1$
                                .execute(listener);
                qs = new QuerySpec(Messages.LeakHunterQuery_AllAccumulatedObjectsByClass, result);
                qs.setCommand("show_retained_set 0x" + Long.toHexString(describedObject.getObjectAddress())); //$NON-NLS-1$
                composite.addResult(qs);
            }
        }
        else
        {
            IResult result = findReferencePattern(suspect);
            if (result != null)
            {
                String msg = (suspect.getSuspectInstances().length > max_paths) ?
                    MessageUtil.format(Messages.LeakHunterQuery_ReferencePatternFor, max_paths) :
                    Messages.LeakHunterQuery_ReferencePattern;
                QuerySpec qs = new QuerySpec(msg, result);
                IObject io = suspect.getSuspect();
                if (io instanceof IClass)
                {
                    String cn = OQLclassName((IClass)io);
                    qs.setCommand("merge_shortest_paths SELECT * FROM " + cn + " s WHERE dominatorof(s) = null; -groupby FROM_GC_ROOTS_BY_CLASS -excludes ;"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                composite.addResult(qs);
            }
        }

        // Postpone building overview until now

        // append keywords
        appendKeywords(keywords, builder);

        // add CSN components data
        appendTroubleTicketInformation(objectsForTroubleTicketInfo, builder);

        overviewResult.setText(builder.toString());

        if (threadDetails != null)
        {
            QuerySpec qs = new QuerySpec(Messages.LeakHunterQuery_ThreadDetails, threadDetails);
            qs.setCommand("thread_details 0x" + Long.toHexString(threadObj.getObjectAddress())); //$NON-NLS-1$
            composite.addResult(qs);
        }

        return composite;
    }

    void addExcludes(StringBuilder sb)
    {
        if (excludes != null && !excludes.isEmpty())
        {
            sb.append(" -excludes"); //$NON-NLS-1$
            for (String ex : excludes)
            {
                sb.append(' ').append(Converters.convertAndEscape(String.class, ex));
            }
            sb.append(';');
        }
    }

    private String escapeHTMLAttribute(String msg)
    {
        return HTMLUtils.escapeText(msg).replaceAll("\"", "&quote;").replaceAll("'", "&apos;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    /**
     * Find the end of the path to GC roots.
     * Relies on end being selected.
     * @param tree the tree
     * @param sel the selection provider (the tree)
     * @param row the current row
     * @param prev previous object in tree
     * @param prev2 the object before the previous object in tree
     * @return array [root object, previous object traversed, second previous object traversed] or
     *     null if no selected object at root
     */
    private int[] findEndTree(IResultTree tree, ISelectionProvider sel, Object row, int prev, int prev2)
    {
        if (sel.isSelected(row))
        {
            IContextObject x = tree.getContext(row);
            if (x != null && x.getObjectId() >= 0)
                return new int[] {x.getObjectId(), prev, prev2};
        }
        if (sel.isExpanded(row))
        {
            // Recurse
            IContextObject x = tree.getContext(row);
            if (x != null)
            {
                prev2 = prev;
                prev = x.getObjectId();
            }
            List<?> children = tree.getChildren(row);
            if (children != null)
            {
                for (Object r2 : children)
                {
                    int ret[] = findEndTree(tree, sel, r2, prev, prev2);
                    if (ret != null)
                        return ret;
                }
            }
        }
        return null;
    }

    private String OQLclassName(IClass ic)
    {
        String className = ic.getName();
        // Check class name is sensible and will parse (not a full Java identifier test)
        // and is unique
        try
        {
            Collection<IClass> classesByName = ic.getSnapshot().getClassesByName(className, false);
            if (className.matches("\\p{javaJavaIdentifierStart}[\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}.]*") && classesByName != null && classesByName.size() == 1) //$NON-NLS-1$
            {
                return className;
            }
        }
        catch (SnapshotException e)
        {
        }
        return "0x" + Long.toHexString(ic.getObjectAddress()); //$NON-NLS-1$
    }

    private String formatRetainedHeap(long retained, long totalHeap)
    {
        return bytesFormatter.format(retained) + " (" //$NON-NLS-1$
                        + percentFormatter.format((double) retained / (double) totalHeap) + ")"; //$NON-NLS-1$
    }

    private Map<String, String> getTroubleTicketMapping(ITroubleTicketResolver resolver, List<IObject> classloaders)
                    throws SnapshotException
    {
        Map<String, String> mapping = new HashMap<String, String>();
        for (IObject suspect : classloaders)
        {
            String ticket = null;
            String key = null;
            if (suspect instanceof IClassLoader)
            {
                ticket = resolver.resolveByClassLoader((IClassLoader) suspect, listener);
                if (ticket != null && !"".equals(ticket.trim())) //$NON-NLS-1$
                {
                    key = suspect.getClassSpecificName();
                    if (key == null) key = suspect.getTechnicalName();

                }
            }
            else
            {
                IClass clazz = (suspect instanceof IClass) ? (IClass) suspect : suspect.getClazz();
                ticket = resolver.resolveByClass(clazz, listener);
                key = clazz.getName();
            }

            if (ticket != null)
            {
                String old = mapping.put(ticket, key);
                if (old != null) mapping.put(ticket, key + ", " + old); //$NON-NLS-1$
            }

        }
        return mapping;
    }

    private String getName(IObject object)
    {
        String name = object.getClassSpecificName();
        if (name == null)
        {
            name = object.getTechnicalName();
        }
        return name;
    }

    /**
     * Get the name of the class loader.
     * @param classloader
     * @param keywords
     * @return The name with HTML escapes already applied.
     */
    private String getClassLoaderName(IObject classloader, Set<String> keywords)
    {
        if (classloader.getObjectAddress() == 0)
        {
            return SYSTEM_CLASSLOADER;
        }
        else
        {
            String classloaderName = getName(classloader);
            if (keywords != null)
            {
                // Do not want the address in the keyword, so do not use getTechnicalName()
                String keywordName = classloader.getClassSpecificName();
                if (keywordName == null)
                    keywordName = classloader.getClazz().getName();
                keywords.add(keywordName);
            }
            return HTMLUtils.escapeText(classloaderName);
        }
    }

    private IResult showInDominatorTree(int objectId) throws SnapshotException
    {
        // show the acc. point in the dominator tree
        Stack<Integer> tmp = new Stack<Integer>();
        int e = objectId;
        while (e != -1)
        {
            tmp.push(e);
            e = snapshot.getImmediateDominatorId(e);
        }
        ObjectTreeFactory.TreePathBuilder treeBuilder = new ObjectTreeFactory.TreePathBuilder(snapshot
                        .getSnapshotInfo().getUsedHeapSize());
        treeBuilder.setIsOutgoing();
        treeBuilder.addBranch(tmp.pop());

        while (tmp.size() > 0)
        {
            e = tmp.pop();
            treeBuilder.addChild(e, e == objectId);
        }
        int[] dominatedByAccPoint = snapshot.getImmediateDominatedIds(objectId);
        for (int i = 0; i < 20 && i < dominatedByAccPoint.length; i++)
        {
            treeBuilder.addSibling(dominatedByAccPoint[i], false);
        }

        return treeBuilder.build(snapshot);
    }

    private IResult getHistogramOfDominated(int objectId) throws SnapshotException
    {
        int[] dominatedByAccPoint = snapshot.getImmediateDominatedIds(objectId);
        Histogram h = snapshot.getHistogram(dominatedByAccPoint, listener);
        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        ClassHistogramRecord[] records = h.getClassHistogramRecords().toArray(new ClassHistogramRecord[0]);

        for (ClassHistogramRecord record : records)
        {
            record.setRetainedHeapSize(snapshot.getMinRetainedSize(record.getObjectIds(), listener));
        }

        Arrays.sort(records, Histogram.reverseComparator(Histogram.COMPARATOR_FOR_RETAINEDHEAPSIZE));

        ArrayList<ClassHistogramRecord> suspects = new ArrayList<ClassHistogramRecord>();

        int limit = 0;
        for (ClassHistogramRecord record : records)
        {
            if (limit >= 20)
                break;
            suspects.add(record);
            limit++;
        }

        ListResult result = new ListResult(ClassHistogramRecord.class, suspects, "label", //$NON-NLS-1$
                        "numberOfObjects", "usedHeapSize", "retainedHeapSize") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        {
            @Override
            public URL getIcon(Object row)
            {
                return Icons.forObject(snapshot, ((ClassHistogramRecord) row).getClassId());
            }

            @Override
            public IContextObject getContext(final Object row)
            {
                return new IContextObjectSet()
                {

                    public int getObjectId()
                    {
                        return ((ClassHistogramRecord) row).getClassId();
                    }

                    public int[] getObjectIds()
                    {
                        return ((ClassHistogramRecord) row).getObjectIds();
                    }

                    public String getOQL()
                    {
                        int clsid = ((ClassHistogramRecord) row).getClassId();
                        return "SELECT OBJECTS s FROM OBJECTS (dominators(" + objectId + ")) s where classof(s).@objectId = " + clsid; //$NON-NLS-1$ //$NON-NLS-2$
                    }
                };
            }
        };
        return result;
    }

    private void appendKeywords(Set<String> keywords, StringBuilder builder)
    {
        String title = Messages.LeakHunterQuery_Keywords;
        builder.append("<p><strong>").append(title).append("</strong>"); //$NON-NLS-1$ //$NON-NLS-2$
        builder.append("</p>"); //$NON-NLS-1$
        builder.append("<ul style=\"list-style-type:none;\" title=\"").append(escapeHTMLAttribute(title)).append("\">"); //$NON-NLS-1$ //$NON-NLS-2$
        for (String s : keywords)
            builder.append("<li>").append(HTMLUtils.escapeText(s)).append("</li>"); //$NON-NLS-1$ //$NON-NLS-2$
        builder.append("</ul>"); //$NON-NLS-1$
    }

    private void appendTroubleTicketInformation(List<IObject> classloaders, StringBuilder builder)
                    throws SnapshotException
    {
        for (ITroubleTicketResolver resolver : TroubleTicketResolverRegistry.instance().delegates())
        {
            Map<String, String> mapping = getTroubleTicketMapping(resolver, classloaders);

            if (!mapping.isEmpty())
            {
                String title = resolver.getTicketSystem();
                builder.append("<p><strong>").append(HTMLUtils.escapeText(title)).append("</strong>"); //$NON-NLS-1$ //$NON-NLS-2$
                builder.append("</p>"); //$NON-NLS-1$
                builder.append("<ul style=\"list-style-type:none;\" title=\"").append(escapeHTMLAttribute(title)).append("\">"); //$NON-NLS-1$ //$NON-NLS-2$
                for (Map.Entry<String, String> entry : mapping.entrySet())
                {
                    builder.append("<li>").append( //$NON-NLS-1$
                                    MessageUtil.format(Messages.LeakHunterQuery_TicketForSuspect, HTMLUtils.escapeText(entry.getKey()), HTMLUtils.escapeText(entry
                                                    .getValue()))).append("</li>"); //$NON-NLS-1$
                }
                builder.append("</ul>"); //$NON-NLS-1$
            }
        }
    }

    private ThreadInfoQuery.Result extractThreadData(SuspectRecord suspect, Set<String> keywords,
                    List<IObject> involvedClassloaders, StringBuilder builder, TextResult textResult)
    {
        final int threadId = suspect.getSuspect().getObjectId();
        ThreadInfoQuery.Result threadDetails = null;

        try
        {
            threadDetails = (ThreadInfoQuery.Result) SnapshotQuery.lookup("thread_details", snapshot) //$NON-NLS-1$
                            .setArgument("threadIds", threadId) //$NON-NLS-1$
                            .execute(listener);

            // append overview & keywords
            IThreadInfo threadInfo = threadDetails.getThreads().get(0);
            keywords.addAll(threadInfo.getKeywords());

            CompositeResult requestInfos = threadInfo.getRequests();
            if (requestInfos != null && !requestInfos.isEmpty())
            {
                builder.append("<p>"); //$NON-NLS-1$
                builder.append("</p>"); //$NON-NLS-1$
                builder.append("<ul style=\"list-style-type:none;\">"); //$NON-NLS-1$
                for (CompositeResult.Entry requestInfo : requestInfos.getResultEntries())
                    builder.append("<li>").append(HTMLUtils.escapeText(requestInfo.getName())).append(" ").append( //$NON-NLS-1$ //$NON-NLS-2$
                                                    textResult.linkTo(Messages.LeakHunterQuery_RequestDetails,
                                                                    requestInfo.getResult())).append("</li>"); //$NON-NLS-1$
                builder.append("</ul>"); //$NON-NLS-1$
            }

            // Add stacktrace information if available
            // TODO may be the stack result should be moved to IThreadInfo
            IThreadStack stack = snapshot.getThreadStack(threadId);
            if (stack != null)
            {
                // Find the local variables involved in the path to the accumulation point
                final SetInt locals = new SetInt();
                IObject acc = suspect.getAccumulationPoint() != null ? suspect.getAccumulationPoint().getObject()
                                : null;
                if (acc != null)
                {
                    IResultTree tree = (IResultTree) SnapshotQuery.lookup("merge_shortest_paths", snapshot) //$NON-NLS-1$
                                    .setArgument("objects", acc) //$NON-NLS-1$
                                    .execute(listener);
                    for (Object row : tree.getElements())
                    {
                        IContextObject co = tree.getContext(row);
                        if (co.getObjectId() == threadId)
                        {
                            for (Object row2 : tree.getChildren(row))
                            {
                                IContextObject co2 = tree.getContext(row2);
                                int o2 = co2.getObjectId();
                                if (o2 >= 0)
                                {
                                    /*
                                     * Stack frames as objects?
                                     * If so, then find the variables in those frames.
                                     */
                                    if (isStackFrame(o2))
                                    {
                                        // So find the actual variable in the frame
                                        for (Object row3 : tree.getChildren(row2))
                                        {
                                            IContextObject co3 = tree.getContext(row3);
                                            int o3 = co3.getObjectId();
                                            if (o3 >= 0)
                                                locals.add(o3);
                                        }
                                        // Allow for accumulation point being the frame itself
                                        if (o2 == acc.getObjectId())
                                            locals.add(o2);
                                    }
                                    else
                                    {
                                        locals.add(o2);
                                    }
                                }
                            }
                        }
                    }
                }

                StringBuilder stackBuilder = new StringBuilder();
                IObject threadObject = snapshot.getObject(threadId);
                String threadName = threadObject.getClassSpecificName();
                if (threadName == null)
                    threadName = threadObject.getTechnicalName();
                stackBuilder.append(threadName).append("\r\n"); //$NON-NLS-1$
                // Have some locals already been identifier as significant?
                boolean foundLocals = !locals.isEmpty();
                // Is one variable a significant part of the suspect
                long significantLocal = (long)(0.1 * suspect.getSuspectRetained());
                // Are several variables from a frame that significant together
                long significantFrame = (long)(0.25 * suspect.getSuspectRetained());
                List<Map<String,SetInt>> involvedFrames = new ArrayList<>();
                for (IStackFrame frame : stack.getStackFrames())
                {
                    boolean involved = false;
                    SetInt frameLocals = new SetInt();
                    for (int l : frame.getLocalObjectsIds())
                    {
                        if (!foundLocals)
                        {
                            /*
                             * We didn't find a local on the accumulation point path, so
                             * just look for big locals.
                             */
                            if (snapshot.getRetainedHeapSize(l) > significantLocal)
                            {
                                int dom = snapshot.getImmediateDominatorId(l);
                                // Also allow for stack frames as objects
                                if (dom == threadId || dom >= 0 && snapshot.getImmediateDominatorId(dom) == threadId)
                                {
                                    locals.add(l);
                                    frameLocals.add(l);
                                    involved = true;
                                }
                            }
                        }
                        else if (locals.contains(l))
                        {
                            frameLocals.add(l);
                            involved = true;
                        }
                    }
                    if (!involved && !foundLocals)
                    {
                        /*
                         * Check whether several variables in this frame
                         * dominated by the thread together retain a lot.
                         */
                        SetInt dominated = new SetInt();
                        for (int l : frame.getLocalObjectsIds())
                        {
                            int dom = snapshot.getImmediateDominatorId(l);
                            // Also allow for stack frames as objects
                            if (dom == threadId || dom >= 0 && snapshot.getImmediateDominatorId(dom) == threadId)
                            {
                                dominated.add(l);
                            }
                        }
                        int doms[] = dominated.toArray();
                        long domsize = snapshot.getMinRetainedSize(doms, listener);
                        if (domsize > significantFrame)
                        {
                            int doms1[] = doms;
                            /*
                             * Eliminate variables until we lose no more than 20% of the size.
                             */
                            long significantFrame1 = (long)(0.8 * domsize);
                            do
                            {
                                doms = doms1;
                                long dombest = 0;
                                doms1 = null;
                                // Try removing each local
                                for (int i = 0; i < doms.length; ++i)
                                {
                                    // Remove a local
                                    int doms2[] = new int[doms.length - 1];
                                    for (int j = 0; j < doms2.length; ++j)
                                    {
                                        doms2[j] = doms[j >= i ? j + 1 : j];
                                    }
                                    // See the retained size now
                                    long domsize1 = snapshot.getMinRetainedSize(doms2, listener);
                                    if (domsize1 > dombest && domsize1 > significantFrame1)
                                    {
                                        doms1 = doms2;
                                        dombest = domsize1;
                                    }
                                }
                            } while (doms1 != null);
                            for (int l : doms)
                            {
                                locals.add(l);
                                frameLocals.add(l);
                            }
                            involved = true;
                        }
                    }
                    stackBuilder.append("  ").append(frame.getText()).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
                    if (involved)
                    {
                        /*
                         * Store details about the involved frame
                         * so we can filter out JDK frames later
                         */
                        String frameText = frame.getText();
                        String p[] = frameText.split("\\s+", 2); //$NON-NLS-1$
                        Map <String,SetInt>m = new HashMap<>();
                        m.put(p.length > 1 ? p[1] : "", frameLocals); //$NON-NLS-1$
                        involvedFrames.add(m);
                    }
                }
                if (involvedFrames.size() > 0)
                {
                    // Find the top skipped frames e.g. JDK
                    int skipped = 0;
                    for (skipped = 0; skipped < involvedFrames.size(); ++skipped)
                    {
                        String frameName = involvedFrames.get(skipped).keySet().iterator().next();
                        if (!skipPattern.matcher(frameName).matches())
                            break;
                    }
                    // Remove duplicated variables from skipped frames
                    for (int i = skipped; i < involvedFrames.size(); ++i)
                    {
                        SetInt vars = involvedFrames.get(i).values().iterator().next();
                        for (int j = 0; j < skipped; ++j)
                        {
                            SetInt vars2 = involvedFrames.get(j).values().iterator().next();
                            for (int v : vars.toArray())
                            {
                                vars2.remove(v);
                            }
                        }
                    }
                    builder.append("<p>").append(Messages.LeakHunterQuery_SignificantStackFrames).append("</p><ul>");  //$NON-NLS-1$//$NON-NLS-2$
                    for (int i = 0; i < involvedFrames.size(); ++i)
                    {
                        SetInt frameLocals = involvedFrames.get(i).values().iterator().next();
                        if (frameLocals.isEmpty())
                            continue;
                        String frameName = involvedFrames.get(i).keySet().iterator().next();
                        String p[] = frameName.split("\\s+", 2); //$NON-NLS-1$
                        keywords.add(p[0]);
                        // Identify the class for the frame and add it
                        String className = p[0];
                        int firstParen = className.indexOf('(');
                        int lastDot = className.lastIndexOf('.', firstParen >= 0 ? firstParen : Integer.MAX_VALUE);
                        if (lastDot > 0)
                        {
                            className = className.substring(0, lastDot);
                            Collection<IClass> clss = snapshot.getClassesByName(className, false);
                            if (clss != null && clss.size() == 1)
                            {
                                involvedClassloaders.add(clss.iterator().next());
                            }
                        }
                        // Extract the source file
                        if (p.length > 1)
                        {
                            // (MyClass.java(Compiled Code))
                            // Remove parentheses and (Compiled Code) or (Native Method)
                            int end = p[1].indexOf('(', 1);
                            if (end < 0)
                                end = p[1].length() - 1;
                            keywords.add(p[1].substring(1, end));
                        }
                        // Add the frame and the interesting locals to the list
                        builder.append("<li>"); //$NON-NLS-1$
                        builder.append(HTMLUtils.escapeText(p[0]));
                        if (p.length > 1)
                        {
                            builder.append(' ').append(HTMLUtils.escapeText(p[1]));
                        }
                        builder.append("<ul>"); //$NON-NLS-1$
                        for (int v : frameLocals.toArray())
                        {
                            IObject obj = snapshot.getObject(v);
                            builder.append("<li>").append(MessageUtil.format(Messages.LeakHunterQuery_Retains, //$NON-NLS-1$
                                            HTMLUtils.escapeText(obj.getDisplayName()),
                                            formatRetainedHeap(snapshot.getRetainedHeapSize(v), totalHeap)))
                                            .append("</li>"); // $NON-NLS-1$ //$NON-NLS-1$
                        }

                        builder.append("</ul>"); //$NON-NLS-1$
                        builder.append("</li>"); //$NON-NLS-1$
                    }
                    builder.append("</ul>"); //$NON-NLS-1$
                }
                QuerySpec stackResult = new QuerySpec(Messages.LeakHunterQuery_ThreadStack, new TextResult(stackBuilder
                                .toString()));
                stackResult.setCommand("thread_details 0x" + Long.toHexString(threadObject.getObjectAddress())); //$NON-NLS-1$

                builder.append("<p>"); //$NON-NLS-1$
                builder.append(Messages.LeakHunterQuery_StackTraceAvailable).append(" ").append( //$NON-NLS-1$
                                textResult.linkTo(Messages.LeakHunterQuery_SeeStackstrace, stackResult)).append('.');

                if (!locals.isEmpty())
                {
                    RefinedResultBuilder rbuilder = SnapshotQuery.lookup("thread_overview", snapshot) //$NON-NLS-1$
                                    .setArgument("objects", suspect.getSuspect()) //$NON-NLS-1$
                                    .refine(listener);
                    final RefinedTree rt = (RefinedTree) rbuilder.build();
                    rt.setSelectionProvider(new ISelectionProvider()
                    {
                        /**
                         * Select the thread and the involved local variables.
                         */
                        public boolean isSelected(Object row)
                        {
                            IContextObject co = rt.getContext(row);
                            if (co != null && (locals.contains(co.getObjectId()) || co.getObjectId() == threadId))
                                return true;
                            // Also select the stack frame(s) containing the locals
                            return isExpanded(row);
                        }

                        /**
                         * Expand the thread and a row if it refers to an involved
                         * local variable.
                         */
                        public boolean isExpanded(Object row)
                        {
                            // Any thread should be expanded
                            if (rt.getElements().contains(row))
                                return true;
                            for (Object r2 : rt.getChildren(row))
                            {
                                // If row has a child which is a local
                                IContextObject co = rt.getContext(r2);
                                if (co != null && locals.contains(co.getObjectId()))
                                {
                                    /*
                                     * It needs to be a stack frame row though to
                                     * be expanded.
                                     */
                                    for (Object r3 : rt.getElements())
                                    {
                                        // Relies on ThreadOverviewQuery.ThreadStackFrameNode equals()
                                        if (rt.getChildren(r3).contains(row))
                                            return true;
                                    }
                                }
                            }
                            return false;
                        }
                    });
                    QuerySpec threadResult = new QuerySpec(Messages.LeakHunterQuery_ThreadStackAndLocals, rt);
                    // Make sure the whole stack trace is expanded
                    List<?> lThreads = rt.getElements();
                    // rt always has some sort of selection provider
                    if (lThreads.size() >= 1 && rt.hasChildren(lThreads.get(0)))
                    {
                        ISelectionProvider sel = rt;
                        List<?> lFrames = rt.getChildren(lThreads.get(0));
                        int limit = lFrames.size();
                        for (Object row : lFrames)
                        {
                            // Max sure all expanded stack frames are rendered in full
                            if (sel.isExpanded(row))
                                limit = Math.max(limit, rt.getChildren(row).size());
                        }
                        threadResult.set(Params.Rendering.LIMIT, String.valueOf(limit));
                    }
                    threadResult.setCommand(
                                    "thread_overview 0x" + Long.toHexString(suspect.getSuspect().getObjectAddress())); //$NON-NLS-1$
                    builder.append(" ").append( //$NON-NLS-1$
                                    textResult.linkTo(Messages.LeakHunterQuery_SeeStackstraceVars, threadResult))
                                    .append('.');
                }
                builder.append("</p>"); //$NON-NLS-1$
            }

            // add context class loader
            int contextClassloaderId = threadInfo.getContextClassLoaderId();
            if (contextClassloaderId != 0)
            {
                involvedClassloaders.add((IClassLoader) snapshot.getObject(contextClassloaderId));
            }
        }
        catch (Exception e)
        {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                            Messages.LeakHunterQuery_ErrorRetrievingRequestDetails, e);
        }

        return threadDetails;
    }

    private IResult findReferencePattern(SuspectRecordGroupOfObjects suspect) throws SnapshotException
    {
        MultiplePathsFromGCRootsClassRecord dummy = new MultiplePathsFromGCRootsClassRecord(null, -1, true, snapshot);

        Object[] allPaths = suspect.getPathsComputer().getAllPaths(listener);
        for (Object path : allPaths)
            dummy.addPath((int[]) path);

        MultiplePathsFromGCRootsClassRecord[] classRecords = dummy.nextLevel();

        int numPaths = allPaths.length;
        double factor = 0.8;
        double threshold = numPaths * factor;
        List<IClass> referencePattern = new ArrayList<IClass>();

        if (classRecords.length > 0)
        {
            Arrays.sort(classRecords, MultiplePathsFromGCRootsClassRecord.getComparatorByNumberOfReferencedObjects());
            MultiplePathsFromGCRootsClassRecord r = classRecords[0];

            while (r.getCount() > threshold)
            {
                threshold = r.getCount() * factor;
                referencePattern.add(r.getClazz());
                classRecords = r.nextLevel();
                if (classRecords == null || classRecords.length == 0)
                    break;

                Arrays.sort(classRecords,
                                MultiplePathsFromGCRootsClassRecord.getComparatorByNumberOfReferencedObjects());
                r = classRecords[0];
            }
        }

        /*
         * build the tree
         */
        int expandedClasses[] = new int[referencePattern.size()];
        for (int i = 0; i < referencePattern.size(); ++i)
        {
            expandedClasses[i] = referencePattern.get(i).getObjectId();
        }
        return MultiplePath2GCRootsQuery.create(snapshot, suspect.getPathsComputer(), expandedClasses, true, listener);

    }

    private List<CompositeResult> findCommonPathForSuspects(HashMap<Integer, List<Integer>> accPoint2ProblemNr)
                    throws SnapshotException
    {
        List<CompositeResult> result = new ArrayList<CompositeResult>(2);

        // get all accumulation point ids
        int[] objectIds = new int[accPoint2ProblemNr.size()];
        int j = 0;
        for (Integer accPointId : accPoint2ProblemNr.keySet())
            objectIds[j++] = accPointId;

        // calculate the shortest paths to all accumulation points
        // avoid weak paths
        // Unfinalized objects from J9
        // convert excludes into the required format
        Map<IClass, Set<String>> excludeMap = ExcludesConverter.convert(snapshot, excludes);

        IMultiplePathsFromGCRootsComputer comp = snapshot.getMultiplePathsFromGCRoots(objectIds, excludeMap);

        MultiplePathsFromGCRootsRecord[] records = comp.getPathsByGCRoot(listener);
        Arrays.sort(records, MultiplePathsFromGCRootsRecord.getComparatorByNumberOfReferencedObjects());

        for (MultiplePathsFromGCRootsRecord rec : records)
        {
            if (rec.getCount() < 2)
                break; // no more common paths

            // build an overview for the problems with common paths
            CompositeResult composite = new CompositeResult();
            List<Integer> problemIds = new ArrayList<Integer>(4);
            int[] referencedAccumulationPoints = rec.getReferencedObjects();
            for (int accPointId : referencedAccumulationPoints)
            {
                for (Integer problemId : accPoint2ProblemNr.get(accPointId))
                {
                    problemIds.add(problemId);
                }
            }
            Collections.sort(problemIds);

            StringBuilder overview = new StringBuilder(256);

            StringBuilder left = new StringBuilder();
            for (int k = 0; k < problemIds.size() - 1; k++)
            {
                if (left.length() > 0)
                    left.append(", "); //$NON-NLS-1$
                left.append(problemIds.get(k));
            }
            overview.append(MessageUtil.format(

            Messages.LeakHunterQuery_Msg_SuspectsRelated, left.toString(), problemIds.get(problemIds.size() - 1)));

            composite.addResult(Messages.LeakHunterQuery_Overview, new TextResult(overview.toString(), true));
            // END build overview

            // find the path which ALL acc.points share
            MultiplePathsFromGCRootsRecord parentRecord = rec;
            ArrayIntBig commonPath = new ArrayIntBig();

            while (parentRecord.getCount() == rec.getCount())
            {
                commonPath.add(parentRecord.getObjectId());

                MultiplePathsFromGCRootsRecord[] children = parentRecord.nextLevel();
                if (children == null || children.length == 0)
                    break; // reached the end

                // take the child with most paths and try again
                Arrays.sort(children, MultiplePathsFromGCRootsRecord.getComparatorByNumberOfReferencedObjects());
                parentRecord = children[0];
            }

            // provide the common path as details
            // Only show the paths for the objects in common
            IMultiplePathsFromGCRootsComputer comp2 = snapshot.getMultiplePathsFromGCRoots(referencedAccumulationPoints, excludeMap);
            QuerySpec qs = new QuerySpec(Messages.LeakHunterQuery_CommonPath, //
                                MultiplePath2GCRootsQuery.create(snapshot, comp2, commonPath.toArray(), listener));
            StringBuilder sb = new StringBuilder("merge_shortest_paths"); //$NON-NLS-1$
            for (int objId : referencedAccumulationPoints)
            {
                long addr = snapshot.mapIdToAddress(objId);
                sb.append(" 0x").append(Long.toHexString(addr)); //$NON-NLS-1$
            }
            //Currently a bug in parsing multiple command line arguments so require another
            //named argument after -excludes
            addExcludes(sb);
            sb.append(" -groupby FROM_GC_ROOTS"); //$NON-NLS-1$
            qs.setCommand(sb.toString());
            composite.addResult(qs);

            result.add(composite);
        }
        return result;
    }

    private int findReferrer(int objectId) throws SnapshotException
    {
        BitField skipped = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
        Collection<IClass> classes = snapshot.getClassesByName(skipPattern, false);
        for (IClass clazz : classes)
            for (int instance : clazz.getObjectIds())
                skipped.set(instance);

        BitField visited = new BitField(snapshot.getSnapshotInfo().getNumberOfObjects());
        LinkedList<int[]> fifo = new LinkedList<int[]>();
        fifo.add(new int[] { objectId });

        while (fifo.size() > 0)
        {
            int[] e = fifo.removeFirst();
            for (int referrer : e)
            {
                if (!visited.get(referrer))
                {
                    /*
                     * If it is a non-interesting object based on class name then look further.
                     * Threads might be named java.lang.Thread but have an interesting
                     * stack trace so do consider threads.
                     * Sometimes it isn't so clear - e.g. a java.util.concurrent.ForkJoinTask[]
                     * might be referred to from several ForkJoinWorkerThreads
                     */
                    if (skipped.get(referrer) && !isThread(referrer))
                    {
                        int[] referrers = snapshot.getInboundRefererIds(referrer);
                        fifo.add(referrers);
                        visited.set(referrer);
                    }
                    else
                    {
                        return referrer;
                    }
                }
            }
        }

        return -1;
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import java.util.HashSet;
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
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.snapshot.query.PieFactory;
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

    private final static Set<String> REFERENCE_FIELD_SET = new HashSet<String>(Arrays
                    .asList(new String[] { "referent" })); //$NON-NLS-1$

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
    public Pattern skipPattern = Pattern.compile("java.*|com\\.sun\\..*"); //$NON-NLS-1$

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
        Set<String> keywords = new HashSet<String>();
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
                        objectsForTroubleTicketInfo.add(suspectClassloader);
                        String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedBy,
                                        referrerClassloaderName));
                    }
                    else if (snapshot.isClass(referrerId))
                    {
                        referrerClassloader = snapshot.getObject(((IClass) referrer).getClassLoaderId());
//                        involvedClassloaders.add(suspectClassloader);
                        objectsForTroubleTicketInfo.add(referrer);
                        String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedByClass, className,
                                        referrerClassloaderName));
                    }
                    else
                    {
                        if (isThread(referrerId))
                        {
                            isThreadRelated = true;
                            suspectId = referrerId;
                        }
                        referrerClassloader = snapshot.getObject(referrer.getClazz().getClassLoaderId());
//                        involvedClassloaders.add(suspectClassloader);
                        objectsForTroubleTicketInfo.add(referrer);
                        String referrerClassloaderName = getClassLoaderName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedByInstance, HTMLUtils.escapeText(referrer
                                        .getDisplayName()), referrerClassloaderName));

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
        if (isThreadRelated)
        {
            threadDetails = extractThreadData(suspect, keywords, objectsForTroubleTicketInfo, overview, overviewResult);
        }
        overview.append("<br><br>"); //$NON-NLS-1$

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
        IObject describedObject = (suspect.getAccumulationPoint() != null) ? suspect.getAccumulationPoint().getObject()
                        : suspect.getSuspect();

        // add a path to the accumulation point
        try
        {
            IResult result = SnapshotQuery.lookup("path2gc", snapshot) //$NON-NLS-1$
                            .setArgument("object", describedObject) //$NON-NLS-1$
                            .execute(listener);
            QuerySpec qs = new QuerySpec(Messages.LeakHunterQuery_ShortestPaths, result);
            qs.setCommand("path2gc 0x" + Long.toHexString(describedObject.getObjectAddress())); //$NON-NLS-1$
            composite.addResult(qs);
        }
        catch (Exception e)
        {
            throw new SnapshotException(Messages.LeakHunterQuery_ErrorShortestPaths, e);
        }

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
            qs.setCommand("thread_details 0x" + Long.toHexString(suspect.getSuspect().getObjectAddress())); //$NON-NLS-1$
            composite.addResult(qs);
        }

        return composite;
    }

    private CompositeResult getLeakDescriptionGroupOfObjects(SuspectRecordGroupOfObjects suspect, IProgressListener listener)
                    throws SnapshotException
    {
        StringBuilder builder = new StringBuilder(256);
        Set<String> keywords = new HashSet<String>();
        List<IObject> involvedClassLoaders = new ArrayList<IObject>(2);

        /* get leak suspect info */
        String className = ((IClass) suspect.getSuspect()).getName();
        keywords.add(className);

        IClassLoader classloader = (IClassLoader) snapshot
                        .getObject(((IClass) suspect.getSuspect()).getClassLoaderId());
        involvedClassLoaders.add(suspect.getSuspect());

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
            builder.append("<ul>"); //$NON-NLS-1$
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
            int accumulationPointId = suspect.getAccumulationPoint().getObject().getObjectId();
            if (snapshot.isClassLoader(accumulationPointId))
            {
                involvedClassLoaders.add((IClassLoader) suspect.getAccumulationPoint().getObject());
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
                involvedClassLoaders.add(suspect.getAccumulationPoint().getObject());
                classloaderName = getClassLoaderName(accPointClassloader, keywords);

                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromClass, className,
                                classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));
            }
            else
            {
                className = suspect.getAccumulationPoint().getObject().getClazz().getName();
                keywords.add(className);

                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(suspect.getAccumulationPoint()
                                .getObject().getClazz().getClassLoaderId());
//                involvedClassLoaders.add(accPointClassloader);
                involvedClassLoaders.add(suspect.getAccumulationPoint().getObject());
              
                classloaderName = getClassLoaderName(accPointClassloader, keywords);

                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromInstance, className,
                                classloaderName, formatRetainedHeap(suspect.getAccumulationPoint().getRetainedHeapSize(), totalHeap)));
            }
        }
        builder.append("<br><br>"); //$NON-NLS-1$

        // append keywords
        appendKeywords(keywords, builder);

        // add CSN components data
        appendTroubleTicketInformation(involvedClassLoaders, builder);

        /*
         * Prepare the composite result from the different pieces
         */
        CompositeResult composite = new CompositeResult();
        composite.addResult(Messages.LeakHunterQuery_Description, new TextResult(builder.toString(), true));

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
                pie.addSlice(i, iObject.getDisplayName(), iObject.getUsedHeapSize(), iObject.getRetainedHeapSize());
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
                qs.setCommand(sb.toString());
            }
            composite.addResult(qs);
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
                    qs.setCommand("merge_shortest_paths SELECT * FROM " + cn + " s WHERE dominatorof(s) = null; -groupby FROM_GC_ROOTS_BY_CLASS"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                composite.addResult(qs);
            }
        }

        return composite;
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
                keywords.add(classloaderName);
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
                        return null;
                    }
                };
            }
        };
        return result;
    }

    private void appendKeywords(Set<String> keywords, StringBuilder builder)
    {
        builder.append("<b>").append(Messages.LeakHunterQuery_Keywords).append("</b><br>"); //$NON-NLS-1$ //$NON-NLS-2$
        for (String s : keywords)
            builder.append(HTMLUtils.escapeText(s)).append("<br>"); //$NON-NLS-1$
    }

    private void appendTroubleTicketInformation(List<IObject> classloaders, StringBuilder builder)
                    throws SnapshotException
    {
        for (ITroubleTicketResolver resolver : TroubleTicketResolverRegistry.instance().delegates())
        {
            Map<String, String> mapping = getTroubleTicketMapping(resolver, classloaders);

            if (!mapping.isEmpty())
            {
                builder.append("<br><b>").append(HTMLUtils.escapeText(resolver.getTicketSystem())).append("</b><br>"); //$NON-NLS-1$ //$NON-NLS-2$
                for (Map.Entry<String, String> entry : mapping.entrySet())
                {
                    builder.append(
                                    MessageUtil.format(Messages.LeakHunterQuery_TicketForSuspect, HTMLUtils.escapeText(entry.getKey()), HTMLUtils.escapeText(entry
                                                    .getValue()))).append("<br>"); //$NON-NLS-1$
                }
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

                for (CompositeResult.Entry requestInfo : requestInfos.getResultEntries())
                    builder.append(HTMLUtils.escapeText(requestInfo.getName())).append(" ").append( //$NON-NLS-1$
                                                    textResult.linkTo(Messages.LeakHunterQuery_RequestDetails,
                                                                    requestInfo.getResult())).append("<br>"); //$NON-NLS-1$

                builder.append("</p>"); //$NON-NLS-1$
            }

            // Add stacktrace information if available
            // TODO may be the stack result should be moved to IThreadInfo
            IThreadStack stack = snapshot.getThreadStack(threadId);
            if (stack != null)
            {
                // Find the local variables involved in the path to the accumulation point
                final SetInt locals = new SetInt();
                IObject acc = suspect.getAccumulationPoint().getObject();
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
                                    locals.add(o2);
                            }
                        }
                    }
                }

                StringBuilder stackBuilder = new StringBuilder();
                IObject threadObject = snapshot.getObject(threadId);
                stackBuilder.append(threadObject.getClassSpecificName()).append("\r\n"); //$NON-NLS-1$
                for (IStackFrame frame : stack.getStackFrames())
                {
                    boolean involved = false;
                    for (int l : frame.getLocalObjectsIds())
                    {
                        if (locals.contains(l))
                            involved = true;
                    }
                    stackBuilder.append("  ").append(frame.getText()).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
                    if (involved)
                    {
                        String frameText = frame.getText();
                        String p[] = frameText.split("\\s+"); //$NON-NLS-1$
                        // Extract the method
                        if (p.length > 1)
                            keywords.add(p[1]);
                        // Extract the source file
                        if (p.length > 2)
                            keywords.add(p[2].substring(1, p[2].length() - 1));
                    }
                }
                QuerySpec stackResult = new QuerySpec(Messages.LeakHunterQuery_ThreadStack, new TextResult(stackBuilder
                                .toString()));
                stackResult.setCommand("thread_details 0x" + Long.toHexString(suspect.getSuspect().getObjectAddress())); //$NON-NLS-1$

                builder.append("<p>"); //$NON-NLS-1$
                builder.append(Messages.LeakHunterQuery_StackTraceAvailable + " ").append( //$NON-NLS-1$
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
                            return false;
                        }

                        /**
                         * Expand the thread and a row if it refers to an involved
                         * local variable.
                         */
                        public boolean isExpanded(Object row)
                        {
                            if (rt.getElements().contains(row))
                                return true;
                            for (Object r2 : rt.getChildren(row))
                            {
                                // if row is a stack frame row
                                IContextObject co = rt.getContext(r2);
                                if (co != null && locals.contains(co.getObjectId()))
                                { return true; }
                            }
                            return false;
                        }
                    });
                    QuerySpec threadResult = new QuerySpec(Messages.LeakHunterQuery_ThreadStackAndLocals, rt);
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
        Map<IClass, Set<String>> excludeMap = new HashMap<IClass, Set<String>>();
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.ref.WeakReference", true); //$NON-NLS-1$
        if (classes != null)
            for (IClass clazz : classes)
            {
                excludeMap.put(clazz, REFERENCE_FIELD_SET);
            }

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
            QuerySpec qs = new QuerySpec(Messages.LeakHunterQuery_CommonPath, //
                                MultiplePath2GCRootsQuery.create(snapshot, comp, commonPath.toArray(), listener));
            StringBuilder sb = new StringBuilder("merge_shortest_paths"); //$NON-NLS-1$
            //Currently a bug in parsing multiple command line arguments so require another
            //named argument after -excludes
            sb.append(" -excludes java.lang.ref.WeakReference:"); //$NON-NLS-1$
            sb.append("referent"); //$NON-NLS-1$
            sb.append(" -excludes java.lang.ref.Finalizer:"); //$NON-NLS-1$
            sb.append("referent"); //$NON-NLS-1$
            sb.append(" -excludes java.lang.Runtime:"); //$NON-NLS-1$
            sb.append("<" + GCRootInfo.getTypeAsString(GCRootInfo.Type.UNFINALIZED) + ">"); //$NON-NLS-1$ //$NON-NLS-2$
            sb.append(" -groupby FROM_GC_ROOTS"); //$NON-NLS-1$
            for (int objId : objectIds)
            {
                long addr = snapshot.mapIdToAddress(objId);
                sb.append(" 0x").append(Long.toHexString(addr)); //$NON-NLS-1$
            }
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
                    if (skipped.get(referrer))
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

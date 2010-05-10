/*******************************************************************************
 * Copyright (c) 2008, 2010 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
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
import org.eclipse.mat.inspections.FindLeaksQuery.AccumulationPoint;
import org.eclipse.mat.inspections.FindLeaksQuery.SuspectRecord;
import org.eclipse.mat.inspections.FindLeaksQuery.SuspectRecordGroupOfObjects;
import org.eclipse.mat.inspections.threads.ThreadInfoQuery;
import org.eclipse.mat.inspections.util.ObjectTreeFactory;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.snapshot.inspections.MultiplePath2GCRootsQuery;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Argument.Advice;
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
import org.eclipse.mat.snapshot.query.PieFactory;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.snapshot.registry.TroubleTicketResolverRegistry;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;

@CommandName("leakhunter")
public class LeakHunterQuery implements IQuery
{

    private final static Set<String> REFERENCE_FIELD_SET = new HashSet<String>(Arrays
                    .asList(new String[] { "referent" })); //$NON-NLS-1$

    static final String SYSTEM_CLASSLOADER = Messages.LeakHunterQuery_SystemClassLoader;

    static NumberFormat percentFormatter = new DecimalFormat("0.00%"); //$NON-NLS-1$
    static NumberFormat numberFormatter = NumberFormat.getNumberInstance();

    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false)
    public int threshold_percent = 10;

    @Argument(isMandatory = false)
    public int max_paths = 10000;

    @Argument(isMandatory = false, advice = Advice.CLASS_NAME_PATTERN, flag = "skip")
    public Pattern skipPattern = Pattern.compile("java.*|com\\.sun\\..*"); //$NON-NLS-1$

    private long totalHeap;

    private final IProgressListener voidListener = new VoidProgressListener();
    private IProgressListener listener;

    public IResult execute(IProgressListener listener) throws Exception
    {
        this.listener = listener;
        totalHeap = snapshot.getSnapshotInfo().getUsedHeapSize();

        /* call find_leaks */
        listener.subTask(Messages.LeakHunterQuery_FindingProblemSuspects);
        FindLeaksQuery.SuspectsResultTable findLeaksResult = callFindLeaks(voidListener);
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
                                rec.suspectRetained);
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

    private FindLeaksQuery.SuspectsResultTable callFindLeaks(IProgressListener listener) throws Exception
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
            return getLeakDescriptionGroupOfObjects((SuspectRecordGroupOfObjects) suspect);
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
                            suspect.getSuspect().getDisplayName(), //
                            formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));
            overview.append("</p>"); //$NON-NLS-1$
        }
        else if (snapshot.isClassLoader(suspectId))
        {
            IClassLoader suspectClassloader = (IClassLoader) suspect.getSuspect();
            objectsForTroubleTicketInfo.add(suspectClassloader);

            String classloaderName = getClassloarerName(suspectClassloader, keywords);

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

            String classloaderName = getClassloarerName(suspectClassloader, keywords);

            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Class, //
                            className, classloaderName, formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));
        }
        else
        {
            String className = suspect.getSuspect().getClazz().getName();
            keywords.add(className);

            IClassLoader suspectClassloader = (IClassLoader) snapshot.getObject(suspect.getSuspect().getClazz()
                            .getClassLoaderId());
//            involvedClassloaders.add(suspectClassloader);
            objectsForTroubleTicketInfo.add(suspect.getSuspect());

            String classloaderName = getClassloarerName(suspectClassloader, keywords);

            overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_Instance, //
                            className, classloaderName, formatRetainedHeap(suspect.getSuspectRetained(), totalHeap)));

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
                        String referrerClassloaderName = getClassloarerName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedBy,
                                        referrerClassloaderName));
                    }
                    else if (snapshot.isClass(referrerId))
                    {
                        referrerClassloader = snapshot.getObject(((IClass) referrer).getClassLoaderId());
//                        involvedClassloaders.add(suspectClassloader);
                        objectsForTroubleTicketInfo.add(referrer);
                        String referrerClassloaderName = getClassloarerName(referrerClassloader, keywords);
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
                        String referrerClassloaderName = getClassloarerName(referrerClassloader, keywords);
                        overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedByInstance, referrer
                                        .getDisplayName(), referrerClassloaderName));

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

                String classloaderName = getClassloarerName(accPointClassloader, keywords);
                overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_AccumulatedBy, classloaderName));

            }
            else if (snapshot.isClass(accumulationPointId))
            {
                IClass clazz = (IClass) accumulationObject;
                keywords.add(clazz.getName());
                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(clazz.getClassLoaderId());
//                involvedClassloaders.add(accPointClassloader);
                objectsForTroubleTicketInfo.add(accumulationObject);

                String classloaderName = getClassloarerName(accPointClassloader, keywords);

                overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_AccumulatedByLoadedBy, clazz.getName(),
                                classloaderName));
            }
            else
            {
                String className = accumulationObject.getClazz().getName();
                keywords.add(className);

                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(accumulationObject.getClazz()
                                .getClassLoaderId());
//                involvedClassloaders.add(accPointClassloader);
                objectsForTroubleTicketInfo.add(accumulationObject);

                String classloaderName = getClassloarerName(accPointClassloader, keywords);
                overview.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_AccumulatedByInstance, className,
                                classloaderName));
            }
        }

        /* extract request information for thread related problems */
        ThreadInfoQuery.Result threadDetails = null;
        if (isThreadRelated)
        {
            threadDetails = extractThreadData(suspectId, keywords, objectsForTroubleTicketInfo, overview, overviewResult);
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
        IObject describedObject = (suspect.getAccumulationPoint() != null) ? suspect.getAccumulationPoint().getObject()
                        : suspect.getSuspect();

        // add a path to the accumulation point
        try
        {
            IResult result = SnapshotQuery.lookup("path2gc", snapshot) //$NON-NLS-1$
                            .set("object", describedObject) //$NON-NLS-1$
                            .execute(listener);
            composite.addResult(Messages.LeakHunterQuery_ShortestPaths, result);
        }
        catch (Exception e)
        {
            throw new SnapshotException(Messages.LeakHunterQuery_ErrorShortestPaths, e);
        }

        // show the acc. point in the dominator tree
        IResult objectInDominatorTree = showInDominatorTree(describedObject.getObjectId());
        composite.addResult(Messages.LeakHunterQuery_AccumulatedObjects, objectInDominatorTree);

        // add histogram of dominated.
        IResult histogramOfDominated = getHistogramOfDominated(describedObject.getObjectId());
        if (histogramOfDominated != null)
        {
            composite.addResult(Messages.LeakHunterQuery_AccumulatedObjectsByClass, histogramOfDominated);
        }

        if (threadDetails != null)
            composite.addResult(Messages.LeakHunterQuery_ThreadDetails, threadDetails);

        return composite;
    }

    private CompositeResult getLeakDescriptionGroupOfObjects(SuspectRecordGroupOfObjects suspect)
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

        String classloaderName = getClassloarerName(classloader, keywords);

        String numberOfInstances = numberFormatter.format(suspect.getSuspectInstances().length);
        builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_InstancesOccupy, numberOfInstances, className,
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
            builder.append("<ul>"); //$NON-NLS-1$
            for (IObject inst : bigSuspectInstances)
            {
                builder.append("<li>").append(inst.getDisplayName()); //$NON-NLS-1$
                builder.append("&nbsp;-&nbsp;") //$NON-NLS-1$
                                .append(
                                                MessageUtil.format(Messages.LeakHunterQuery_Msg_Bytes,
                                                                formatRetainedHeap(inst.getRetainedHeapSize(),
                                                                                totalHeap)));
            }
            builder.append("</ul>"); //$NON-NLS-1$
            builder.append("</p>"); //$NON-NLS-1$
        }

        /* get accumulation point info */
        if (suspect.getAccumulationPoint() != null)
        {
            int accumulationPointId = suspect.getAccumulationPoint().getObject().getObjectId();
            if (snapshot.isClassLoader(accumulationPointId))
            {
                involvedClassLoaders.add((IClassLoader) suspect.getAccumulationPoint().getObject());
                classloaderName = getClassloarerName(suspect.getAccumulationPoint().getObject(), keywords);
                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromClassLoader,
                                classloaderName));
            }
            else if (snapshot.isClass(accumulationPointId))
            {
                className = ((IClass) suspect.getAccumulationPoint().getObject()).getName();
                keywords.add(className);

                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(((IClass) suspect
                                .getAccumulationPoint().getObject()).getClassLoaderId());
//                involvedClassLoaders.add(accPointClassloader);
                involvedClassLoaders.add(suspect.getAccumulationPoint().getObject());
                classloaderName = getClassloarerName(accPointClassloader, keywords);

                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromClass, className,
                                classloaderName));
            }
            else
            {
                className = suspect.getAccumulationPoint().getObject().getClazz().getName();
                keywords.add(className);

                IClassLoader accPointClassloader = (IClassLoader) snapshot.getObject(suspect.getAccumulationPoint()
                                .getObject().getClazz().getClassLoaderId());
//                involvedClassLoaders.add(accPointClassloader);
                involvedClassLoaders.add(suspect.getAccumulationPoint().getObject());
              
                classloaderName = getClassloarerName(accPointClassloader, keywords);

                builder.append(MessageUtil.format(Messages.LeakHunterQuery_Msg_ReferencedFromInstance, className,
                                classloaderName));
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

        AccumulationPoint accPoint = suspect.getAccumulationPoint();
        if (accPoint != null)
        {
            composite.addResult(Messages.LeakHunterQuery_CommonPath, //
                            MultiplePath2GCRootsQuery.create(snapshot, suspect.getPathsComputer(), suspect
                                            .getCommonPath()));
        }
        else
        {
            IResult result = findReferencePattern(suspect);
            if (result != null)
                composite.addResult(Messages.LeakHunterQuery_ReferencePattern, result);
        }

        return composite;
    }

    private String formatRetainedHeap(long retained, long totalHeap)
    {
        return numberFormatter.format(retained) + " (" //$NON-NLS-1$
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
				ticket = resolver.resolveByClassLoader((IClassLoader) suspect, new VoidProgressListener());
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

    private String getClassloarerName(IObject classloader, Set<String> keywords)
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
            return classloaderName;
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
        Histogram h = snapshot.getHistogram(dominatedByAccPoint, voidListener);
        ClassHistogramRecord[] records = h.getClassHistogramRecords().toArray(new ClassHistogramRecord[0]);

        for (ClassHistogramRecord record : records)
        {
            record.setRetainedHeapSize(snapshot.getMinRetainedSize(record.getObjectIds(), voidListener));
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
                return new IContextObject()
                {

                    public int getObjectId()
                    {
                        return ((ClassHistogramRecord) row).getClassId();
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
            builder.append(s).append("<br>"); //$NON-NLS-1$
    }

    private void appendTroubleTicketInformation(List<IObject> classloaders, StringBuilder builder)
                    throws SnapshotException
    {
        for (ITroubleTicketResolver resolver : TroubleTicketResolverRegistry.instance().delegates())
        {
            Map<String, String> mapping = getTroubleTicketMapping(resolver, classloaders);

            if (!mapping.isEmpty())
            {
                builder.append("<p><b>").append(resolver.getTicketSystem()).append("</b><br>"); //$NON-NLS-1$ //$NON-NLS-2$
                for (Map.Entry<String, String> entry : mapping.entrySet())
                {
                    builder.append(
                                    MessageUtil.format(Messages.LeakHunterQuery_TicketForSuspect, entry.getKey(), entry
                                                    .getValue())).append("<br>"); //$NON-NLS-1$
                }
                builder.append("</p>"); //$NON-NLS-1$
            }
        }
    }

    private ThreadInfoQuery.Result extractThreadData(int threadId, Set<String> keywords,
                    List<IObject> involvedClassloaders, StringBuilder builder, TextResult textResult)
    {
        ThreadInfoQuery.Result threadDetails = null;

        try
        {
            threadDetails = (ThreadInfoQuery.Result) SnapshotQuery.lookup("thread_details", snapshot) //$NON-NLS-1$
                            .set("threadIds", threadId) //$NON-NLS-1$
                            .execute(listener);

            // append overview & keywords
            IThreadInfo threadInfo = threadDetails.getThreads().get(0);
            keywords.addAll(threadInfo.getKeywords());

            CompositeResult requestInfos = threadInfo.getRequests();
            if (requestInfos != null && !requestInfos.isEmpty())
            {
                builder.append("<p>"); //$NON-NLS-1$

                for (CompositeResult.Entry requestInfo : requestInfos.getResultEntries())
                    builder.append(requestInfo.getName()).append(" ").append( //$NON-NLS-1$
                                                    textResult.linkTo(Messages.LeakHunterQuery_RequestDetails,
                                                                    requestInfo.getResult())).append("<br>"); //$NON-NLS-1$

                builder.append("</p>"); //$NON-NLS-1$
            }

            // Add stacktrace information if available
            // TODO may be the stack result should be moved to IThreadInfo
            IThreadStack stack = snapshot.getThreadStack(threadId);
            if (stack != null)
            {
                StringBuilder stackBuilder = new StringBuilder();
                IObject threadObject = snapshot.getObject(threadId);
                stackBuilder.append(threadObject.getClassSpecificName()).append("\r\n"); //$NON-NLS-1$
                for (IStackFrame frame : stack.getStackFrames())
                {
                    stackBuilder.append("  ").append(frame.getText()).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                QuerySpec stackResult = new QuerySpec(Messages.LeakHunterQuery_ThreadStack, new TextResult(stackBuilder
                                .toString()));

                builder.append("<p>"); //$NON-NLS-1$
                builder.append(Messages.LeakHunterQuery_StackTraceAvailable + " ").append( //$NON-NLS-1$
                                textResult.linkTo(Messages.LeakHunterQuery_SeeStackstrace, stackResult)).append('.');
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

        Object[] allPaths = suspect.getPathsComputer().getAllPaths(voidListener);
        for (Object path : allPaths)
            dummy.addPath((int[]) path);

        MultiplePathsFromGCRootsClassRecord[] classRecords = dummy.nextLevel();

        int numPaths = allPaths.length;
        double threshold = numPaths * 0.9;
        List<IClass> referencePattern = new ArrayList<IClass>();

        Arrays.sort(classRecords, MultiplePathsFromGCRootsClassRecord.getComparatorByNumberOfReferencedObjects());
        MultiplePathsFromGCRootsClassRecord r = classRecords[0];

        while (r.getCount() > threshold)
        {
            referencePattern.add(r.getClazz());
            classRecords = r.nextLevel();
            if (classRecords == null || classRecords.length == 0)
                break;

            Arrays.sort(classRecords, MultiplePathsFromGCRootsClassRecord.getComparatorByNumberOfReferencedObjects());
            r = classRecords[0];
        }

        if (referencePattern.isEmpty())
            return null;

        /*
         * build the tree
         */
        ObjectTreeFactory.TreePathBuilder treeBuilder = new ObjectTreeFactory.TreePathBuilder(snapshot
                        .getSnapshotInfo().getUsedHeapSize());
        treeBuilder.addBranch(referencePattern.get(0).getObjectId());

        for (int i = 1; i < referencePattern.size(); i++)
        {
            treeBuilder.addChild(referencePattern.get(i).getObjectId(), false);
        }

        return treeBuilder.build(snapshot);

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

        MultiplePathsFromGCRootsRecord[] records = comp.getPathsByGCRoot(voidListener);
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
            composite.addResult(Messages.LeakHunterQuery_CommonPath, //
                            MultiplePath2GCRootsQuery.create(snapshot, comp, commonPath.toArray()));

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

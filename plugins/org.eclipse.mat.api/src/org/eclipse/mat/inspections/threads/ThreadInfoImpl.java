/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG & IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - optional columns
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;
import org.eclipse.mat.snapshot.registry.RequestDetailResolverRegistry;
import org.eclipse.mat.snapshot.registry.ThreadDetailResolverRegistry;
import org.eclipse.mat.util.IProgressListener;

/* package */class ThreadInfoImpl implements IThreadInfo
{
    // //////////////////////////////////////////////////////////////
    // column meta-data
    // //////////////////////////////////////////////////////////////

    public static final Column COL_CLASSNAME = new Column(Messages.Column_ClassName);
    public static final Column COL_NAME = new Column(Messages.ThreadInfoImpl_Column_Name);
    public static final Column COL_INSTANCE = new Column(Messages.ThreadStackQuery_Column_ObjectStackFrame);
    public static final Column COL_SHALLOW = new Column(Messages.Column_ShallowHeap, Bytes.class);
    public static final Column COL_RETAINED = new Column(Messages.Column_RetainedHeap, Bytes.class);
    public static final Column COL_CONTEXTCL = new Column(Messages.ThreadInfoImpl_Column_ContextClassLoader);
    public static final Column COL_ISDAEMON = new Column(Messages.ThreadInfoImpl_Column_IsDaemon, Boolean.class);
    public static final Column COL_MAXLOCALRETAINED = new Column(Messages.ThreadInfoImpl_Column_MaxLocalRetainedHeap, Bytes.class).noTotals();

    private static final List<Column> defaultColumns = Arrays.asList(new Column[] {
                    //COL_CLASSNAME, //
                    COL_INSTANCE, //
                    COL_NAME, //
                    COL_SHALLOW, //
                    COL_RETAINED, //
                    COL_MAXLOCALRETAINED, //
                    COL_CONTEXTCL,
                    COL_ISDAEMON});

    // //////////////////////////////////////////////////////////////
    // factory methods
    // //////////////////////////////////////////////////////////////

    /* package */static ThreadInfoImpl build(IObject thread, boolean readFully, IProgressListener listener)
                    throws SnapshotException
    {
        ThreadInfoImpl info = new ThreadInfoImpl();
        info.subject = thread;

        extractGeneralAttributes(info);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        extractFromDetailsResolver(info, readFully, listener);

        if (readFully)
            extractFromRequestResolver(info, listener);

        return info;
    }

    private static void extractFromRequestResolver(ThreadInfoImpl info, IProgressListener listener)
                    throws SnapshotException
    {
        ISnapshot snapshot = info.subject.getSnapshot();

        int[] localVars = getLocalVarsForThread(info.subject);
        for (int localId : localVars)
        {
            IClass clazz = snapshot.getClassOf(localId);
            while (clazz != null)
            {
                IRequestDetailsResolver resolver = RequestDetailResolverRegistry.instance().lookup(clazz.getName());
                if (resolver != null)
                {
                    resolver.complement(snapshot, info, localVars, localId, listener);
                    break;
                }
                clazz = clazz.getSuperClass();
            }
        }
    }

    private static int[] getLocalVarsForThread(IObject thread) throws SnapshotException
    {
        List<NamedReference> refs = thread.getOutboundReferences();
        ArrayInt result = new ArrayInt();
        for (NamedReference ref : refs)
        {
            if (ref instanceof ThreadToLocalReference)
            {
                result.add(ref.getObjectId());
                ThreadToLocalReference tlr = (ThreadToLocalReference)ref;
                for (GCRootInfo gr : tlr.getGcRootInfo())
                {
                    // Allow for stack frames as pseudo-objects
                    if (gr.getType() == GCRootInfo.Type.JAVA_STACK_FRAME)
                    {
                        List<NamedReference> refs2 = ref.getObject().getOutboundReferences();
                        for (NamedReference ref2 : refs2)
                        {
                            if (ref2 instanceof ThreadToLocalReference)
                            {
                                result.add(ref2.getObjectId());
                            }
                        }
                    }
                }
            }
        }
        return result.toArray();
    }

    private static void extractGeneralAttributes(ThreadInfoImpl info) throws SnapshotException
    {
        info.className = info.subject.getDisplayName();
        info.name = info.subject.getClassSpecificName();
        info.instance = info.subject.getTechnicalName();
        info.shallowHeap = new Bytes(info.subject.getUsedHeapSize());
        info.retainedHeap = new Bytes(info.subject.getRetainedHeapSize());
        info.isDaemon = resolveIsDaemon(info.subject);

        IObject contextClassLoader = resolveContextClassLoader(info);
        if (contextClassLoader != null)
        {
            info.contextClassLoader = contextClassLoader.getClassSpecificName();
            if (info.contextClassLoader == null)
                info.contextClassLoader = contextClassLoader.getTechnicalName();
            info.contextClassLoaderId = contextClassLoader.getObjectId();
        }
    }

    private static IObject resolveContextClassLoader(ThreadInfoImpl info) throws SnapshotException
    {
        try
        {
            IObject contextClassLoader = (IObject) info.subject.resolveValue("contextClassLoader"); //$NON-NLS-1$
            return contextClassLoader;
        }
        catch (SnapshotException e)
        {
            // Failing to get context class loader is not the end of the world
        }
        return null;
    }

    private static Boolean resolveIsDaemon(IObject thread)
    {
        try
        {
            Object daemon = thread.resolveValue("daemon"); //$NON-NLS-1$
            if (daemon == null) {
                daemon = thread.resolveValue("isDaemon"); //$NON-NLS-1$
            }
            if (daemon != null) {
                if (daemon instanceof Boolean) {
                    return (Boolean)daemon;
                }
            }
        }
        catch (SnapshotException e)
        {
            // Failing to get daemon status is not the end of the world
        }
        return null;
    }

    private static void extractFromDetailsResolver(ThreadInfoImpl info, boolean readFully, IProgressListener listener)
                    throws SnapshotException
    {
        for (IThreadDetailsResolver resolver : ThreadDetailResolverRegistry.instance().delegates())
        {
            if (readFully)
                resolver.complementDeep(info, listener);
            else
                resolver.complementShallow(info, listener);
        }
    }

    /* package */List<Column> getUsedColumns()
    {
        List<Column> answer = getDefaultColumnsCopy();
        // Used sorted list as otherwise the column order can change
        for (IThreadDetailsResolver resolver : sortedResolvers())
        {
            Column[] cols = resolver.getColumns();
            if (cols != null)
                for (int ii = 0; ii < cols.length; ii++)
                {
                    if (properties.containsKey(cols[ii]))
                        if (!answer.contains(cols[ii]))
                            answer.add(cols[ii]);
                }
        }

        return answer;
    }

    /* package */static List<Column> getUsedColumns(List<ThreadInfoImpl> threads)
    {
        List<Column> answer = getDefaultColumnsCopy();
        // Used sorted list as otherwise the column order can change
        for (IThreadDetailsResolver resolver : sortedResolvers())
        {
            Column[] cols = resolver.getColumns();
            if (cols != null)
                for (int ii = 0; ii < cols.length; ii++)
                {
                    for (ThreadInfoImpl thread : threads)
                        if (thread.properties.containsKey(cols[ii]))
                        {
                            if (!answer.contains(cols[ii]))
                            {
                                answer.add(cols[ii]);
                                break;
                            }
                        }
                }
        }

        return answer;
    }

    private static List<Column> getDefaultColumnsCopy()
    {
        List<Column> answer = new ArrayList<Column>();
        // Return copy of columns so independent and columns don't permanently
        // retain decorators
        for (Column col : defaultColumns)
        {
            // Copy everything that's used in Column.equals
            Column col2 = new Column(col.getLabel(), col.getType(), col.getAlign(), col.getSortDirection(),
                            col.getFormatter(), col.getComparator());
            if (!col.getCalculateTotals())
            {
                col2.noTotals();
            }
            answer.add(col2);
        }
        return answer;
    }

    /**
     * Return a list of resolvers in a reproducible order.
     * @return the sorted list
     */
    private static List<IThreadDetailsResolver> sortedResolvers()
    {
        Collection<IThreadDetailsResolver> delegates = ThreadDetailResolverRegistry.instance().delegates();
        List<IThreadDetailsResolver> delegates2 = new ArrayList<IThreadDetailsResolver>(delegates);
        Collections.sort(delegates2, new CompResolver());
        return delegates2;
    }

    /**
     * Compare two resolvers.
     * Tries to get an api resolver first, then dtfj then hprof.
     */
    private static final class CompResolver implements Comparator<IThreadDetailsResolver>, Serializable
    {
        @Override
        public int compare(IThreadDetailsResolver o1, IThreadDetailsResolver o2)
        {
            ClassLoader l1 = o1.getClass().getClassLoader();
            ClassLoader l2 = o2.getClass().getClassLoader();
            if (l1 == null)
                if (l2 != null)
                    return -1;
                else
                    return 0;
            if (l2 == null)
                return 1;
            // Equinox loaders don't have a name but have a toString like:
            // org.eclipse.osgi.internal.loader.EquinoxClassLoader@31e233a3[org.eclipse.mat.api:1.13.0.qualifier(id=196)]
            String n1 = l1.toString().replaceFirst("[^\\[]*", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String n2 = l2.toString().replaceFirst("[^\\[]*", ""); //$NON-NLS-1$ //$NON-NLS-2$
            int r = n1.compareTo(n2);
            if (r != 0)
                return r;
            return o1.getClass().getName().compareTo(o2.getClass().getName());
        }
    }

    // //////////////////////////////////////////////////////////////
    // instance
    // //////////////////////////////////////////////////////////////

    private IObject subject;

    // general attributes
    private String name;
    private String className;
    private String instance;
    private Bytes shallowHeap = new Bytes(0);
    private Bytes retainedHeap = new Bytes(0);
    private String contextClassLoader;
    private int contextClassLoaderId;
    private Boolean isDaemon;

    // extended properties
    private Map<Column, Object> properties = new HashMap<Column, Object>();
    private List<String> keywords = new ArrayList<String>();
    private CompositeResult details;
    private CompositeResult requests;

    private ThreadInfoImpl()
    {}

    public IObject getThreadObject()
    {
        return subject;
    }

    public int getThreadId()
    {
        return subject.getObjectId();
    }

    public String getName()
    {
        return name;
    }

    public String getClassName()
    {
        return className;
    }

    public String getInstance()
    {
        return instance;
    }

    public Bytes getShallowHeap()
    {
        return shallowHeap;
    }

    public Bytes getRetainedHeap()
    {
        return retainedHeap;
    }

    public String getContextClassLoader()
    {
        return contextClassLoader;
    }

    public int getContextClassLoaderId()
    {
        return contextClassLoaderId;
    }

    public void addDetails(String name, IResult detail)
    {
        if (details == null)
            details = new CompositeResult();
        details.addResult(name, detail);
    }

    public CompositeResult getDetails()
    {
        return details;
    }

    public void addKeyword(String keyword)
    {
        keywords.add(keyword);
    }

    public Collection<String> getKeywords()
    {
        return keywords;
    }

    public void setValue(Column column, Object value)
    {
        properties.put(column, value);
    }

    public void addRequest(String summary, IResult detail)
    {
        if (requests == null)
            requests = new CompositeResult();
        requests.addResult(summary, detail);
    }

    public CompositeResult getRequests()
    {
        return requests;
    }

    private Object isDaemon()
    {
        return isDaemon;
    }

    public Object getValue(Column column)
    {
        // Rely on Column equality via just the label
        if (COL_CLASSNAME.equals(column))
            return getClassName();
        else if (COL_NAME.equals(column))
            return getName();
        else if (COL_INSTANCE.equals(column))
            return getInstance();
        else if (COL_SHALLOW.equals(column))
            return getShallowHeap();
        else if (COL_RETAINED.equals(column))
            return getRetainedHeap();
        else if (COL_CONTEXTCL.equals(column))
            return getContextClassLoader();
        else if (COL_ISDAEMON.equals(column))
            return isDaemon();
        else if (COL_MAXLOCALRETAINED.equals(column))
            return null;
        else
            return properties.get(column);
    }

}

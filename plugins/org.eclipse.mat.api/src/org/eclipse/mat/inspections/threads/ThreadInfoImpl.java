/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG & IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - optional columns
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    private static final Column COL_CLASSNAME = new Column(Messages.Column_ClassName);
    private static final Column COL_NAME = new Column(Messages.ThreadInfoImpl_Column_Name);
    private static final Column COL_INSTANCE = new Column(Messages.ThreadStackQuery_Column_ObjectStackFrame);
    private static final Column COL_SHALLOW = new Column(Messages.Column_ShallowHeap, Bytes.class);
    private static final Column COL_RETAINED = new Column(Messages.Column_RetainedHeap, Bytes.class);
    private static final Column COL_CONTEXTCL = new Column(Messages.ThreadInfoImpl_Column_ContextClassLoader);
    private static final Column COL_ISDAEMON = new Column(Messages.ThreadInfoImpl_Column_IsDaemon, Boolean.class);

    private static final List<Column> defaultColumns = Arrays.asList(new Column[] {
                    //COL_CLASSNAME, //
                    COL_INSTANCE, //
                    COL_NAME, //
                    COL_SHALLOW, //
                    COL_RETAINED, //
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

        extractGeneralAttribtes(info);

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
                result.add(ref.getObjectId());
        }
        return result.toArray();
    }

    private static void extractGeneralAttribtes(ThreadInfoImpl info) throws SnapshotException
    {
        info.className = info.subject.getDisplayName();
        info.name = info.subject.getClassSpecificName();
        info.instance = info.subject.getTechnicalName();
        info.shallowHeap = new Bytes(info.subject.getUsedHeapSize());
        info.retainedHeap = new Bytes(info.subject.getRetainedHeapSize());
        info.isDaemon = resolveIsDaemon(info.subject);

        IObject contextClassLoader = (IObject) info.subject.resolveValue("contextClassLoader"); //$NON-NLS-1$
        if (contextClassLoader != null)
        {
            info.contextClassLoader = contextClassLoader.getClassSpecificName();
            if (info.contextClassLoader == null)
                info.contextClassLoader = contextClassLoader.getTechnicalName();
            info.contextClassLoaderId = contextClassLoader.getObjectId();
        }
    }

    private static Boolean resolveIsDaemon(IObject thread)
    {
        try
        {
            Object daemon = thread.resolveValue("daemon");
            if (daemon == null) {
                daemon = thread.resolveValue("isDaemon");
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
        List<Column> answer = new ArrayList<Column>();
        answer.addAll(defaultColumns);
        for (IThreadDetailsResolver resolver : ThreadDetailResolverRegistry.instance().delegates())
        {
            Column[] cols = resolver.getColumns();
            if (cols != null)
                for (int ii = 0; ii < cols.length; ii++)
                {
                    if (properties.containsKey(cols[ii]))
                        answer.add(cols[ii]);
                }
        }

        return answer;
    }

    /* package */static List<Column> getUsedColumns(List<ThreadInfoImpl> threads)
    {
        List<Column> answer = new ArrayList<Column>();
        answer.addAll(defaultColumns);
        for (IThreadDetailsResolver resolver : ThreadDetailResolverRegistry.instance().delegates())
        {
            Column[] cols = resolver.getColumns();
            if (cols != null)
                for (int ii = 0; ii < cols.length; ii++)
                {
                    for (ThreadInfoImpl thread : threads)
                        if (thread.properties.containsKey(cols[ii]))
                        {
                            answer.add(cols[ii]);
                            break;
                        }
                }
        }

        return answer;
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
        if (column == COL_CLASSNAME)
            return getClassName();
        else if (column == COL_NAME)
            return getName();
        else if (column == COL_INSTANCE)
            return getInstance();
        else if (column == COL_SHALLOW)
            return getShallowHeap();
        else if (column == COL_RETAINED)
            return getRetainedHeap();
        else if (column == COL_CONTEXTCL)
            return getContextClassLoader();
        else if (column == COL_ISDAEMON)
            return isDaemon();
        else
            return properties.get(column);
    }

}

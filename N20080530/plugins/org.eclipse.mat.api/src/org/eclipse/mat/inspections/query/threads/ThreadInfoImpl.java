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
package org.eclipse.mat.inspections.query.threads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ThreadToLocalReference;
import org.eclipse.mat.util.IProgressListener;


/* package */class ThreadInfoImpl implements IThreadInfo
{
    private static final ThreadDetailResolverRegistry threadRegistry = new ThreadDetailResolverRegistry();
    private static final RequestDetailResolverRegistry requestRegistry = new RequestDetailResolverRegistry();

    // //////////////////////////////////////////////////////////////
    // column meta-data
    // //////////////////////////////////////////////////////////////

    private static final Column COL_NAME = new Column("Name");
    private static final Column COL_INSTANCE = new Column("Instance");
    private static final Column COL_SHALLOW = new Column("Shallow Heap", int.class);
    private static final Column COL_RETAINED = new Column("Retained Heap", long.class);
    private static final Column COL_STATUS = new Column("Status", Integer.class);
    private static final Column COL_CONTEXTCL = new Column("Context Class Loader");

    private static final List<Column> defaultColumns = Arrays.asList(new Column[] { COL_NAME, //
                    COL_INSTANCE, //
                    COL_SHALLOW, //
                    COL_RETAINED, //
                    COL_STATUS, //
                    COL_CONTEXTCL });

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
            String className = snapshot.getClassOf(localId).getName();
            IRequestDetailsResolver resolver = requestRegistry.lookup(className);
            if (resolver != null)
                resolver.complement(snapshot, info, localVars, localId, listener);
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
        info.name = info.subject.getClassSpecificName();
        info.instance = info.subject.getTechnicalName();
        info.shallowHeap = info.subject.getUsedHeapSize();
        info.retainedHeap = info.subject.getRetainedHeapSize();

        Integer status = (Integer) info.subject.resolveValue("threadStatus");
        info.status = status != null ? status : 0;

        IObject contextClassLoader = (IObject) info.subject.resolveValue("contextClassLoader");
        if (contextClassLoader != null)
        {
            info.contextClassLoader = contextClassLoader.getClassSpecificName();
            info.contextClassLoaderId = contextClassLoader.getObjectId();
        }
    }

    private static void extractFromDetailsResolver(ThreadInfoImpl info, boolean readFully, IProgressListener listener)
                    throws SnapshotException
    {
        for (IThreadDetailsResolver resolver : threadRegistry.delegates())
        {
            if (readFully)
                resolver.complementDeep(info, listener);
            else
                resolver.complementShallow(info, listener);
        }
    }

    /* package */static List<Column> getColumns()
    {
        List<Column> answer = new ArrayList<Column>();
        answer.addAll(defaultColumns);
        for (IThreadDetailsResolver resolver : threadRegistry.delegates())
        {
            Column[] cols = resolver.getColumns();
            if (cols != null)
                for (int ii = 0; ii < cols.length; ii++)
                    answer.add(cols[ii]);
        }

        return answer;
    }

    // //////////////////////////////////////////////////////////////
    // instance
    // //////////////////////////////////////////////////////////////

    private IObject subject;

    // general attributes
    private String name;
    private String instance;
    private int shallowHeap;
    private long retainedHeap;
    private Integer status;
    private String contextClassLoader;
    private int contextClassLoaderId;

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

    public String getInstance()
    {
        return instance;
    }

    public int getShallowHeap()
    {
        return shallowHeap;
    }

    public long getRetainedHeap()
    {
        return retainedHeap;
    }

    public Integer getStatus()
    {
        return status;
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

    public Object getValue(Column column)
    {
        if (column == COL_NAME)
            return getName();
        else if (column == COL_INSTANCE)
            return getInstance();
        else if (column == COL_SHALLOW)
            return getShallowHeap();
        else if (column == COL_RETAINED)
            return getRetainedHeap();
        else if (column == COL_STATUS)
            return getStatus();
        else if (column == COL_CONTEXTCL)
            return getContextClassLoader();
        else
            return properties.get(column);
    }

}

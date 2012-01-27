/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.query;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.snapshot.HeapObjectArgumentFactory;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;
import org.eclipse.mat.query.annotations.descriptors.IArgumentDescriptor;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentFactory;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

/**
 * This class provides possibility to lookup, inspect, parameterize and execute queries on a given heap dump.
 * 
 * <p>NOTE: The queries delivered with Memory Analyzer and their
 * expected parameters are not part of the API. Both names/identifiers and parameters may change.</p>
 * 
 * Usage example: 
 * <pre>
 * IResult result = SnapshotQuery.lookup(&quot;top_consumers_html&quot;, snapshot) //
 *                 .setArgument(&quot;objects&quot;, retained) //
 *                 .execute(listener);
 * </pre>
 */
public class SnapshotQuery
{

    /**
     * Factory method to create a query by name.
     */
    public static SnapshotQuery lookup(String name, ISnapshot snapshot) throws SnapshotException
    {
        QueryDescriptor query = QueryRegistry.instance().getQuery(name);
        if (query == null)
            throw new SnapshotException(MessageUtil.format(Messages.SnapshotQuery_ErrorMsg_QueryNotAvailable, name));

        IQueryContext context = new SnapshotQueryContext(snapshot);
        if (!query.accept(context))
            throw new SnapshotException(query.explain(context));
        checkSubjects(name, snapshot, query, context);

        ArgumentSet arguments = query.createNewArgumentSet(context);

        return new SnapshotQuery(snapshot, arguments);
    }

    private static void checkSubjects(String name, ISnapshot snapshot, QueryDescriptor query, IQueryContext queryContext) throws SnapshotException
    {
        if (unsuitableSubjects(query, queryContext))
        {
            throw new SnapshotException(MessageUtil.format(Messages.SnapshotQuery_ErrorMsg_UnsuitableSubjects, name, Arrays.asList(extractSubjects(query))));
        }
    }

    private static boolean unsuitableSubjects(QueryDescriptor query, IQueryContext queryContext)
    {
        final String cls[];
        boolean skip;
        cls = extractSubjects(query);
        if (cls != null)
        {
            ISnapshot snapshot =  (ISnapshot)queryContext.get(ISnapshot.class, null);
            int count = 0;
            for (String cn : cls)
            {
                try
                {
                    Collection<IClass> ss = snapshot.getClassesByName(cn, false);
                    if (ss == null || ss.size() == 0)
                        continue;
                    count += ss.size();
                    break;
                }
                catch (SnapshotException e)
                {}
            }
            skip = (count == 0);
        }
        else
        {
            skip = false;
        }
        return skip;
    }

    private static String[] extractSubjects(QueryDescriptor query)
    {
        final String[] cls;
        Subjects subjects = query.getCommandType().getClass().getAnnotation(Subjects.class);
        if (subjects != null) 
        {
            cls = subjects.value();
        }
        else
        {
            Subject s = query.getCommandType().getAnnotation(Subject.class);
            if (s != null)
            {
                cls = new String[] { s.value() };
            }
            else
            {
                cls = null;
            }
        }
        return cls;
    }

    /**
     * Factory method to create a query by command line, i.e. setting the
     * arguments accordingly.
     */
    public static SnapshotQuery parse(String commandLine, ISnapshot snapshot) throws SnapshotException
    {
        IQueryContext context = new SnapshotQueryContext(snapshot);
        ArgumentSet arguments = CommandLine.parse(context, commandLine);
        return new SnapshotQuery(snapshot, arguments);
    }

    private final ISnapshot snapshot;
    private final QueryDescriptor query;
    private final ArgumentSet arguments;

    private SnapshotQuery(ISnapshot snapshot, ArgumentSet arguments)
    {
        this.snapshot = snapshot;
        this.query = arguments.getQueryDescriptor();
        this.arguments = arguments;
    }

    /**
     * Get a descriptor for the query. From it one can inspect the Name, Help, Icon, etc... for the query.
     *  
     * @return {@link IAnnotatedObjectDescriptor} a descriptor for the query
     * @since 1.0
     */
    public IAnnotatedObjectDescriptor getDescriptor()
    {
    	return query;
    }
    
    /**
     * Get the list of the query arguments. 
     * 
     * @return the list of {@link IArgumentDescriptor} describing the arguments which the query expects
     * @since 1.0
     */
    public List<? extends IArgumentDescriptor> getArguments()
    {
    	return Collections.unmodifiableList(query.getArguments());
    }
    
    /**
     * Set the argument identified by <code>name</code>. Heap objects can be
     * provided as <code>int</code>, <code>Integer</code>, <code>int[]</code> or
     * <code>IObject</code>.
     * 
     * @param name 	the name of the argument
     * @param value	the new value of the argument
     * 
     * @return the modified SnapshotQuery object
     * @throws SnapshotException
     * @since 1.0
     */
    public SnapshotQuery setArgument(String name, Object value) throws SnapshotException
    {
        ArgumentDescriptor argument = query.getArgumentByName(name);
        if (argument == null)
            throw new SnapshotException(MessageUtil.format(Messages.SnapshotQuery_ErrorMsg_UnkownArgument, name, query
                            .getIdentifier()));

        // special checks: support heap objects

        if ((argument.getType() == int.class && argument.getAdvice() == Argument.Advice.HEAP_OBJECT) //
                        || argument.getType().isAssignableFrom(IObject.class) //
                        || argument.getType().isAssignableFrom(IHeapObjectArgument.class))
        {
            if (value instanceof ArgumentFactory)
            {
                // do nothing: correct
            }
            if (value instanceof IObject)
            {
                value = HeapObjectArgumentFactory.build(snapshot, new int[] { ((IObject) value).getObjectId() });
            }
            else if (value instanceof Integer)
            {
                value = HeapObjectArgumentFactory.build(snapshot, new int[] { (Integer) value });
            }
            else if (value instanceof int[])
            {
                value = HeapObjectArgumentFactory.build(snapshot, (int[]) value);
            }
            else if (value instanceof ArrayInt)
            {
                value = HeapObjectArgumentFactory.build(snapshot, ((ArrayInt) value).toArray());
            }
            else if (value instanceof IHeapObjectArgument)
            {
                value = HeapObjectArgumentFactory.build(snapshot, (IHeapObjectArgument) value);
            }
            else
            {
                throw new SnapshotException(MessageUtil.format(Messages.SnapshotQuery_ErrorMsg_UnsupportedTyp, name,
                                value.getClass().getName()));
            }
        }

        arguments.setArgumentValue(argument, value);

        return this;
    }
    
    /**
     * @deprecated use setArgument() instead
     */
    public SnapshotQuery set(String name, Object value) throws SnapshotException
    {
    	return setArgument(name, value);
    }

    /**
     * Execute the query and return the result.
     */
    public IResult execute(IProgressListener listener) throws SnapshotException
    {
        QueryResult result = arguments.execute(listener);
        return result != null ? result.getSubject() : null;
    }

    /**
     * Execute the query and return a {@link RefinedResultBuilder} which allows
     * for filtering, sorting and limiting of the result.
     */
    public RefinedResultBuilder refine(IProgressListener listener) throws SnapshotException
    {
        IResult result = execute(listener);
        if (result == null)
            throw new SnapshotException(MessageUtil.format(Messages.SnapshotQuery_ErrorMsg_NoResult, arguments
                            .getQueryDescriptor().getIdentifier()));
        return new RefinedResultBuilder(new SnapshotQueryContext(snapshot), (IStructuredResult) result);
    }

}

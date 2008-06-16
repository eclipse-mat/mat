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
package org.eclipse.mat.impl.query;

import java.io.File;
import java.lang.reflect.Array;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.results.ObjectListResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;


public class ArgumentSet
{
    private IArgumentContextProvider contextProvider;
    private QueryDescriptor query;
    private Map<ArgumentDescriptor, Object> values;

    ArgumentSet(QueryDescriptor query, IArgumentContextProvider contextProvider) throws SnapshotException
    {
        this.query = query;
        this.values = new HashMap<ArgumentDescriptor, Object>();

        ArgumentDescriptor argument = query.getPrimarySnapshotArgument();
        if (argument != null)
            values.put(argument, contextProvider.getPrimarySnapshot());

        this.contextProvider = contextProvider;
    }

    public QueryResult execute(IProgressListener listener) throws SnapshotException, SnapshotException
    {
        try
        {
            IQuery impl = query.getCommandType().newInstance();

            for (ArgumentDescriptor parameter : query.getArguments())
            {
                Object value = values.get(parameter);

                if (value == null && parameter.isMandatory())
                {
                    value = parameter.getDefaultValue();
                    if (value == null)
                        throw new SnapshotException(MessageFormat.format("Missing required parameter: {0}", parameter
                                        .getName()));
                }

                if (value == null)
                {
                    if (values.containsKey(parameter))
                    {
                        Logger.getLogger(getClass().getName()).log(Level.INFO,
                                        "Setting null value for: " + parameter.getName());
                        parameter.getField().set(impl, null);
                    }
                    continue;
                }

                try
                {
                    // do some magic conversion for snapshots
                    if (parameter.getAdvice() == Argument.Advice.SECONDARY_SNAPSHOT)
                    {
                        if (parameter.isMultiple())
                        {
                            @SuppressWarnings("unchecked")
                            List<SnapshotArgument> arguments = (List<SnapshotArgument>) value;
                            List<ISnapshot> list = new ArrayList<ISnapshot>();

                            for (SnapshotArgument obj : arguments)
                                list.add(toSnapshot(obj, listener));

                            value = list;
                        }
                        else
                        {
                            value = toSnapshot((SnapshotArgument) value, listener);
                        }
                    }

                    // special care has to be taken with objects
                    if (value instanceof IHeapObjectFactory)
                    {
                        setHeapObjectValue(impl, parameter, (IHeapObjectFactory) value, listener);
                    }
                    else if (parameter.isArray())
                    {
                        List<?> list = (List<?>) value;
                        Object array = Array.newInstance(parameter.getType(), list.size());

                        int ii = 0;
                        for (Object v : list)
                            Array.set(array, ii++, v);

                        parameter.getField().set(impl, array);
                    }
                    else
                    {
                        parameter.getField().set(impl, value);
                    }
                }
                catch (IllegalArgumentException e)
                {
                    throw new SnapshotException(MessageFormat.format(
                                    "Illegal argument: {0} of type {1} cannot be set to field {2} of type {3}",
                                    new Object[] { value, value.getClass().getName(), parameter.getName(),
                                                    parameter.getType().getName() }), e);
                }
                catch (IllegalAccessException e)
                {
                    // should not happen as we check accessibility when
                    // registering queries
                    throw new SnapshotException(MessageFormat.format("Unable to access field {0} of type {1}",
                                    new Object[] { parameter.getName(), parameter.getType().getName() }), e);
                }
            }

            IResult result = impl.execute(listener);
            
            // legacy: convert "pseudo" results
            if (result instanceof ObjectListResult)
                result = ((ObjectListResult)result).asTree(contextProvider.getPrimarySnapshot());

            return new QueryResult(this.query, this.writeToLine(), result);
        }
        catch (InstantiationException e)
        {
            throw new SnapshotException("Unable to instantiate command " + query.getCommandType().getName(), e);
        }
        catch (IllegalAccessException e)
        {
            throw new SnapshotException("Unable to set field of " + query.getCommandType().getName(), e);
        }
        catch (IProgressListener.OperationCanceledException e)
        {
            throw e; // no nesting!
        }
        catch (SnapshotException e)
        {
            throw e; // no nesting!
        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }
    }

    private ISnapshot toSnapshot(SnapshotArgument value, IProgressListener listener) throws SnapshotException
    {
        // TODO (ab) secondary snapshots are not disposed!
        return SnapshotFactory.openSnapshot(new File(value.getFilename()), listener);
    }

    public String writeToLine() throws SnapshotException
    {
        StringBuilder answer = new StringBuilder(128);
        answer.append(query.getIdentifier()).append(" ");
        for (ArgumentDescriptor arg : query.getArguments())
        {
            Object value = values.get(arg);
            
            if (value == null && !values.containsKey(arg))
                continue;
            
            if (!arg.isPrimarySnapshot())
            {
                if (value != null)
                    arg.appendUsage(answer, value);
                else if (value == null && arg.getDefaultValue() != null)
                    arg.appendUsage(answer, null);
                else if (arg.isMandatory() && arg.getDefaultValue() != null)
                    arg.appendUsage(answer, arg.getDefaultValue());
            }
        }
        return answer.toString().trim();
    }

    public void setArgumentValue(ArgumentDescriptor arg, Object value)
    {
        values.put(arg, value);
    }

    public void removeArgumentValue(ArgumentDescriptor arg)
    {
        values.remove(arg);
    }

    public Object getArgumentValue(ArgumentDescriptor desc)
    {
        return values.get(desc);
    }

    // //////////////////////////////////////////////////////////////
    // 
    // //////////////////////////////////////////////////////////////

    public QueryDescriptor getQueryDescriptor()
    {
        return query;
    }

    public boolean isExecutable()
    {
        // all mandatory parameters must be set
        for (ArgumentDescriptor parameter : query.getArguments())
        {
            if (parameter.isMandatory() && !values.containsKey(parameter) && parameter.getDefaultValue() == null)
                return false;
        }

        return true;
    }

    public List<ArgumentDescriptor> getUnsetArguments()
    {
        List<ArgumentDescriptor> answer = new ArrayList<ArgumentDescriptor>();
        for (ArgumentDescriptor parameter : query.getArguments())
        {
            if (!values.containsKey(parameter))
                answer.add(parameter);
        }
        return answer;
    }

    public String getUnsetUsage()
    {
        StringBuilder answer = new StringBuilder(128);
        for (ArgumentDescriptor parameter : query.getArguments())
        {
            if (!values.containsKey(parameter))
                parameter.appendUsage(answer);
        }
        return answer.toString().trim();
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(256);
        buf.append(query);

        if (!values.isEmpty())
            for (Map.Entry<ArgumentDescriptor, Object> entry : values.entrySet())
                buf.append(" ").append(entry.getKey()).append(" = ").append(entry.getValue());

        return buf.toString();
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    private void setHeapObjectValue(IQuery queryInstance, ArgumentDescriptor argDescriptor, IHeapObjectFactory factory,
                    IProgressListener listener) throws SnapshotException
    {
        ISnapshot snapshot = contextProvider.getPrimarySnapshot();
        IHeapObjectArgument value = factory.create(snapshot);

        try
        {
            // resolving of object ids is deferred
            if (IHeapObjectArgument.class.isAssignableFrom(argDescriptor.getType()))
            {
                if (argDescriptor.isArray())
                {
                    Object array = Array.newInstance(argDescriptor.getType(), 1);
                    Array.set(array, 0, value);
                    argDescriptor.getField().set(queryInstance, array);
                }
                else if (argDescriptor.isList())
                {
                    List<IHeapObjectArgument> list = new ArrayList<IHeapObjectArgument>(1);
                    list.add(value);
                    argDescriptor.getField().set(queryInstance, list);
                }
                else
                {
                    argDescriptor.getField().set(queryInstance, value);
                }
            }
            else
            {
                int[] objectIds = value.getIds(listener);
                assignObjectIds(queryInstance, argDescriptor, snapshot, objectIds);
            }
        }
        catch (SnapshotException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new SnapshotException(MessageFormat.format(
                            "{0}: Error setting heap objects to field ''{1}'' of ''{2}''", new Object[] {
                                            e.getClass().getCanonicalName(), argDescriptor.getName(),
                                            queryInstance.getClass().getName() }), e);
        }
    }

    private void assignObjectIds(IQuery queryInstance, ArgumentDescriptor argDescriptor, ISnapshot snapshot,
                    int[] objectIds) throws IllegalArgumentException, IllegalAccessException, SnapshotException
    {
        if (argDescriptor.isArray())
        {
            if (argDescriptor.getType() == int.class)
            {
                argDescriptor.getField().set(queryInstance, objectIds);
            }
            else if (argDescriptor.getType() == Integer.class)
            {
                Object array = Array.newInstance(argDescriptor.getType(), objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    Array.set(array, ii, Integer.valueOf(objectIds[ii]));

                argDescriptor.getField().set(queryInstance, array);
            }
            else if (IObject.class.isAssignableFrom(argDescriptor.getType()))
            {
                Object array = Array.newInstance(argDescriptor.getType(), objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    Array.set(array, ii, snapshot.getObject(objectIds[ii]));

                argDescriptor.getField().set(queryInstance, array);
            }
            else
            {
                throw new SnapshotException();
            }
        }
        else if (argDescriptor.isList())
        {
            if (argDescriptor.getType() == Integer.class)
            {
                List<Integer> list = new ArrayList<Integer>(objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    list.add(objectIds[ii]);

                argDescriptor.getField().set(queryInstance, list);
            }
            else if (IObject.class.isAssignableFrom(argDescriptor.getType()))
            {
                List<IObject> list = new ArrayList<IObject>(objectIds.length);

                for (int ii = 0; ii < objectIds.length; ii++)
                    list.add(snapshot.getObject(objectIds[ii]));

                argDescriptor.getField().set(queryInstance, list);
            }
            else
            {
                throw new SnapshotException();
            }
        }
        else
        {
            if (objectIds.length != 1)
                throw new SnapshotException(MessageFormat.format(
                                "Argument ''{0}'' does not allow to assign multiple objects",
                                new Object[] { argDescriptor.getName() }));

            if (argDescriptor.getType() == int.class)
            {
                argDescriptor.getField().set(queryInstance, objectIds[0]);
            }
            else if (argDescriptor.getType() == Integer.class)
            {
                argDescriptor.getField().set(queryInstance, Integer.valueOf(objectIds[0]));
            }
            else if (IObject.class.isAssignableFrom(argDescriptor.getType()))
            {
                argDescriptor.getField().set(queryInstance, snapshot.getObject(objectIds[0]));
            }
            else
            {
                throw new SnapshotException();
            }

        }
    }

}

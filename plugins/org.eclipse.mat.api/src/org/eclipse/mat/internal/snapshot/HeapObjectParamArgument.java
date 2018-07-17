/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson/IBM Corporation - pass through listener for cancel
 *******************************************************************************/
package org.eclipse.mat.internal.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.registry.Converters;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;

public final class HeapObjectParamArgument extends HeapObjectArgumentFactory
{
    public interface Flags
    {
        String INCLUDE_SUBCLASSES = "-include_subclasses"; //$NON-NLS-1$
        String INCLUDE_CLASS_INSTANCE = "-include_class_instance"; //$NON-NLS-1$
        String INCLUDE_LOADED_INSTANCES = "-include_loaded_instances"; //$NON-NLS-1$
        String VERBOSE = "-verbose"; //$NON-NLS-1$
        String RETAINED = "-retained"; //$NON-NLS-1$
    }

    private boolean isRetained = false;
    private boolean includeSubclasses = false;
    private boolean includeClassInstance = false;
    private boolean includeLoadedInstances = false;
    private boolean isVerbose = false;

    private List<Pattern> patterns = new ArrayList<Pattern>();
    private List<Long> addresses = new ArrayList<Long>();
    private List<String> oqls = new ArrayList<String>();

    // //////////////////////////////////////////////////////////////
    // inspect & assign value
    // //////////////////////////////////////////////////////////////

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(128);

        for (Pattern p : patterns)
            buf.append(Converters.convertAndEscape(Pattern.class, p)).append(" "); //$NON-NLS-1$

        for (Long a : addresses)
            buf.append("0x").append(Long.toHexString(a)).append(" "); //$NON-NLS-1$ //$NON-NLS-2$

        for (String o : oqls)
            buf.append(escape(o)).append(";").append(" "); //$NON-NLS-1$ //$NON-NLS-2$

        if (includeClassInstance)
            buf.append(" " + Flags.INCLUDE_CLASS_INSTANCE); //$NON-NLS-1$

        if (includeSubclasses)
            buf.append(" " + Flags.INCLUDE_SUBCLASSES); //$NON-NLS-1$

        if (includeLoadedInstances)
            buf.append(" " + Flags.INCLUDE_LOADED_INSTANCES); //$NON-NLS-1$

        if (isRetained)
            buf.append(" " + Flags.RETAINED); //$NON-NLS-1$

        if (isVerbose)
            buf.append(" " + Flags.VERBOSE); //$NON-NLS-1$

        return buf.toString();
    }

    public HeapObjectParamArgument(ISnapshot snapshot)
    {
        super(snapshot);
    }

    @Override
    protected int[] asIntegerArray() throws SnapshotException
    {
        return create().getIds(new VoidProgressListener());
    }

    @Override
    protected IHeapObjectArgument asObjectArgument()
    {
        return create();
    }

    public void appendUsage(StringBuilder buf)
    {
        buf.append(toString());
    }

    private String escape(String query)
    {
        int p = query.indexOf('"');
        if (p < 0)
            return query;

        StringBuilder buf = new StringBuilder(query.length() + 5);

        for (int ii = 0; ii < query.length(); ii++)
        {
            char c = query.charAt(ii);
            if (c == '"')
                buf.append("\\"); //$NON-NLS-1$
            buf.append(c);
        }

        return buf.toString();
    }

    public boolean isIncludeSubclasses()
    {
        return includeSubclasses;
    }

    public void setIncludeSubclasses(boolean includeSubclasses)
    {
        this.includeSubclasses = includeSubclasses;
    }

    public boolean isIncludeClassInstance()
    {
        return includeClassInstance;
    }

    public void setIncludeClassInstance(boolean includeClassInstance)
    {
        this.includeClassInstance = includeClassInstance;
    }

    public boolean isIncludeLoadedInstances()
    {
        return includeLoadedInstances;
    }

    public void setIncludeLoadedInstances(boolean includeLoadedInstances)
    {
        this.includeLoadedInstances = includeLoadedInstances;
    }

    public boolean isRetained()
    {
        return isRetained;
    }

    public void setRetained(boolean isRetained)
    {
        this.isRetained = isRetained;
    }

    public boolean isVerbose()
    {
        return isVerbose;
    }

    public void setVerbose(boolean isVerbose)
    {
        this.isVerbose = isVerbose;
    }

    public void addObjectAddress(long address)
    {
        addresses.add(address);
    }

    public void addPattern(Pattern pattern)
    {
        patterns.add(pattern);
    }

    public void addOql(String query)
    {
        oqls.add(query);
    }

    public List<Object> getArguments()
    {
        List<Object> answer = new ArrayList<Object>();
        answer.addAll(patterns);
        answer.addAll(addresses);
        answer.addAll(oqls);
        return answer;
    }

    public boolean isComplete()
    {
        return !patterns.isEmpty() || !addresses.isEmpty() || !oqls.isEmpty();
    }

    public IHeapObjectArgument create()
    {
        // the object set must contain only unique objects

        return new IHeapObjectArgument()
        {

            public int[] getIds(IProgressListener listener) throws SnapshotException
            {
                ArrayIntBig answer = new ArrayIntBig();
                for (Iterator<int[]>it = this.iterator(listener); it.hasNext(); )
                {
                    int[] a = it.next();
                    answer.addAll(a);
                    if (listener.isCanceled())
                    {
                        throw new OperationCanceledException();
                    }
                }
                return answer.toArray();
            }

            public Iterator<int[]> iterator(IProgressListener listener)
            {
                return new IteratorImpl(snapshot, HeapObjectParamArgument.this, listener);
            }

            public Iterator<int[]> iterator()
            {
                return new IteratorImpl(snapshot, HeapObjectParamArgument.this, new VoidProgressListener());
            }

            public String getLabel()
            {
                return HeapObjectParamArgument.this.toString();
            }
        };
    }

    // //////////////////////////////////////////////////////////////
    // convert to actual object ids
    // //////////////////////////////////////////////////////////////

    private static class IteratorImpl implements Iterator<int[]>
    {
        Logger logger;

        ISnapshot snapshot;
        HeapObjectParamArgument hopa;
        LinkedList<Object> arguments;
        int[] next;
        IProgressListener listener;
        Iterator<IClass> classIterator;

        public IteratorImpl(ISnapshot snapshot, HeapObjectParamArgument hopa, IProgressListener listener)
        {
            this.snapshot = snapshot;
            this.hopa = hopa;
            this.arguments = new LinkedList<Object>(hopa.getArguments());
            this.listener = listener;

            if (hopa.isVerbose)
                this.logger = Logger.getLogger(this.getClass().getName());

            if (hopa.isRetained)
                next = getAll();
            else
                next = getNext();
        }

        public boolean hasNext()
        {
            return next != null;
        }

        public int[] next()
        {
            int[] answer = next;
            next = getNext();
            return answer;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        private int[] getNext()
        {
            if (arguments.isEmpty() && classIterator == null)
                return null;

            try
            {
                if (classIterator != null)
                {
                    while (classIterator.hasNext())
                    {
                        int ret[] = collectObjectsByClass(classIterator.next());
                        if (ret.length > 0)
                            return ret;
                    }
                    classIterator = null;
                    if (arguments.isEmpty())
                    {
                        // No other arguments left, so indicate no more
                        return null;
                    }
                }
                Object argument = arguments.removeFirst();
                if (argument instanceof Pattern)
                {
                    return collectObjectsByPattern((Pattern) argument);
                }
                else if (argument instanceof Long)
                {
                    return collectObjectsByAddress((Long) argument);
                }
                else if (argument instanceof String)
                {
                    return collectObjectsByOQL((String) argument);
                }
                else
                {
                    throw new SnapshotException(MessageUtil.format(
                                    Messages.HeapObjectParamArgument_ErrorMsg_UnknownArgument, argument.getClass()
                                                    .getName()));
                }
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        private int[] getAll()
        {
            try
            {
                ArrayInt objectIds = new ArrayInt();
                int[] batch = getNext();
                while (batch != null)
                {
                    objectIds.addAll(batch);
                    batch = getNext();
                }
                return snapshot.getRetainedSet(objectIds.toArray(), listener);
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        private int[] collectObjectsByClass(IClass clazz) throws SnapshotException
        {
            int[] answer = clazz.getObjectIds();
            if (hopa.isVerbose)
            {
                logger.log(Level.INFO, MessageUtil.format(Messages.HeapObjectParamArgument_Msg_AddedInstances,
                                clazz.getName(), answer.length));
            }

            if (hopa.includeClassInstance)
            {
                int[] addtl = new int[answer.length + 1];
                addtl[0] = clazz.getObjectId();
                System.arraycopy(answer, 0, addtl, 1, answer.length);
                answer = addtl;
            }

            return evalLoaderInstances(answer);
        }

        private int[] collectObjectsByPattern(Pattern pattern) throws SnapshotException
        {
            if (hopa.isVerbose)
            {
                logger.log(Level.INFO, MessageUtil.format(Messages.HeapObjectParamArgument_Msg_SearchingByPattern,
                                pattern.toString()));
            }

            int[] answer;

            Collection<IClass> classesByName = snapshot.getClassesByName(pattern, hopa.includeSubclasses);
            if (hopa.isVerbose)
            {
                int instanceCount = 0;
                for (IClass clazz: classesByName)
                {
                    if (hopa.includeClassInstance)
                        ++instanceCount;
                    instanceCount += clazz.getNumberOfObjects();
                }
                logger.log(Level.INFO, MessageUtil.format(Messages.HeapObjectParamArgument_Msg_MatchingPattern,
                                classesByName.size(), instanceCount));
            }
            if (classesByName.size() == 1)
            {
                // shortcut -> no array copying necessary
                IClass clazz = classesByName.iterator().next();
                answer = collectObjectsByClass(clazz);
                return answer;
            }
            else
            {
                classIterator = classesByName.iterator();
                while (classIterator.hasNext())
                {
                    if (listener.isCanceled())
                    {
                        throw new OperationCanceledException();
                    }
                    answer = collectObjectsByClass(classIterator.next());
                    if (answer.length > 0)
                        return answer;
                }
                classIterator = null;
                // We didn't find any instances for the pattern, so return an empty array
                return new int[0];
            }
        }

        private int[] collectObjectsByAddress(long address) throws SnapshotException
        {
            int objectId = snapshot.mapAddressToId(address);

            if (objectId < 0)
                throw new SnapshotException(MessageUtil.format(Messages.HeapObjectParamArgument_Msg_ObjectFound, Long
                                .toHexString(address)));

            return evalIncludeSubClassesAndInstances(new int[] { objectId });

        }

        private int[] collectObjectsByOQL(String queryString) throws OQLParseException, SnapshotException
        {
            IOQLQuery oqlQuery = SnapshotFactory.createQuery(queryString);
            Object result = oqlQuery.execute(snapshot, listener);
            if (result instanceof int[])
            {
                return evalIncludeSubClassesAndInstances((int[]) result);
            }
            else if (result == null)
            {
                throw new SnapshotException(MessageUtil.format(Messages.HeapObjectParamArgument_ErrorMsg_NorResult,
                                queryString));
            }
            else
            {
                throw new SnapshotException(MessageUtil.format(
                                Messages.HeapObjectParamArgument_ErrorMsg_NotAListOfObjects, queryString));
            }
        }

        private int[] evalIncludeSubClassesAndInstances(int[] array) throws SnapshotException
        {
            int[] answer = array;

            if (hopa.includeSubclasses)
            {
                ArrayInt objIds = new ArrayInt();

                for (int id : array)
                {
                    IObject object = snapshot.getObject(id);
                    if (!(object instanceof IClass))
                    {
                        String msg = Messages.HeapObjectParamArgument_ErrorMsg_NotAClass;
                        throw new SnapshotException(MessageUtil.format(msg,
                                        Long.toHexString(object.getObjectAddress()), id, object.getClazz().getName(),
                                        Flags.INCLUDE_CLASS_INSTANCE));
                    }

                    IClass clazz = (IClass) object;

                    if (hopa.includeClassInstance)
                        objIds.add(clazz.getObjectId());
                    objIds.addAll(clazz.getObjectIds());

                    for (IClass subClazz : clazz.getAllSubclasses())
                    {
                        if (hopa.includeClassInstance)
                            objIds.add(subClazz.getObjectId());
                        objIds.addAll(subClazz.getObjectIds());
                    }

                    if (listener.isCanceled())
                    {
                        throw new OperationCanceledException();
                    }
                }

                answer = objIds.toArray();
            }

            return evalLoaderInstances(answer);
        }

        private int[] evalLoaderInstances(int[] answer) throws SnapshotException
        {
            if (!hopa.includeLoadedInstances)
                return answer;

            ArrayInt objIdxs = new ArrayInt();

            for (int ii = 0; ii < answer.length; ii++)
            {
                int objectId = answer[ii];

                IObject classLoader = snapshot.getObject(objectId);
                if (!(classLoader instanceof IClassLoader))
                {
                    String msg = Messages.HeapObjectParamArgument_ErrorMsg_NotAClassLoader;
                    throw new SnapshotException(MessageUtil.format(msg, Long
                                    .toHexString(classLoader.getObjectAddress()), objectId, classLoader.getClazz()
                                    .getName(), Flags.INCLUDE_LOADED_INSTANCES));
                }

                objIdxs.add(objectId);

                for (IClass clazz : snapshot.getClasses())
                {
                    if (clazz.getClassLoaderId() == objectId)
                    {
                        // add the class & all instances of it
                        objIdxs.add(clazz.getObjectId());
                        int[] toAdd = clazz.getObjectIds();
                        objIdxs.addAll(toAdd);

                        if (hopa.isVerbose)
                        {
                            logger.log(Level.INFO, MessageUtil.format(
                                            Messages.HeapObjectParamArgument_Msg_AddedInstances, clazz.getName(),
                                            toAdd.length));
                        }

                    }
                }

                if (listener.isCanceled())
                {
                    throw new OperationCanceledException();
                }
            }

            return objIdxs.toArray();
        }

    }

    public List<Pattern> getPatterns()
    {
        return patterns;
    }

    public List<Long> getAddresses()
    {
        return addresses;
    }

    public List<String> getOqls()
    {
        return oqls;
    }

}

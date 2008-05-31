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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;


public final class HeapObjectParamArgument implements IHeapObjectFactory
{
    interface Flags
    {
        String INCLUDE_SUBCLASSES = "-include_subclasses";
        String INCLUDE_CLASS_INSTANCE = "-include_class_instance";
        String INCLUDE_LOADED_INSTANCES = "-include_loaded_instances";
        String VERBOSE = "-verbose";
        String RETAINED = "-retained";
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

    public HeapObjectParamArgument()
    {}

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(128);

        for (Pattern p : patterns)
            buf.append(Converters.convertAndEscape(Pattern.class, p)).append(" ");

        for (Long a : addresses)
            buf.append("0x").append(Long.toHexString(a)).append(" ");

        for (String o : oqls)
            buf.append(escape(o)).append(";").append(" ");

        if (includeClassInstance)
            buf.append(" " + Flags.INCLUDE_CLASS_INSTANCE);

        if (includeSubclasses)
            buf.append(" " + Flags.INCLUDE_SUBCLASSES);

        if (includeLoadedInstances)
            buf.append(" " + Flags.INCLUDE_LOADED_INSTANCES);

        if (isRetained)
            buf.append(" " + Flags.RETAINED);

        if (isVerbose)
            buf.append(" " + Flags.VERBOSE);

        return buf.toString();
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
                buf.append("\\");
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

    public IHeapObjectArgument create(final ISnapshot snapshot)
    {
        // the object set must contain only unique objects

        return new IHeapObjectArgument()
        {

            public int[] getIds(IProgressListener listener) throws SnapshotException
            {
                ArrayIntBig answer = new ArrayIntBig();
                for (int[] a : this)
                    answer.addAll(a);
                return answer.toArray();
            }

            public Iterator<int[]> iterator()
            {
                return new IteratorImpl(snapshot, HeapObjectParamArgument.this);
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

        public IteratorImpl(ISnapshot snapshot, HeapObjectParamArgument hopa)
        {
            this.snapshot = snapshot;
            this.hopa = hopa;
            this.arguments = new LinkedList<Object>(hopa.getArguments());

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
            if (arguments.isEmpty())
                return null;

            try
            {
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
                    throw new SnapshotException("Unknown argument type:" + argument.getClass().getName());
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
                return snapshot.getRetainedSet(objectIds.toArray(), new VoidProgressListener());
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        private int[] collectObjectsByPattern(Pattern pattern) throws SnapshotException
        {
            if (hopa.isVerbose)
            {
                logger.log(Level.INFO, MessageFormat.format("Looking up objects for class name pattern ''{0}''",
                                pattern.toString()));
            }

            int[] answer;

            Collection<IClass> classesByName = snapshot.getClassesByName(pattern, hopa.includeSubclasses);
            if (classesByName.size() == 1)
            {
                // shortcut -> no array copying necessary
                IClass clazz = classesByName.iterator().next();
                answer = clazz.getObjectIds();
                if (hopa.isVerbose)
                {
                    logger.log(Level.INFO, MessageFormat.format("Added class {0} and {1} instances of it", clazz
                                    .getName(), answer.length));
                }

                if (hopa.includeClassInstance)
                {
                    int[] addtl = new int[answer.length + 1];
                    addtl[0] = clazz.getObjectId();
                    System.arraycopy(answer, 0, addtl, 1, answer.length);
                    answer = addtl;
                }
            }
            else
            {
                int classCount = 0;
                int instanceCount = 0;

                ArrayInt objIds = new ArrayInt();

                for (IClass clazz : classesByName)
                {
                    if (hopa.includeClassInstance)
                        objIds.add(clazz.getObjectId());

                    int[] toAdd = clazz.getObjectIds();
                    objIds.addAll(toAdd);

                    classCount++;
                    instanceCount += toAdd.length;

                    if (hopa.isVerbose)
                    {
                        logger.log(Level.INFO, MessageFormat.format("Added class {0} and {1} instances of it", clazz
                                        .getName(), toAdd.length));
                    }
                }

                if (hopa.isVerbose)
                {
                    logger.log(Level.INFO, MessageFormat.format("{0} classes ({1} instances) are matching the pattern",
                                    classCount, instanceCount));
                }

                answer = objIds.toArray();
            }

            return evalLoaderInstances(answer);
        }

        private int[] collectObjectsByAddress(long address) throws SnapshotException
        {
            int objectId = snapshot.mapAddressToId(address);

            if (objectId < 0)
                throw new SnapshotException(MessageFormat.format("Object 0x{0} not found", Long.toHexString(address)));

            return evalIncludeSubClassesAndInstances(new int[] { objectId });

        }

        private int[] collectObjectsByOQL(String queryString) throws OQLParseException, SnapshotException
        {
            IOQLQuery oqlQuery = SnapshotFactory.createQuery(queryString);
            Object result = oqlQuery.execute(snapshot, new VoidProgressListener());
            if (result instanceof int[])
            {
                return evalIncludeSubClassesAndInstances((int[]) result);
            }
            else if (result == null)
            {
                throw new SnapshotException(MessageFormat.format("OQL Query does not yield a result: {0}", queryString));
            }
            else
            {
                throw new SnapshotException(MessageFormat.format("OQL query does not return a list of objects: {0}",
                                queryString));
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
                        String msg = "Not a class: 0x{0} ({2}). If specifying ''{3}'', the selected objects all must be classes.";
                        throw new SnapshotException(MessageFormat.format(msg, Long.toHexString(object
                                        .getObjectAddress()), id, object.getClazz().getName(),
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
                    String msg = "Not a class loader: 0x{0} ({2}). If specifying ''{3}'', the selected objects all must be class loaders.";
                    throw new SnapshotException(MessageFormat.format(msg, Long.toHexString(classLoader
                                    .getObjectAddress()), objectId, classLoader.getClazz().getName(),
                                    Flags.INCLUDE_LOADED_INSTANCES));
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
                            logger.log(Level.INFO, MessageFormat.format("Added class {0} and {1} instances of it",
                                            clazz.getName(), toAdd.length));
                        }

                    }
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

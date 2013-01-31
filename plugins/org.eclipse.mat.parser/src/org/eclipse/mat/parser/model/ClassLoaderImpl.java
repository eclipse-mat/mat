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
package org.eclipse.mat.parser.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.parser.internal.SnapshotImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.registry.ClassSpecificNameResolverRegistry;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

/**
 * Implementation of a Java object representing a java.lang.ClassLoader object.
 * As well as standard object information it contains information about the class loader
 * and summary details about classes loaded by this class loader.
 * @noextend
 */
public class ClassLoaderImpl extends InstanceImpl implements IClassLoader
{
    private static final long serialVersionUID = 1L;

    public static final String NO_LABEL = "__none__";//$NON-NLS-1$

    private volatile transient List<IClass> definedClasses = null;

    /**
     * Constructs a class loader object.
     * @param objectId the object id
     * @param address the actual address of the object
     * @param clazz its type
     * @param fields the fields of the object
     */
    public ClassLoaderImpl(int objectId, long address, ClassImpl clazz, List<Field> fields)
    {
        super(objectId, address, clazz, fields);
    }

    @Override
    protected synchronized void readFully()
    {
        // check for the special case of the system class loader
        if (getObjectAddress() == 0)
        {
            setFields(new ArrayList<Field>());
        }
        else
        {
            super.readFully();
        }
    }

    @Override
    public String getClassSpecificName()
    {
        String label = source.getClassLoaderLabel(getObjectId());

        if (NO_LABEL.equals(label))
        {
            label = ClassSpecificNameResolverRegistry.resolve(this);
            if (label != null)
                source.setClassLoaderLabel(getObjectId(), label);
        }

        return label;
    }

    public List<IClass> getDefinedClasses() throws SnapshotException
    {
        // Double-checked locking, but okay as definedClasses is volatile and running with Java 1.5 or later
        List<IClass> result = definedClasses;
        if (result == null)
        {
            synchronized (this)
            {
                result = definedClasses;
                if (result == null)
                {
                    definedClasses = result = doGetDefinedClasses(source, getObjectId());
                }
            }
        }
        return result;
    }

    public long getRetainedHeapSizeOfObjects(boolean calculateIfNotAvailable, boolean calculateMinRetainedSize,
                    IProgressListener listener) throws SnapshotException
    {
        return doGetRetainedHeapSizeOfObjects(source, getObjectId(), calculateIfNotAvailable, calculateMinRetainedSize,
                        listener);
    }

    /**
     * Gets the classes defined by a given loader
     * @param dump the snapshot
     * @param classLoaderId the class loader to look for
     * @return a list of classes
     * @throws SnapshotException
     */
    public static final List<IClass> doGetDefinedClasses(ISnapshot dump, int classLoaderId) throws SnapshotException
    {
        List<IClass> answer = new ArrayList<IClass>();
        for (IClass clasz : dump.getClasses())
        {
            if (clasz.getClassLoaderId() == classLoaderId)
                answer.add(clasz);
        }
        return answer;
    }

    /**
     * Calculates the retained size of all classes and instances of the class loaded
     * by a class loader.
     * @param dump the snapshot
     * @param classLoaderId the class loader if
     * @param calculateIfNotAvailable true if to skip calculations if not already done
     * @param calculateMinRetainedSize true if to make an approximate calculation
     * @param listener to indicate progress, errors and to cancel
     * @return the size, negative if approximate
     * @throws SnapshotException
     */
    public static final long doGetRetainedHeapSizeOfObjects(ISnapshot dump, int classLoaderId,
                    boolean calculateIfNotAvailable, boolean calculateMinRetainedSize, IProgressListener listener)
                    throws SnapshotException
    {
        long answer = ((SnapshotImpl) dump).getRetainedSizeCache().get(classLoaderId);

        if (answer > 0 || !calculateIfNotAvailable)
            return answer;

        if (answer < 0 && calculateMinRetainedSize)
            return answer;

        if (listener == null)
            listener = new VoidProgressListener();

        ArrayInt objectIds = new ArrayInt();
        objectIds.add(classLoaderId);
        for (IClass clasz : doGetDefinedClasses(dump, classLoaderId))
        {
            objectIds.add(clasz.getObjectId());
            objectIds.addAll(clasz.getObjectIds());
        }

        int[] retainedSet;
        long retainedSize = 0;

        if (!calculateMinRetainedSize)
        {
            retainedSet = dump.getRetainedSet(objectIds.toArray(), listener);
            if (listener.isCanceled())
                return 0;
            retainedSize = dump.getHeapSize(retainedSet);
        }
        else
        {
            retainedSize = dump.getMinRetainedSize(objectIds.toArray(), listener);
            if (listener.isCanceled())
                return 0;
        }

        if (calculateMinRetainedSize)
            retainedSize = -retainedSize;

        ((SnapshotImpl) dump).getRetainedSizeCache().put(classLoaderId, retainedSize);
        return retainedSize;
    }

}

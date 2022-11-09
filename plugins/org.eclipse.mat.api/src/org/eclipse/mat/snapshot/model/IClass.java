/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener;

/**
 * Interface for a class instance in the heap dump.
 * 
 * @noimplement
 */
public interface IClass extends IObject
{
    /** Name of java.lang.Class */
    String JAVA_LANG_CLASS = "java.lang.Class"; //$NON-NLS-1$
    /** Name of java.lang.ClassLoader */
    String JAVA_LANG_CLASSLOADER = "java.lang.ClassLoader"; //$NON-NLS-1$

    /**
     * Returns the fully qualified class name of this class.
     * The package components are separated by dots '.'.
     * Inner classes use $ to separate the parts.
     * @return the name of the class
     */
    public String getName();

    /**
     * Returns the number of instances of this class present in the heap dump.
     * @return the number of instances
     */
    public int getNumberOfObjects();

    /**
     * Ids of all instances of this class (an empty array if there are no instances of the class)
     * @return an array of all the object IDs of instances of this class
     * @throws SnapshotException if there is a problem retrieving the data
     */
    public int[] getObjectIds() throws SnapshotException;

    /**
     * Returns the id of the class loader which loaded this class.
     * @return the object ID of the class loader
     */
    public int getClassLoaderId();

    /**
     * Returns the address of the class loader which loaded this class.
     * @return the address of the class loader
     */
    public long getClassLoaderAddress();

    /**
     * Returns field descriptors for all member variables of instances of this
     * class.
     * If the snapshot data format does not contain field data then this will be an
     * empty list.
     * @return the field descriptors for this class
     */
    public List<FieldDescriptor> getFieldDescriptors();

    /**
     * Returns the static fields and it values.
     * If the snapshot data format does not contain field data then this will be an
     * empty list.
     * @return the static fields of this class
     */
    public List<Field> getStaticFields();

    /**
     * Returns the heap size of one instance of this class. Not valid if this
     * class represents an array.
     * @since 1.0
     * @return the size of an instance of this class in bytes
     */
    public long getHeapSizePerInstance();

    /**
     * Returns the retained size of all objects of this instance including the
     * class instance.
     * @param calculateIfNotAvailable whether to calculate
     * @param calculateMinRetainedSize  whether an approximate calculation is sufficient
     * @param listener for reporting progress or for the user to cancel the calculation
     * @return the total retained size in bytes, negative if an approximation
     * @throws SnapshotException if there is a problem
     */
    public long getRetainedHeapSizeOfObjects(boolean calculateIfNotAvailable, boolean calculateMinRetainedSize,
                    IProgressListener listener) throws SnapshotException;

    /**
     * Returns the id of the super class. -1 if it has no super class, i.e. if
     * it is java.lang.Object.
     * @return the super class ID
     */
    public int getSuperClassId();

    /**
     * Returns the super class.
     * @return the super class
     */
    public IClass getSuperClass();

    /**
     * Does the class have a super class?
     * @return true if the class has a super class.
     */
    public boolean hasSuperClass();

    /**
     * Returns the direct sub-classes.
     * @return a list of the immediate subclasses
     */
    public List<IClass> getSubclasses();

    /**
     * Returns all sub-classes including sub-classes of its sub-classes.
     * @return a list of all the subclasses
     */
    public List<IClass> getAllSubclasses();

    /**
     * Does this class extend a class of the supplied name?
     * With multiple class loaders the supplied name might not 
     * be the class you were intending to find.
     * @param className the candidate class name
     * @return true if it does extend
     * @throws SnapshotException if there is a problem retrieving the information
     */
    public boolean doesExtend(String className) throws SnapshotException;

    /**
     * Test if this class an array type.
     * @return true if the class is an array class.
     */
    public boolean isArrayType();
}

/*******************************************************************************
 * Copyright (c) 2009, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.snapshot.query.Icons;

/**
 * Summary information about objects discarded from the snapshot
 * @since 0.8
 */
public class UnreachableObjectsHistogram implements IResultTable, IIconProvider, Serializable
{
    /**
      * Holds details about the unreachable objects for objects of one particular
      */
    public static class Record implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private String className;
        private int objectCount;
        private long shallowHeapSize;
        private long classAddress;

        /**
         * Details about a particular class
         * @param className the class name
         * @param classAddress the address of the class object
         * @param nrOfObjects the number of instances
         * @param sizeOfObjects the total size of the instances
         * @since 1.0
         */
        public Record(String className, long classAddress, int nrOfObjects, long sizeOfObjects)
        {
            this.className = className;
            this.objectCount = nrOfObjects;
            this.shallowHeapSize = sizeOfObjects;
            this.classAddress = classAddress;
        }

        /**
         * Details about a particular class
         * @param className the class name
         * @param nrOfObjects the number of instances
         * @param sizeOfObjects the total size of the instances
         */
        public Record(String className, int nrOfObjects, long sizeOfObjects)
        {
            this(className, 0L, nrOfObjects, sizeOfObjects);
        }

        /**
         * The name of the class
         * @return the class name
         */
        public String getClassName()
        {
            return className;
        }

        /**
         * the number of instances discarded by Memory Analyzer
         * @return the number of instances
         */
        public int getObjectCount()
        {
            return objectCount;
        }

        /**
         * the total size occupied by instances of this class that were discarded
         * @return the total size
         */
        public long getShallowHeapSize()
        {
            return shallowHeapSize;
        }

        /**
         * the actual address of the class
         * @return the class address
         * @since 1.0
         */
        public long getClassAddress()
        {
            return classAddress;
        }
    }

    private static final long serialVersionUID = 1L;

    private List<Record> histogram;
    
    private transient ISnapshot snapshot;

    public UnreachableObjectsHistogram(Collection<Record> records)
    {
        histogram = new ArrayList<Record>(records);
    }

    public List<Record> getRecords()
    {
        return Collections.unmodifiableList(histogram);
    }

    // //////////////////////////////////////////////////////////////
    // result table, icon provider
    // //////////////////////////////////////////////////////////////

    public ResultMetaData getResultMetaData()
    {
        return null;
    }

    public Column[] getColumns()
    {
        return new Column[] { new Column(Messages.Column_ClassName), //
                        new Column(Messages.Column_Objects, long.class), //
                        new Column(Messages.Column_ShallowHeap, long.class).sorting(SortDirection.DESC) };
    }

    public int getRowCount()
    {
        return histogram.size();
    }

    public Object getRow(int rowId)
    {
        return histogram.get(rowId);
    }

    public Object getColumnValue(Object row, int columnIndex)
    {
        Record r = (Record) row;
        switch (columnIndex)
        {
            case 0:
                return r.getClassName();
            case 1:
                return r.getObjectCount();
            case 2:
                return r.getShallowHeapSize();
        }

        return null;
    }

    public IContextObject getContext(Object row)
    {
        final Record record = (Record) row;

        long classAddress = record.getClassAddress();
        if (classAddress == 0)
            return null;
        
        final int classId;
        try 
        {
            classId = snapshot.mapAddressToId(classAddress);
        } 
        catch (SnapshotException e)
        {
            // Class not found in dump as unreachable and discarded
            return null;
        }

        return new IContextObjectSet()
        {
            public int getObjectId()
            {
                return classId;
            }

            public int[] getObjectIds()
            {
                return new int[0];
            }

            public String getOQL()
            {
                return null;
            }
        };
    }

    public URL getIcon(Object row)
    {
        return Icons.CLASS;
    }

    /**
	 * @since 1.0
	 */
    public void setSnapshot(ISnapshot snapshot)
    {
        // Stop the user changing this!
        if (this.snapshot == null)
            this.snapshot = snapshot;
    }
}

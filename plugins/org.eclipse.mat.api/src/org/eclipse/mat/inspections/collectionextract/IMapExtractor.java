/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG, IBM Corporation and others
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
 *    James Livingston - expose collection utils as API
 *******************************************************************************/
package org.eclipse.mat.inspections.collectionextract;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;

/**
 * MapExtractors are used to extract from the heap dump the contents of an
 * object which represents a map of a certain type. It knows the internal
 * details of how the map contents are stored and if the collection has certain
 * properties or not
 * 
 * @since 1.5
 */
public interface IMapExtractor extends ICollectionExtractor
{
    /**
     * Check if the extractor can calculate collision ratio
     * 
     * @return true if @{@link #getCollisionRatio(IObject)} could be called
     * @see #getCollisionRatio(IObject)
     */
    boolean hasCollisionRatio();

    /**
     * Calculates the collision ratio in the collection.
     * Could be an estimate.
     * @param collection
     *            - the collection to calculate the collision ratio of
     * @return Double number of elements with colliding keys / size
     * @throws SnapshotException
     * @see #hasCollisionRatio()
     */
    Double getCollisionRatio(IObject collection) throws SnapshotException;

    /**
     * Extracts the contents of a map (i.e. an IObject representing a Map) and
     * provides an Iterator over them
     * 
     * @param collection
     *            - the map to extract contents from
     * @return an Iterator over the entries. If the original Map had an Entry
     *         object, the content of the iterator would usually be EntryObject
     * @throws SnapshotException
     */
    Iterator<Map.Entry<IObject, IObject>> extractMapEntries(IObject collection) throws SnapshotException;

    public class EntryObject implements Map.Entry<IObject, IObject>, IObject
    {

        private static final long serialVersionUID = 1L;
        private final IObject self;
        private final IObject key;
        private final IObject value;

        public EntryObject(IObject self, IObject key, IObject value)
        {
            this.self = self;
            this.key = key;
            this.value = value;
        }

        public IObject getKey()
        {
            return key;
        }

        public IObject getValue()
        {
            return value;
        }

        public int getObjectId()
        {
            return self.getObjectId();
        }

        public long getObjectAddress()
        {
            return self.getObjectAddress();
        }

        public IClass getClazz()
        {
            return self.getClazz();
        }

        public long getUsedHeapSize()
        {
            return self.getUsedHeapSize();
        }

        public long getRetainedHeapSize()
        {
            return self.getRetainedHeapSize();
        }

        public String getTechnicalName()
        {
            return self.getTechnicalName();
        }

        public String getClassSpecificName()
        {
            return self.getClassSpecificName();
        }

        public String getDisplayName()
        {
            return self.getDisplayName();
        }

        public List<NamedReference> getOutboundReferences()
        {
            return self.getOutboundReferences();
        }

        public Object resolveValue(String field) throws SnapshotException
        {
            return self.resolveValue(field);
        }

        public GCRootInfo[] getGCRootInfo() throws SnapshotException
        {
            return self.getGCRootInfo();
        }

        public ISnapshot getSnapshot()
        {
            return self.getSnapshot();
        }

        public IObject setValue(IObject value)
        {
            throw new IllegalArgumentException();
        }

        public String toString()
        {
            return "" + getKey() + "=" + getValue(); //$NON-NLS-1$ //$NON-NLS-2$
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + ((self == null) ? 0 : self.hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EntryObject other = (EntryObject) obj;
            if (key == null)
            {
                if (other.key != null)
                    return false;
            }
            else if (!key.equals(other.key))
                return false;
            if (self == null)
            {
                if (other.self != null)
                    return false;
            }
            else if (!self.equals(other.self))
                return false;
            if (value == null)
            {
                if (other.value != null)
                    return false;
            }
            else if (!value.equals(other.value))
                return false;
            return true;
        }

    }
}

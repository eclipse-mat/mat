/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.results.ListResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.Units;

@CommandName("heap_dump_overview")
@Category(Category.HIDDEN)
public class HeapDumpInfoQuery implements IQuery
{
    public static class TextEntry
    {
        private String propertyValue;
        private String propertyName;

        public TextEntry(String propertyName, String propertyValue)
        {
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
        }

        public String getPropertyValue()
        {
            return propertyValue;
        }

        public String getPropertyName()
        {
            return propertyName;
        }

    }

    /**
     * Provides translatable names for the columns.
     * @author ajohnson
     *
     */
    public static class TextEntryBeanInfo extends SimpleBeanInfo
    {
        public TextEntryBeanInfo()
        {
        }
        public PropertyDescriptor[] getPropertyDescriptors()
        {
            PropertyDescriptor ret[];
            try
            {
                final PropertyDescriptor propertyDescriptor1 = new PropertyDescriptor("propertyName", TextEntry.class, "getPropertyName", null) //$NON-NLS-1$ //$NON-NLS-2$
                {
                    public String getDisplayName()
                    {
                        return Messages.HeapDumpInfoQuery_PropertyName;
                    }
                };
                final PropertyDescriptor propertyDescriptor2 = new PropertyDescriptor("propertyValue", TextEntry.class, "getPropertyValue", null) //$NON-NLS-1$ //$NON-NLS-2$
                {
                    public String getDisplayName()
                    {
                        return Messages.HeapDumpInfoQuery_ProperyValue;
                    }
                };
                ret = new PropertyDescriptor[] { propertyDescriptor1, propertyDescriptor2 };
            }
            catch (IntrospectionException e)
            {
                ret = null;
            }
            return ret;
        }
    }

    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SnapshotInfo info = snapshot.getSnapshotInfo();

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        List<TextEntry> entries = new ArrayList<TextEntry>(12);
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_UsedHeapDump, getUsedHeapInMb(info
                        .getUsedHeapSize())));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumObjects, MessageUtil.format(Messages.HeapDumpInfoQuery_NumObjectsFormat, 
                        info.getNumberOfObjects())));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumClasses, MessageUtil.format(Messages.HeapDumpInfoQuery_NumClassesFormat,
                        info.getNumberOfClasses())));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumClassLoaders, MessageUtil.format(Messages.HeapDumpInfoQuery_NumClassLoadersFormat,
                        info.getNumberOfClassLoaders())));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumGCRoots, MessageUtil.format(Messages.HeapDumpInfoQuery_NumGCRootsFormat,
                        info.getNumberOfGCRoots())));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_HeapFormat, info.getProperty("$heapFormat").toString())); //$NON-NLS-1$
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_JVMVersion, info.getJvmInfo()));
        if (info.getCreationDate() != null)
        {
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_Time, MessageUtil.format(Messages.HeapDumpInfoQuery_TimeFormat, info.getCreationDate())));
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_Date, MessageUtil.format(Messages.HeapDumpInfoQuery_DateFormat, info.getCreationDate())));
        }
        else
        {
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_Time, null));
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_Date, null));
        }
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_IdentifierSize, getSize(info.getIdentifierSize())));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_FilePath, info.getPath()));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_FileLength, MessageUtil.format(Messages.HeapDumpInfoQuery_FileLengthFormat, (new File(info.getPath())).length())));

        return new ListResult(TextEntry.class, entries, "propertyName", "propertyValue"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private String getUsedHeapInMb(long usedHeapSize)
    {
        return Units.Storage.of(usedHeapSize).format(usedHeapSize);

    }

    private String getSize(int identifierSize)
    {
        switch (identifierSize)
        {
            case 0:
                return null;
            case 4:
                return Messages.HeapDumpInfoQuery_32bit;
            case 8:
                return Messages.HeapDumpInfoQuery_64bit;
            default:
                return String.valueOf(identifierSize);
        }
    }
}

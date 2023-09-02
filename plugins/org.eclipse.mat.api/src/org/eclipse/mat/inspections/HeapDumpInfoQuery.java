/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
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
package org.eclipse.mat.inspections;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collections.HashEntriesQuery;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.results.ListResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.Units;

import com.ibm.icu.text.NumberFormat;

@Category(Category.HIDDEN)
@CommandName("heap_dump_overview")
@Icon("/META-INF/icons/heapdump_details.gif")
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
    public SnapshotInfo info;

    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception
    {
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
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_JVMVersion, getJvmInfo(listener)));
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
        if (info.getIdentifierSize() == 8) 
        {
            Boolean useCompressedOops = (Boolean) info.getProperty("$useCompressedOops"); //$NON-NLS-1$
            if (useCompressedOops != null)
            {
                entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_UseCompressedOops, useCompressedOops.toString()));
            }
        }
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_FilePath, info.getPath()));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_FileLength, MessageUtil.format(Messages.HeapDumpInfoQuery_FileLengthFormat, (new File(info.getPath())).length())));
        Serializable identifier = info.getProperty("$runtimeId"); //$NON-NLS-1$
        if (identifier instanceof String)
        {
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_MultipleSnapshotIdentifier, identifier.toString()));
        }
        NumberFormat percentFormatter = NumberFormat.getPercentInstance();
        percentFormatter.setMaximumFractionDigits(0);
        Serializable discard_ratio = info.getProperty("discard_ratio"); //$NON-NLS-1$
        if (discard_ratio instanceof Integer)
        {
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_DiscardRatio, MessageUtil.format(Messages.HeapDumpInfoQuery_DiscardRatio_format, percentFormatter.format((Integer)discard_ratio * 0.01))));
        }
        Serializable discard_pattern = info.getProperty("discard_pattern"); //$NON-NLS-1$
        if (discard_pattern instanceof String)
        {
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_DiscardPattern, discard_pattern.toString()));
        }
        Serializable discard_offset = info.getProperty("discard_offset"); //$NON-NLS-1$
        if (discard_offset instanceof Integer)
        {
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_DiscardOffset, MessageUtil.format(Messages.HeapDumpInfoQuery_DiscardOffset_format, percentFormatter.format((Integer)discard_offset * 0.01))));
        }
        Serializable discard_seed = info.getProperty("discard_seed"); //$NON-NLS-1$
        if (discard_seed instanceof Integer)
        {
            entries.add(new TextEntry(Messages.HeapDumpInfoQuery_DiscardSeed, MessageUtil.format(Messages.HeapDumpInfoQuery_DiscardSeed_format, discard_seed)));
        }

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

    /**
     * Return the typical result from 'java -version'.
     * @return a String with the JVM version information
     */
    private String getJvmInfo(IProgressListener listener) throws SnapshotException
    {
        // Available directly from the snapshot info?
        String v = info.getJvmInfo();
        if (v != null)
            return v;
        // Try from the system properties instead.
        try 
        {
            HashEntriesQuery.Result result = (HashEntriesQuery.Result)SnapshotQuery.lookup("system_properties", snapshot).execute(listener); //$NON-NLS-1$
            if (result == null)
                return null;
            int rows = result.getRowCount();
            Map <String,String> props = new HashMap<String, String>();
            for (int i = 0; i < rows; ++i)
            {
                Object row = result.getRow(i);
                String key = (String)result.getColumnValue(row, 1);
                String value = (String)result.getColumnValue(row, 2);
                props.put(key, value);
            }
            StringBuilder sb = new StringBuilder();
            /*
             * For example:
               openjdk version "17.0.4" 2022-07-19
               OpenJDK Runtime Environment (17.0.4+8)
               OpenJDK 64-Bit Server VM (build 17.0.4+8, mixed mode)
             */
            String version = props.get("java.version"); //$NON-NLS-1$
            if (version == null || "<null>".equals(version)) //$NON-NLS-1$
                version = props.get("java.specification.version"); //$NON-NLS-1$
            if (version == null || "<null>".equals(version)) //$NON-NLS-1$
                return null;
            String versionDate = props.get("java.version.date"); //$NON-NLS-1$ 
            String runtimeName = props.get("java.runtime.name"); //$NON-NLS-1$
            String runtimeVersion = props.get("java.runtime.version"); //$NON-NLS-1$
            String vmName = props.get("java.vm.name"); //$NON-NLS-1$
            String vminfo = props.get("java.vm.info"); //$NON-NLS-1$
            
            // java/openjdk (but doesn't do jre)
            String vname;
            if (runtimeName != null)
                vname = runtimeName;
            else if (vmName != null)
                vname = vmName;
            else
                vname = "java"; //$NON-NLS-1$
            vname = vname.split("[ (]")[0].toLowerCase(Locale.ROOT); //$NON-NLS-1$
            sb.append(vname).append(" version \"").append(version).append('"'); //$NON-NLS-1$
            if (versionDate != null)
                sb.append(' ').append(versionDate);
            
            sb.append('\n').append(runtimeName).append(" (") //$NON-NLS-1$
                .append(runtimeVersion).append(')');
            

            sb.append('\n').append(vmName).append(" (build ") //$NON-NLS-1$
                .append(props.get("java.vm.version")).append(", ").append(vminfo) //$NON-NLS-1$ //$NON-NLS-2$
                .append(')');
            return sb.toString();
        }
        catch (SnapshotException e)
        {
            return null;
        }
    }
}

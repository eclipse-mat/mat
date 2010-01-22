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
package org.eclipse.mat.inspections;

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
import org.eclipse.mat.util.Units;

import com.ibm.icu.text.NumberFormat;

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

    @Argument
    public ISnapshot snapshot;

    static NumberFormat numberFormatter = NumberFormat.getNumberInstance();

    public IResult execute(IProgressListener listener) throws Exception
    {
        SnapshotInfo info = snapshot.getSnapshotInfo();

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        List<TextEntry> entries = new ArrayList<TextEntry>(6);
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_UsedHeapDump, getUsedHeapInMb(info
                        .getUsedHeapSize())));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumObjects, numberFormatter.format(
                        info.getNumberOfObjects()).toString()));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumClasses, numberFormatter.format(
                        info.getNumberOfClasses()).toString()));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumClassLoaders, numberFormatter.format(
                        info.getNumberOfClassLoaders()).toString()));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_NumGCRoots, numberFormatter.format(
                        info.getNumberOfGCRoots()).toString()));
        entries.add(new TextEntry(Messages.HeapDumpInfoQuery_Column_IdentifierSize, getSize(info.getIdentifierSize())));

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

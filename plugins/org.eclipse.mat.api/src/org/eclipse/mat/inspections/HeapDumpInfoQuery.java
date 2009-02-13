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

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.ListResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.util.IProgressListener;

@Name("Heap Dump Overview")
@Category(Category.HIDDEN)
@Help("Displays heap dump details: number of objects, etc.")
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
        entries.add(new TextEntry("Used heap dump", getUsedHeapInMb(info.getUsedHeapSize())));
        entries.add(new TextEntry("Number of objects", numberFormatter.format(info.getNumberOfObjects()).toString()));
        entries.add(new TextEntry("Number of classes", numberFormatter.format(info.getNumberOfClasses()).toString()));
        entries.add(new TextEntry("Number of classloaders", numberFormatter.format(info.getNumberOfClassLoaders())
                        .toString()));
        entries.add(new TextEntry("Number of GC roots", numberFormatter.format(info.getNumberOfGCRoots()).toString()));
        entries.add(new TextEntry("Identifier size", getSize(info.getIdentifierSize())));

        return new ListResult(TextEntry.class, entries, "propertyName", "propertyValue");
    }

    private String getUsedHeapInMb(long usedHeapSize)
    {
        double roundedHeapSize = Math.round(usedHeapSize / 10000);
        return new DecimalFormat("#,##0.0 M").format(roundedHeapSize / 100);

    }

    private String getSize(int identifierSize)
    {
        switch (identifierSize)
        {
            case 0:
                return null;
            case 4:
                return "32bit";
            case 8:
                return "64bit";
            default:
                return String.valueOf(identifierSize);
        }
    }
}

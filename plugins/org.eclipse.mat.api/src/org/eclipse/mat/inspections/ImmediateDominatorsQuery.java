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

import java.net.URL;
import java.util.regex.Pattern;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.snapshot.DominatorsSummary;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.DominatorsSummary.ClassDominatorRecord;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;


@Name("Immediate Dominators")
@Icon("/META-INF/icons/immediate_dominators.gif")
@Help("Find and aggregate on class level all objects dominating a given set of objects.\n\n"
                + "The immediate dominators of all char arrays (immediate_dominators char[]) "
                + "are all objects responsible for keeping the char[] alive. The result will "
                + "contain most likely java.lang.String objects. Now add the skip pattern java.*, "
                + "and you will see the non-JDK classes responsible for the char arrays.")
public class ImmediateDominatorsQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false, advice = Advice.CLASS_NAME_PATTERN, flag = "skip")
    @Help("A regular expression specifying which dominators to skip while going up the dominator tree. "
                    + "If the dominator of an object matches the pattern, then the dominator of the "
                    + "dominator will be taken, and so on, until an object not matching the skip pattern "
                    + "is reached.")
    public Pattern skipPattern = Pattern.compile("java.*|com\\.sun\\..*");

    public IResult execute(IProgressListener listener) throws Exception
    {
        DominatorsSummary summary = snapshot.getDominatorsOf(objects.getIds(listener), skipPattern, listener);

        return new ResultImpl(summary);
    }

    public static class ResultImpl implements IResultTable, IIconProvider
    {
        DominatorsSummary summary;

        public ResultImpl(DominatorsSummary summary)
        {
            this.summary = summary;
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder() //

                            .addContext(new ContextProvider("Objects")
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    return getObjects(row);
                                }
                            }) //

                            .addContext(new ContextProvider("Dominated Objects")
                            {
                                @Override
                                public IContextObject getContext(Object row)
                                {
                                    return getDominatedObjects(row);
                                }
                            }) //

                            .build();
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column("Class name"), //
                            new Column("Objects", Long.class), //
                            new Column("Dom. Objects", Long.class), //
                            new Column("Shallow Heap", Long.class), //
                            new Column("Dom. Shallow Heap", Long.class).sorting(SortDirection.DESC) };
        }

        public int getRowCount()
        {
            return summary.getClassDominatorRecords().length;
        }

        public ClassDominatorRecord getRow(int rowId)
        {
            return summary.getClassDominatorRecords()[rowId];
        }

        public URL getIcon(Object row)
        {
            return Icons.CLASS;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            switch (columnIndex)
            {
                case 0:
                    return record.getClassName();
                case 1:
                    return record.getDominatorCount();
                case 2:
                    return record.getDominatedCount();
                case 3:
                    return record.getDominatorNetSize();
                case 4:
                    return record.getDominatedNetSize();
            }

            return null;
        }

        public IContextObject getContext(Object row)
        {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            if (record.getClassId() >= 0)
            {
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return record.getClassId();
                    }
                };
            }
            else
            {
                return null;
            }
        }

        IContextObject getObjects(Object row)
        {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            if (record.getClassId() >= 0)
            {
                return new IContextObjectSet()
                {
                    public int getObjectId()
                    {
                        return record.getClassId();
                    }

                    public int[] getObjectIds()
                    {
                        return record.getDominators();
                    }

                    public String getOQL()
                    {
                        return null;
                    }
                };
            }
            else
            {
                return null;
            }
        }

        IContextObject getDominatedObjects(Object row)
        {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            return new IContextObjectSet()
            {
                public int getObjectId()
                {
                    return record.getClassId();
                }

                public int[] getObjectIds()
                {
                    return record.getDominated();
                }

                public String getOQL()
                {
                    return null;
                }
            };
        }
    }
}

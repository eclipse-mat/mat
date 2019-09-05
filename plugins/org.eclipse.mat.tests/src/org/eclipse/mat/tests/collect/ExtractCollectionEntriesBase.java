/*******************************************************************************
 * Copyright (c) 2010, 2018 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation - moved from ExtractCollectionEntriesTest
 *    Andrew Johnson - lots of extra tests including all Java 7 collections
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;

import java.io.Serializable;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.rules.ErrorCollector;

public class ExtractCollectionEntriesBase
{
    /**
     * Hamcrest style matcher for regular expression matching.
     * Hamcrest 1.1 doesn't have this.
     * @param regex
     * @return true if the string matches the pattern
     */
    public static Matcher<java.lang.String> matchesPattern(final java.lang.String regex)
    {
        return new TypeSafeMatcher<String>() {
            Pattern pat = Pattern.compile(regex);

            public void describeTo(Description description)
            {
                description.appendText("a string matching '" + regex + "'");
            }

            @Override
            protected boolean matchesSafely(String item)
            {
                return pat.matcher(item).matches();
            }

        };
    }

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    void checkListObjects(long objAddress, int numEntries, boolean checkVals, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        String prefix = obj.getTechnicalName()+": ";
        SnapshotQuery query = SnapshotQuery.parse("extract_list_values 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        String name = obj.getClazz().getName();
        try
        {
            IResult result = query.execute(new VoidProgressListener());
            IResultTree table = (IResultTree) result;
            if (table != null)
            {
                int rowCount = table.getElements().size();

                collector.checkThat(MessageUtil.format(
                                "Expected to extract {0} entries from list {1} [{2}], but got {3} entries in the result", //$NON-NLS-1$
                                numEntries, obj, snapshot.getSnapshotInfo().getPath(), rowCount), rowCount,
                                equalTo(numEntries));
                for (Object o : table.getElements())
                {
                    IContextObject co = table.getContext(o);
                    IClass cls = snapshot.getClassOf(co.getObjectId());
                    collector.checkThat(prefix + "List should contain Strings", cls.getName(), equalTo("java.lang.String"));
                    IObject lobj = snapshot.getObject(co.getObjectId());

                    String cv = lobj.getClassSpecificName();
                    if (cv != null)
                    {
                        if (checkVals)
                        {
                            collector.checkThat(prefix + "List entry should start with the type", cv, startsWith(name));
                        }
                        collector.checkThat(prefix + "Should end with number or aAbB", cv,
                                    matchesPattern(".*([0-9]+|[aAbB]+)$"));
                    }
                    else
                    {
                        collector.checkThat(prefix + "snapshot type is DTFJ-PHD when list item contents is null", snapshot.getSnapshotInfo().getProperty("$heapFormat"), equalTo((Serializable)"DTFJ-PHD"));
                    }
                }
            }
        }
        catch (IllegalArgumentException e)
        {
            collector.addError(e);
        }
    }

    /**
     * Checks all the entries are the special values from CreateCollectionDump
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    protected void checkHashSetObjects(long objAddress, int numEntries, boolean checkVals, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        String prefix = obj.getTechnicalName()+": ";
        SnapshotQuery query = SnapshotQuery.parse("hash_set_values 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        String name = obj.getClazz().getName();

        try
        {
            IResult result = query.execute(new VoidProgressListener());
            IResultTree table = (IResultTree) result;
            if (table != null)
            {
                int rowCount = table.getElements().size();

                collector.checkThat(MessageUtil.format(
                                "Expected to extract {0} entries from hash set {1} [{2}], but got {3} entries in the result", //$NON-NLS-1$
                                numEntries, obj, snapshot.getSnapshotInfo().getPath(), rowCount), rowCount,
                                equalTo(numEntries));
                for (Object o : table.getElements())
                {
                    IContextObject co = table.getContext(o);
                    IClass cls = snapshot.getClassOf(co.getObjectId());
                    collector.checkThat(prefix + "hash set should contain Strings", cls.getName(),
                                    equalTo("java.lang.String"));
                    IObject cobj = snapshot.getObject(co.getObjectId());
                    String cv = cobj.getClassSpecificName();
                    if (cv != null)
                    {
                        if (checkVals)
                        {
                            collector.checkThat(prefix + "Hash set entry should start with the type", cv, startsWith(name));
                        }
                        collector.checkThat(prefix + "Should end with number or aAbB", cv,
                                    matchesPattern(".*([0-9]+|[aAbB]+)$"));
                    }
                    else
                    {
                        collector.checkThat(prefix+"snapshot type is DTFJ-PHD when hash set item contents is null", snapshot.getSnapshotInfo().getProperty("$heapFormat"), equalTo((Serializable)"DTFJ-PHD"));
                    }
                }
            }
        }
        catch (IllegalArgumentException e)
        {
            collector.addError(e);
        }
    }

    protected void checkCollection(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        checkMap(objAddress, numEntries, snapshot);
        checkHashEntries(objAddress, numEntries, snapshot, false, false);
    }

    /**
     * HashMap entries.
     * Check keys and values.
     * Map:
     * 123 : full.class.name:123
     * Set from map
     * full.class.name:123 : <other>
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @param checkValueString
     * @param isMap whether this is a normal test map, or a map from a set
     * @throws SnapshotException
     */
    protected void checkHashEntries(long objAddress, int numEntries, ISnapshot snapshot, boolean checkValueString, boolean isMap) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        String prefix = obj.getTechnicalName()+": ";
        SnapshotQuery query = SnapshotQuery.parse("hash_entries 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$

        IResult result;
        try
        {
            result = query.execute(new VoidProgressListener());
        }
        catch (UnsupportedOperationException e)
        {
            collector.checkThat(prefix + "snapshot type is DTFJ-PHD when unsupported operation for hash entries", snapshot.getSnapshotInfo().getProperty("$heapFormat"), equalTo((Serializable)"DTFJ-PHD"));
            return;
        }
        IResultTable table = (IResultTable) result;
        int rowCount = table.getRowCount();

        collector.checkThat(MessageUtil.format(prefix+"Expected to extract {0} entries from collection {1} [{2}], but got {3} entries in the result", //$NON-NLS-1$
                        numEntries, obj, snapshot.getSnapshotInfo().getPath(), rowCount), rowCount, equalTo(numEntries));
        // Check that at least one key and value value differs
        boolean diff = rowCount == 0;
        for (int i = 0; i < rowCount; ++i)
        {
            Object row = table.getRow(i);
            Object k1 = table.getColumnValue(row, 1);
            Object v1 = table.getColumnValue(row, 2);
            if (k1 != null ? !k1.equals(v1) : v1 != null)
            {
                diff = true;
            }
            if (k1 != null)
            {
                collector.checkThat(prefix+"Key should be an String", k1, instanceOf(String.class));
                if (isMap && checkValueString && k1 instanceof String)
                {
                    collector.checkThat(prefix+"Key should be a number or aAbB", (String)k1, matchesPattern("[0-9]+|[aAbB]+"));
                }
                if (checkValueString && k1 instanceof String)
                {
                    collector.checkThat(prefix+"Key should end with a number or aAbB", (String)k1, matchesPattern(".*([0-9]+|[aAbB]+)$"));
                }
                if (!isMap && checkValueString && k1 instanceof String)
                {
                    // Some of the sets are KeySets from Maps so have numeric/aAbB not class names
                    collector.checkThat(prefix+"Key contains name of collection class or a number or aAbB", (String)k1, anyOf(containsString(obj.getClazz().getName()), matchesPattern("[0-9]+|[aAbB]+")));
                }
            }
            if (v1 != null)
            {
                collector.checkThat(prefix+"Value should be an String", v1, instanceOf(String.class));
            }
            if (isMap && checkValueString && k1 instanceof String &&  v1 instanceof String)
            {
                collector.checkThat(prefix+"Value contains key", (String)v1, containsString((String)k1));
            }
            if (isMap && checkValueString && v1 instanceof String)
            {
                collector.checkThat(prefix+"Value contains name of collection class", (String)v1, containsString(obj.getClazz().getName()));
            }

            IContextObject ctx = table.getContext(row);
            collector.checkThat(prefix+"Row object should be the map", ctx.getObjectId(), equalTo(obj.getObjectId()));
        }
        if (checkValueString)
        {
            collector.checkThat(MessageUtil.format(
                            "Expected some differing keys and values {0} from collection {1} [{2}]", obj, snapshot
                            .getSnapshotInfo().getPath()), diff, equalTo(true));
        }
    }

    /**
     * Also run the size query
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    protected void checkCollectionSize(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        String prefix = obj.getTechnicalName()+": ";
        SnapshotQuery query2 = SnapshotQuery.parse("collections_grouped_by_size 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        collector.checkThat("Rows for size "+obj, rowCount2, equalTo(1));
        if (rowCount2 == 1)
        {
            Object row = table2.getRow(0);
            int sizeBucket = (Integer)table2.getColumnValue(row, 0);
            collector.checkThat(prefix+"size "+obj, sizeBucket, equalTo(numEntries));
        }
    }

    protected void checkMap(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        checkCollectionSize(objAddress, numEntries, snapshot);
        checkCollectionFillRatio(objAddress, numEntries, snapshot);
        checkMapCollisionRatio(objAddress, numEntries, snapshot);
    }

    protected void checkList(long objAddress, int numEntries, boolean checkVals, ISnapshot snapshot) throws SnapshotException
    {
        checkCollectionSize(objAddress, numEntries, snapshot);
        checkListObjects(objAddress, numEntries, checkVals, snapshot);
    }

    /**
     * Run the fill ratio query
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    protected void checkCollectionFillRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        String prefix = obj.getTechnicalName()+": ";
        SnapshotQuery query2 = SnapshotQuery.parse("collection_fill_ratio 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        String className = snapshot.getClassOf(snapshot.mapAddressToId(objAddress)).getName();
        if (className.equals("java.util.TreeMap") || className.equals("java.util.TreeMap$KeySet") ||
            className.equals("java.util.TreeMap$EntrySet") ||
            className.equals("java.util.concurrent.ConcurrentSkipListMap") || className.equals("java.util.concurrent.ConcurrentSkipListMap$KeySet") ||
            className.equals("java.util.concurrent.ConcurrentSkipListMap$EntrySet") ||
            className.equals("java.util.TreeSet") || className.equals("java.util.concurrent.ConcurrentSkipListSet"))
        {
            // TreeMaps and ConcurrentSkipListMap don't appear in the fill ratio report as they
            // don't have a backing array
            collector.checkThat(prefix+"Fill ratio rows", rowCount2, equalTo(0));
        }
        else
        {
            if (!className.startsWith("java.util.Collections") && !className.equals("javax.script.SimpleBindings"))
            {
                collector.checkThat(prefix+"Fill ratio rows", rowCount2, equalTo(1));
            }
            if (rowCount2 == 1)
            {
                Object row = table2.getRow(0);
                double v = (Double)table2.getColumnValue(row, 0);
                if (numEntries > 0)
                {
                    collector.checkThat(prefix+"Fill ratio value > 0.0", v, greaterThan(0.0));
                    // If there are a lot of collisions the ratio could be greater than one
                    collector.checkThat(prefix+"Fill ratio value <= 1.2", v, lessThanOrEqualTo(1.2));
                }
                else
                {
                    // 1.0 if the size == 0, capacity == 0, 0.0 if the size == 0, capacity > 0
                    collector.checkThat(prefix+"Fill ratio value == 0.0 or 1.0 ", v, isOneOf(0.0, 1.0));
                }
            }
        }
    }

    /**
     * Also run the map collision ratio query
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    protected void checkMapCollisionRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException
    {
        IObject obj = snapshot.getObject(snapshot.mapAddressToId(objAddress));
        String prefix = obj.getTechnicalName()+": ";
        SnapshotQuery query2 = SnapshotQuery.parse("map_collision_ratio 0x" + Long.toHexString(objAddress), snapshot); //$NON-NLS-1$
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        collector.checkThat(prefix+"Rows for Map collision ratio ", rowCount2, equalTo(1));
        if (rowCount2 == 1)
        {
            Object row = table2.getRow(0);
            double v = (Double)table2.getColumnValue(row, 0);
            collector.checkThat(prefix+"Map collision value >= 0.0", v, greaterThanOrEqualTo(0.0));
            // 100% collisions shouldn't be possible, but might be if every entry in the first table maps to 2 keys
            collector.checkThat(prefix+"Map collision value <= 1.0", v, lessThanOrEqualTo(1.0));
            // No collisions possible if no entries
            if (numEntries == 0)
                collector.checkThat(prefix+"Map collision ratio if no entries", v, equalTo(0.0));
            if (numEntries == 1)
                collector.checkThat(prefix+"Map collision ratio if one entry", v, equalTo(0.0));
        }
    }

}

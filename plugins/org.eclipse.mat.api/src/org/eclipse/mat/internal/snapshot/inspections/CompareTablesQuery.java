/*******************************************************************************
 * Copyright (c) 2010, 2020 SAP AG and IBM Corporation. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: SAP AG - initial API and implementation
 * Andrew Johnson - conversion to proper query, set operations via contexts
 ******************************************************************************/
package org.eclipse.mat.internal.snapshot.inspections;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.BytesFormat;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Menu.Entry;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor;
import org.eclipse.mat.util.VoidProgressListener;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.NumberFormat;

@Icon("/META-INF/icons/compare.gif")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/comparingdata.html")
@Menu({ @Entry(options = "-setop ALL")
,@Entry(options = "-mode DIFF_TO_PREVIOUS -prefix -mask \"\\s@ 0x[0-9a-f]+|^\\[[0-9]+\\]$\" -x java.util.HashMap$Node:key java.util.Hashtable$Entry:key java.util.WeakHashMap$Entry:referent java.util.concurrent.ConcurrentHashMap$Node:key")
,@Entry(options = "-mode DIFF_TO_PREVIOUS -prefix -mask \"\\s@ 0x[0-9a-f]+|^\\[[0-9]+\\]$|(?<=\\p{javaJavaIdentifierPart}\\[)\\d+(?=\\])\" -x java.util.HashMap$Node:key java.util.Hashtable$Entry:key java.util.WeakHashMap$Entry:referent java.util.concurrent.ConcurrentHashMap$Node:key")
,@Entry(options = "-mode DIFF_TO_PREVIOUS -prefix -mask \"\\s@ 0x[0-9a-f]+|^\\[[0-9]+\\]$|(?<=\\p{javaJavaIdentifierPart}\\[)\\d+(?=\\])\" -x java.util.HashMap$Node:key java.util.Hashtable$Entry:key java.util.WeakHashMap$Entry:referent java.util.concurrent.ConcurrentHashMap$Node:key -setop ALL")
})
public class CompareTablesQuery implements IQuery
{
    @Argument
    public IStructuredResult[] tables;

    @Argument
    public IQueryContext queryContext;

    @Argument(isMandatory = false)
    public ISnapshot[] snapshots;

    @Argument(isMandatory = false)
    public Mode mode = Mode.ABSOLUTE;

    @Argument(isMandatory = false)
    public Operation setOp = Operation.NONE;

    @Argument(isMandatory = false)
    public int keyColumn = 1;

    @Argument(isMandatory = false)
    public Pattern mask;

    @Argument(isMandatory = false)
    public String replace;

    @Argument(isMandatory = false)
    public boolean prefix;

    @Argument(isMandatory = false)
    public boolean suffix;

    // @Argument(isMandatory = false)
    public boolean addrefs = true;

    @Argument(isMandatory = false, flag = "x")
    public String[] extraReferences;

    @Argument(isMandatory = false, flag = "xfile")
    public File extraReferencesListFile;

    private boolean[] sameSnapshot;

    public enum Mode
    {
        ABSOLUTE("ABSOLUTE"), // //$NON-NLS-1$
        DIFF_TO_FIRST("DIFF_TO_FIRST"), // //$NON-NLS-1$
        DIFF_TO_PREVIOUS("DIFF_TO_PREVIOUS"), //$NON-NLS-1$
        DIFF_RATIO_TO_FIRST("DIFF_RATIO_TO_FIRST"), // //$NON-NLS-1$
        DIFF_RATIO_TO_PREVIOUS("DIFF_RATIO_TO_PREVIOUS"); //$NON-NLS-1$

        String label;

        private Mode(String label)
        {
            this.label = label;
        }

        public String toString()
        {
            return label;
        }
    }

    public enum Operation
    {
        NONE,
        ALL,
        INTERSECTION,
        UNION,
        SYMMETRIC_DIFFERENCE,
        DIFFERENCE,
        REVERSE_DIFFERENCE
    }

    public IResult execute(IProgressListener listener) throws Exception
    {
        if (tables == null) return null;

        // Length 1 table is valid, and we need to process it in case it is from a different snapshot

        // Check key column is present
        for (int i = 0; i < tables.length; ++i)
        {
            if (keyColumn > tables[i].getColumns().length || keyColumn < 1)
                throw new IllegalArgumentException(MessageUtil.format(Messages.CompareTablesQuery_MissingKeyColumn, keyColumn, i + 1));
        }

        IStructuredResult base = tables[0];
        Column[] baseColumns = base.getColumns();
        Column key = baseColumns[keyColumn-1];

        sameSnapshot = new boolean[tables.length];
        ISnapshot sn = (ISnapshot)queryContext.get(ISnapshot.class, null);
        boolean foundTree = false;
        for (int i = 0; i < tables.length; ++i)
        {
            sameSnapshot[i] = (snapshots[i] == null || sn.equals(snapshots[i]));
            if (tables[i] instanceof IResultTree)
                foundTree = true;
        }

        List<ComparedColumn> attributes = new ArrayList<ComparedColumn>();
        for (int i = 0; i < baseColumns.length; i++)
        {
            if (i == keyColumn-1)
                continue;
            // Check for duplicate column names
            int prevDuplicateCol = -1;
            for (int k = 0; k < i - 1; ++k)
            {
                if (baseColumns[i].getLabel().equals(baseColumns[k].getLabel()))
                    prevDuplicateCol = k > keyColumn - 1 ? k - 1 : k; // Adjust for key column
            }
            int[] indexes = new int[tables.length];
            for (int j = 0; j < indexes.length; j++)
            {
                if (prevDuplicateCol >= 0)
                {
                    // Start search after previous found column
                    int pc = attributes.get(prevDuplicateCol).getColumnIndexes()[j];
                    int ci = getColumnIndex(baseColumns[i].getLabel(), tables[j], pc + 1);
                    // Not found, so duplicate the last column
                    if (ci == -1)
                        ci = pc;
                    indexes[j] = ci;
                }
                else
                {
                    indexes[j] = getColumnIndex(baseColumns[i].getLabel(), tables[j], 0);
                }
            }
            attributes.add(new ComparedColumn(baseColumns[i], indexes, true));
        }

        collectExtraRefs();
        return foundTree ? new ComparisonResultTree(mergeKeys(null, listener), key, attributes, mode, setOp)
                        : new ComparisonResultTable(mergeKeys(null, listener), key, attributes, mode, setOp);
    }

    private int getColumnIndex(String name, IStructuredResult table, int colstart)
    {
        Column[] columns = table.getColumns();
        for (int i = colstart; i < columns.length; i++)
        {
            if (columns[i].getLabel().equals(name)) return i;
        }
        return -1;
    }

    /**
     * Calculate extra parts for the key.
     * @param table index
     * @param row
     * @return a String or null
     */
    String extraKey(int table, Object row)
    {
        final String noextra = null;
        if (extraReferences == null || extraReferences.length == 0)
            return noextra;
        if (snapshots[table] == null)
            return noextra;
        IContextObject ctx = tables[table].getContext(row);
        if (ctx == null)
            return noextra;
        int objId = ctx.getObjectId();
        if (objId == -1)
            return noextra;
        IObject obj;
        try
        {
            obj = snapshots[table].getObject(objId);
        }
        catch (SnapshotException e)
        {
            return noextra;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : extraReferences)
        {
            String p1[] = s.split(":", 2); //$NON-NLS-1$
            if (p1.length < 1)
                continue;
            else if (p1.length < 2)
            {
                // Just the class name, so resolve just the object
                String val = obj.getClassSpecificName();
                if (val != null)
                {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(val);
                }
                continue;
            }
            try
            {
                if (obj.getClazz().doesExtend(p1[0]))
                {
                    for (String field : p1[1].split(",")) //$NON-NLS-1$
                    {
                        Object o;
                        try
                        {
                            o = obj.resolveValue(field);
                        }
                        catch (SnapshotException e)
                        {
                            continue;
                        }
                        String val;
                        if (o instanceof IObject)
                        {
                            val = ((IObject)o).getClassSpecificName();
                        }
                        else if (o != null)
                        {
                            val = o.toString();
                        }
                        else
                        {
                            val = null;
                        }

                        if (val != null)
                        {
                            if (sb.length() > 0)
                                sb.append(' ');
                            sb.append(val);
                        }
                    }
                }
            }
            catch (SnapshotException e)
            {
            }
        }
        if (sb.length() > 0)
            return sb.toString();
        return noextra;
    }

    /**
     * Calculate extra prefix for the key.
     * Look for the immediate dominator and see if any fields point to this.
     * Also do this for small object arrays.
     * @param table index
     * @param row
     * @return a String or null
     */
    String extraPrefix(int table, Object row)
    {
        // Only generate prefixes for small dominator arrays
        final int SMALL_ARRAY_SIZE = 512;
        final String noextra = null;
        if (!addrefs)
            return noextra;
        if (snapshots[table] == null)
            return noextra;
        IContextObject ctx = tables[table].getContext(row);
        if (ctx == null)
            return noextra;
        int objId = ctx.getObjectId();
        if (objId == -1)
            return noextra;
        IObject obj;
        try
        {
            obj = snapshots[table].getObject(objId);
        }
        catch (SnapshotException e)
        {
            return noextra;
        }
        StringBuilder sb = new StringBuilder();
        try
        {
            int immdom = snapshots[table].getImmediateDominatorId(obj.getObjectId());
            if (immdom >= 0)
            {
                if (!snapshots[table].isArray(immdom))
                {
                    IObject immobj = snapshots[table].getObject(immdom);
                    for (NamedReference ref : immobj.getOutboundReferences())
                    {
                        if (ref.getObjectId() == objId)
                        {
                            if (sb.length() > 0)
                                sb.append(',');
                            sb.append(ref.getName());
                        }
                    }
                }
                else
                {
                    if (snapshots[table].getClassOf(immdom).getObjectId() == obj.getObjectId())
                        sb.append("<class>"); //$NON-NLS-1$
                    // Big arrays could be expensive to read
                    if (snapshots[table].getHeapSize(immdom) < SMALL_ARRAY_SIZE)
                    {
                        IObject immobj = snapshots[table].getObject(immdom);
                        // Don't get named references for object array - expensive
                        if (immobj instanceof IObjectArray)
                        {
                            IObjectArray immarr = (IObjectArray)immobj;
                            long arr[] = immarr.getReferenceArray();
                            for (int j = 0; j < arr.length; ++j)
                            {
                                if (arr[j] == obj.getObjectAddress())
                                {
                                    if (sb.length() > 0)
                                        sb.append(',');
                                    sb.append('[').append(j).append(']');
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (SnapshotException e1)
        {
        }
        if (sb.length() > 0)
            return sb.toString();
        return noextra;
    }

    /**
     * Collect the extra type+field references.
     */
    void collectExtraRefs() throws IOException
    {
        // extra key refs
        // read the file (if any)
        String[] fromFile = getLinesFromFile();
        if (fromFile != null && fromFile.length > 0)
        {
            if (extraReferences != null)
            {
                // merge from file and manually entered entries
                String[] tmp = new String[fromFile.length + extraReferences.length];
                System.arraycopy(fromFile, 0, tmp, 0, fromFile.length);
                System.arraycopy(extraReferences, 0, tmp, fromFile.length, extraReferences.length);
                extraReferences = tmp;
            }
            else
            {
                extraReferences = fromFile;
            }
        }
    }

    /**
     * Read from a file.
     * @return
     * @throws IOException
     */
    private String[] getLinesFromFile() throws IOException
    {
        if (extraReferencesListFile == null)
            return null;

        BufferedReader in = null;
        List<String> result = new ArrayList<String>();
        try
        {
            in = new BufferedReader(new FileReader(extraReferencesListFile));
            String line = null;
            while ((line = in.readLine()) != null)
            {
                result.add(line);
            }
        }
        finally
        {
            if (in != null)
            {
                in.close();
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * Hold a place for a row when the key is a duplicate.
     */
    static class PlaceHolder
    {
        Object key;
        int pos;
        PlaceHolder(Object key, int pos)
        {
            this.key = key;
            this.pos = pos;
        }
    }

    private List<ComparedRow> mergeKeys(ComparedRow parent, IProgressListener listener)
    {
        SimpleMonitor sm = new SimpleMonitor(Messages.CompareTablesQuery_Comparing, listener, new int[] {60,30,10});
        Map<Object, Object[]> map = new LinkedHashMap<Object, Object[]>();
        int sizes[] = new int[tables.length];
        // Only get elements once
        List<?>elements[] = new List<?>[tables.length];
        // Calculate the total number of rows
        int totalsize = 0;
        for (int i = 0; i < tables.length; i++)
        {
            IStructuredResult table = tables[i];
            int size;
            if (table instanceof IResultTable)
            {
                if (parent == null)
                    size = ((IResultTable)table).getRowCount();
                else
                    size = 0;
            }
            else if (table instanceof IResultTree)
            {
                if (parent == null)
                {
                    elements[i] = ((IResultTree)table).getElements();
                    size = elements[i].size();
                }
                else
                {
                    Object treerow = parent.getRows()[i];
                    if (treerow != null && ((IResultTree)table).hasChildren(treerow))
                    {
                        elements[i] = ((IResultTree)table).getChildren(treerow);
                        size = elements[i].size();
                    }
                    else
                    {
                        size = 0;
                    }
                }
            }
            else
            {
                size = 0;
            }
            sizes[i] = size;
            totalsize += size;
            // For overflow
            if (totalsize < 0)
                totalsize = Integer.MAX_VALUE;
        }
        listener = sm.nextMonitor();
        listener.beginTask(Messages.CompareTablesQuery_Initial, totalsize);
        long sortwork = 0;
        for (int i = 0; i < tables.length; i++)
        {
            listener.subTask(MessageUtil.format(Messages.CompareTablesQuery_InitialComparisonForTable, i + 1));
            IStructuredResult table = tables[i];
            int size = sizes[i];
            List<?>treeRows;
            if (table instanceof IResultTree)
            {
                treeRows = elements[i];
            }
            else
            {
                treeRows = null;
            }
            Map<Object, Integer>lastcache = new HashMap<Object,Integer>();
            for (int j = 0; j < size; j++)
            {

                Object row;
                if (table instanceof IResultTable)
                {
                    row = ((IResultTable)table).getRow(j);
                }
                else if (table instanceof IResultTree)
                {
                    row = treeRows.get(j);
                }
                else
                {
                    row = null;
                }
                Object key = table.getColumnValue(row, keyColumn-1);
                key = modifyKey(i, row, key);
                Object[] rows = map.get(key);
                if (rows == null)
                {
                    rows = new Object[tables.length];
                    map.put(key, rows);
                }
                int ii = 0;
                if (lastcache.containsKey(key))
                {
                    ii = lastcache.get(key);
                }
                while (rows[ii + i] != null)
                {
                    /*
                     * Normally:
                     * rows[rowTable1, rowTableb]
                     * With duplicate keys from one Table we extend the array
                     * rows[rowTable1, rowTable2, rowTable1v2, rowTable2v2, rowTable1v3, null]
                     * etc.
                     * and later convert to multiple rows
                     */
                    ii += tables.length;
                    if (ii >= rows.length)
                    {
                        sortwork -= rows.length * rows.length;
                        // Grow the row array
                        int spare = rows.length / tables.length / 2;
                        rows = Arrays.copyOf(rows, rows.length + (spare + 1) * tables.length);
                        map.put(key, rows);
                        sortwork += rows.length * rows.length;
                    }
                }
                rows[ii + i] = row;
                if (ii > 0)
                {
                    // With many duplicates it can take a long time to find a free slot, so cache the last used
                    lastcache.put(key, ii);
                    // Add a placeholder so that a row goes here
                    map.put(new PlaceHolder(key, ii), new Object[0]);
                }
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
                listener.worked(1);
            }
        }
        listener.done();

        listener = sm.nextMonitor();
        listener.beginTask(Messages.CompareTablesQuery_Initial, totalsize);
        // Match up duplicate keys
        for (Map.Entry<Object, Object[]> entry : map.entrySet())
        {
            Object rows[] = entry.getValue();
            if (rows.length > tables.length)
            {
                listener.subTask(MessageUtil.format(Messages.CompareTablesQuery_ResolvingDuplicateKey, entry.getKey()));
                // Duplicated key, so expand to separate rows
                sortRows(rows);
                // Guess n-squared work
                listener.worked((int)(totalsize * (long)rows.length * rows.length / sortwork));
            }
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
        }
        listener.done();

        listener = sm.nextMonitor();
        listener.beginTask(Messages.CompareTablesQuery_BuildingResult, totalsize);
        List<ComparedRow> result = new ArrayList<ComparedRow>(map.size());
        for (Map.Entry<Object, Object[]> entry : map.entrySet())
        {
            Object key = entry.getKey();
            Object rows[] = entry.getValue();
            if (key instanceof PlaceHolder)
            {
                // A subsequent duplicated key
                PlaceHolder ph = (PlaceHolder)key;
                // Find the real, sorted row list
                rows = map.get(ph.key);
                // Extract the appropriate part
                Object rows1[] = Arrays.copyOfRange(rows, ph.pos, ph.pos + tables.length);
                result.add(new ComparedRow(ph.key, rows1));
            }
            else if (rows.length <= tables.length)
                result.add(new ComparedRow(entry.getKey(), entry.getValue()));
            else
            {
                // Duplicated key, but this is the first record
                Object rows1[] = Arrays.copyOfRange(rows, 0, tables.length);
                result.add(new ComparedRow(entry.getKey(), rows1));
            }
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();
            listener.worked(1);
        }
        listener.done();

        return result;
    }

    private Object modifyKey(int i, Object row, Object key)
    {
        String extrakey = extraKey(i, row);
        if (extrakey != null)
        {
            key = key + " " + extrakey; //$NON-NLS-1$
        }
        if (mask != null && key != null)
        {
            String keystr = key.toString();
            String keystr2 = mask.matcher(keystr).replaceAll(replace == null ? "" : replace); //$NON-NLS-1$
            if (!keystr.equals(keystr2))
            {
                key = keystr2;
            }
        }
        // Fix up decoration
        if (prefix || suffix)
        {
            Column c = tables[i].getColumns()[keyColumn - 1];
            IDecorator id = c.getDecorator();
            String pfx;
            if (prefix)
            {
                if (id != null)
                {
                    pfx = id.prefix(row);
                    if (pfx == null)
                        pfx = extraPrefix(i, row);
                }
                else
                {
                    pfx = extraPrefix(i, row);
                }
            }
            else
            {
                pfx = null;
            }
            String sfx = suffix && id != null ? id.suffix(row) : null;
            if (mask != null && pfx != null)
            {
                pfx = mask.matcher(pfx).replaceAll(replace == null ? "" : replace); //$NON-NLS-1$
                if (pfx.length() == 0)
                    pfx = null;
            }
            if (mask != null && sfx != null)
            {
                sfx = mask.matcher(sfx).replaceAll(replace == null ? "" : replace); //$NON-NLS-1$
                if (sfx.length() == 0)
                    sfx = null;
            }
            if (pfx != null || sfx != null)
                key = new ComparedRow(pfx, key, sfx, null);
        }
        return key;
    }

    /**
     * See if two rows from two tables match.
     * Do a bit more matching via IContextObject.
     * Match via context object ID or address.
     * @param table1 index
     * @param row1
     * @param table2 index
     * @param row2
     * @param matchType - the higher the number the more likely a match
     * @return true if they seem the same
     */
    boolean rowMatches(int table1, Object row1, int table2, Object row2, int matchType)
    {
        IContextObject ctx1 = tables[table1].getContext(row1);
        IContextObject ctx2 = tables[table2].getContext(row2);
        if (ctx1 == null && ctx2 == null)
            return true;
        if (ctx1 == null || ctx2 == null)
            return false;
        int objectId1 = ctx1.getObjectId();
        int objectId2 = ctx2.getObjectId();
        if (sameSnapshot[table1] && sameSnapshot[table2] ||
                        snapshots[table1].equals(snapshots[table2]))
        {
            //System.out.println("compare "+ctx1.getObjectId() +" "+ ctx2.getObjectId());
            return objectId1 == objectId2;
        }
        if (objectId1 == -1 && objectId2 == -1)
            return true;
        if (objectId1 == -1 || objectId2 == -1)
            return false;
        try
        {
            long addr1 = snapshots[table1].mapIdToAddress(objectId1);
            long addr2 = snapshots[table2].mapIdToAddress(objectId2);
            // Address match, guess the same?
            if (addr1 == addr2)
                return true;
            // Classes don't match, so different
            if (snapshots[table1].getClassOf(objectId1).equals(snapshots[table2].getClassOf(objectId2)))
                return false;
            if (matchType >= 1)
            {
                // Guess match on retained size, presuming it retains more than itself!
                if (snapshots[table1].getRetainedHeapSize(objectId1) > snapshots[table1].getHeapSize(1)
                                && snapshots[table1].getRetainedHeapSize(objectId1) == snapshots[table2]
                                                .getRetainedHeapSize(objectId2))
                    return true;
            }
            // Don't compare using getClassSpecificName() as this could be done
            // on the key
            if (matchType >= 2)
            {
                String val1 = snapshots[table1].getObject(objectId1).getClassSpecificName();
                String val2 = snapshots[table1].getObject(objectId1).getClassSpecificName();
                if (val1 != null && val1.equals(val2))
                    return true;
            }
            return false;
        }
        catch (SnapshotException e)
        {
            return false;
        }
    }

    /**
     * Try to match rows from different tables. Have definite matches in same
     * row. Unmatched can be anywhere.
     *
     * <pre>
     * A2=B3,A2=C1,A3=B5,A4=C2
     * [A1,B1,C1,A2,B2,C2,A3,B3,null,A4,B4,null,null,B5,null]
     * A1 B1 C1
     * A2 B2 C2
     * A3 B3
     * A4 B4
     *    B5
     * </pre>
     *
     * Goes to:
     *
     * <pre>
     * A1 B1
     * A2 B3 C1
     * A3 B5
     * A4 B2 C2
     *    B4
     * </pre>
     * <ol>
     * <li>find first match of B[1]..B[5] with A[1] and put in first slot (&
     * shift others up)</li>
     * <li>find next match of B[2]..B[5] in with A[2] and put in second slot (&
     * shift others up)</li>
     * <li>..</li>
     * <li>find first match of C[1]..C[2] with A[1] and put in first slot (&
     * shift others up)</li>
     * <li>if no match find first match of C[1]..C[2] with B[1] and put in first
     * slot (& shift others up)</li>
     * <li>if no match find first match of C[2]..C[2] with A[2] and put in first
     * slot (& shift others up)</li>
     * </ol>
     * Not perfect though:
     *
     * <pre>
     * 1 1 2
     * 2 3 5
     * 3 5
     * 4
     * </pre>
     *
     * gives
     *
     * <pre>
     * 1 1 5
     * 2 5 2
     * 3 3
     * 4
     * </pre>
     *
     * not
     *
     * <pre>
     * 1 1
     * 2   2
     * 3 3
     * 4 5 5
     * </pre>
     * Try a second pass to fix up those.
     * @param rows
     */
    void sortRows(Object rows[])
    {
        int rn = rows.length;
        int tn = tables.length;
        int n = rn / tn;
        Object queue[] = new Object[n];
        // This entry is matched to another table
        boolean matched[] = new boolean[rows.length];
        //System.out.println("Sorting " + n);
        // Consider each table other than the first
        for (int i = 1; i < tn; ++i)
        {
            // Distribute this table to matching slots
            // Fill the queue
            int q1 = 0, q2 = 0;
            for (int l = 0; l < n; ++l)
            {
                Object o = rows[l * tn + i];
                if (o != null)
                {
                    queue[q2++] = o;
                    rows[l * tn + i] = null;
                }
            }
            // We don't need to clear the remainder of the queue
            // Matching slot in preceding table
            slot: for (int j = 0; j < n; ++j)
            {
                // System.out.println("Slot "+i+":"+j);
                for (int pass = 0; pass < 2; ++pass)
                {
                    // Choose preceding table
                    for (int k = 0; k < i; ++k)
                    {
                        // Preceding value
                        Object rowPrev = rows[j * tn + k];
                        if (rowPrev == null)
                            continue;
                        // Matching preceding tables
                        // Choose entry from the queue
                        for (int l = q1; l < q2; ++l)
                        {
                            Object rowThis = queue[l];
                            if (rowThis == null)
                            {
                                // Optimization - adjust the queue limit
                                if (l == q1)
                                    ++q1;
                                continue;
                            }
                            //System.out.println("Comparing " + i + ":" + l + " to " + k + ":" + j);
                            if (rowMatches(i, rowThis, k, rowPrev, pass))
                            {
                                //System.out.println("Matched " + i + ":" + l + " to " + k + ":" + j);
                                matched[j * tn + i] = true;
                                matched[j * tn + k] = true;
                                rows[j * tn + i] = rowThis;
                                // stop reuse
                                queue[l] = null;
                                // optimization: last queue entry, so avoid looking here again
                                if (l == q2 - 1)
                                    --q2;
                                continue slot;
                            }
                        }
                    }
                }
                // Say this slot might need to be filled later
                rows[j * tn + i] = null;
            }
            // Distribute the unused
            for (int l = 0, lq = q1; l < n && lq < q2; ++l)
            {
                // Read slot
                Object o = rows[l * tn + i];
                if (o == null)
                {
                    // Read unused element from queue
                    while (lq < q2)
                    {
                        Object q = queue[lq++];
                        if (q != null)
                        {
                            // and fill the slot
                            rows[l * tn + i] = q;
                            break;
                        }
                    }
                }
            }
        }
        // Try some polishing of the result
        // Consider each table other than the first
        for (int i = 1; i < tn; ++i)
        {
            // Matching slot in preceding table
            for (int j = 0; j < n; ++j)
            {
                for (int pass = 0; pass < 2; ++pass)
                {
                    // Choose preceding table
                    for (int k = 0; k < i; ++k)
                    {
                        if (matched[j * tn + k])
                            continue;
                        // Preceding value
                        Object rowPrev = rows[j * tn + k];
                        if (rowPrev == null)
                            continue;
                        for (int l = 0; l < n; ++l)
                        {
                            if (matched[l * tn + i])
                                continue;
                            Object rowThis = rows[l * tn + i];
                            if (rowThis == null)
                                continue;
                            if (rowMatches(i, rowThis, k, rowPrev, 0))
                            {
                                // System.out.println("extra match " + i + ":" + l + " " + k + ":" + j);
                                // Find a corresponding free slot in both tables
                                for (int m = 0; m < n; ++m)
                                {
                                    if (!matched[m * tn + i] && !matched[m * tn + k])
                                    {
                                        // System.out.println("Swapped " + i + ":" + l + " " + i + ":" + m + " " + k + ":" + j + " " + k + ":" + m);
                                        // Swap and mark as matched
                                        Object oldThis = rows[m * tn + i];
                                        rows[m * tn + i] = rowThis;
                                        rows[l * tn + i] = oldThis;
                                        matched[m * tn + i] = true;
                                        Object oldPrev = rows[m * tn + k];
                                        rows[m * tn + k] = rowPrev;
                                        rows[j * tn + i] = oldPrev;
                                        matched[m * tn + k] = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static class ComparedColumn
    {
        Column description;
        int[] columnIndexes;
        boolean displayed;

        public ComparedColumn(Column description, int[] columnIndexes, boolean displayed)
        {
            super();
            this.displayed = displayed;
            this.description = description;
            this.columnIndexes = columnIndexes;
        }

        public Column getDescription()
        {
            return description;
        }

        public void setDescription(Column description)
        {
            this.description = description;
        }

        public int[] getColumnIndexes()
        {
            return columnIndexes;
        }

        public void setColumnIndexes(int[] columnIndexes)
        {
            this.columnIndexes = columnIndexes;
        }

        public boolean isDisplayed()
        {
            return displayed;
        }

        public void setDisplayed(boolean displayed)
        {
            this.displayed = displayed;
        }

    }

    static class ComparedRow
    {
        Object key;
        private String prefix;
        private String suffix;
        Object[] rows;

        public ComparedRow(Object key, Object[] rows)
        {
            super();
            if (key instanceof ComparedRow)
            {
                ComparedRow cr = (ComparedRow)key;
                this.key = cr.key;
                this.prefix = cr.prefix;
                this.suffix = cr.suffix;
            }
            else
            {
                this.key = key;
            }
            this.rows = rows;
        }

        public ComparedRow(String prefix, Object key, String suffix, Object[] rows)
        {
            super();
            this.prefix = prefix;
            this.key = key;
            this.suffix = suffix;
            this.rows = rows;
        }

        public Object getKey()
        {
            return key;
        }

        public void setKey(Object key)
        {
            this.key = key;
        }

        public Object[] getRows()
        {
            return rows;
        }

        public void setRows(Object[] rows)
        {
            this.rows = rows;
        }

        String getPrefix()
        {
            return prefix;
        }

        void setPrefix(String prefix)
        {
            this.prefix = prefix;
        }

        String getSuffix()
        {
            return suffix;
        }

        void setSuffix(String suffix)
        {
            this.suffix = suffix;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
            result = prime * result + ((suffix == null) ? 0 : suffix.hashCode());
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
            ComparedRow other = (ComparedRow) obj;
            if (key == null)
            {
                if (other.key != null)
                    return false;
            }
            else if (!key.equals(other.key))
                return false;
            if (prefix == null)
            {
                if (other.prefix != null)
                    return false;
            }
            else if (!prefix.equals(other.prefix))
                return false;
            if (suffix == null)
            {
                if (other.suffix != null)
                    return false;
            }
            else if (!suffix.equals(other.suffix))
                return false;
            return true;
        }

        public String toString()
        {
            String p1 = prefix != null ? "(" + prefix + ") " : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String s1 = suffix != null ? " (" + suffix + ")" : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            return p1 + key + s1 + ":" + Arrays.toString(rows); //$NON-NLS-1$
        }
    }

    /**
     * Types of delta retained size.
     */
    enum DeltaEncoding {
        /**
         * The size delta is exact.
         */
        EXACT,
        /**
         * The size delta is at least this much.
         */
        GE,
        /**
         * The size delta is not more than this.
         */
        LE,
        /**
         * The size delta is uncertain, but this is an
         * estimate. For example the difference between
         * two sizes given as greater than or equal to.
         */
        APPROX
    }

    /**
     * A class to format the difference between two retained sizes.
     * Similar to {@link org.eclipse.mat.snapshot.query.RetainedSizeDerivedData.RetainedSizeFormat}.
     * Sorting should use {@link Filter.ValueConverter} so that a dedicated comparator is
     * not required. See {@link org.eclipse.mat.query.refined.RefinedStructuredResult.NaturalComparator}.
     */
    private static class DeltaRetainedBytesFormat extends BytesFormat
    {
        /**
         * Converts a encoded stored value to a simple value, losing any
         * approximation details.
         */
        private class DeltaRetainedConverter implements Filter.ValueConverter, Serializable
        {
            private static final long serialVersionUID = 1L;

            public double convert(double source)
            {
                if (source >= SPECIAL + SPECIAL2)
                    return source - SPECIAL - SPECIAL2; // +ve approx
                else if (source >= SPECIAL)
                    return source - SPECIAL2; // >=
                else if (source < -SPECIAL - SPECIAL2)
                    return source + SPECIAL + SPECIAL2; // -ve approx
                else if (source < -SPECIAL)
                    return source + SPECIAL2; // <=
                return source;
            }
        }

        /**
         *
         */
        private static final long serialVersionUID = 1L;
        /*
         * encode >=, <= for +ve -ve byte values
         * convert long to double for Filter.ValueConverter
         * > 1,000,000,000,000,000 means >=
         * <-1,000,000,000,000,000 means <=
         * <-3,000,000,000,000,000 means ~
         *
         * e.g.
         * ~=   : 39
         * ~=3  : 33
         * ~=0  : 30
         * >=9  : 29
         * >=3  : 23
         * >=-3 : 17
         * >=-10: 10
         *
         * <=9  : -11
         * <=3  : -17
         * <=-3 : -23
         * <=-10: -30
         *
         * ~-3  : -33
         * ~-9  : -39
         */
        /**
         * Break point for special encoding.
         * Chosen to be big, but to fit precisely in a double
         * as well as a long.
         */
        private long SPECIAL = 1000000000000000L;
        /**
         * How much to adjust a value to move it into a different range.
         */
        private long SPECIAL2 = SPECIAL * 2;
        /**
         * Convert the encoded value to a normal value.
         */
        final Filter.ValueConverter converter = new DeltaRetainedConverter();

        /**
         * Encode a value as greater than or equal.
         * @param l the raw value
         * @return the encoded value
         */
        private long encodege(long l)
        {
            if (l >= SPECIAL)
                return SPECIAL - 1 + SPECIAL2; // saturate
            else if (l < -SPECIAL)
                return encodeun(l); // can't be encoded as GE
            else
                return l + SPECIAL2;
        }

        /**
         * Encode a value as less than or equal.
         * @param l the raw value
         * @return the encoded value
         */
        private long encodele(long l)
        {
            if (l >= SPECIAL)
                return encodeun(l); // can't be coded as LE
            else if (l < -SPECIAL)
                return -SPECIAL - SPECIAL2; // saturate
            else
                return l - SPECIAL2;
        }

        /**
         * Encode a value as inexact.
         * @param l the raw value
         * @return the encoded value
         */
        private long encodeun(long l)
        {
            if (l >= 0)
                if (l >= Long.MAX_VALUE - SPECIAL - SPECIAL2)
                    return Long.MAX_VALUE; // saturate
                else
                    return l + SPECIAL2 + SPECIAL;
            else if (l <= Long.MIN_VALUE + SPECIAL + SPECIAL2)
                return Long.MIN_VALUE; // saturate
            else
                return l - SPECIAL2 - SPECIAL;
        }

        /**
         * Create a formatter for the difference between two retained sizes.
         * @param encapsulatedNumberFormat
         * @param encapsulatedDecimalFormat
         */
        public DeltaRetainedBytesFormat(Format encapsulatedNumberFormat, Format encapsulatedDecimalFormat)
        {
            super(encapsulatedNumberFormat, encapsulatedDecimalFormat);
        }

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos)
        {
            Number v;
            if (obj instanceof Bytes)
                v = ((Bytes)obj).getValue();
            else
                v = (Number) obj;


            if (v.longValue() >= SPECIAL)
            {
                if (v.longValue() >= SPECIAL + SPECIAL2)
                {
                    String approx = Messages.CompareTablesQuery_APPROX;
                    toAppendTo.append(approx);
                    return super.format(new Bytes(v.longValue() - SPECIAL - SPECIAL2), toAppendTo, pos);
                }
                else
                {
                    String approx = Messages.CompareTablesQuery_GE;
                    toAppendTo.append(approx);
                    return super.format(new Bytes(v.longValue() - SPECIAL2), toAppendTo, pos);
                }
            }
            else if (v.longValue() < -SPECIAL)
            {
                if (v.longValue() < -SPECIAL - SPECIAL2)
                {
                    String approx = Messages.CompareTablesQuery_APPROX;
                    toAppendTo.append(approx);
                    return super.format(new Bytes(v.longValue() + SPECIAL + SPECIAL2), toAppendTo, pos);
                }
                else
                {
                    String approx = Messages.CompareTablesQuery_LE;
                    toAppendTo.append(approx);
                    return super.format(new Bytes(v.longValue() + SPECIAL2), toAppendTo, pos);
                }
            }
            else
            {
                return super.format(new Bytes(v.longValue()), toAppendTo, pos);
            }
        }

        @Override
        public Object parseObject(String source, ParsePosition pos)
        {
            Object ret;
            for (String match : new String[] {Messages.CompareTablesQuery_GE, Messages.CompareTablesQuery_LE, Messages.CompareTablesQuery_APPROX})
            {
                if (source.regionMatches(pos.getIndex(), match, 0, match.length()))
                {
                    int pi = pos.getIndex();
                    pos.setIndex(pi + match.length());
                    ret = super.parseObject(source, pos);
                    if (ret != null)
                    {
                        long v;
                        if (ret instanceof Bytes)
                        {
                            v = ((Bytes)ret).getValue();
                            if (match.equals(Messages.CompareTablesQuery_GE))
                                v = encodege(v);
                            else if (match.equals(Messages.CompareTablesQuery_LE))
                                v = encodele(v);
                            else if (match.equals(Messages.CompareTablesQuery_APPROX))
                                v = encodeun(v);
                            return new Bytes(v);
                        }
                        else if (ret instanceof Number)
                        {
                            v = ((Number)ret).longValue();
                            if (match.equals(Messages.CompareTablesQuery_GE))
                                v = encodege(v);
                            else if (match.equals(Messages.CompareTablesQuery_LE))
                                v = encodele(v);
                            else if (match.equals(Messages.CompareTablesQuery_APPROX))
                                v = encodeun(v);
                            return new Bytes(v);
                        }
                    }
                    // >= in front of something else
                    pos.setErrorIndex(pi + match.length());
                    pos.setIndex(pi);
                    ret = null;
                    return ret;
                }
            }
            ret = super.parseObject(source, pos);
            return ret;
        }
    }

    public class TableComparisonResult implements IStructuredResult, IIconProvider
    {
        private Column key;
        protected List<ComparedRow> rows;
        /** each compared column is a column from the original table, and is displayed as several actual columns, one or more for each table */
        private List<ComparedColumn> comparedColumns;
        /** each displayed column is a column from the original table, and is displayed as several actual columns, one or more for each table */
        private List<ComparedColumn> displayedColumns;
        /** Actual columns displayed */
        private Column[] columns;
        private Mode mode;
        private Operation setOp;

        public TableComparisonResult(List<ComparedRow> rows, Column key, List<ComparedColumn> comparedColumns, Mode mode, Operation setOp)
        {
            this.key = key;
            this.mode = mode;
            this.rows = rows;
            this.comparedColumns = comparedColumns;
            updateColumns();
            setMode(mode);
            this.setOp = setOp;
        }

        public List<ComparedColumn> getComparedColumns()
        {
            return comparedColumns;
        }

        public void setComparedColumns(List<ComparedColumn> comparedColumns)
        {
            this.comparedColumns = comparedColumns;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            ComparedRow cr = (ComparedRow) row;
            if (columnIndex == 0) return cr.getKey();

            /*
             * Each compared column has several subcolumns for data from each
             * table. The first column is the key and is common for all tables,
             * so there is only one actual column. For absolute or difference
             * modes there is one column per table. For percentage modes there
             * is one column for the first table and two for the rest.
             */
            int subCols = mode == Mode.DIFF_RATIO_TO_FIRST || mode == Mode.DIFF_RATIO_TO_PREVIOUS ? tables.length
                            + tables.length - 1 : tables.length;
            int comparedColumnIdx = (columnIndex - 1) / subCols;
            int tableIdx = (columnIndex - 1) % subCols;

            if (tableIdx == 0) return getAbsoluteValue(cr, comparedColumnIdx, tableIdx);

            switch (mode)
            {
                case ABSOLUTE:
                    return getAbsoluteValue(cr, comparedColumnIdx, tableIdx);
                case DIFF_TO_FIRST:
                    return getDiffToPrevious(cr, columnIndex, comparedColumnIdx, tableIdx, 0, false);
                case DIFF_TO_PREVIOUS:
                    return getDiffToPrevious(cr, columnIndex, comparedColumnIdx, tableIdx, tableIdx - 1, false);
                case DIFF_RATIO_TO_FIRST:
                    return getDiffToPrevious(cr, columnIndex, comparedColumnIdx, (tableIdx + 1) / 2, 0, tableIdx % 2 == 0);
                case DIFF_RATIO_TO_PREVIOUS:
                    return getDiffToPrevious(cr, columnIndex, comparedColumnIdx, (tableIdx + 1) / 2, (tableIdx + 1) / 2 - 1, tableIdx % 2 == 0);

                default:
                    break;
            }

            return null;

        }

        public Column[] getColumns()
        {
            return columns;
        }

        public IContextObject getContext(Object row)
        {
            // Find a context from one of the tables
            IContextObject ret = null;
            for (int i = 0; i < tables.length && ret == null; ++i)
            {
                ret = getContextFromTable(i, row);
            }
            return ret;
        }

        public ResultMetaData getResultMetaData()
        {
            ResultMetaData.Builder bb = new ResultMetaData.Builder();
            int previous = -1;
            for (int i = 0; i < tables.length; ++i)
            {
                if (!sameSnapshot[i])
                    continue;

                if (setOp != Operation.NONE && previous >= 0)
                {
                    for (int op = 0; op < 5; ++op)
                    {
                        if (setOp == Operation.INTERSECTION && op != 0) continue;
                        if (setOp == Operation.UNION && op != 1) continue;
                        if (setOp == Operation.SYMMETRIC_DIFFERENCE && op != 2) continue;
                        if (setOp == Operation.DIFFERENCE && op != 3) continue;
                        if (setOp == Operation.REVERSE_DIFFERENCE && op != 4) continue;
                        final int op1 = op;
                        // intersection, union, symmetric difference, difference, difference
                        String title1;
                        final LinkedList<Integer> toDo = new LinkedList<Integer>();
                        if (mode == Mode.ABSOLUTE)
                        {
                            toDo.add(previous);
                            if (op == 4)
                            {
                                for (int k = previous + 1; k <= i; ++k)
                                {
                                    if (!sameSnapshot[k])
                                        continue;
                                    toDo.addFirst(k);
                                }
                            }
                            else
                            {
                                for (int k = previous + 1; k <= i; ++k)
                                {
                                    if (!sameSnapshot[k])
                                        continue;
                                    toDo.add(k);
                                }
                            }
                        }
                        else
                        {
                            if (op == 4)
                            {
                                toDo.add(i);
                                toDo.add(previous);
                            }
                            else
                            {
                                toDo.add(previous);
                                toDo.add(i);
                            }
                        }
                        // Convert the list of tables to a readable menu item
                        if (toDo.size() == 2)
                        {
                            switch (op)
                            {
                                case 0:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_IntersectionOf2, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 1:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_UnionOf2, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 2:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceOf2, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 3:
                                case 4:
                                default:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_DifferenceOf2, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                            }
                        } else {
                            String soFar;
                            switch (op)
                            {
                                case 0:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_IntersectionFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 1:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_UnionFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 2:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                                case 3:
                                case 4:
                                default:
                                    soFar = MessageUtil.format(Messages.CompareTablesQuery_DifferenceFirst, toDo.get(0)+1, toDo.get(1)+1);
                                    break;
                            }
                            for (int t = 2; t < toDo.size() - 1; ++t)
                            {
                                switch (op)
                                {
                                    case 0:
                                        soFar = MessageUtil.format(Messages.CompareTablesQuery_IntersectionMiddle, soFar, toDo.get(t)+1);
                                        break;
                                    case 1:
                                        soFar = MessageUtil.format(Messages.CompareTablesQuery_UnionMiddle, soFar, toDo.get(t)+1);
                                        break;
                                    case 2:
                                        soFar = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceMiddle, soFar, toDo.get(t)+1);
                                        break;
                                    case 3:
                                    case 4:
                                    default:
                                        soFar = MessageUtil.format(Messages.CompareTablesQuery_DifferenceMiddle, soFar, toDo.get(t)+1);
                                        break;
                                }
                            }
                            int t = toDo.size() - 1;
                            switch (op)
                            {
                                case 0:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_IntersectionLast, soFar, toDo.get(t)+1);
                                    break;
                                case 1:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_UnionLast, soFar, toDo.get(t)+1);
                                    break;
                                case 2:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_SymmetricDifferenceLast, soFar, toDo.get(t)+1);
                                    break;
                                case 3:
                                case 4:
                                default:
                                    title1 = MessageUtil.format(Messages.CompareTablesQuery_DifferenceLast, soFar, toDo.get(t)+1);
                                    break;
                            }
                        }

                        ContextProvider prov2 = new ContextProvider(title1)
                        {
                            private static final String EMPTY_QUERY = "SELECT * FROM OBJECTS 0 WHERE false"; //$NON-NLS-1$
                            public URL getIcon()
                            {
                                switch (op1)
                                {
                                    case 0:
                                        return Icons.getURL("set_intersection.gif"); //$NON-NLS-1$
                                    case 1:
                                        return Icons.getURL("set_union.gif"); //$NON-NLS-1$
                                    case 2:
                                        return Icons.getURL("set_symmetric_difference.gif"); //$NON-NLS-1$
                                    case 3:
                                        return Icons.getURL("set_differenceA.gif"); //$NON-NLS-1$
                                    case 4:
                                        return Icons.getURL("set_differenceB.gif"); //$NON-NLS-1$
                                }
                                return null;
                            }
                            public IContextObject getContext(final Object row)
                            {
                                int nullContexts = 0;
                                for (int i = 0; i < toDo.size(); ++i)
                                {
                                    if (getContextFromTable(i, row) == null)
                                    {
                                        ++nullContexts;
                                    }
                                    else
                                    {
                                        break;
                                    }
                                }
                                // If all the contexts were null then skip this generated context too
                                if (nullContexts == toDo.size())
                                    return null;
                                // First non-null context
                                final IContextObject cb = getContextFromTable(nullContexts, row);

                                return new IContextObjectSet()
                                {
                                    public int getObjectId()
                                    {
                                        return cb.getObjectId();
                                    }

                                    public String getOQL()
                                    {
                                        LinkedList<Integer> toDo2 = new LinkedList<Integer>(toDo);
                                        switch (op1)
                                        {
                                            // Intersection
                                            case 0:
                                                String resultOQL0 = null;
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                    {
                                                        // No objects so intersection empty
                                                        return EMPTY_QUERY;
                                                    }
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        if (resultOQL0 == null)
                                                            resultOQL0 = oql;
                                                        else
                                                        {
                                                            resultOQL0 = OQLintersection(resultOQL0, oql);
                                                        }
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                return resultOQL0;
                                                // Union
                                            case 1:
                                                StringBuilder resultOQL = new StringBuilder();
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                        continue;
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        OQL.union(resultOQL, oql);
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                // Remove duplicates
                                                return OQLdistinct(resultOQL.toString());
                                                // Symmetric Difference
                                            case 2:
                                                String resultOQL2 = null;
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                        continue;
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        if (resultOQL2 == null)
                                                            resultOQL2 = oql;
                                                        else
                                                        {
                                                            // A^B = A&~B | ~A&B
                                                            StringBuilder sb = new StringBuilder();
                                                            sb.append(OQLexcept(resultOQL2, oql));
                                                            OQL.union(sb, OQLexcept(oql, resultOQL2));
                                                            resultOQL2 = sb.toString();
                                                        }
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                return resultOQL2;
                                                // Difference
                                            case 3:
                                            case 4:
                                                String resultOQL3 = null;
                                                for (int j : toDo2)
                                                {
                                                    IContextObject cb = getContextFromTable(j, row);
                                                    if (cb == null)
                                                    {
                                                        if (resultOQL3 == null)
                                                        {
                                                            // First table has no objects so subtraction empty
                                                            return EMPTY_QUERY;
                                                        }
                                                        continue;
                                                    }
                                                    String oql = getOQLfromContext(cb);
                                                    if (oql != null)
                                                    {
                                                        if (resultOQL3 == null)
                                                            resultOQL3 = oql;
                                                        else
                                                        {
                                                            resultOQL3 = OQLexcept(resultOQL3, oql);
                                                        }
                                                    }
                                                    else
                                                    {
                                                        return null;
                                                    }
                                                }
                                                return resultOQL3;
                                        }
                                        return null;
                                    }

                                    /**
                                     * Simulate EXCEPT
                                     * @param oql1
                                     * @param oql2
                                     * @return
                                     */
                                    private String OQLexcept(String oql1, String oql2)
                                    {
                                        //SELECT * FROM OBJECTS (a) where @objectid in (b)
                                        if ((OQLobjectQuery(oql1)))
                                            oql1 = "SELECT * FROM OBJECTS ("+oql1+")" +  //$NON-NLS-1$//$NON-NLS-2$
                                                            " WHERE @objectId in ("+oql2+") = false";  //$NON-NLS-1$//$NON-NLS-2$
                                        else
                                            oql1 = "SELECT s as \"\" FROM OBJECTS ("+oql1+") s" +  //$NON-NLS-1$//$NON-NLS-2$
                                                            " WHERE s in ("+oql2+") = false";  //$NON-NLS-1$//$NON-NLS-2$
                                        return oql1;
                                    }

                                    /**
                                     * Simulate INTERSECT
                                     * @param oql1
                                     * @param oql2
                                     * @return
                                     */
                                    private String OQLintersection(String oql1, String oql2)
                                    {
                                        //SELECT * FROM OBJECTS (a) where @objectid in (b)
                                        if ((OQLobjectQuery(oql1)))
                                            oql1 = "SELECT * FROM OBJECTS (" + oql1 + ")" + //$NON-NLS-1$//$NON-NLS-2$
                                                            " WHERE @objectId in (" + oql2 + ")"; //$NON-NLS-1$//$NON-NLS-2$
                                        else
                                            oql1 = "SELECT s as \"\" FROM OBJECTS (" + oql1 + ") s" + //$NON-NLS-1$//$NON-NLS-2$
                                                            " WHERE s in (" + oql2 + ")"; //$NON-NLS-1$//$NON-NLS-2$
                                        return oql1;
                                    }

                                    /**
                                     * Handle DISTINCT
                                     * @param oql
                                     * @return
                                     */
                                    private String OQLdistinct(String oql)
                                    {
                                        //SELECT DISTINCT OBJECTS @objectId FROM OBJECTS (a)
                                        if (OQLobjectQuery(oql))
                                            return "SELECT DISTINCT OBJECTS @objectId FROM OBJECTS (" + oql +")"; //$NON-NLS-1$ //$NON-NLS-2$
                                        else
                                            return "SELECT DISTINCT s as \"\" FROM OBJECTS (" + oql +") s"; //$NON-NLS-1$ //$NON-NLS-2$
                                    }

                                    /**
                                     * Is this a query returning a list of objects
                                     * or one with columns?
                                     * @param oql
                                     * @return
                                     */
                                    public boolean OQLobjectQuery(String oql)
                                    {
                                        oql = oql.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " "); //$NON-NLS-1$ //$NON-NLS-2$
                                        return oql.startsWith("select * ") || oql.startsWith("select distinct objects ") //$NON-NLS-1$ //$NON-NLS-2$
                                                        || oql.startsWith("select as retained set "); //$NON-NLS-1$
                                    }

                                    /**
                                     * Calculate the object ids
                                     */
                                    public int[] getObjectIds()
                                    {
                                        LinkedList<Integer> toDo2 = new LinkedList<Integer>(toDo);
                                        int j = toDo2.remove();
                                        final IContextObject cb = getContextFromTable(j, row);
                                        int b[] = getObjectIdsFromContext(cb);
                                        ArrayInt bb;
                                        if (b == null)
                                        {
                                            bb = new ArrayInt();
                                        }
                                        else
                                        {
                                            bb = new ArrayInt(b);
                                            bb.sort();
                                        }

                                        while (!toDo2.isEmpty())
                                        {
                                            j = toDo2.remove();
                                            IContextObject ca = getContextFromTable(j, row);
                                            int a[] = getObjectIdsFromContext(ca);
                                            ArrayInt aa;
                                            if (a == null)
                                            {
                                                aa = new ArrayInt();
                                            }
                                            else
                                            {
                                                aa = new ArrayInt(a);
                                                aa.sort();
                                            }
                                            switch (op1)
                                            {
                                                case 0:
                                                    bb = intersectionArray(aa, bb);
                                                    break;
                                                case 1:
                                                    bb = unionArray(aa, bb);
                                                    break;
                                                case 2:
                                                    bb = symdiffArray(aa, bb);
                                                    break;
                                                case 3:
                                                case 4:
                                                    bb = diffArray(aa, bb);
                                                    break;
                                            }
                                        }

                                        final int res[] = bb.toArray();
                                        return res;
                                    }

                                    /**
                                     * Union of aa and bb
                                     *
                                     * @param aa sorted
                                     * @param bb sorted
                                     * @return
                                     */
                                    private ArrayInt unionArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (aa.size() == 0)
                                            return bb;
                                        if (bb.size() == 0)
                                            return aa;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                            {
                                                cc.add(aa.get(j));
                                                ++j;
                                            }
                                            cc.add(bb.get(i));
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                ++j;
                                            }
                                        }
                                        while (j < aa.size())
                                        {
                                            cc.add(aa.get(j));
                                            ++j;
                                        }
                                        return cc;
                                    }

                                    /**
                                     * Remove aa from bb
                                     *
                                     * @param aa sorted
                                     * @param bb sorted
                                     * @return
                                     */
                                    private ArrayInt diffArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (bb.size() == 0)
                                            return bb;
                                        if (aa.size() == 0)
                                            return bb;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                                ++j;
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                ++j;
                                            }
                                            else
                                            {
                                                cc.add(bb.get(i));
                                            }
                                        }
                                        return cc;
                                    }

                                    /**
                                     * Intersection of aa and bb
                                     *
                                     * @param aa sorted
                                     * @param bb sorted
                                     * @return
                                     */
                                    private ArrayInt intersectionArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (aa.size() == 0)
                                            return aa;
                                        if (bb.size() == 0)
                                            return bb;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                                ++j;
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                cc.add(bb.get(i));
                                                ++j;
                                            }
                                        }
                                        return cc;
                                    }

                                    /**
                                     * Symmetric difference of aa and bb
                                     *
                                     * @param aa sorted
                                     * @param bb sorted
                                     * @return
                                     */
                                    private ArrayInt symdiffArray(ArrayInt aa, ArrayInt bb)
                                    {
                                        if (aa.size() == 0)
                                            return bb;
                                        if (bb.size() == 0)
                                            return aa;
                                        ArrayInt cc = new ArrayInt();
                                        int j = 0;
                                        for (int i = 0; i < bb.size(); ++i)
                                        {
                                            while (j < aa.size() && aa.get(j) < bb.get(i))
                                            {
                                                cc.add(aa.get(j));
                                                ++j;
                                            }
                                            if (j < aa.size() && aa.get(j) == bb.get(i))
                                            {
                                                ++j;
                                            }
                                            else
                                            {
                                                cc.add(bb.get(i));
                                            }
                                        }
                                        while (j < aa.size())
                                        {
                                            cc.add(aa.get(j));
                                            ++j;
                                        }
                                        return cc;
                                    }

                                    private int[] getObjectIdsFromContext(IContextObject b)
                                    {
                                        int bobjs[];
                                        if (b instanceof IContextObjectSet)
                                        {
                                            bobjs = ((IContextObjectSet) b).getObjectIds();
                                        }
                                        else if (b != null)
                                        {
                                            int id = b.getObjectId();
                                            if (id >= 0)
                                                bobjs = new int[] { id };
                                            else
                                                bobjs = null;
                                        }
                                        else
                                        {
                                            bobjs = null;
                                        }
                                        return bobjs;
                                    }

                                    private String getOQLfromContext(IContextObject b)
                                    {
                                        String oql;
                                        if (b instanceof IContextObjectSet)
                                        {
                                            oql = ((IContextObjectSet) b).getOQL();
                                        }
                                        else if (b != null)
                                        {
                                            int id = b.getObjectId();
                                            if (id >= 0)
                                                oql = OQL.forObjectId(id);
                                            else
                                                oql = null;
                                        }
                                        else
                                        {
                                            oql = null;
                                        }
                                        return oql;
                                    }
                                };
                            }
                        };
                        bb.addContext(prov2);
                    }
                }
                if (setOp == Operation.NONE || setOp == Operation.ALL)
                {
                    final int i2 = i;
                    String title = MessageUtil.format(Messages.CompareTablesQuery_Table, i + 1);
                    ContextProvider prov = new ContextProvider(title)
                    {
                        public IContextObject getContext(Object row)
                        {
                            return getContextFromTable(i2, row);
                        }
                    };
                    bb.addContext(prov);
                }
                if (mode == Mode.DIFF_TO_PREVIOUS || mode == Mode.DIFF_RATIO_TO_PREVIOUS ||previous == -1)
                    previous = i;
            }
            /*
             * No need to add derived operations as a refined table
             * will have the extra columns already.
             */
            //derivedops(bb);
            return bb.build();
        }

        private Object getAbsoluteValue(ComparedRow cr, int comparedColumnIdx, int tableIdx)
        {
            Object tableRow = cr.getRows()[tableIdx];
            if (tableRow == null) return null;

            int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
            if (tableColumnIdx == -1) return null;

            return tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
        }

        private Object percentDivide(Number d1, Number d2)
        {
            if (d1.doubleValue() == 0.0
                            && d2.doubleValue() == 0.0
                            && (d2 instanceof Integer || d2 instanceof Long || d2 instanceof Byte || d2 instanceof Short))
            {
                // Helps sorting if 0 -> 0 maps to +0% rather than NaN%
                return Double.valueOf(0.0);
            }
            else
            {
                return Double.valueOf(d1.doubleValue() / d2.doubleValue());
            }
        }

        /**
         * Convert a encoded value from a cell.
         * The value can be encoded, and needs to be
         * decoded before arithmetic or filtering.
         * The formatter can handle the encoded
         * value.
         * @param value
         * @param tableIdx
         * @param comparedColumnIdx
         * @return the converted value, of the same type
         * as the original.
         */
        private Object valueConvert(Object value, int tableIdx, int comparedColumnIdx)
        {
            // Optimization, presume no converter will change null
            if (value == null)
                return null;
            int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
            if (tableColumnIdx == -1)
                return value;
            Filter.ValueConverter vc = (Filter.ValueConverter) tables[tableIdx].getColumns()[tableColumnIdx]
                            .getData(Filter.ValueConverter.class);
            if (vc != null)
            {
                if (value instanceof Bytes)
                {
                    double v0 = ((Bytes) value).getValue();
                    double v = vc.convert(v0);
                    if (v != v0)
                        value = new Bytes(Double.valueOf(v).longValue());
                }
                else if (value instanceof Number)
                {
                    double v0 = ((Number) value).doubleValue();
                    double v = vc.convert(v0);
                    if (v != v0)
                    {
                        // Try to convert back to original type
                        if (value instanceof Long)
                        {
                            long v1 = (long)v;
                            if (v1 == v)
                                value = v1;
                            else
                                value = v;
                        }
                        else if (value instanceof Integer)
                        {
                            int v1 = (int)v;
                            if (v1 == v)
                                value = v1;
                            else
                                value = v;
                        }
                        else if (value instanceof Short)
                        {
                            short v1 = (short)v;
                            if (v1 == v)
                                value = v1;
                            else
                                value = v;
                        }
                        else if (value instanceof Byte)
                        {
                            byte v1 = (byte)v;
                            if (v1 == v)
                                value = v1;
                            else
                                value = v;
                        }
                        else if (value instanceof Double)
                        {
                            double v1 = (double)v;
                            if (v1 == v)
                                value = v1;
                            else
                                value = v;
                        }
                        else if (value instanceof Float)
                        {
                            float v1 = (float)v;
                            if (v1 == v)
                                value = v1;
                            else
                                value = v;
                        }
                        else
                            value = v;
                    }
                }
            }
            return value;
        }

        /**
         * Did the converted value look like an approximate value?
         * @param value original value
         * @param value2 converted value
         * @param tableIdx the table
         * @param comparedColumnIdx the column index
         * @return the approximation type
         */
        private DeltaEncoding approxValue(Object value, Object value2, int tableIdx, int comparedColumnIdx)
        {
            if (value != null && !value.equals(value2))
            {
                int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
                if (tableColumnIdx == -1)
                    return DeltaEncoding.EXACT;
                try
                {
                    String fv = tables[tableIdx].getColumns()[tableColumnIdx].getFormatter().format(value);
                    if (fv != null)
                    {
                        if (fv.startsWith(Messages.RetainedSizeDerivedData_Approximate))
                            return DeltaEncoding.GE;
                        else if (fv.startsWith(Messages.CompareTablesQuery_GE))
                            return DeltaEncoding.GE;
                        else if (fv.startsWith("<= ")) //$NON-NLS-1$
                            // E.g. Quantize_LessEq_Prefix
                            return DeltaEncoding.LE;
                        else if (fv.startsWith(Messages.CompareTablesQuery_LE))
                            return DeltaEncoding.LE;
                        else if (fv.startsWith(Messages.CompareTablesQuery_APPROX))
                            return DeltaEncoding.APPROX;
                    }
                }
                catch (IllegalArgumentException e)
                {}
            }
            return DeltaEncoding.EXACT;
        }

        /**
         * Get the value for a row and column which is a difference to a previous table
         * (either the first or the immediately previous table).
         * @param cr the row of created compared table/tree
         * @param columnIdx the column index of the created compared table/tree
         * @param comparedColumnIdx the index for the set of columns of the same name which are compared
         * @param tableIdx the table to read
         * @param prevTableIdx the previous table to compare to
         * @param ratio calculate a ration, not a difference
         * @return
         */
        private Object getDiffToPrevious(ComparedRow cr, int columnIdx, int comparedColumnIdx, int tableIdx, int prevTableIdx, boolean ratio)
        {
            Object tableRow = cr.getRows()[tableIdx];
            if (tableRow == null) return null;

            int tableColumnIdx = displayedColumns.get(comparedColumnIdx).getColumnIndexes()[tableIdx];
            if (tableColumnIdx == -1) return null;

            Object value = tables[tableIdx].getColumnValue(tableRow, tableColumnIdx);
            Object previousTableValue = getAbsoluteValue(cr, comparedColumnIdx, prevTableIdx);
            Object value2 = valueConvert(value, tableIdx, comparedColumnIdx);
            DeltaEncoding approxValue = approxValue(value, value2, tableIdx, comparedColumnIdx);
            value = value2;
            value2 = valueConvert(previousTableValue, prevTableIdx, comparedColumnIdx);
            DeltaEncoding approxPreviousValue = approxValue(previousTableValue, value2, prevTableIdx, comparedColumnIdx);
            previousTableValue = value2;

            if (value == null && previousTableValue == null) return null;

            if (value == null && (previousTableValue instanceof Number || previousTableValue instanceof Bytes)) return null;

            if ((value instanceof Number || value instanceof Bytes) && previousTableValue == null)
            {
                if (ratio)
                    return null;
                /*
                 * Fix up encoding of single value
                 * The source value has a difference formatter to the output so could need conversion.
                 */
                if (approxValue != DeltaEncoding.EXACT)
                {
                    if (value instanceof Bytes)
                        return encodeResult(((Bytes)value).getValue(), true, approxValue, approxPreviousValue, columnIdx);
                    else
                        return encodeResult(value, false, approxValue, approxPreviousValue, columnIdx);
                }
                return value;
            }

            boolean returnBytes = value instanceof Bytes && previousTableValue instanceof Bytes;
            if (value instanceof Bytes)
                value = Long.valueOf(((Bytes)value).getValue());

            if (previousTableValue instanceof Bytes)
                previousTableValue = Long.valueOf(((Bytes)previousTableValue).getValue());

            if (value instanceof Number && previousTableValue instanceof Number)
            {
                Object ret = computeDiff((Number) previousTableValue, (Number) value);
                if (ratio && ret instanceof Number)
                {
                    return percentDivide((Number)ret, (Number)previousTableValue);
                }
                else
                {
                    return encodeResult(ret, returnBytes, approxValue, approxPreviousValue, columnIdx);
                }
            }
            else
            {
                if (ratio)
                    return null;
                if (previousTableValue == null || !previousTableValue.equals(value))
                    return value;
            }
            return null;
        }

        /**
         * Encode the result for a delta retained size formatter.
         * @param ret
         * @param returnBytes
         * @param approxValue
         * @param approxPreviousValue
         * @param columnIdx
         * @return
         */
        private Object encodeResult(Object ret, boolean returnBytes, DeltaEncoding approxValue, DeltaEncoding approxPreviousValue,
                        int columnIdx)
        {
            if (returnBytes || ret instanceof Long)
            {
                long val = ((Number)ret).longValue();
                Format fmt = columns[columnIdx].getFormatter();
                if (fmt instanceof DeltaRetainedBytesFormat)
                {
                    DeltaRetainedBytesFormat dfmt = (DeltaRetainedBytesFormat)fmt;
                    if (approxValue == DeltaEncoding.EXACT && approxPreviousValue == DeltaEncoding.EXACT)
                        ;
                    else if ((approxValue == DeltaEncoding.GE || approxValue == DeltaEncoding.EXACT) && (approxPreviousValue == DeltaEncoding.EXACT || approxPreviousValue == DeltaEncoding.LE))
                        val = dfmt.encodege(val);
                    else if ((approxValue == DeltaEncoding.LE || approxValue == DeltaEncoding.EXACT) && (approxPreviousValue == DeltaEncoding.EXACT || approxPreviousValue == DeltaEncoding.GE))
                        val = dfmt.encodele(val);
                    else if (approxValue != DeltaEncoding.EXACT || approxPreviousValue != DeltaEncoding.EXACT)
                        val = dfmt.encodeun(val);
                }
                if (returnBytes)
                    return new Bytes(val);
                else
                    return val;
            }
            return ret;
        }

        private Object computeDiff(Number o1, Number o2)
        {
            if (o1 instanceof Long && o2 instanceof Long) return (o2.longValue() - o1.longValue());

            if (o1 instanceof Integer && o2 instanceof Integer) return (o2.intValue() - o1.intValue());

            if (o1 instanceof Short && o2 instanceof Short) return (o2.shortValue() - o1.shortValue());

            if (o1 instanceof Byte && o2 instanceof Byte) return (o2.byteValue() - o1.byteValue());

            if (o1 instanceof Float && o2 instanceof Float) return (o2.floatValue() - o1.floatValue());

            if (o1 instanceof Double && o2 instanceof Double) return (o2.doubleValue() - o1.doubleValue());

            return null;
        }

        /**
         * Get the icon for the row.
         * Chose the icon from the underlying tables if they all agree,
         * others choose a special compare icon.
         */
        public URL getIcon(Object row)
        {
            URL ret = null;
            final ComparedRow cr = (ComparedRow) row;
            // Find the rows from the tables
            boolean foundIcon = false;
            for (int i = 0; i < tables.length; ++i)
            {
                Object r = cr.getRows()[i];
                if (r != null)
                {
                    URL tableIcon = (tables[i] instanceof IIconProvider) ? ((IIconProvider) tables[i]).getIcon(r)
                                    : null;
                    if (foundIcon)
                    {
                        try
                        {
                            if (ret == null ? tableIcon != null : tableIcon == null
                                            || !ret.toURI().equals(tableIcon.toURI()))
                            {
                                // Mismatch, so use compare icon instead
                                ret = Icons.getURL("compare.gif"); //$NON-NLS-1$
                                break;
                            }
                        }
                        catch (URISyntaxException e)
                        {
                            // URI problem, so use compare icon instead
                            ret = Icons.getURL("compare.gif"); //$NON-NLS-1$
                            break;
                        }
                    }
                    else
                    {
                        ret = tableIcon;
                        foundIcon = true;
                    }
                }
            }
            return ret;
        }

        public Mode getMode()
        {
            return mode;
        }

        public void setMode(Mode mode)
        {
            this.mode = mode;
            updateColumns();
        }

        public Operation getOperation()
        {
            return setOp;
        }

        public void setOperation(Operation op)
        {
            if (op != Operation.NONE)
            {
                // Don't allow an operation if there are not enough local tables
                int samec = 0;
                for (boolean same : CompareTablesQuery.this.sameSnapshot)
                {
                    if (same)
                        ++samec;
                }
                if (samec < 2)
                    op = Operation.NONE;
            }
            this.setOp = op;
            // No need to update columns
        }

        private void addPositiveIndicator(Format formatter)
        {
            if (formatter instanceof DecimalFormat)
            {
                DecimalFormat pctFmt = (DecimalFormat) formatter;
                if ((pctFmt.getPositivePrefix().length() == 0
                                || pctFmt.getPositivePrefix().equals(pctFmt.getNegativePrefix()))
                                && (pctFmt.getPositiveSuffix().length() == 0
                                || pctFmt.getPositiveSuffix().equals(pctFmt.getNegativeSuffix())))
                {
                    // No positive prefix, or positive suffix
                    DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance();
                    // find the symbol
                    String plus = Character.toString(sym.getPlusSign());
                    // Make it a prefix, unless there is a prefix (same as negative) but no suffix
                    if (pctFmt.getPositivePrefix().length() > 0 && pctFmt.getPositiveSuffix().length() == 0)
                        pctFmt.setPositiveSuffix(plus);
                    else
                        pctFmt.setPositivePrefix(plus);
                }
            }
        }

        private void setFormatter()
        {
            int i = 1;
            // Rather than giving a format, modify the default
            Format formatter = NumberFormat.getIntegerInstance();
            addPositiveIndicator(formatter);
            NumberFormat formatterPercent = NumberFormat.getPercentInstance();
            addPositiveIndicator(formatterPercent);
            // Force the sign for Bytes formatting
            // Compare with org.eclipse.mat.snapshot.Histogram.getColumns()
            String detailed1 = "+"+BytesFormat.DETAILED_DECIMAL_FORMAT+";-"+BytesFormat.DETAILED_DECIMAL_FORMAT; //$NON-NLS-1$ //$NON-NLS-2$
            DecimalFormat bcf = new DecimalFormat(detailed1);
            NumberFormat nf = NumberFormat.getNumberInstance();
            if (nf instanceof DecimalFormat)
            {
                DecimalFormat bcf2 = (DecimalFormat)nf;
                bcf2.setMinimumFractionDigits(bcf.getMinimumFractionDigits());
                bcf2.setMaximumFractionDigits(bcf.getMaximumFractionDigits());
                addPositiveIndicator(bcf2);
                bcf = bcf2;
            }
            BytesFormat bfm = new BytesFormat(formatter, bcf);
            DeltaRetainedBytesFormat drbfm = new DeltaRetainedBytesFormat(formatter, bcf);

            for (ComparedColumn comparedColumn : displayedColumns)
            {
                Column c = comparedColumn.description;
                for (int j = 0; j < comparedColumn.getColumnIndexes().length; j++)
                {
                    if (mode != Mode.ABSOLUTE && j > 0)
                    {
                        if (!columns[i].getCalculateTotals() && c.getCalculateTotals())
                        {
                            // Set the totals mode
                            IDecorator decorator = columns[i].getDecorator();
                            columns[i] = new Column(columns[i].getLabel(), columns[i].getType(), columns[i].getAlign(),
                                            columns[i].getSortDirection(), columns[i].getFormatter(),
                                            columns[i].getComparator());
                            columns[i].decorator(decorator);
                        }
                        // Set the converter
                        Object converter = c.getData(Filter.ValueConverter.class);
                        if (converter != null || columns[i].getData(Filter.ValueConverter.class) != null)
                            columns[i].setData(Filter.ValueConverter.class, converter);
                        if (c.getFormatter() instanceof DecimalFormat)
                        {
                            DecimalFormat fm = ((DecimalFormat) c.getFormatter().clone());
                            fm.setPositivePrefix("+"); //$NON-NLS-1$
                            columns[i].formatting(fm);
                        }
                        else if (c.getFormatter() instanceof BytesFormat)
                        {
                            //BytesFormat fm = ((BytesFormat) c.getFormatter().clone());
                            // Force the sign - can't retrieve information from existing formatter
                            if (c.getFormatter().getClass() == BytesFormat.class)
                            {
                                columns[i].formatting(bfm);
                            }
                            else
                            {
                                columns[i].formatting(drbfm);
                                columns[i].setData(Filter.ValueConverter.class, drbfm.converter);
                            }
                        }
                        else
                        {
                            columns[i].formatting(formatter);
                        }
                    }
                    else
                    {
                        if (!columns[i].getCalculateTotals() && c.getCalculateTotals())
                        {
                            // Set the totals mode
                            IDecorator decorator = columns[i].getDecorator();
                            columns[i] = new Column(columns[i].getLabel(), columns[i].getType(), columns[i].getAlign(),
                                            columns[i].getSortDirection(), columns[i].getFormatter(),
                                            columns[i].getComparator());
                            columns[i].decorator(decorator);
                        }
                        columns[i].formatting(c.getFormatter());
                        // Set the converter
                        Object converter = c.getData(Filter.ValueConverter.class);
                        if (converter != null || columns[i].getData(Filter.ValueConverter.class) != null)
                            columns[i].setData(Filter.ValueConverter.class, converter);
                    }
                    i++;
                    if ((mode == Mode.DIFF_RATIO_TO_FIRST || mode == Mode.DIFF_RATIO_TO_PREVIOUS) && j > 0)
                    {
                        columns[i].formatting(formatterPercent);
                        columns[i].noTotals();
                        i++;
                    }
                }
            }
        }

        class KeyDecorator implements IDecorator
        {

            public String prefix(Object row)
            {
                final ComparedRow cr = (ComparedRow) row;
                return cr.prefix;
            }

            public String suffix(Object row)
            {
                final ComparedRow cr = (ComparedRow) row;
                return cr.suffix;
            }
        }

        class Decorator implements IDecorator
        {
            int table;
            IDecorator dec;
            Decorator(IDecorator dec, int table)
            {
                this.table = table;
                this.dec = dec;
            }

            public String prefix(Object row)
            {
                final ComparedRow cr = (ComparedRow) row;
                Object r = cr.getRows()[table];
                return r != null ? dec.prefix(r) : null;
            }

            public String suffix(Object row)
            {
                final ComparedRow cr = (ComparedRow) row;
                Object r = cr.getRows()[table];
                return r != null ? dec.suffix(r) : null;
            }
        }

        public void updateColumns()
        {
            List<Column> result = new ArrayList<Column>();
            result.add(new Column(key.getLabel(), key.getType(), key.getAlign(), null, key.getFormatter(), null));
            result.get(result.size() - 1).decorator(new KeyDecorator());

            displayedColumns = new ArrayList<ComparedColumn>();

            for (ComparedColumn comparedColumn : comparedColumns)
            {
                Column c = comparedColumn.description;
                if (comparedColumn.isDisplayed())
                {
                    displayedColumns.add(comparedColumn);
                    for (int j = 0; j < comparedColumn.getColumnIndexes().length; j++)
                    {
                        String label;
                        final int prev = mode == Mode.DIFF_TO_PREVIOUS || mode == Mode.DIFF_RATIO_TO_PREVIOUS ? j - 1 : 0;
                        Comparator<?>comparator;
                        if (j == 0 || mode == Mode.ABSOLUTE)
                        {
                            label = MessageUtil.format(Messages.CompareTablesQuery_ColumnAbsolute, c.getLabel(), j + 1);
                            final Comparator<Object> cmp = (Comparator<Object>) c.getComparator();
                            if (cmp != null)
                            {
                                final int tab = j;
                                comparator = new Comparator<ComparedRow>(){
                                    public int compare(ComparedRow o1, ComparedRow o2)
                                    {
                                        Object row1 = o1.rows[tab];
                                        Object row2 = o2.rows[tab];
                                        // Compare nulls - sort first
                                        if (row1 == null)
                                            return row2 == null ? 0 : -1;
                                        else if (row2 == null)
                                            return 1;
                                        else
                                            return cmp.compare(row1, row2);
                                    }
                                };
                            }
                            else
                            {
                                comparator = null;
                            }
                        }
                        else
                        {
                            label = MessageUtil.format(Messages.CompareTablesQuery_ColumnDifference,
                                            c.getLabel(), j + 1,
                                            prev + 1);
                            comparator = null;
                        }
                        result.add(new Column(label, c.getType(), c.getAlign(), c.getSortDirection(), c.getFormatter(),
                                        comparator));
                        // Pass through the decorator
                        if (c.getDecorator() != null)
                            result.get(result.size() - 1).decorator(new Decorator(c.getDecorator(), j));
                        // For percentage modes also add a percent change column for subsequent tables
                        if (j > 0 && (mode == Mode.DIFF_RATIO_TO_FIRST || mode == Mode.DIFF_RATIO_TO_PREVIOUS))
                        {
                            label = MessageUtil.format(Messages.CompareTablesQuery_ColumnPercentDifference,
                                            c.getLabel(), j + 1,
                                            prev + 1);
                            result.add(new Column(label, c.getType(), c.getAlign(), c.getSortDirection(), c
                                            .getFormatter(), null));
                        }
                    }
                }
            }

            columns = result.toArray(new Column[result.size()]);
            setFormatter();
        }

        IContextObject getContextFromTable(int i, Object row)
        {
            if (!sameSnapshot[i])
                return null;
            final ComparedRow cr = (ComparedRow) row;
            IContextObject ret = null;
            Object r = cr.getRows()[i];
            if (r != null)
            {
                ret = tables[i].getContext(r);
            }
            return ret;
        }

        /**
         * Add the derived operations from the source tables.
         * No needed as using the RefinedResult versions of the tables
         * already has them refined.
         * @param answer
         */
        void derivedops(ResultMetaData.Builder answer)
        {
            int found = 0;
            Set<DerivedOperation>allops = new HashSet<DerivedOperation>();
            for (int i = 0; i < tables.length; ++i)
            {
                IStructuredResult rr = tables[i];
                ResultMetaData data = rr.getResultMetaData();
                if (data != null)
                {
                    ++found;
                    Collection<DerivedOperation>ops = data.getDerivedOperations();
                    if (ops != null)
                    {
                        for (DerivedOperation op : data.getDerivedOperations())
                        {
                            allops.add(op);
                        }
                    }
                }
            }
            if (found == 0)
                return;
            if (allops.size() == 0)
                return;

            for (ContextDerivedData.DerivedOperation operation : allops)
                answer.addDerivedData(operation);

            return;
        }

    }

    public class ComparisonResultTable extends TableComparisonResult implements IResultTable
    {
        public ComparisonResultTable(List<ComparedRow> rows, Column key, List<ComparedColumn> comparedColumns,
                        Mode mode, Operation setOp)
        {
            super(rows, key, comparedColumns, mode, setOp);
        }

        public Object getRow(int rowId)
        {
            return rows.get(rowId);
        }

        public int getRowCount()
        {
            return rows.size();
        }
    }

    public class ComparisonResultTree extends TableComparisonResult implements IResultTree
    {
        public ComparisonResultTree(List<ComparedRow> rows, Column key, List<ComparedColumn> comparedColumns,
                        Mode mode, Operation setOp)
        {
            super(rows, key, comparedColumns, mode, setOp);
        }

        public List<?> getElements()
        {
            return rows;
        }

        public boolean hasChildren(Object element)
        {
            for (int i = 0; i < tables.length; i++)
            {
                IStructuredResult table = tables[i];
                if (table instanceof IResultTree)
                {
                    Object treerow = ((ComparedRow)element).getRows()[i];
                    if (treerow != null && ((IResultTree)table).hasChildren(treerow))
                        return true;
                }
            }
            return false;
        }

        public List<?> getChildren(Object parent)
        {
            return mergeKeys((ComparedRow)parent, new VoidProgressListener());
        }
    }
}

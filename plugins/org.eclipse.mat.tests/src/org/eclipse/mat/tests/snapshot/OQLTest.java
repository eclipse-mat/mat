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
package org.eclipse.mat.tests.snapshot;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

public class OQLTest
{
    @Test
    public void testUnreservedKeywords() throws SnapshotException
    {
        SnapshotFactory.createQuery("SELECT * FROM something.as.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.distinct.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.from.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.in.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.like.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.not.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.objects.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.union.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.where.Object");
    }

    @Test
    public void testErrorParseException() throws SnapshotException
    {
        String[] statements = new String[] { "select dominatorsof(s) from", //
                        "SELECT * FROM something.as retained set", //
                        "SELECT * FROM x WHERE" };

        for (String statement : statements)
        {
            try
            {
                SnapshotFactory.createQuery(statement);
                throw new SnapshotException(MessageFormat.format("Statement should generated error: {0}", statement));
            }
            catch (OQLParseException expected)
            {}
        }
    }

    @Test
    public void testSelectStar() throws SnapshotException
    {
        Object result = execute("select * from java.lang.String");

        assert result instanceof int[] : "'SELECT *' must return a result of type int[]";
        int[] objectIds = (int[]) result;
        assert objectIds.length == 492 : "492 objects of type java.lang.String expected";
    }

    @Test
    public void testSelectAttributes() throws SnapshotException
    {
        Object result = execute("select s.@objectId, s.value, s.count, s.offset from java.lang.String s");

        assert result instanceof IResultTable : "'SELECT x, y, z' must return a result of type IResultTable";
        IResultTable table = (IResultTable) result;
        assert table.getRowCount() == 492 : "492 objects of type java.lang.String expected";
        assert table.getColumns().length == 4 : "4 columns expected";

        // check if context is available
        Object row = table.getRow(0);
        int objectId = (Integer) table.getColumnValue(row, 0);
        assert objectId == table.getContext(row).getObjectId() : "Result must return underlying object id as context";
    }

    @Test
    public void testSelectRetained() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select as retained set * from java.lang.String");
        assert objectIds.length == 963 : "963 objects expected";
    }

    @Test
    public void testSelectObjects() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select objects dominators(s) from objects 0x1295e2f8 s");
        assert objectIds.length == 7;

        objectIds = (int[]) execute("select objects dominators(s) from objects ${snapshot}.getClassesByName(\"java.lang.String\", false) s");
        assert objectIds.length == 2;

        objectIds = (int[]) execute("select objects dominators(s) from objects (select * from java.lang.String) s");
        assert objectIds.length == 465;
    }

    @Test
    public void testUnion() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select * from objects 0 union (select * from objects 1)");
        assert objectIds.length == 2;

        IResultTable table = (IResultTable) execute("select toString(s) from objects 0x17c180b8 s union (select toHex(s.@objectAddress) from objects 1 s)");
        assert table.getRowCount() == 2;
        assert "main".equals(table.getColumnValue(table.getRow(0), 0));
        assert "0x12832b50".equals(table.getColumnValue(table.getRow(1), 0));
    }

    @Test
    public void testFromPattern() throws SnapshotException
    {
        Object result = execute("select * from \"java.lang.*\"");

        assert result instanceof int[] : "'SELECT *' must return a result of type int[]";
        int[] objectIds = (int[]) result;
        assert objectIds.length == 1198 : "1198 objects of type java.lang.* expected";
    }

    @Test
    public void testFromAddress() throws SnapshotException
    {
        Object result = execute("select * from objects 0x0");
        assert result instanceof int[] : "'SELECT *' must return a result of type int[]";
        int[] objectIds = (int[]) result;
        assert objectIds.length == 1 : "one object matching 0x0 expected";
    }

    @Test
    public void testFromObject() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM ${snapshot}.getClassesByName(\"java.lang.ref.Reference\", true)");
        assert objectIds.length == 21 : "expected 21 instanceof of java.lang.ref.Reference";
    }

    @Test
    public void testFromSubSelect() throws SnapshotException
    {
        String oql = "SELECT * FROM ( SELECT * " //
                        + "FROM java.lang.Class c " //
                        + "WHERE c implements org.eclipse.mat.snapshot.model.IClass )";
        int[] objectIds = (int[]) execute(oql);
        assert objectIds.length == 2058 : "expected 2058 instances of IClass";
    }

    @Test
    public void testFromInstanceOf() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM INSTANCEOF java.lang.ref.Reference");
        assert objectIds.length == 21 : "expected 21 instances of java.lang.ref.Reference";

        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK5_64BIT, false);

        Set<IClass> classes = new HashSet<IClass>(snapshot.getClassesByName("java.lang.ref.Reference", true));
        for (int id : objectIds)
            assert classes.contains(snapshot.getClassOf(id)) : MessageFormat.format(
                            "Object {0} not an instance of java.lang.ref.Reference ", id);
    }

    @Test
    public void testWhereRelationalOperators() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 51");
        assert objectIds.length == 16;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count >= 51");
        assert objectIds.length == 19;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < 51");
        assert objectIds.length == 473;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count <= 51");
        assert objectIds.length == 476;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE toString(s) LIKE \"java.*\"");
        assert objectIds.length == 27;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.value NOT IN dominators(s)");
        assert objectIds.length == 27;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE toString(s) = \"file.separator\"");
        assert objectIds.length == 1;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 100 AND s.@retainedHeapSize > s.@usedHeapSize");
        assert objectIds.length == 6;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 1000 OR s.value.@length > 1000");
        assert objectIds.length == 3;
    }

    @Test
    public void testWhereArithmetic() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length * 0.5");
        assert objectIds.length == 16;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length / 2");
        assert objectIds.length == 16;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length - 20");
        assert objectIds.length == 16;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count + 20 < s.value.@length");
        assert objectIds.length == 16;
    }

    @Test
    public void testWhereLiterals() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE ( s.count > 1000 ) = true");
        assert objectIds.length == 3;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE dominators(s).size() = 0");
        assert objectIds.length == 27;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE dominators(s).length = 0");
        assert objectIds.length == 27;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.@retainedHeapSize > 1024L");
        assert objectIds.length == 4;

        objectIds = (int[]) execute("SELECT * FROM java.lang.Thread s WHERE s.@GCRootInfo != null");
        assert objectIds.length == 4;
    }

    @Test
    public void testBuildInFunctions() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK5_64BIT, false);
        int objectId = ((int[]) execute("select * from objects 0x17c38b80 s"))[0];

        IResultTable result = (IResultTable) execute("select toString(s) from objects 0x17c38b80 s");
        assert "little".equals(result.getColumnValue(result.getRow(0), 0));

        result = (IResultTable) execute("select toHex(s.@objectAddress) from objects 0x17c38b80 s");
        assert "0x17c38b80".equals(result.getColumnValue(result.getRow(0), 0));

        result = (IResultTable) execute("select dominators(s).length from objects 0x17c38b80 s");
        assert Integer.valueOf(1).equals(result.getColumnValue(result.getRow(0), 0));

        result = (IResultTable) execute("select outbounds(s) from objects 0x17c38b80 s");
        int[] outbounds = (int[]) result.getColumnValue(result.getRow(0), 0);
        assert outbounds.length == 2;
        assert Arrays.toString(snapshot.getOutboundReferentIds(objectId)).equals(Arrays.toString(outbounds));

        result = (IResultTable) execute("select inbounds(s) from objects 0x17c38b80 s");
        int[] inbounds = (int[]) result.getColumnValue(result.getRow(0), 0);
        assert inbounds.length == 1;
        assert Arrays.toString(snapshot.getInboundRefererIds(objectId)).equals(Arrays.toString(inbounds));

        result = (IResultTable) execute("select classof(s) from objects 0x17c38b80 s");
        IClass obj = (IClass) result.getColumnValue(result.getRow(0), 0);
        assert "java.lang.String".equals(obj.getName());

        result = (IResultTable) execute("select dominatorof(s) from objects 0x17c38b80 s");
        assert result.getColumnValue(result.getRow(0), 0) != null;
    }

    @Test(expected = SnapshotException.class)
    public void testErrorRuntimeException() throws SnapshotException
    {
        execute("select s from objects 0 where dominatorof(s) = 0");
    }

    // //////////////////////////////////////////////////////////////
    // internal helper
    // //////////////////////////////////////////////////////////////

    private Object execute(String oql) throws SnapshotException
    {
        try
        {
            ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK5_64BIT, false);
            IOQLQuery query = SnapshotFactory.createQuery(oql);
            return query.execute(snapshot, new VoidProgressListener());
        }
        catch (OQLParseException e)
        {
            throw new SnapshotException("Error while parsing: " + oql, e);
        }
    }
}

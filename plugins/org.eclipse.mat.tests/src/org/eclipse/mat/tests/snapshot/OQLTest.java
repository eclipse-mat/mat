/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - test AS clause
 *    IBM Corporation - test instanceof with object id/address
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;

import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@SuppressWarnings("nls")
public class OQLTest
{
    @Test
    public void testUnreservedKeywords() throws SnapshotException
    {
        SnapshotFactory.createQuery("SELECT * FROM something.as.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.distinct.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.from.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.in.Object");
        SnapshotFactory.createQuery("SELECT * FROM something.is.Object");
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
                throw new SnapshotException(MessageUtil.format("Statement should generated error: {0}", statement));
            }
            catch (OQLParseException expected)
            {}
        }
    }

    @Test
    public void testSelectStar() throws SnapshotException
    {
        Object result = execute("select * from java.lang.String");

        assertThat("'SELECT *' must return a result of type int[]", result, instanceOf(int[].class));
        int[] objectIds = (int[]) result;
        assertThat("492 objects of type java.lang.String expected", objectIds.length, equalTo(492));
    }

    @Test
    public void testSelectAttributes() throws SnapshotException
    {
        Object result = execute("select s.@objectId, s.value, s.count, s.offset from java.lang.String s");

        assertThat("'SELECT x, y, z' must return a result of type IResultTable", result, instanceOf(IResultTable.class));
        IResultTable table = (IResultTable) result;
        assertThat("492 objects of type java.lang.String expected", table.getRowCount(), equalTo(492));
        assertThat("4 columns expected", table.getColumns().length, equalTo(4));

        // check if context is available
        Object row = table.getRow(0);
        int objectId = (Integer) table.getColumnValue(row, 0);
        assertThat("Result must return underlying object id as context", objectId, equalTo(table.getContext(row).getObjectId()));
        checkGetOQL(table);
    }

    @Test
    public void testSelectRetained1() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select as retained set * from java.lang.String");
        assertThat("963 objects expected", objectIds.length, equalTo(963));
    }

    @Test
    public void testSelectRetained2() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT AS RETAINED SET * FROM OBJECTS ( SELECT * FROM java.lang.String  ) s ");
        assertThat("963 objects expected", objectIds.length, equalTo(963));
    }

    @Test
    public void testSelectRetained3() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT AS RETAINED SET OBJECTS s FROM OBJECTS ( SELECT * FROM java.lang.String  ) s ");
        assertThat("963 objects expected", objectIds.length, equalTo(963));
    }

    @Test
    public void testSelectRetained4() throws SnapshotException
    {
        IResultTable resulttable = (IResultTable)execute("SELECT AS RETAINED SET s FROM OBJECTS ( SELECT * FROM java.lang.String  ) s ");
        assertThat("963 objects expected", resulttable.getRowCount(), equalTo(963));
        checkGetOQL(resulttable);
    }

    @Test
    public void testSelectObjects() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select objects dominators(s) from objects 0x1295e2f8 s");
        assertThat(objectIds.length, equalTo(7));

        objectIds = (int[]) execute("select objects dominators(s) from objects ${snapshot}.getClassesByName(\"java.lang.String\", false) s");
        assertThat(objectIds.length, equalTo(2));

        objectIds = (int[]) execute("select objects dominators(s) from objects (select * from java.lang.String) s");
        assertThat(objectIds.length, equalTo(465));
    }

    @Test
    public void testDistinct1() throws SnapshotException
    {
        Object result = execute("select distinct objects classof(s) from java.lang.String s");
        assertThat("'SELECT distinct objects' must return a result of type int[]", result, instanceOf(int[].class));
        int[] objectIds = (int[]) result;
        assertThat("1 object of type java.lang.Class expected", objectIds.length, equalTo(1));
    }

    @Test
    public void testDistinct2() throws SnapshotException
    {
        Object result = execute("select distinct classof(s) from java.lang.String s");
        assertThat("'SELECT distinct' must return a result of type IResultTable", result, instanceOf(IResultTable.class));
        IResultTable resulttable = (IResultTable) result;
        assertThat("1 object of type java.lang.Class expected", resulttable.getRowCount(), equalTo(1));
        checkGetOQL(resulttable);
    }


    @Test
    public void testDistinct3() throws SnapshotException
    {
        Object result = execute("SELECT DISTINCT z FROM OBJECTS ( SELECT classof(s) FROM java.lang.String s  ) z");
        assertThat("'SELECT distinct' must return a result of type IResultTable", result, instanceOf(IResultTable.class));
        IResultTable resulttable = (IResultTable) result;
        assertThat("1 object of type java.lang.Class expected", resulttable.getRowCount(), equalTo(1));
        checkGetOQL(resulttable);
    }

    @Test
    public void testOperations1int() throws SnapshotException
    {
        Object result = execute("select * from objects ((1+2+3--2-2)*-4/-3)");
        assertEquals(8.0, result);
    }

    @Test
    public void testOperations1long() throws SnapshotException
    {
        Object result = execute("select * from objects ((1L+2L+3L--2L-2L)*-4L/-3L)");
        assertEquals(8.0, result);
    }

    @Test
    public void testOperations1double() throws SnapshotException
    {
        Object result = execute("select * from objects ((1.0+2.0+3.0--2.0-2.0)*-4.0/-3.0)");
        assertEquals(8.0, result);
    }

    @Test
    public void testOperations2int() throws SnapshotException
    {
        Object result = execute("select * from objects (1=1 AND 3<2 OR 4>3 AND 4>=4 AND 4 <= 4 AND 4 < 5)");
        assertEquals(true, result);
    }


    @Test
    public void testOperations2long() throws SnapshotException
    {
        Object result = execute("select * from objects (1L=1L AND 3L<2L OR 4L>3L AND 4L>=4L AND 4L <= 4L AND 4L < 5L)");
        assertEquals(true, result);
    }


    @Test
    public void testOperations2double() throws SnapshotException
    {
        Object result = execute("select * from objects (1.0=1.0 AND 3.0<2.0 OR 4.0>3.0 AND 4.0>=4.0 AND 4.0 <= 4.0 AND 4.0 < 5.0)");
        assertEquals(true, result);
    }

    @Test
    public void testOperations2date() throws SnapshotException
    {
        Object result = execute("select * from objects (" +
                        "${snapshot}.@snapshotInfo.@creationDate < ${snapshot}.@snapshotInfo.@creationDate OR " +
                        "${snapshot}.@snapshotInfo.@creationDate > ${snapshot}.@snapshotInfo.@creationDate OR " +
                        "${snapshot}.@snapshotInfo.@creationDate != ${snapshot}.@snapshotInfo.@creationDate OR " +
                        "${snapshot}.@snapshotInfo.@creationDate = ${snapshot}.@snapshotInfo.@creationDate AND " +
                        "${snapshot}.@snapshotInfo.@creationDate <= ${snapshot}.@snapshotInfo.@creationDate AND " +
                        "${snapshot}.@snapshotInfo.@creationDate >= ${snapshot}.@snapshotInfo.@creationDate)");
        assertEquals(true, result);
    }

    @Test
    public void testUnion() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select * from objects 0 union (select * from objects 1)");
        assertThat(objectIds.length, equalTo(2));

        IResultTable table = (IResultTable) execute("select toString(s) from objects 0x17c180b8 s union (select toHex(s.@objectAddress) from objects 1 s)");
        assertThat(table.getRowCount(), equalTo(2));
        assertThat(table.getColumnValue(table.getRow(0), 0), equalTo((Object)"main"));
        assertThat(table.getColumnValue(table.getRow(1), 0), equalTo((Object)"0x12832b50"));
        checkGetOQL(table);
    }

    @Test
    public void testUnionCommand1() throws SnapshotException
    {
        String oql = "select * from objects 0,1 union (select * from objects 1,2)";
        IOQLQuery q1 = SnapshotFactory.createQuery(oql);
        String oql2 = q1.toString();
        IOQLQuery q2 = SnapshotFactory.createQuery(oql2);
        String oql3 = q2.toString();
        assertEquals(oql2, oql3);
    }

    @Test
    public void testUnionCommand1a() throws SnapshotException
    {
        int[] r = (int[])execute("select * from objects 0,1 union (select * from objects 1,2)");
        // Should this be 3 (distinct elements) or 4 (union all)
        //assertEquals(3, r.length);
        //assertEquals(4, r.length);
        assertTrue(r.length == 3 || r.length == 4);
    }

    @Test
    public void testUnionCommand2() throws SnapshotException
    {
        String oql = "select s from objects 0,1 s union (select t from objects 1,2 t)";
        Object r = execute(oql);
        IOQLQuery.Result oo = (IOQLQuery.Result)r;
        String oql2 = oo.getOQLQuery();
        r = execute(oql2);
        oo = (IOQLQuery.Result)r;
        String oql3 = oo.getOQLQuery();
        assertEquals(oql2, oql3);
    }

    @Test
    public void testUnionCommand2a() throws SnapshotException
    {
        IResultTable t = (IResultTable)execute("select s from objects 0,1 s union (select t from objects 1,2 t)");
        // Should this be 3 (distinct elements) or 4 (union all)
        //assertEquals(3, t.getRowCount());
        //assertEquals(4, t.getRowCount());
        assertTrue(t.getRowCount() == 3 || t.getRowCount() == 4);
        checkGetOQL(t);
    }

    @Test
    public void testUnionCommand3() throws SnapshotException
    {
        String oql = "select * from notfound union (select * from objects 1,2)";
        IOQLQuery q1 = SnapshotFactory.createQuery(oql);
        String oql2 = q1.toString();
        IOQLQuery q2 = SnapshotFactory.createQuery(oql2);
        String oql3 = q2.toString();
        assertEquals(oql2, oql3);
    }

    @Test
    public void testUnionCommand3a() throws SnapshotException
    {
        int[] r = (int[])execute("select * from notfound union (select * from objects 1,2)");
        assertEquals(2, r.length);
    }

    @Test
    public void testUnionCommand4() throws SnapshotException
    {
        String oql = "select s from notfound s union (select t from objects 1,2 t)";
        Object r = execute(oql);
        IOQLQuery.Result oo = (IOQLQuery.Result)r;
        String oql2 = oo.getOQLQuery();
        r = execute(oql2);
        oo = (IOQLQuery.Result)r;
        String oql3 = oo.getOQLQuery();
        assertEquals(oql2, oql3);
    }

    @Test
    public void testUnionCommand4a() throws SnapshotException
    {
        IResultTable t = (IResultTable)execute("select s from notfound s union (select t from objects 1,2 t)");
        assertEquals(2, t.getRowCount());
        checkGetOQL(t);
    }

    @Test
    public void testUnionCommand5() throws SnapshotException
    {
        String oql = "select * from objects 0,1 union (select * from notfound)";
        IOQLQuery q1 = SnapshotFactory.createQuery(oql);
        String oql2 = q1.toString();
        IOQLQuery q2 = SnapshotFactory.createQuery(oql2);
        String oql3 = q2.toString();
        assertEquals(oql2, oql3);
    }

    @Test
    public void testUnionCommand5a() throws SnapshotException
    {
        int[] r = (int[])execute("select * from objects 0,1 union (select * from notfound)");
        assertEquals(2, r.length);
    }

    @Test
    public void testUnionCommand6() throws SnapshotException
    {
        String oql = "select s from objects 0,1 s union (select t from notfound t)";
        Object r = execute(oql);
        IOQLQuery.Result oo = (IOQLQuery.Result)r;
        String oql2 = oo.getOQLQuery();
        r = execute(oql2);
        oo = (IOQLQuery.Result)r;
        String oql3 = oo.getOQLQuery();
        assertEquals(oql2, oql3);
    }


    @Test
    public void testUnionCommand6a() throws SnapshotException
    {
        IResultTable t = (IResultTable)execute("select s from objects 0,1 s union (select t from notfound t)");
        assertEquals(2, t.getRowCount());
        checkGetOQL(t);
    }

    @Test
    public void testUnionCommand7() throws SnapshotException
    {
        int[] r = (int[])execute("select distinct * from objects 0,0,1 union (select * from objects 1,1,2,2)");
        assertEquals(6, r.length);
    }

    @Test
    public void testUnionCommand8() throws SnapshotException
    {
        int[] r = (int[])execute("select * from objects 0,0,1 union (select distinct * from objects 1,1,2,2)");
        assertEquals(5, r.length);
    }

    @Test
    public void testUnionCommand9() throws SnapshotException
    {
        int[] r = (int[])execute("select distinct * from objects 0,0,1 union (select distinct * from objects 1,1,2,2)");
        assertEquals(4, r.length);
    }

    @Test
    public void testUnionCommand10() throws SnapshotException
    {
        String oql = "SELECT s FROM missing s  UNION (SELECT s FROM missing s ) UNION (SELECT s FROM java.lang.String s )";
        IOQLQuery.Result  r = (IOQLQuery.Result)execute(oql);
        assertEquals(oql, r.getOQLQuery());
        assertEquals(492, ((IResultTable)r).getRowCount());
        checkGetOQL((IResultTable)r);
    }

    @Test
    public void testUnionCommand11() throws SnapshotException
    {
        String oql = "SELECT s FROM java.lang.String[] s  UNION (SELECT s FROM missing s ) UNION (SELECT s FROM java.lang.String s )";
        IOQLQuery.Result  r = (IOQLQuery.Result)execute(oql);
        assertEquals(oql, r.getOQLQuery());
        assertEquals(28 + 492, ((IResultTable)r).getRowCount());
        checkGetOQL((IResultTable)r);
    }

    @Test
    public void testUnionCommand12() throws SnapshotException
    {
        String oql = "SELECT * FROM missing  UNION (SELECT * FROM missing ) UNION (SELECT * FROM java.lang.String )";
        int r[] = (int[])execute(oql);
        assertEquals(492, r.length);
    }

    @Test
    public void testUnionCommand13() throws SnapshotException
    {
        String oql = "SELECT * FROM java.lang.String[]  UNION (SELECT * FROM missing ) UNION (SELECT * FROM java.lang.String )";
        int r[] = (int[])execute(oql);
        assertEquals(28 + 492, r.length);
    }

    @Test
    public void testFromPattern() throws SnapshotException
    {
        Object result = execute("select * from \"java.lang.*\"");

        assertThat("'SELECT *' must return a result of type int[]", result, instanceOf(int[].class));
        int[] objectIds = (int[]) result;
        assertThat("1198 objects of type java.lang.* expected", objectIds.length, equalTo(1198));
    }

    @Test
    public void testFromAddress() throws SnapshotException
    {
        Object result = execute("select * from objects 0x0");
        assertThat("'SELECT *' must return a result of type int[]", result, instanceOf(int[].class));
        int[] objectIds = (int[]) result;
        assertThat("one object matching 0x0 expected", objectIds.length, equalTo(1));
    }

    @Test
    public void testFromObject() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM ${snapshot}.getClassesByName(\"java.lang.ref.Reference\", true)");
        assertThat("expected 21 instanceof of java.lang.ref.Reference", objectIds.length, equalTo(21));
    }

    @Test
    public void testFromByName1() throws SnapshotException
    {
        IResultTable irt = (IResultTable)execute("SELECT s, snapshot FROM OBJECTS ${snapshot}.getClassesByName(\"java.lang.Class\",false).iterator().next().getObjectIds() s WHERE ${snapshot}.isClass(s)");
        ISnapshot sn = (ISnapshot)irt.getColumnValue(0, 1);
        assertThat(irt.getRowCount(), equalTo(sn.getSnapshotInfo().getNumberOfClasses()));
    }

    @Test
    public void testFromByName2() throws SnapshotException
    {
        IResultTable irt = (IResultTable)execute("SELECT t FROM ("
                        + "SELECT OBJECTS s FROM OBJECTS ${snapshot}.getClassesByName(\"java.lang.Class\",false).iterator().next().getObjectIds() s WHERE ${snapshot}.isClass(s)"
                        + ") t");
        IObject o = (IObject)irt.getColumnValue(0, 0);
        assertThat(irt.getRowCount(), equalTo(o.getSnapshot().getSnapshotInfo().getNumberOfObjects()));
        checkGetOQL(irt);
    }

    @Test
    public void testFromByName3() throws SnapshotException
    {
        IResultTable irt = (IResultTable)execute("SELECT t,snapshot FROM ( eval(("
                        + "SELECT OBJECTS s FROM OBJECTS ${snapshot}.getClassesByName(\"java.lang.Class\",false).iterator().next().getObjectIds() s WHERE ${snapshot}.isClass(s)"
                        + "))"
                        + ") t");
        IObject o = (IObject)irt.getColumnValue(0, 0);
        assertThat(irt.getRowCount(), equalTo(o.getSnapshot().getSnapshotInfo().getNumberOfObjects()));
        checkGetOQL(irt);
    }

    @Test
    public void testFromByName4() throws SnapshotException
    {
        IResultTable irt = (IResultTable)execute("SELECT s,snapshot FROM OBJECTS ${snapshot}.getClassesByName(\"java.lang.Object\",true).toArray() s WHERE ${snapshot}.isClass(s.@objectId)");
        ISnapshot sn = (ISnapshot)irt.getColumnValue(0, 1);
        assertThat(irt.getRowCount(), equalTo(sn.getSnapshotInfo().getNumberOfClasses()));
        checkGetOQL(irt);
    }

    @Test
    public void testFromByName5() throws SnapshotException
    {
        IResultTable irt = (IResultTable)execute("SELECT s, snapshot FROM ${snapshot}.getClassesByName(\"java.lang.Object\",true).toArray() s WHERE ${snapshot}.isClass(s.@objectId)");
        ISnapshot sn = (ISnapshot)irt.getColumnValue(0, 1);
        assertThat(irt.getRowCount(), equalTo(sn.getSnapshotInfo().getNumberOfClasses()));
        checkGetOQL(irt);
    }

    @Test
    public void testFromByName6() throws SnapshotException
    {
        IResultTable irt = (IResultTable)execute("SELECT s, snapshot FROM INSTANCEOF ${snapshot}.getClassesByName(\"java.lang.Object\",false).toArray() s WHERE ${snapshot}.isClass(s.@objectId)");
        ISnapshot sn = (ISnapshot)irt.getColumnValue(0, 1);
        assertThat(irt.getRowCount(), equalTo(sn.getSnapshotInfo().getNumberOfClasses()));
        checkGetOQL(irt);
    }

    @Test
    public void testFromByName7() throws SnapshotException
    {
        IResultTable irt = (IResultTable)execute("SELECT AS RETAINED SET s, snapshot FROM ${snapshot}.getClassesByName(\"java.lang.Class\",false) s");
        ISnapshot sn = (ISnapshot)irt.getColumnValue(0, 1);
        assertThat(irt.getRowCount(), greaterThan(sn.getSnapshotInfo().getNumberOfClasses()));
        checkGetOQL(irt);
    }

    @Test
    public void testFromSubSelect() throws SnapshotException
    {
        String oql = "SELECT * FROM ( SELECT * " //
                        + "FROM java.lang.Class c " //
                        + "WHERE c implements org.eclipse.mat.snapshot.model.IClass )";
        int[] objectIds = (int[]) execute(oql);
        assertThat("expected 2058 instances of IClass", objectIds.length, equalTo(2058));
    }

    @Test
    public void testImplements() throws SnapshotException
    {
        String oql = "SELECT * " //
                        + "FROM java.lang.Class c " //
                        + "WHERE c implements org.eclipse.mat.snapshot.model.IClass ";
        IOQLQuery q1 = SnapshotFactory.createQuery(oql);
        String oql2 = q1.toString();
        int[] objectIds = (int[]) execute(oql2);
        assertEquals("expected 383 instances of IClass", 383, objectIds.length);
    }

    @Test
    public void testFromInstanceOf() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM INSTANCEOF java.lang.ref.Reference");
        assertThat("expected 21 instances of java.lang.ref.Reference", objectIds.length, equalTo(21));

        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK5_64BIT, false);

        Collection<IClass>rClasses = snapshot.getClassesByName("java.lang.ref.Reference", true);
        assertNotNull(rClasses);
        Set<IClass> classes = new HashSet<IClass>(rClasses);
        for (int id : objectIds)
        {
            assertThat(MessageUtil.format("Object {0} not an instance of java.lang.ref.Reference ", id), 
                            classes, hasItems(snapshot.getClassOf(id)));
            assert classes.contains(snapshot.getClassOf(id)) : MessageUtil.format(
                            "Object {0} not an instance of java.lang.ref.Reference ", id);
        }
    }

    @Test
    public void testWhereRelationalOperators() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 51");
        assertThat(objectIds.length, equalTo(16));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count >= 51");
        assertThat(objectIds.length, equalTo(19));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < 51");
        assertThat(objectIds.length, equalTo(473));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count <= 51");
        assertThat(objectIds.length, equalTo(476));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE toString(s) LIKE \"java.*\"");
        assertThat(objectIds.length, equalTo(27));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE toString(s) NOT LIKE \"java.*\"");
        assertThat(objectIds.length, equalTo(492 - 27));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.value IN dominators(s)");
        assertThat(objectIds.length, equalTo(492 - 27));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.value NOT IN dominators(s)");
        assertThat(objectIds.length, equalTo(27));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE toString(s) = \"file.separator\"");
        assertThat(objectIds.length, equalTo(1));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 100 AND s.@retainedHeapSize > s.@usedHeapSize");
        assertThat(objectIds.length, equalTo(6));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 1000 OR s.value.@length > 1000");
        assertThat(objectIds.length, equalTo(3));
    }

    /**
     * Check reads of attributes declared in subclasses
     * @throws SnapshotException
     */
    @Test
    public void testWhereRelationalOperatorsOnAttributes() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select * from instanceof java.util.Vector s where s.elementCount < 10");
        assertEquals("Expected to read ", 11, objectIds.length);
    }

    /**
     * Check reads of statics declared in classes
     * @throws SnapshotException
     */
    @Test
    public void testWhereRelationalOperatorsOnStaticAttributes() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("select * from java.lang.Class s where s.serialVersionUID.toString().contains(\"-\")");
        assertEquals("Expected to read ", 52, objectIds.length);
    }

    @Test
    public void testWhereArithmetic() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length * 0.5");
        assertThat(objectIds.length, equalTo(16));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length / 2");
        assertThat(objectIds.length, equalTo(16));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length - 20");
        assertThat(objectIds.length, equalTo(16));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count + 20 < s.value.@length");
        assertThat(objectIds.length, equalTo(16));
    }

    @Test
    public void testWhereArithmeticInt() throws SnapshotException
    {
        // get(int) with an int, not a long
        int[] objectIds0 = (int[])execute("SELECT * FROM int[]");
        int[] objectIds1 = (int[])execute("SELECT * FROM int[] c WHERE (c.@valueArray.get(c.@length - 1) > 0)");
        int[] objectIds2 = (int[])execute("SELECT * FROM int[] c WHERE (c.@valueArray.get(c.@length + -1) <= 0)");

        assertEquals(objectIds0.length, objectIds1.length + objectIds2.length);
    }

    @Test
    public void testWhereLiterals() throws SnapshotException
    {
        int[] objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE ( s.count > 1000 ) = true");
        assertThat(objectIds.length, equalTo(3));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE dominators(s).size() = 0");
        assertThat(objectIds.length, equalTo(27));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE dominators(s).length = 0");
        assertThat(objectIds.length, equalTo(27));

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.@retainedHeapSize > 1024L");
        assertThat(objectIds.length, equalTo(4));

        objectIds = (int[]) execute("SELECT * FROM java.lang.Thread s WHERE s.@GCRootInfo != null");
        assertThat(objectIds.length, equalTo(4));
    }

    @Test
    public void testBuildInFunctions() throws SnapshotException
    {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK5_64BIT, false);
        int objectId = ((int[]) execute("select * from objects 0x17c38b80 s"))[0];

        IResultTable result = (IResultTable) execute("select toString(s) from objects 0x17c38b80 s");
        assertThat(result.getColumnValue(result.getRow(0), 0), equalTo((Object)"little"));

        result = (IResultTable) execute("select toHex(s.@objectAddress) from objects 0x17c38b80 s");
        assertThat(result.getColumnValue(result.getRow(0), 0), equalTo((Object)"0x17c38b80"));

        result = (IResultTable) execute("select dominators(s).length from objects 0x17c38b80 s");
        assertThat(result.getColumnValue(result.getRow(0), 0), equalTo((Object)1));

        result = (IResultTable) execute("select outbounds(s) from objects 0x17c38b80 s");
        int[] outbounds = (int[]) result.getColumnValue(result.getRow(0), 0);
        assertThat(outbounds.length, equalTo(2));
        assertThat(outbounds, equalTo(snapshot.getOutboundReferentIds(objectId)));

        result = (IResultTable) execute("select inbounds(s) from objects 0x17c38b80 s");
        int[] inbounds = (int[]) result.getColumnValue(result.getRow(0), 0);
        assertThat(inbounds.length, equalTo(1));
        assertThat(inbounds, equalTo(snapshot.getInboundRefererIds(objectId)));

        result = (IResultTable) execute("select classof(s) from objects 0x17c38b80 s");
        IClass obj = (IClass) result.getColumnValue(result.getRow(0), 0);
        assertThat(obj.getName(), equalTo("java.lang.String"));

        result = (IResultTable) execute("select dominatorof(s) from objects 0x17c38b80 s");
        assertNotNull(result.getColumnValue(result.getRow(0), 0));
    }

    @Test(expected = SnapshotException.class)
    public void testErrorRuntimeException() throws SnapshotException
    {
        execute("select s from objects 0 where dominatorof(s) = 0");
    }

    /**
     * Test spaces in as clause
     * @throws SnapshotException
     */
    @Test
    public void testAsSpaces() throws SnapshotException
    {
        String label = "A B";
        IOQLQuery q1 = SnapshotFactory.createQuery("SELECT s.@objectId AS \"" + label + "\" FROM java.lang.Object s");
        String s = q1.toString();
        IResultTable r = (IResultTable)execute(s);
        assertEquals(label, r.getColumns()[0].getLabel());
        IOQLQuery q2 = SnapshotFactory.createQuery(s);
        String s2 = q2.toString();
        assertEquals(s, s2);
        checkGetOQL(r);
    }

    /**
     * Test empty as clause
     * @throws SnapshotException
     */
    @Test
    public void testAsEmpty() throws SnapshotException
    {
        String label = "";
        IOQLQuery q1 = SnapshotFactory.createQuery("SELECT s.@objectId AS \"" + label + "\" FROM java.lang.Object s");
        String s = q1.toString();
        IResultTable r = (IResultTable)execute(s);
        assertEquals(label, r.getColumns()[0].getLabel());
        IOQLQuery q2 = SnapshotFactory.createQuery(s);
        String s2 = q2.toString();
        assertEquals(s, s2);
        checkGetOQL(r);
    }

    /**
     * Test punctuation in as clause
     * @throws SnapshotException
     */
    @Test
    public void testAsPunctution() throws SnapshotException
    {
        String label = "A,B";
        IOQLQuery q1 = SnapshotFactory.createQuery("SELECT s.@objectId AS \"" + label + "\" FROM java.lang.Object s");
        String s = q1.toString();
        IResultTable r = (IResultTable)execute(s);
        assertEquals(label, r.getColumns()[0].getLabel());
        IOQLQuery q2 = SnapshotFactory.createQuery(s);
        String s2 = q2.toString();
        assertEquals(s, s2);
        checkGetOQL(r);
    }

    /**
     * Test AND clause is not collapsed.
     * @throws SnapshotException
     */
    @Test
    public void testAndClauseSpaces() throws SnapshotException
    {
        final String queryString = "SELECT s.value FROM INSTANCEOF java.lang.Number s WHERE (true and s.value)";
        IOQLQuery q1 = SnapshotFactory.createQuery(queryString);
        String s = q1.toString();
        IOQLQuery q2 = SnapshotFactory.createQuery(s);
        String s2 = q2.toString();
        assertEquals(s, s2);
        IResultTable r = (IResultTable)execute(s);
        checkGetOQL(r);
    }

    /**
     * Test AND clause is evaluated.
     * @throws SnapshotException
     */
    @Test
    public void testAndClause() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s.value FROM INSTANCEOF java.lang.Number s WHERE (s.value and true)");
        assertThat("3 non-zero Numbers expected", res.getRowCount(), equalTo(3));
    }

    @Test
    public void testConversionsLong1() throws SnapshotException
    {
        // Array of 2 Strings and an object array
        int res[] = (int[])execute("SELECT OBJECTS @referenceArray.get(0) FROM OBJECTS 0x17c17128");
        assertEquals(1, res.length);
    }

    @Test
    public void testConversionsLong() throws SnapshotException
    {
        // Array of 2 Strings and an object array
        int res[] = (int[])execute("SELECT OBJECTS @referenceArray FROM OBJECTS 0x17c17128");
        assertEquals(3, res.length);
    }

    @Test
    public void testGetClasses1() throws SnapshotException
    {
        // 21 byte arrays
        int res[] = (int[])execute("SELECT * FROM ${snapshot}.getClasses().get(2)");
        assertEquals(21, res.length);
    }

    @Test
    public void testGetClasses2() throws SnapshotException
    {
        // 21 byte arrays
        int res[] = (int[])execute("SELECT * FROM (SELECT OBJECTS s FROM OBJECTS ${snapshot}.getClasses().get(2).@objectId s)");
        assertEquals(21, res.length);
    }

    @Test
    public void testGetClasses3() throws SnapshotException
    {
        // 21 byte arrays
        Class<?> s = (Class<?>)execute("SELECT * FROM OBJECTS ${snapshot}.getClasses().@class");
        assertEquals("class java.util.Arrays$ArrayList", s.toString());
    }

    @Test
    public void testGetClasses4() throws SnapshotException
    {
        // 492 Strings
        int[] objectIds = (int[])execute("select * from java.lang.String s where (select * from objects ${snapshot}.isClass(s.@objectId)) = false");
        assertEquals(492, objectIds.length);
    }

    @Test
    public void testInstanceOf1() throws SnapshotException
    {
        // 25 Abstract Maps
        int[] objectIds = (int[])execute("select * from instanceof java.util.AbstractMap");
        assertEquals(25, objectIds.length);
    }

    @Test
    public void testInstanceOf2() throws SnapshotException
    {
        // 25 Abstract Maps
        int[] objectIds = (int[])execute("select * from instanceof 0x128da800");
        assertEquals(25, objectIds.length);
    }

    @Test
    public void testInstanceOf3() throws SnapshotException
    {
        // 25 Abstract Maps
        int[] objectIds = (int[])execute("select * from instanceof 120");
        assertEquals(25, objectIds.length);
    }

    @Test
    public void testInstanceOf4() throws SnapshotException
    {
        // 10 Abstract Map classes
        int[] objectIds = (int[])execute("select * from objects instanceof java.util.AbstractMap");
        assertEquals(10, objectIds.length);
    }

    @Test
    public void testInstanceOf5() throws SnapshotException
    {
        // 10 Abstract Map classes
        int[] objectIds = (int[])execute("select * from objects instanceof 0x128da800");
        assertEquals(10, objectIds.length);
    }

    @Test
    public void testInstanceOf6() throws SnapshotException
    {
        // 10 Abstract Map classes
        int[] objectIds = (int[])execute("select * from objects instanceof 120");
        assertEquals(10, objectIds.length);
    }

    @Test
    public void testInstanceOf7() throws SnapshotException
    {
        // 25 Abstract Maps
        int[] objectIds = (int[])execute("select * from instanceof (select * from objects java.util.AbstractMap)");
        assertEquals(25, objectIds.length);
    }

    @Test
    public void testInstanceOf8() throws SnapshotException
    {
        // 25 Abstract Maps
        int[] objectIds = (int[])execute("select * from objects instanceof (select * from objects java.util.AbstractMap)");
        assertEquals(10, objectIds.length);
    }

    @Test
    public void testInstanceOf9() throws SnapshotException
    {
        // 25 Abstract Maps
        int[] objectIds = (int[])execute("select * from instanceof ${snapshot}.getClassesByName(\"java.util.AbstractMap\", false)");
        assertEquals(25, objectIds.length);
    }

    @Test
    public void testInstanceOf10() throws SnapshotException
    {
        // 25 Abstract Maps
        int[] objectIds = (int[])execute("select * from objects instanceof ${snapshot}.getClassesByName(\"java.util.AbstractMap\", false)");
        assertEquals(10, objectIds.length);
    }

    @Test
    public void testOverloadedMethod1() throws SnapshotException
    {
        // 80 objects - baseline for other tests
        int[] objectIds = (int[])execute("SELECT * FROM \".....i.*\"");
        assertEquals(80, objectIds.length);
    }

    @Test
    public void testOverloadedMethod2() throws SnapshotException
    {
        // 80 objects
        // indexOf(String)
        int[] objectIds = (int[])execute("SELECT * FROM \".*\" c WHERE (c.@clazz.@name.indexOf(\"i\") = 5)");
        assertEquals(80, objectIds.length);
    }

    @Test
    public void testOverloadedMethod3() throws SnapshotException
    {
        // 80 objects
        // indexOf(int) with a char
        int[] objectIds = (int[])execute("SELECT * FROM \".*\" c WHERE (c.@clazz.@name.indexOf('i') = 5)");
        assertEquals(80, objectIds.length);
    }

    @Test
    public void testOverloadedMethod4() throws SnapshotException
    {
        // 80 objects
        // indexOf(int) with an int
        int[] objectIds = (int[])execute("SELECT * FROM \".*\" c WHERE (c.@clazz.@name.indexOf(105) = 5)");
        assertEquals(80, objectIds.length);
    }

    @Test
    public void testPrimitiveArray() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s[2] FROM int[] s");
        assertEquals(5, res.getRowCount());
        checkGetOQL(res);
    }

    @Test
    public void testObjectArray() throws SnapshotException
    {
        int[] objectIds = (int[])execute("SELECT OBJECTS s[2] FROM byte[][] s");
        assertEquals(2, objectIds.length);
    }

    @Test
    public void testJavaArray() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s.@GCRoots[2] FROM OBJECTS ${snapshot} s");
        assertEquals(1, res.getRowCount());
        checkGetOQL(res);
    }

    @Test
    public void testJavaList() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s.@GCRoots.subList(1,3)[1] FROM OBJECTS ${snapshot} s");
        assertEquals(1, res.getRowCount());
        checkGetOQL(res);
    }

    /**
     * Check IObject returned from FROM OBJECTS
     * @throws SnapshotException
     */
    @Test
    public void testDirectIObject() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT v, v.@objectId "
                        + "FROM OBJECTS ${snapshot}.getObject(123) v");
        assertEquals(1, res.getRowCount());
        assertEquals(123, res.getColumnValue(res.getRow(0), 1));
        checkGetOQL(res);
    }

    @Test
    public void testPrimitiveArrayRange() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s[1:3][1] FROM int[] s");
        assertEquals(5, res.getRowCount());
        checkGetOQL(res);
    }

    @Test
    public void testObjectArrayRange() throws SnapshotException
    {
        int[] objectIds = (int[])execute("SELECT OBJECTS s[1:3][1] FROM byte[][] s");
        assertEquals(2, objectIds.length);
    }

    @Test
    public void testJavaArrayRange() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s.@GCRoots[1:3][1] FROM OBJECTS ${snapshot} s");
        assertEquals(1, res.getRowCount());
        checkGetOQL(res);
    }

    @Test
    public void testJavaArrayRange2() throws SnapshotException
    {
        int res[] = (int[])execute("SELECT OBJECTS s.@GCRoots[1:3] FROM OBJECTS ${snapshot} s");
        assertEquals(3, res.length);
    }

    @Test
    public void testJavaArrayRange3() throws SnapshotException
    {
        int res[] = (int[])execute("SELECT OBJECTS s.@GCRoots[-2:-1] FROM OBJECTS ${snapshot} s");
        assertEquals(2, res.length);
    }

    @Test
    public void testJavaListRange() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s.@GCRoots.subList(0,3)[1:3][1] FROM OBJECTS ${snapshot} s");
        assertEquals(1, res.getRowCount());
        checkGetOQL(res);
    }

    @Test
    public void testPrimitiveArrayRange2() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s[1:3][1] FROM int[] s");
        assertEquals(5, res.getRowCount());
        IResultTable res2 = (IResultTable)execute("SELECT s[(1 - s.@length):(3 - s.@length)][1] FROM int[] s");
        assertEquals(5, res.getRowCount());
        assertEquals(res.getColumnValue(res.getRow(2), 0), res2.getColumnValue(res2.getRow(2), 0));
        checkGetOQL(res);
        checkGetOQL(res2);
    }

    @Test
    public void testConcatentation() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT \"ABC\"+s FROM OBJECTS (123) s");
        assertEquals(1, res.getRowCount());
        Object r = res.getRow(0);
        Object val = res.getColumnValue(r, 0);
        assertEquals("ABC123", val);
        checkGetOQL(res);
    }

    @Test
    public void testEval() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT eval(\"ABC\"+s) FROM OBJECTS (123) s");
        assertEquals(1, res.getRowCount());
        Object r = res.getRow(0);
        Object val = res.getColumnValue(r, 0);
        assertEquals("ABC123", val);
        checkGetOQL(res);
    }

    @Test
    public void testOQLunion1() {
        StringBuilder sb = new StringBuilder();
        sb.append("select s from 1,2,3 s1");
        OQL.union(sb, "select s from 17,23 s1");
        assertEquals("select s from 1,2,3,17,23 s1", sb.toString());
    }

    @Test
    public void testOQLunion2() {
        StringBuilder sb = new StringBuilder();
        sb.append("select s from 1,2,3");
        OQL.union(sb, "select s from 17,23");
        assertEquals("select s from 1,2,3,17,23", sb.toString());
    }

    @Test
    public void testOQLunion3() {
        StringBuilder sb = new StringBuilder();
        sb.append("select s from 0");
        for (int i = 1; i < 200000; ++i) {
            sb.append(", ").append(i);
        }
        sb.append(" s");
        OQL.union(sb, "select s from 17,23 s");
        assertTrue(sb.toString(), sb.toString().endsWith("17,23 s"));
    }

    @Test
    public void testOQLunion4() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; ++i) {
            OQL.union(sb, ("select s from "+i+" s"));
        }
        assertEquals(-1, sb.indexOf("UNION"));
        assertTrue(sb.length() < 20000 * 10);
    }

    @Test
    public void testOQLunion5() {
        StringBuilder sb = new StringBuilder();
        sb.append("select s from 1,2,3 s");
        OQL.union(sb, "select s from 17,23 t");
        assertEquals("select s from 1,2,3 s UNION (select s from 17,23 t)", sb.toString());
    }

    @Test
    public void testOQLunion6() {
        StringBuilder sb = new StringBuilder();
        sb.append("select s from 1 s");
        OQL.union(sb, "select s from 2 s");
        OQL.union(sb, "select t from 17 t");
        OQL.union(sb, "select t from 23 t");
        OQL.union(sb, "select s from 3 s");
        assertEquals("select s from 1,2,3 s UNION (select t from 17,23 t)", sb.toString());
    }

    @Test
    public void testOQLunion7() throws SnapshotException {
        String dump = TestSnapshots.SUN_JDK6_18_32BIT;
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT a.t.s.value FROM OBJECTS (SELECT * FROM OBJECTS ( SELECT (SELECT ${snapshot}.getObject(2839) AS s FROM OBJECTS ( null ) ) AS t FROM OBJECTS ( null ) a  ) a )  a");
        OQL.union(sb, "SELECT a.t.s.value FROM OBJECTS (SELECT * FROM OBJECTS ( SELECT (SELECT ${snapshot}.getObject(2896) AS s FROM OBJECTS ( null ) ) AS t FROM OBJECTS ( null ) a  ) a )  a");
        OQL.union(sb, "SELECT a.t.s.value FROM OBJECTS (SELECT * FROM OBJECTS ( SELECT (SELECT ${snapshot}.getObject(2796) AS s FROM OBJECTS ( null ) ) AS t FROM OBJECTS ( null ) a  ) a )  a");
        String s1 = sb.toString();
        assertThat(s1, containsString("2839"));
        assertThat(s1, containsString("2896"));
        assertThat(s1, containsString("2796"));
        IResultTable irt = (IResultTable)execute(s1, dump);
        assertThat(irt.getRowCount(), equalTo(3));
        checkGetOQL(irt, dump);
    }

    /**
     * Complex test to check that the second FROM clause is reevaluated.
     * @throws SnapshotException
     */
    @Test
    public void testComplex1() throws SnapshotException {
        int res[] = (int[])execute("SELECT OBJECTS r from OBJECTS ${snapshot}.@GCRoots r "
                    + " WHERE (SELECT s FROM OBJECTS ${snapshot}.getGCRootInfo(r) s WHERE s.@type = 8) != null");
        assertEquals(23, res.length);
    }

    /**
     * Complex test to check that the second FROM clause inside a UNION is reevaluated.
     * @throws SnapshotException
     */
    @Test
    public void testComplex2() throws SnapshotException {
        int res[] = (int[])execute("SELECT OBJECTS r from OBJECTS ${snapshot}.@GCRoots r "
                    + " WHERE (SELECT s FROM dummy s UNION (SELECT s FROM OBJECTS ${snapshot}.getGCRootInfo(r) s WHERE s.@type = 8)) != null");
        assertEquals(23, res.length);
    }

    /**
     * Complex test to check that the second and third FROM clause
     * is not reevaluated each time - otherwise this will take a long time.
     * @throws SnapshotException
     */
    @Test
    public void testComplex3() throws SnapshotException {
        int res[] = (int[])execute("SELECT OBJECTS r from OBJECTS ${snapshot}.@GCRoots r "
                        + "WHERE (SELECT s FROM INSTANCEOF java.lang.Object s "
                        + "WHERE (SELECT t FROM INSTANCEOF java.lang.String t) != null) != null");
        assertEquals(354, res.length);
    }

    @Test
    public void testComplex4() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT s AS HashMap, eval((SELECT t, t.getKey(), t.getValue() as value "
                        + "FROM OBJECTS ( s[0:-1] ) t "
                        + "WHERE (toString(t.getKey()) = \"META-INF\")))[0].value AS \"Value for META-INF\" "
                        + "FROM java.util.HashMap s "
                        + "WHERE "
                        + "((SELECT t FROM OBJECTS ( s[0:-1] ) t "
                        + "WHERE (toString(t.getKey()) = \"META-INF\")) != null)");
        assertEquals(3, irt.getRowCount());
        // 0 is the whole row, 1 is column 0, 2 is column 1
        assertEquals("Value for META-INF", irt.getResultMetaData().getContextProviders().get(2).getLabel());
        int id = irt.getResultMetaData().getContextProviders().get(1).getContext(irt.getRow(0)).getObjectId();
        assertTrue(id >= 0);
        checkGetOQL(irt);
    }

    @Test
    public void testComplex5() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT v.t, v.get(\"t[0:-1]\")[0].getKey(), v.t[0:-1], v.get(\"t[0:-1]\") "
                        + "FROM OBJECTS ( "
                        + "eval((" // Avoid auto-flattening
                        + "SELECT t, t[0:-1] "
                        + "FROM java.util.HashSet t "
                        + "UNION "
                        + "( SELECT s, s[0:-1] "
                        + "FROM java.util.Hashtable s  ) )"
                        + "))"
                        + " v ");
        assertEquals(11, irt.getRowCount());
        checkGetOQL(irt);
    }

    @Test
    public void testComplex5a() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT v.t, v.get(\"t[0:-1]\")[0].getKey(), v.t[0:-1], v.get(\"t[0:-1]\") "
                        + "FROM OBJECTS ( "
                        + "eval((" // Avoid auto-flattening
                        + "SELECT t, t[0:-1] "
                        + "FROM java.util.HashSet t )"
                        + "))"
                        + " v");
        assertEquals(5, irt.getRowCount());
        checkGetOQL(irt);
    }

    @Test
    public void testComplex5b() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT map, map[0:-1] "
                        + "FROM OBJECTS ( SELECT * FROM java.util.HashSet t  )");
        assertEquals(5, irt.getRowCount());
        checkGetOQL(irt);
    }

    @Test
    public void testComplex5c() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT v.t, v.get(\"t[0:-1]\")[0].getKey(), v.t[0:-1], v.get(\"t[0:-1]\") "
                        + "FROM OBJECTS ( "
                        + "eval((" // Avoid auto-flattening
                        + "SELECT t, t[0:-1] "
                        + "FROM java.util.HashSet t  )"
                        + "))"
                        + " v "
                        + "UNION ("
                        + "SELECT v.t, v.get(\"t[0:-1]\")[0].getKey(), v.t[0:-1], v.get(\"t[0:-1]\") "
                        + "FROM OBJECTS ("
                        + "eval((" // Avoid auto-flattening
                        + "SELECT t, t[0:-1] FROM java.util.Hashtable t  )"
                        + "))"
                        + " v )");
        assertEquals(11, irt.getRowCount());
        checkGetOQL(irt);
    }


    @Test
    public void testComplex5d() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT v, v[0:-1][0].getKey(), v[0:-1] "
                        + "FROM OBJECTS ( "
                        + "SELECT * FROM java.util.HashSet t  ) v  "
                        + "UNION "
                        + "(SELECT v, v[0:-1][0].getKey(), v[0:-1] "
                        + "FROM OBJECTS "
                        + "( SELECT * FROM java.util.Hashtable t  ) v )");
        assertEquals(11, irt.getRowCount());
        checkGetOQL(irt);
    }

    @Test
    public void testComplex6() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT toString(v[0].getKey()) AS Key "
                        + "FROM OBJECTS "
                        + "( SELECT * "
                        + "FROM java.util.HashSet t  "
                        + "UNION "
                        + "( SELECT * "
                        + "FROM java.util.Hashtable s  ) ) v ");
        assertEquals(11, irt.getRowCount());
        assertEquals("META-INF", irt.getColumnValue(irt.getRow(1), 0));
        checkGetOQL(irt);
    }

    @Test
    public void testComplex7() throws SnapshotException {
        Object result = execute("select * from OBJECTS (SELECT * FROM OBJECTS ( SELECT s.table, s.@usedHeapSize, s[0:-1], s.@objectId, s.@usedHeapSize FROM java.util.HashMap s WHERE (s.table != null) ) z)");
        assertNotNull(result);
    }

    /**
     * Check select item is reevaluated as sub-select is context dependent
     * @throws SnapshotException
     */
    @Test
    public void testComplex8() throws SnapshotException {
        int r1[] = (int[])execute("SELECT OBJECTS ( SELECT OBJECTS q.getKey() AS k FROM OBJECTS ( t[0:-1] ) q  ) FROM java.util.HashMap t");
        int r2[] = (int[])execute("SELECT OBJECTS (SELECT * FROM OBJECTS ( SELECT OBJECTS q.getKey() AS k FROM OBJECTS ( t[0:-1] ) q  ) ) FROM java.util.HashMap t");
        assertEquals("Context dependent subselect", r1.length, r2.length);
    }

    @Test
    public void testComplex9() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT m AS map, classof(m).@name AS type, m[0:-1].size() AS size, (SELECT OBJECTS z.getKey() FROM OBJECTS ( m[0:-1] ) z ) AS keys, (SELECT OBJECTS z.getValue() FROM OBJECTS ( m[0:-1] ) z ) AS values FROM INSTANCEOF java.util.AbstractMap m ");
        // 3 columns plus base row have context providers
        assertEquals(4, irt.getResultMetaData().getContextProviders().size());
        checkGetOQL(irt);
    }

    @Test
    public void testComplex10() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT z.map AS Map, z.kv.key AS Key, z.kv.value AS Value FROM OBJECTS ( SELECT h AS map, (SELECT e.getKey() AS key, e.getValue() AS value FROM OBJECTS ${h}[0:-1] e ) AS kv FROM java.util.HashMap h  ) z WHERE (z.kv.key != null)");
        assertThat(irt.getRowCount(), greaterThan(0));
        assertThat(irt.getColumns().length, equalTo(3));
        checkGetOQL(irt);
    }

    @Test
    public void testComplex11() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT 'c', \"String\", 1, 1234567890123456789L, 1.23456, 1.23456F, 1.23456789, 1.23456789D, true, false, null FROM OBJECTS 0");
        assertThat(irt.getRowCount(), equalTo(1));
        assertThat(irt.getColumns().length, equalTo(11));
        Object row = irt.getRow(0);
        assertNotNull(row);
        checkGetOQL(irt);
    }

    @Test
    public void testComplex12() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT 'c' as a, \"String\" as b, 1 as c, 1234567890123456789L as d, 1.23456 as e, 1.23456F as f, 1.23456789 as g, 1.23456789D as h, true as i, false as j, null as k FROM OBJECTS 0");
        assertThat(irt.getRowCount(), equalTo(1));
        assertThat(irt.getColumns().length, equalTo(11));
        Object row = irt.getRow(0);
        assertNotNull(row);
        checkGetOQL(irt);
    }

    @Test
    public void testComplex13() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT z.a,z.b,z.c,z.d,z.e,z.f,z.g,z.h,z.i,z.j,z.k FROM OBJECTS ("
                        + "SELECT 'c' as a, \"String\" as b, 1 as c, 1234567890123456789L as d, 1.23456 as e, 1.23456F as f, 1.23456789 as g, 1.23456789D as h, true as i, false as j, null as k FROM OBJECTS 0"
                        + ") z");
        assertThat(irt.getRowCount(), equalTo(1));
        assertThat(irt.getColumns().length, equalTo(11));
        Object row = irt.getRow(0);
        assertNotNull(row);
        checkGetOQL(irt);
    }

    @Test
    public void testComplex14() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT "
                        + "(SELECT * FROM OBJECTS ('c')) as a, "
                        + "(SELECT * FROM OBJECTS (\"String\")) as b, "
                        + "(SELECT * FROM OBJECTS (1)) as c, "
                        + "(SELECT * FROM OBJECTS (1234567890123456789L)) as d, "
                        + "(SELECT * FROM OBJECTS (1.23456)) as e, "
                        + "(SELECT * FROM OBJECTS (1.23456F)) as f, "
                        + "(SELECT * FROM OBJECTS (1.23456789)) as g, "
                        + "(SELECT * FROM OBJECTS (1.23456789D)) as h, "
                        + "(SELECT * FROM OBJECTS (true)) as i, "
                        + "(SELECT * FROM OBJECTS (false)) as j, "
                        + "(SELECT * FROM OBJECTS (null)) as k"
                        + " FROM OBJECTS 0");
        assertThat(irt.getRowCount(), equalTo(1));
        assertThat(irt.getColumns().length, equalTo(11));
        Object row = irt.getRow(0);
        assertNotNull(row);
        checkGetOQL(irt);
    }

    @Test
    public void testComplex15() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT a.t.s.value, a, outbounds(a.t.s.value) "
                        + "FROM OBJECTS ( "
                        + "SELECT "
                        + "(SELECT s FROM java.lang.String s ) AS t "
                        + "FROM OBJECTS ( \"A\" )  ) a");
        assertThat(irt.getColumns().length, equalTo(3));
        checkGetOQL(irt);
    }

    @Test
    public void testComplex16() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT a.t.s.value, a, outbounds(a.t.s.value) "
                        + "FROM OBJECTS ( "
                        + "SELECT "
                        + "(SELECT s FROM java.lang.String s ) AS t "
                        + "FROM OBJECTS ( \"A\" )  ) a  "
                        + "UNION ("
                        + "SELECT a.t.s.value, a, outbounds(a.t.s.value) "
                        + "FROM OBJECTS ( "
                        + "SELECT "
                        + "(SELECT s FROM java.lang.String s ) AS t "
                        + "FROM OBJECTS ( \"A\" )  ) a )");
        assertThat(irt.getColumns().length, equalTo(3));
        checkGetOQL(irt);
    }

    /**
     *  check a column holding objects and ints 
     */
    @Test
    public void testComplex17() throws SnapshotException {
        IResultTable irt = (IResultTable)execute("SELECT v, v.i.value FROM OBJECTS ( SELECT i FROM java.lang.String i  UNION ( SELECT i FROM java.lang.Integer i  ) ) v");
        assertThat(irt.getColumns().length, equalTo(2));
        checkGetOQL(irt);
    }

    /**
     * Cross Join.
     * Integer and Boolean
     * takes too long with Integer and Long
     * @throws SnapshotException
     */
    @Test
    public void testCrossJoin() throws SnapshotException {
        String dump = TestSnapshots.SUN_JDK6_18_32BIT;
        int ints[] = (int[])execute("SELECT * from java.lang.Integer", dump);
        assertNotNull(ints);
        int bools[] = (int[])execute("SELECT * from java.lang.Boolean", dump);
        assertNotNull(bools);
        IResultTable irt = (IResultTable)execute("SELECT z.i AS Integer, z.i.value AS \"Integer value\", z.lv.l AS Boolean, z.lv.l.value as \"Boolean value\" \r\n" +
                        "FROM OBJECTS ( SELECT i, (SELECT l FROM java.lang.Boolean l ) AS lv FROM java.lang.Integer i  ) z", dump);
        assertThat(irt.getRowCount(), equalTo(ints.length * bools.length));
        for (int i = 0; i < irt.getRowCount(); ++i) {
            Object row = irt.getRow(i);
            Object iv1 = irt.getColumnValue(row, 1);
            assertThat(iv1, instanceOf(Integer.class));
            Object iv2 = irt.getColumnValue(row, 3);
            assertThat(iv2, instanceOf(Boolean.class));
        }
        checkGetOQL(irt, dump);
    }

    @Test
    public void testLeftJoin() throws SnapshotException {
        String dump = TestSnapshots.SUN_JDK6_18_32BIT;
        int ints[] = (int[])execute("SELECT * from java.lang.Integer", dump);
        assertNotNull(ints);
        int longs[] = (int[])execute("SELECT * from java.lang.Long", dump);
        assertNotNull(longs);
        IResultTable irt = (IResultTable)execute("SELECT z.i AS Integer, z.i.value AS \"Integer value\", z.lv.l AS Long, z.lv.l.value as \"Long value\"  \r\n" +
                        "FROM OBJECTS ( SELECT i, (SELECT l FROM java.lang.Long l WHERE (l.value = i.value)) AS lv FROM java.lang.Integer i  ) z", dump);
        assertThat(irt.getRowCount(), greaterThanOrEqualTo(ints.length));
        for (int i = 0; i < irt.getRowCount(); ++i) {
            Object row = irt.getRow(i);
            Object iv1 = irt.getColumnValue(row, 1);
            assertThat(iv1, instanceOf(Integer.class));
            Object iv2 = irt.getColumnValue(row, 3);
            assertThat(iv2, anyOf(nullValue(),instanceOf(Long.class)));
            if (iv2 != null)
                assertThat((long)(Integer)iv1, equalTo(iv2));
        }
        checkGetOQL(irt, dump);
    }

    @Test
    public void testInnerJoin1() throws SnapshotException {
        String dump = TestSnapshots.SUN_JDK6_18_32BIT;
        int ints[] = (int[])execute("SELECT * from java.lang.Integer", dump);
        assertNotNull(ints);
        int longs[] = (int[])execute("SELECT * from java.lang.Long", dump);
        assertNotNull(longs);
        IResultTable irt = (IResultTable)execute("SELECT z.i AS Integer, z.i.value AS \"Integer value\", z.lv.l AS Long, z.lv.l.value as \"Long value\" \r\n" +
                        "FROM OBJECTS ( SELECT i, (SELECT l FROM java.lang.Long l WHERE (l.value = i.value)) AS lv FROM java.lang.Integer i  ) z WHERE (z.lv != null)", dump);
        assertThat(irt.getRowCount(), lessThanOrEqualTo(ints.length));
        assertThat(irt.getRowCount(), lessThanOrEqualTo(longs.length));
        for (int i = 0; i < irt.getRowCount(); ++i) {
            Object row = irt.getRow(i);
            Object iv1 = irt.getColumnValue(row, 1);
            assertThat(iv1, instanceOf(Integer.class));
            Object iv2 = irt.getColumnValue(row, 3);
            assertThat(iv2, instanceOf(Long.class));
            assertThat((long)(Integer)iv1, equalTo(iv2));
        }
        checkGetOQL(irt, dump);
    }

    @Test
    public void testInnerJoin2() throws SnapshotException {
        String dump = TestSnapshots.SUN_JDK6_18_32BIT;
        int ints[] = (int[])execute("SELECT * from java.lang.Integer", dump);
        assertNotNull(ints);
        int longs[] = (int[])execute("SELECT * from java.lang.Long", dump);
        assertNotNull(longs);
        IResultTable irt = (IResultTable)execute("SELECT z.iv.i AS Integer, z.iv.i.value AS \"Integer value\", z.l AS Long, z.l.value as \"Long value\" \r\n" +
                        "FROM OBJECTS ( SELECT (SELECT i FROM java.lang.Integer i WHERE (i.value = l.value)) AS iv, l FROM java.lang.Long l  ) z WHERE (z.iv != null)", dump);
        assertThat(irt.getRowCount(), lessThanOrEqualTo(ints.length));
        assertThat(irt.getRowCount(), lessThanOrEqualTo(longs.length));
        for (int i = 0; i < irt.getRowCount(); ++i) {
            Object row = irt.getRow(i);
            Object iv1 = irt.getColumnValue(row, 1);
            assertThat(iv1, instanceOf(Integer.class));
            Object iv2 = irt.getColumnValue(row, 3);
            assertThat(iv2, instanceOf(Long.class));
            assertThat((long)(Integer)iv1, equalTo(iv2));
        }
        checkGetOQL(irt, dump);
    }

    @Test
    public void testRightJoin() throws SnapshotException {
        String dump = TestSnapshots.SUN_JDK6_18_32BIT;
        int ints[] = (int[])execute("SELECT * from java.lang.Integer", dump);
        assertNotNull(ints);
        int longs[] = (int[])execute("SELECT * from java.lang.Long", dump);
        assertNotNull(longs);
        IResultTable irt = (IResultTable)execute("SELECT z.iv.i AS Integer, z.iv.i.value AS \"Integer value\", z.l AS Long, z.l.value as \"Long value\" \r\n" +
                        "FROM OBJECTS ( SELECT (SELECT i FROM java.lang.Integer i WHERE (i.value = l.value)) AS iv, l FROM java.lang.Long l  ) z", dump);
        assertThat(irt.getRowCount(), greaterThanOrEqualTo(longs.length));
        for (int i = 0; i < irt.getRowCount(); ++i) {
            Object row = irt.getRow(i);
            Object iv1 = irt.getColumnValue(row, 1);
            assertThat(iv1, anyOf(nullValue(),instanceOf(Integer.class)));
            Object iv2 = irt.getColumnValue(row, 3);
            assertThat(iv2, instanceOf(Long.class));
            if (iv1 != null)
                assertThat((long)(Integer)iv1, equalTo(iv2));
        }
        checkGetOQL(irt, dump);
    }

    @Test
    public void testFullOuterJoin() throws SnapshotException {
        String dump = TestSnapshots.SUN_JDK6_18_32BIT;
        int ints[] = (int[])execute("SELECT * from java.lang.Integer", dump);
        assertNotNull(ints);
        int longs[] = (int[])execute("SELECT * from java.lang.Long", dump);
        assertNotNull(longs);
        IResultTable irt = (IResultTable)execute("SELECT z.i AS Integer, z.i.value AS \"Integer value\", z.lv.l AS Long, z.lv.l.value as \"Long value\" \r\n" +
                        "FROM OBJECTS ( SELECT i, (SELECT l FROM java.lang.Long l WHERE (l.value = i.value)) AS lv FROM java.lang.Integer i  ) z \r\n" +
                        "UNION (\r\n" +
                        "SELECT z.iv.i AS Integer, z.iv.i.value AS \"Integer value\", z.l AS Long, z.l.value as \"Long value\" \r\n" +
                        "FROM OBJECTS ( SELECT (SELECT i FROM java.lang.Integer i WHERE (i.value = l.value)) AS iv, l FROM java.lang.Long l  ) z WHERE (z.iv = null)\r\n" +
                        ")", dump);
        assertThat(irt.getRowCount(), greaterThanOrEqualTo(ints.length));
        assertThat(irt.getRowCount(), greaterThanOrEqualTo(longs.length));
        for (int i = 0; i < irt.getRowCount(); ++i) {
            Object row = irt.getRow(i);
            Object iv1 = irt.getColumnValue(row, 1);
            assertThat(iv1, anyOf(nullValue(),instanceOf(Integer.class)));
            Object iv2 = irt.getColumnValue(row, 3);
            assertThat(iv2, anyOf(nullValue(),instanceOf(Long.class)));
            if (iv1 != null && iv2 != null)
                assertThat((long)(Integer)iv1, equalTo(iv2));
        }
        checkGetOQL(irt, dump);
    }

    /**
     * Check all getOQL() from contexts from the result are sensible.
     * @param rt ResultTable
     * @throws SnapshotException
     */
    void checkGetOQL(IResultTable rt) throws SnapshotException
    {
        checkGetOQL(rt, TestSnapshots.SUN_JDK5_64BIT);
    }

    /**
     * Check all getOQL() from contexts from the result are sensible.
     * @param rt ResultTable
     * @param dump the snapshot to test against
     * @throws SnapshotException
     */
    void checkGetOQL(IResultTable rt, String dump) throws SnapshotException
    {
        // Check the default contextx
        for (int i = 0; i < rt.getRowCount(); ++i)
        {
            IContextObject c = rt.getContext(rt.getRow(i));
            checkDefaultContext(rt, i, c, dump);
            for (ContextProvider p : rt.getResultMetaData().getContextProviders())
            {
                if (p.getLabel().startsWith("SELECT ... ")) {
                    // Default provider as metadata provider
                    c = p.getContext(rt.getRow(i));
                    checkDefaultContext(rt, i, c, dump);
                }
            }
        }
        // check the other providers
        for (ContextProvider p : rt.getResultMetaData().getContextProviders())
        {
            String l = p.getLabel();
            for (int j = 0; j < rt.getColumns().length; ++j) {
                if (l.equals(rt.getColumns()[j].getLabel()))
                {
                    // Now check each row
                    for (int i = 0; i < rt.getRowCount(); ++i)
                    {
                        Object o = rt.getColumnValue(rt.getRow(i), j);
                        if (o instanceof IObject)
                        {
                            // SimpleObject
                            IObject io = (IObject)o;
                            IContextObject c = p.getContext(rt.getRow(i));
                            checkSingleObjectContext(io.getObjectId(), c, dump);
                        }
                        else if (o instanceof Iterable && ((Iterable<?>)o).iterator().hasNext() && ((Iterable<?>)o).iterator().next() instanceof IObject || o instanceof int[])
                        {
                            // Iterable, look for objects
                            List<IObject> os = new ArrayList<IObject>();
                            ArrayInt ai = new ArrayInt();
                            if (o instanceof Iterable<?>)
                            {
                                for (Object o1 : (Iterable<?>)o)
                                {
                                    if (o1 instanceof IObject)
                                    {
                                        ai.add(((IObject)o1).getObjectId());
                                        os.add((IObject)o1);
                                    }
                                }
                            }
                            else
                            {
                                int oa[] = (int[])o;
                                for (int o1 : oa)
                                {
                                    ai.add(o1);
                                }
                            }
                            IContextObject c = p.getContext(rt.getRow(i));
                            assertThat("row="+i+" col="+j, c, instanceOf(IContextObjectSet.class));
                            IContextObjectSet cs = (IContextObjectSet)c;
                            int []csobjs = cs.getObjectIds();
                            assertThat("row="+i+" col="+j, csobjs, equalTo(ai.toArray()));
                            String oql = cs.getOQL();
                            Object res = execute(oql, dump);
                            if (res instanceof int[])
                            {
                                int r[] = (int[])res;
                                assertEquals("row="+i+" col="+j, ai.size(), r.length);
                                for (IteratorInt it = ai.iterator(); it.hasNext(); )
                                {
                                    int o1 = it.next();
                                    boolean found = false;
                                    for (int ri : r)
                                    {
                                        if (ri == o1)
                                            found = true;
                                    }
                                    assertTrue("row="+i+" col="+j, found);
                                }
                                for (int ri : r)
                                {
                                    boolean found = false;
                                    for (IteratorInt it = ai.iterator(); it.hasNext(); )
                                    {
                                        int o1 = it.next();
                                        if (ri == o1)
                                            found = true;
                                    }
                                    assertTrue("row="+i+" col="+j, found);
                                }
                            }
                            else if (res instanceof IResultTable)
                            {
                                IResultTable rt2 = (IResultTable)res;
                                assertEquals("row="+i+" col="+j, 1, rt2.getRowCount());
                                assertEquals("row="+i+" col="+j, 1, rt2.getColumns().length);
                                List<IObject>os2 = new ArrayList<IObject>();
                                ArrayInt ai2 = new ArrayInt();
                                for (int r = 0; r < rt2.getRowCount(); ++r)
                                {
                                    Object o2 = rt2.getColumnValue(rt2.getRow(r), 0);
                                    if (o2 instanceof int[])
                                    {
                                        ai2.addAll((int[])o2);
                                    }
                                    else
                                    {
                                        assertThat(o2, instanceOf(Iterable.class));
                                        for (Object o3 : (Iterable<?>)o2)
                                        {
                                            assertThat(o3, instanceOf(IObject.class));
                                            IObject io2 = (IObject)o3;
                                            assertTrue("row="+i+" col="+j, os.contains(io2));
                                            os2.add(io2);
                                            ai2.add(io2.getObjectId());
                                        }
                                    }
                                }
                                if (ai2.isEmpty())
                                {
                                    for (IObject ox : os)
                                    {
                                        assertTrue(os2.contains(ox));
                                    }
                                    for (IObject ox : os2)
                                    {
                                        assertTrue(os.contains(ox));
                                    }
                                }
                                else
                                {
                                    assertThat(ai.toArray(), equalTo(ai2.toArray()));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkDefaultContext(IResultTable rt, int row, IContextObject c, String dump) throws SnapshotException
    {
        if (c instanceof IContextObjectSet)
        {
            IContextObjectSet cs = (IContextObjectSet)c;
            if (cs.getObjectIds().length == 1)
                checkSingleObjectContext(rt, row, cs.getObjectIds()[0], c, dump);
            else {
                int os[] = cs.getObjectIds();
                String oql = cs.getOQL();
                Object res = execute(oql, dump);
                if (res instanceof int[])
                {
                    int r[] = (int[])res;
                    assertEquals(os.length, r.length);
                    for (int i = 0; i < os.length; ++i)
                    {
                        assertEquals("index="+i, os[i], r[i]);
                    }
                }
                else if (res instanceof IResultTable)
                {
                    IResultTable rt2 = (IResultTable)res;
                    assertEquals("row="+row+" oql="+oql, 1, rt2.getRowCount());
                    assertEquals("row="+row+" oql="+oql, rt.getColumns().length, rt2.getColumns().length);
                    for (int j = 0; j < rt.getColumns().length; ++j)
                    {
                        Object o1 = rt.getColumnValue(rt.getRow(row), j);
                        Object o2 = rt2.getColumnValue(rt2.getRow(0), j);
                        assertThat("row="+row+" col="+j+" oql="+oql, o1, equalTo(o2));
                    }
                    IContextObject ic = rt2.getContext(rt2.getRow(0));
                    assertThat("row="+row+" oql="+oql, ic, instanceOf(IContextObjectSet.class));
                    IContextObjectSet ics = (IContextObjectSet)ic;
                    assertEquals("row="+row, oql, ics.getOQL());
                    assertEquals("row="+row+" oql="+oql ,cs.getObjectId(), ics.getObjectId());
                    assertEquals("row="+row+" oql="+oql, os.length, ics.getObjectIds().length);
                    for (int ix = 0; ix < os.length; ++ix)
                    {
                        assertEquals("row="+row+" oql="+oql, os[ix], ics.getObjectIds()[ix]);
                    }
                }
            }
        } else {
            // Not everything has a backing object
            assertEquals(null, c);
        }
    }

    private void checkSingleObjectContext(int ioid, IContextObject c, String dump) throws SnapshotException
    {
        assertThat(c, instanceOf(IContextObjectSet.class));
        IContextObjectSet cs = (IContextObjectSet)c;
        assertEquals(ioid, cs.getObjectId());
        assertEquals(1, cs.getObjectIds().length);
        assertEquals(ioid, cs.getObjectIds()[0]);
        String oql = cs.getOQL();
        Object res = execute(oql, dump);
        if (res instanceof int[])
        {
            int r[] = (int[])res;
            assertEquals(1, r.length);
            assertEquals(ioid, r[0]);
        }
        else if (res instanceof IResultTable)
        {
            IResultTable rt2 = (IResultTable)res;
            assertEquals(1, rt2.getRowCount());
            assertEquals(1, rt2.getColumns().length);
            Object o2 = rt2.getColumnValue(rt2.getRow(0), 0);
            assertThat(o2, instanceOf(IObject.class));
            IObject io2 = (IObject)o2;
            assertEquals(ioid, io2.getObjectId());
        }
    }

    private void checkSingleObjectContext(IResultTable rt, int row, int ioid, IContextObject c, String dump) throws SnapshotException
    {
        assertThat(c, instanceOf(IContextObjectSet.class));
        IContextObjectSet cs = (IContextObjectSet)c;
        String oql = cs.getOQL();
        Object res = execute(oql, dump);
        if (res instanceof int[])
        {
            int r[] = (int[])res;
            assertEquals(oql, 1, r.length);
            assertEquals(oql, ioid, r[0]);
        }
        else if (res instanceof IResultTable)
        {
            IResultTable rt2 = (IResultTable)res;
            assertEquals(oql, 1, rt2.getRowCount());
            assertEquals(oql, rt.getColumns().length, rt2.getColumns().length);
            for (int j = 0; j < rt.getColumns().length; ++j)
            {
                Object o1 = rt.getColumnValue(rt.getRow(row), j);
                Object o2 = rt2.getColumnValue(rt2.getRow(0), j);
                if (o1 instanceof int[] && o2 instanceof int[])
                    assertArrayEquals("Row="+row+" col="+j, (int[])o1, (int[])o2);
                else
                    assertEquals("Row="+row+" col="+j, o1, o2);
            }
        }
    }

    /**
     * Test that null objects from a from clause don't cause problems
     */
    @Test
    public void testFromClauseWithNullObjects() throws SnapshotException {
        int res[] = (int[])execute("SELECT * FROM java.lang.ThreadGroup s"
                    + " WHERE ((SELECT t FROM OBJECTS ( s.threads[0:-1] ) t WHERE t.name.toString().startsWith(\"R\")) != null)");
        assertEquals(1, res.length);
    }

    /**
     * Test reading static fields of a class
     * @throws SnapshotException
     */
    @Test
    public void testStatic() throws SnapshotException
    {
        Object result = execute("select MAX_VALUE from OBJECTS java.lang.Integer");
        IResultTable table = (IResultTable) result;
        assertThat("1 row expected", table.getRowCount(), equalTo(1));
        Object row = table.getRow(0);
        int val = (Integer) table.getColumnValue(row, 0);
        assertThat("Integer.MAX_VALUE", val, equalTo(Integer.MAX_VALUE));
        checkGetOQL(table);
    }

    /**
     * Test extracting objects from a collection
     * @throws SnapshotException
     */
    @Test
    public void testArrayList() throws SnapshotException
    {
        int objs[] = (int[])execute("select objects s[0:-1] from java.util.ArrayList s");
        assertThat("Multiple objects expected", objs.length, greaterThanOrEqualTo(2));
    }

    /**
     * Test extracting objects from a collection.
     * Check we can read the last element from a collection.
     * @throws SnapshotException
     */
    @Test
    public void testArrayListGet() throws SnapshotException
    {
        int objs[] = (int[])execute("select objects s[0:-1][-1:-1][0] from java.util.ArrayList s");
        assertThat("Multiple objects expected", objs.length, greaterThanOrEqualTo(2));
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * Test method calls disallowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallClassLoader() throws SnapshotException
    {
        expectedException.expectCause(isA(java.security.AccessControlException.class));
        Object o = execute("SELECT s.@class.forName(\"java.lang.ClassLoader\").getSystemClassLoader() FROM OBJECTS 1 s");
        assertNull(o);
    }

    /**
     * Test method calls disallowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallCompiler() throws SnapshotException
    {
        expectedException.expectCause(isA(java.security.AccessControlException.class));
        Object o = execute("SELECT s.@class.forName(\"java.lang.Compiler\").disable() FROM OBJECTS 1 s");
        assertNull(o);
    }

    /**
     * Test method calls disallowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallProcess() throws SnapshotException
    {
        expectedException.expectCause(isA(java.security.AccessControlException.class));
        Object o = execute("SELECT s.@class.forName(\"java.lang.ProcessBuilder\").getConstructor(s.@class.forName(\"[Ljava.lang.String;\")).newInstance(\"calc\") FROM OBJECTS 1 s");
        assertNull(o);
    }

    /**
     * Test method calls disallowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallRuntime() throws SnapshotException
    {
        expectedException.expectCause(isA(java.security.AccessControlException.class));
        Object o = execute("SELECT s.@class.forName(\"java.lang.Runtime\").getRuntime() FROM OBJECTS 1 s");
        assertNull(o);
    }

    /**
     * Test method calls disallowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallSecurityManager() throws SnapshotException
    {
        expectedException.expectCause(isA(java.security.AccessControlException.class));
        Object o = execute("SELECT s.@class.forName(\"java.lang.SecurityManager\").newInstance().checkExec(\"calc\") FROM OBJECTS 1 s");
        assertNull(o);
    }

    /**
     * Test method calls disallowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallSystem() throws SnapshotException
    {
        expectedException.expectCause(isA(java.security.AccessControlException.class));
        Object o = execute("SELECT s.@class.forName(\"java.lang.System\").currentTimeMillis() FROM OBJECTS 1 s");
        assertNull(o);
    }

    /**
     * Test method calls disallowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallThread() throws SnapshotException
    {
        expectedException.expectCause(isA(java.security.AccessControlException.class));
        Object o = execute("SELECT s.@class.forName(\"java.lang.Thread\").activeCount() FROM OBJECTS 1 s");
        assertNull(o);
    }

    /**
     * Test method calls allowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallInteger() throws SnapshotException
    {
        Object result = execute("SELECT eval(123).intValue() FROM OBJECTS 1 s");
        assertThat(result, instanceOf(IResultTable.class));
        IResultTable table = (IResultTable) result;
        Object row = table.getRow(0);
        assertThat(table.getColumnValue(row, 0), equalTo((Object)123));
    }

    /**
     * Test method calls allowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallVarArgs1() throws SnapshotException
    {
        Object result = execute("SELECT s.@class.forName(\"java.util.Arrays\").asList(1) FROM OBJECTS 1 s");
        assertThat(result, instanceOf(IResultTable.class));
        IResultTable table = (IResultTable) result;
        Object row = table.getRow(0);
        Object item = table.getColumnValue(row, 0);
        assertThat(item, instanceOf(List.class));
        List<?> al = (List<?>)item;
        assertThat(al.size(), equalTo(1));
    }

    /**
     * Test method calls allowed.
     * @throws SnapshotException
     */
    @Test
    public void testMethodCallVarArgs5() throws SnapshotException
    {
        Object result = execute("SELECT s.@class.forName(\"java.util.Arrays\").asList(1,2,3,4,5) FROM OBJECTS 1 s");
        assertThat(result, instanceOf(IResultTable.class));
        IResultTable table = (IResultTable) result;
        Object row = table.getRow(0);
        Object item = table.getColumnValue(row, 0);
        assertThat(item, instanceOf(List.class));
        List<?> al = (List<?>)item;
        assertThat(al.size(), equalTo(5));
    }

    // //////////////////////////////////////////////////////////////
    // internal helpers
    // //////////////////////////////////////////////////////////////

    /**
     * Use to check that the work reports from OQL processing are sensible.
     */
    static class CountListener extends VoidProgressListener
    {
        int work;
        int done;
        public void beginTask(String name, int totalWork)
        {
            work = totalWork;
        }

        public void done()
        {
            if (done < work)
                done = work;
        }

        public void worked(int work)
        {
            if (done + work > this.work)
                throw new IllegalArgumentException("Too much work "+work+" for a listener "+done+"/"+this.work);
            done += work;
        }
    }

    private Object execute(String oql) throws SnapshotException
    {
        return execute(oql, TestSnapshots.SUN_JDK5_64BIT);
    }

    private Object execute(String oql, String snapshotName) throws SnapshotException
    {
        try
        {
            ISnapshot snapshot = TestSnapshots.getSnapshot(snapshotName, false);
            IOQLQuery query = SnapshotFactory.createQuery(oql);
            CountListener cl = new CountListener();
            Object ret = query.execute(snapshot, cl);
            assertThat(oql, cl.done, equalTo(cl.work));
            return ret;
        }
        catch (OQLParseException e)
        {
            throw new SnapshotException("Error while parsing: " + oql, e);
        }
    }
}

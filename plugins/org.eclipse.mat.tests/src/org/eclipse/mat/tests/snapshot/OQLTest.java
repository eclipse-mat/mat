/*******************************************************************************
 * Copyright (c) 2008, 2017 SAP AG and IBM Corporation.
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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

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
        assert objectIds.length == 21 : "expected 21 instances of java.lang.ref.Reference";

        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK5_64BIT, false);

        Collection<IClass>rClasses = snapshot.getClassesByName("java.lang.ref.Reference", true);
        assertNotNull(rClasses);
        Set<IClass> classes = new HashSet<IClass>(rClasses);
        for (int id : objectIds)
            assert classes.contains(snapshot.getClassOf(id)) : MessageUtil.format(
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

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE toString(s) NOT LIKE \"java.*\"");
        assert objectIds.length == 492 - 27;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.value IN dominators(s)");
        assert objectIds.length == 492 - 27;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.value NOT IN dominators(s)");
        assert objectIds.length == 27;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE toString(s) = \"file.separator\"");
        assert objectIds.length == 1;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 100 AND s.@retainedHeapSize > s.@usedHeapSize");
        assert objectIds.length == 6;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count > 1000 OR s.value.@length > 1000");
        assert objectIds.length == 3;
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
        assert objectIds.length == 16;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length / 2");
        assert objectIds.length == 16;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count < s.value.@length - 20");
        assert objectIds.length == 16;

        objectIds = (int[]) execute("SELECT * FROM java.lang.String s WHERE s.count + 20 < s.value.@length");
        assert objectIds.length == 16;
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
        execute(s);
    }

    /**
     * Test AND clause is evaluated.
     * @throws SnapshotException
     */
    @Test
    public void testAndClause() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s.value FROM INSTANCEOF java.lang.Number s WHERE (s.value and true)");
        assert 3 ==  res.getRowCount() : "3 non-zero Numbers expected";
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
    }

    @Test
    public void testJavaList() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s.@GCRoots.subList(1,3)[1] FROM OBJECTS ${snapshot} s");
        assertEquals(1, res.getRowCount());
    }

    @Test
    public void testPrimitiveArrayRange() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s[1:3][1] FROM int[] s");
        assertEquals(5, res.getRowCount());
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
    }

    @Test
    public void testPrimitiveArrayRange2() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT s[1:3][1] FROM int[] s");
        assertEquals(5, res.getRowCount());
        IResultTable res2 = (IResultTable)execute("SELECT s[(1 - s.@length):(3 - s.@length)][1] FROM int[] s");
        assertEquals(5, res.getRowCount());
        assertEquals(res.getColumnValue(res.getRow(2), 0), res2.getColumnValue(res2.getRow(2), 0));
    }

    @Test
    public void testConcatentation() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT \"ABC\"+s FROM OBJECTS (123) s");
        assertEquals(1, res.getRowCount());
        Object r = res.getRow(0);
        Object val = res.getColumnValue(r, 0);
        assertEquals("ABC123", val);
    }

    @Test
    public void testEval() throws SnapshotException
    {
        IResultTable res = (IResultTable)execute("SELECT eval(\"ABC\"+s) FROM OBJECTS (123) s");
        assertEquals(1, res.getRowCount());
        Object r = res.getRow(0);
        Object val = res.getColumnValue(r, 0);
        assertEquals("ABC123", val);
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

    /**
     * Complex test to check that the second FROM clause is reevaluated.
     * @throws SnapshotException
     */
    @Test
    public void testComplex1() throws SnapshotException {
        int res[] = (int[])execute("SELECT OBJECTS r from OBJECTS ${snapshot}.@GCRoots r "
                    + " WHERE (SELECT s FROM OBJECTS ${snapshot}.getGCRootInfo(r) s WHERE s.@type = 128) != null");
        assertEquals(23, res.length);
    }

    /**
     * Complex test to check that the second FROM clause inside a UNION is reevaluated.
     * @throws SnapshotException
     */
    @Test
    public void testComplex2() throws SnapshotException {
        int res[] = (int[])execute("SELECT OBJECTS r from OBJECTS ${snapshot}.@GCRoots r "
                    + " WHERE (SELECT s FROM dummy s UNION (SELECT s FROM OBJECTS ${snapshot}.getGCRootInfo(r) s WHERE s.@type = 128)) != null");
        assertEquals(23, res.length);
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

/*******************************************************************************
 * Copyright (c) 2012,2019 Filippo Pacifici and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson (IBM Corporation) - test bug fix
 *******************************************************************************/
package org.eclipse.mat.tests.ui.snapshot.panes.textPartitioning;

import static org.junit.Assert.assertEquals;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.OQLScanner;
import org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning.OQLPartitionScanner;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that partition works for complex queries
 * 
 * @author Filippo Pacifici
 */
public class TestOQLPartitionScanner
{

    IDocumentPartitioner partitioner = null;
    IDocument doc = new Document();

    @Before
    public void setup() throws Exception
    {

        OQLPartitionScanner scanner = new OQLPartitionScanner();

        partitioner = new FastPartitioner(
                                        scanner,
                                        new String[] {
                                                        OQLPartitionScanner.SELECT_CLAUSE,
                                                        OQLPartitionScanner.FROM_CLAUSE,
                                                        OQLPartitionScanner.WHERE_CLAUSE,
                                                        OQLPartitionScanner.UNION_CLAUSE,
                                                        OQLPartitionScanner.COMMENT_CLAUSE});
        partitioner.connect(doc);
        doc.setDocumentPartitioner(partitioner);

    }

    /**
     * Tests the number of partitions of a simple query
     * 
     * @throws Exception
     */
    @Test
    public void testBaseQueryPartitions() throws Exception
    {
        doc.set("SELECT a FROM java.util.List WHERE a.length == null");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(3, regions.length);
        assertEquals(OQLPartitionScanner.SELECT_CLAUSE, regions[0].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[1].getType());
        assertEquals(OQLPartitionScanner.WHERE_CLAUSE, regions[2].getType());

        ITypedRegion t = regions[1];
        String from = doc.get(t.getOffset(), t.getLength());
        assertEquals("FROM java.util.List ", from);
    }

    /**
     * Tests the number of FROM clause partitions in a complex query
     * 
     * @throws Exception
     */
    @Test
    public void testComplexQueryPartitions() throws Exception
    {
        doc.set("select toString(s) from objects 0x17c180b8 s union (select toHex(s.@objectAddress) from objects 1 s)");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(5, regions.length);
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[1].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[4].getType());

        ITypedRegion t = regions[1];
        String from = doc.get(t.getOffset(), t.getLength());
        assertEquals("from objects 0x17c180b8 s ", from);

        ITypedRegion t2 = regions[4];
        String from2 = doc.get(t2.getOffset(), t2.getLength());
        assertEquals("from objects 1 s)", from2);
    }

    /**
     * Ensures that the ending incomplete partition is correctly recognized
     */
    @Test
    public void testIncompletePartitions()
    {
        doc.set("SELECT t FROM java. ");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(2, regions.length);
        assertEquals(OQLPartitionScanner.SELECT_CLAUSE, regions[0].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[1].getType());

    }

    /**
     * Ensures that comments are correctly recognized
     */
    @Test
    public void testCommentPartitions()
    {
        doc.set("SELECT t /* comment */ FROM java. ");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(4, regions.length);
        assertEquals(OQLPartitionScanner.SELECT_CLAUSE, regions[0].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[3].getType());
        assertEquals(OQLPartitionScanner.COMMENT_CLAUSE, regions[1].getType());

    }

    /**
     * Checks that CR character does not break partitions
     */
    @Test
    public void testCRInPartitions()
    {
        doc.set("SELECT\nt\nFROM\njava.");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(2, regions.length);
        assertEquals(OQLPartitionScanner.SELECT_CLAUSE, regions[0].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[1].getType());

    }

    /**
     * Checks complex partitions and where clauses is not prematurely terminated
     * and that end of line does not stop highlighting.
     */
    @Test
    public void testComplexPartitions1()
    {
        doc.set("select * from java.lang.String where\n"
                + "s.count > 0 and s.count > 0 and s.count > 0 and\n"
                + "s.count > 0 and s.count > 0 and s.count > 0 and\n"
                + "s.count > 0 and s.count > 0 and s.count > 0");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(3, regions.length);
        assertEquals(OQLPartitionScanner.SELECT_CLAUSE, regions[0].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[1].getType());
        assertEquals(OQLPartitionScanner.WHERE_CLAUSE, regions[2].getType());
        assertEquals(regions[2].getOffset(), doc.get().indexOf("where"));
        assertEquals(regions[2].getOffset() + regions[2].getLength(), doc.get().length());

        OQLScanner sc = new OQLScanner(null);
        int ands = 0;
        sc.setRange(doc, regions[2].getOffset(), regions[2].getLength());
        while (sc.nextToken() != Token.EOF)
        {
            if (doc.get().substring(sc.getTokenOffset(), sc.getTokenOffset() + sc.getTokenLength()).equals("and"))
                ++ands;
        }
        assertEquals(8, ands);
    }

    /**
     * Checks complex partitions and where clause is not prematurely terminated
     * by a hidden 'SELECT'.
     */
    @Test
    public void testComplexPartitions2()
    {
        doc.set("select * from java.lang.String s where \n" +
                "s implements org.eclipse.mat.snapshot.model.IObject and s implements org.eclipse.mat.snapshot.model.IObject");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(3, regions.length);
        assertEquals(OQLPartitionScanner.SELECT_CLAUSE, regions[0].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[1].getType());
        assertEquals(OQLPartitionScanner.WHERE_CLAUSE, regions[2].getType());
        assertEquals(regions[2].getOffset(), doc.get().indexOf("where"));
        assertEquals(regions[2].getOffset() + regions[2].getLength(), doc.get().length());
    }

    /**
     * Checks complex partitions and where clauses is not prematurely terminated
     * and that end of line does not stop highlighting.
     */
    @Test
    public void testComplexPartitions3()
    {
        doc.set("select s,s.set from java.lang.String s where s.set != null or s.value != null");

        ITypedRegion[] regions = partitioner.computePartitioning(0, doc.getLength());

        assertEquals(3, regions.length);
        assertEquals(OQLPartitionScanner.SELECT_CLAUSE, regions[0].getType());
        assertEquals(OQLPartitionScanner.FROM_CLAUSE, regions[1].getType());
        assertEquals(OQLPartitionScanner.WHERE_CLAUSE, regions[2].getType());
        assertEquals(regions[2].getOffset(), doc.get().indexOf("where"));
        assertEquals(regions[2].getOffset() + regions[2].getLength(), doc.get().length());

        OQLScanner sc = new OQLScanner(null);
        int sets = 0;
        sc.setRange(doc, regions[2].getOffset(), regions[2].getLength());
        while (sc.nextToken() != Token.EOF)
        {
            String token = doc.get().substring(sc.getTokenOffset(), sc.getTokenOffset() + sc.getTokenLength());
            //System.out.println(token);
            if (token.equals("set"))
                ++sets;
        }
        // The set in the where clause should not be highlighted
        assertEquals(0, sets);
    }
}

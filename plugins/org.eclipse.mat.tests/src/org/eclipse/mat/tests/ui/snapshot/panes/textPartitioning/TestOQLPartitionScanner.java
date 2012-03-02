/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.ui.snapshot.panes.textPartitioning;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning.OQLPartitionScanner;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

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
}

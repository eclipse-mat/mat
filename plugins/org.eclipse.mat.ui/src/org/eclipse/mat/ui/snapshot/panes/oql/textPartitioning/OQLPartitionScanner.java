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
package org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning;

import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;

/**
 * Defines rules for identifying partitions in an OQL queries. Defined
 * partitions are: SELECT, FROM, WHERE, UNION clauses.
 * 
 * @author Filippo Pacifici
 */
public class OQLPartitionScanner extends RuleBasedPartitionScanner
{

    public static final String SELECT_CLAUSE = "__oql_select"; //$NON-NLS-1$
    public static final String FROM_CLAUSE = "__oql_from"; //$NON-NLS-1$
    public static final String WHERE_CLAUSE = "__oql_where"; //$NON-NLS-1$
    public static final String UNION_CLAUSE = "__oql_union"; //$NON-NLS-1$
    public static final String COMMENT_CLAUSE = "__oql_comment"; //$NON-NLS-1$

    /**
	 * Defines the rules for splitting query into.
	 * 
	 *  Each rule starts with one of the reserved word and terminates with any of the 
	 *  others (due t onested queries).
	 *  EOF is a valid terminator for the partition
     */
    public OQLPartitionScanner()
    {
        IToken selectT = new Token(SELECT_CLAUSE);
        IToken fromT = new Token(FROM_CLAUSE);
        IToken whereT = new Token(WHERE_CLAUSE);
        IToken unionT = new Token(UNION_CLAUSE);
        IToken commentT = new Token(COMMENT_CLAUSE);

        IPredicateRule[] rules = new IPredicateRule[6];

        rules[0] = new MultilineNonConsumingRule("SELECT", new String[] { "FROM", "/*", "//" }, selectT); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        rules[1] = new MultilineNonConsumingRule("FROM", new String[] { "UNION", "WHERE", "SELECT", "/*", "//" }, fromT); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

        rules[2] = new MultilineNonConsumingRule("WHERE", new String[] { "UNION", "SELECT", "/*", "//" }, whereT); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

        rules[3] = new MultilineNonConsumingRule("UNION", new String[] { "SELECT", "/*", "//" }, unionT); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        rules[4] = new MultiLineRule("/*", "*/", commentT); //$NON-NLS-1$ //$NON-NLS-2$

        rules[5] = new SingleLineRule("//", "\n", commentT); //$NON-NLS-1$ //$NON-NLS-2$

        setPredicateRules(rules);
    }
}

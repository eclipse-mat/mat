/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson - additional highlighting
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.graphics.Color;

/**
 * Provides color tokens for syntax highlighting.
 * 
 * @author Filippo Pacifici
 */
public class OQLScanner extends RuleBasedScanner
{

    /**
     * Assigns keyword coloring rule to the ruleset
     */
    public OQLScanner(Color color)
    {
        IToken tKeyWord = new Token(new TextAttribute(color));

        IRule[] r = new IRule[4];
        WordRule wr = new WordRule(new IWordDetector()
        {

            public boolean isWordPart(char arg0)
            {
                return arg0 != ' ' && arg0 != '\n' && arg0 != '\r';
            }

            public boolean isWordStart(char arg0)
            {
                return arg0 != ' ';
            }
        }, Token.UNDEFINED, true);
        wr.addWord("SELECT", tKeyWord); //$NON-NLS-1$
        wr.addWord("FROM", tKeyWord); //$NON-NLS-1$
        wr.addWord("WHERE", tKeyWord); //$NON-NLS-1$
        wr.addWord("UNION", tKeyWord); //$NON-NLS-1$
        
        wr.addWord("DISTINCT", tKeyWord); //$NON-NLS-1$
        wr.addWord("INSTANCEOF", tKeyWord); //$NON-NLS-1$
        wr.addWord("AS", tKeyWord); //$NON-NLS-1$
        wr.addWord("RETAINED", tKeyWord); //$NON-NLS-1$
        wr.addWord("SET", tKeyWord); //$NON-NLS-1$
        wr.addWord("OBJECTS", tKeyWord); //$NON-NLS-1$
       
        r[0] = wr;
        
        // Add some constants
        WordRule wr2 = new WordRule(new IWordDetector()
        {

            public boolean isWordPart(char arg0)
            {
                return arg0 != ' ' && arg0 != '\n' && arg0 != '\r' && arg0 != ')' && arg0 != '!' && arg0 != '=';
            }

            public boolean isWordStart(char arg0)
            {
                return arg0 != ' ';
            }
        }, Token.UNDEFINED, true);
        wr2.addWord("true", tKeyWord); //$NON-NLS-1$
        wr2.addWord("false", tKeyWord); //$NON-NLS-1$
        wr2.addWord("null", tKeyWord); //$NON-NLS-1$
        
        r[1] = wr2;
        
        WordRule wr3 = new WordRule(new IWordDetector()
        {

            public boolean isWordPart(char arg0)
            {
                return arg0 != ' ' && arg0 != '\n' && arg0 != '\r' && arg0 != '(';
            }

            public boolean isWordStart(char arg0)
            {
                return arg0 != ' ';
            }
        }, Token.UNDEFINED, true);
        wr3.addWord("or", tKeyWord); //$NON-NLS-1$
        wr3.addWord("and", tKeyWord); //$NON-NLS-1$
        wr3.addWord("not", tKeyWord); //$NON-NLS-1$
        wr3.addWord("like", tKeyWord); //$NON-NLS-1$
        wr3.addWord("in", tKeyWord); //$NON-NLS-1$
        wr3.addWord("implements", tKeyWord); //$NON-NLS-1$

        r[2] = wr3;
        
        // Add functions (case sensitive)
        WordRule wr4 = new WordRule(new IWordDetector()
        {

            public boolean isWordPart(char arg0)
            {
                return arg0 != ' ' && arg0 != '\n' && arg0 != '\r' && arg0 != '(';
            }

            public boolean isWordStart(char arg0)
            {
                return arg0 != ' ';
            }
        }, Token.UNDEFINED, false);
        wr4.addWord("toHex", tKeyWord); //$NON-NLS-1$
        wr4.addWord("toString", tKeyWord); //$NON-NLS-1$
        wr4.addWord("dominators", tKeyWord); //$NON-NLS-1$
        wr4.addWord("dominatorof", tKeyWord); //$NON-NLS-1$
        wr4.addWord("outbounds", tKeyWord); //$NON-NLS-1$
        wr4.addWord("inbounds", tKeyWord); //$NON-NLS-1$
        wr4.addWord("classof", tKeyWord); //$NON-NLS-1$
        wr4.addWord("eval", tKeyWord); //$NON-NLS-1$

        r[3] = wr4;
        
        setRules(r);
    }
}

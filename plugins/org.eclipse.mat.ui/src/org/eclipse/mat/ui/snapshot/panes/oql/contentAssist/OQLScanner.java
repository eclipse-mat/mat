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

        IRule[] r = new IRule[1];
        WordRule wr = new WordRule(new IWordDetector()
        {

            public boolean isWordPart(char arg0)
            {
                return arg0 != ' ' && arg0 != '\n';
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

        r[0] = wr;

        setRules(r);
    }
}

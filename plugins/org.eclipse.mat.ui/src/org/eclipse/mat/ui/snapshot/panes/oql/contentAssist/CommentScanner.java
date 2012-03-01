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
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;


/**
 * Provides correct color for comments.
 * 
 * @author Filippo Pacifici
 *
 */
public class CommentScanner extends RuleBasedScanner {
	
	/**
	 * Defines the token for comments.
	 */
	public CommentScanner() {
		/* asd asd */
		IToken tKeyWord = new Token(new TextAttribute(ColorProvider.COMMENT_COLOR));
		
		IRule[] rules = new IRule[2];
		
		rules[0] = new MultiLineRule("/*", "*/", tKeyWord);
		
		rules[1] = new SingleLineRule("//", "\n", tKeyWord);
		
		setRules(rules);
	}
}

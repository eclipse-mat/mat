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

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;

/**
 * Context extractor to be used in the FROM clause of a query. If last character
 * is not a space gets back up to the first space.
 * 
 * @author Filippo Pacifici
 */
public class ClassNameExtractor implements ContextExtractor
{

    /**
	 * If the last character is a space it returns an empty String
	 * If not it returns the last substring up to a space
     */
    public String getPrefix(ITextViewer source, int currentPosition)
    {
        IDocument doc = source.getDocument();
        try
        {
            if (doc.getChar(currentPosition - 1) == ' ')
            {
                return ""; //$NON-NLS-1$
            }
            else
            {
                int pos = currentPosition - 1;
                char readChar;
                do
                {
                    readChar = doc.getChar(pos);
                    pos--;
                }
                while (pos >= 0 && readChar != ' ' && readChar != '\n' && readChar != '\r');

                if (readChar == ' ' || readChar == '\n' || readChar == '\r')
                    pos++;
                return doc.get(pos + 1, currentPosition - 1 - pos);

            }
        }
        catch (BadLocationException e)
        {
            return ""; //$NON-NLS-1$
        }

    }

}

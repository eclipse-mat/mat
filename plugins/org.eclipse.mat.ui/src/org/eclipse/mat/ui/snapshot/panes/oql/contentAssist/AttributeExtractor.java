/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;

/**
 * Context extractor to be used in the SELECT and WHERE clause of a query. If last character
 * is not a space gets back up to the first space or at sign or dot.
 */
public class AttributeExtractor implements ContextExtractor
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
                while (pos >= 0 && readChar != ' ' && readChar != '@' && readChar != '.' && readChar != '\n' && readChar != '\r');

                if (readChar == ' ' || readChar == '\n' || readChar == '\r' ||readChar == '.')
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

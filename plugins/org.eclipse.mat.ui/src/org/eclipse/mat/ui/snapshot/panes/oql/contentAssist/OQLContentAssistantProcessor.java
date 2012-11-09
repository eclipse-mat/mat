/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson - add images and descriptions
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ContextInformationValidator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

/**
 * Provides the actual context assistant
 * 
 * @author Filippo Pacifici
 */
public class OQLContentAssistantProcessor implements IContentAssistProcessor
{

    /**
     * provides suggestions given the context
     */
    private SuggestionProvider suggestionProvider;

    private IContextInformation[] last;

    /**
     * Extracts the context from TextViewer
     */
    private ContextExtractor extractor;

    /**
     * Base constructor that initializes suggestion provider and extractor.
     * 
     * @param suggestionProvider
     * @param extractor
     */
    public OQLContentAssistantProcessor(SuggestionProvider suggestionProvider, ContextExtractor extractor)
    {
        super();
        this.suggestionProvider = suggestionProvider;
        this.extractor = extractor;
    }

    /**
     * Asks the extractor to get the context, then gets the list of suggestions
     * through the provider and builds the ICompletionProposal array to be
     * returned.
     * 
     * @param arg0
     *            is the text viewer we are working on
     * @param arg1
     *            is the current position.
     */
    public ICompletionProposal[] computeCompletionProposals(ITextViewer arg0, int arg1)
    {
        String context = extractor.getPrefix(arg0, arg1);
        List<ContentAssistElement> suggestions = suggestionProvider.getSuggestions(context);

        return buildResult(suggestions, arg1, context.length());

    }

    public IContextInformation[] computeContextInformation(ITextViewer arg0, int arg1)
    {
        String context = extractor.getPrefix(arg0, arg1);
        // Perhaps we were activated by space
        if (context.length() == 0)
            context = extractor.getPrefix(arg0, arg1 - 1);
        // For methods just match up to the parenthesis so arguments are ignored
        int paren = context.indexOf('(');
        if (paren >= 0)
            context = context.substring(0, paren + 1);
        List<ContentAssistElement> suggestions = suggestionProvider.getSuggestions(context);
        // Check we have a full match and not just a prefix match
        if (!context.startsWith("\"") && paren == -1) //$NON-NLS-1$
        {
            for (Iterator<ContentAssistElement> it = suggestions.iterator(); it.hasNext();)
            {
                ContentAssistElement ce = it.next();
                if (!ce.getClassName().equals(context))
                    it.remove();
            }
        }
        IContextInformation[] ret = new IContextInformation[suggestions.size()];
        int c = 0;
        ContextInformation ct;
        for (ContentAssistElement ce : suggestions)
        {
            ct = new ContextInformation(ce.getImage(), ce.getDisplayString(), ce.getClassName());
            ret[c++] = ct;
        }
        if (c > 0)
        {
            setLastContextInformation(ret);
        }
        
        return ret;
    }

    public char[] getCompletionProposalAutoActivationCharacters()
    {
        return new char[] { '.', '"' };
    }

    public char[] getContextInformationAutoActivationCharacters()
    {
        return new char[] { ' ', '(', '.' };
    }

    public IContextInformationValidator getContextInformationValidator()
    {
        return new ContextInformationValidator(this);
    }

    public String getErrorMessage()
    {
        return null;
    }

    /**
     * Builds the results in the form of ICompletionProposal[] from the list of
     * Strings.
     * 
     * @param suggestions
     *            the list of suggestions
     * @param arg1
     *            is the position where to place the suggestion
     * @param replaceLength
     *            is the substring to be substituted length
     * @return
     */
    private ICompletionProposal[] buildResult(List<ContentAssistElement> suggestions, int currentCursor,
                    int replaceLength)
    {
        if (suggestions == null)
            throw new IllegalArgumentException("Cannot produce a suggestion. List is null");

        ICompletionProposal[] retProposals = new ICompletionProposal[suggestions.size()];
        Iterator<ContentAssistElement> it = suggestions.iterator();

        int c = 0;
        while (it.hasNext())
        {
            ContentAssistElement cp = it.next();
            String classname = cp.getClassName();
            ICompletionProposal completion = new CompletionProposal(classname, currentCursor - replaceLength,
                            replaceLength, currentCursor - replaceLength + classname.length(),
                            cp.getImage(), cp.getDisplayString(), null, null);
            retProposals[c] = completion;
            c++;
        }

        return retProposals;
    }

    public IContextInformation[] getLastContextInformation()
    {
        return last;
    }

    public void setLastContextInformation(IContextInformation[] last)
    {
        this.last = last;
    }
}

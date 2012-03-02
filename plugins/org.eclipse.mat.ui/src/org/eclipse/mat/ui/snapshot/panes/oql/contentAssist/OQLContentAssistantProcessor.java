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

import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
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
        return null;
    }

    public char[] getCompletionProposalAutoActivationCharacters()
    {
        return new char[] { '.', '"' };
    }

    public char[] getContextInformationAutoActivationCharacters()
    {
        return null;
    }

    public IContextInformationValidator getContextInformationValidator()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public String getErrorMessage()
    {
        // TODO Auto-generated method stub
        return "ARF!!!";
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
                            replaceLength, currentCursor - replaceLength + classname.length());
            retProposals[c] = completion;
            c++;
        }

        return retProposals;
    }
}

/*******************************************************************************
 * Copyright (c) 2012,2019 Filippo Pacifici and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson - more content assist for fields and methods
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.AttributeExtractor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.BuiltinSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ClassNameExtractor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ClassesSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.CommentScanner;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ContextExtractor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.FieldsSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.MultiSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.OQLContentAssistantProcessor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.OQLScanner;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.PropertySuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.SuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning.OQLPartitionScanner;
import org.eclipse.swt.graphics.Color;

/**
 * Configuration provider to assign content assist to the SourceViewer.
 * 
 * @author Filippo Pacifici
 */
public class OQLTextViewerConfiguration extends SourceViewerConfiguration
{

    /**
     * Snapshot the pane is instantiated onto
     */
    private ISnapshot snapshot;
    
    /**
     * holds colors
     */
    private Color comment;
    private Color keyword;

    /**
     * Associates snapshot at this object.
     * 
     * @param snapshot
     */
    public OQLTextViewerConfiguration(ISnapshot snapshot, Color comment, Color keyword)
    {
        super();
        
        // if (snapshot == null) {
        // throw new
        // IllegalArgumentException("Cannot instantiate content assist without snapshot");
        // }
        this.snapshot = snapshot;
        this.comment = comment;
        this.keyword = keyword;
    }

    /**
     * @return the list of managed content types.
     */
    @Override
    public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
        return new String[]{
                        IDocument.DEFAULT_CONTENT_TYPE,
                        OQLPartitionScanner.SELECT_CLAUSE,
                        OQLPartitionScanner.FROM_CLAUSE,
                        OQLPartitionScanner.WHERE_CLAUSE,
                        OQLPartitionScanner.UNION_CLAUSE
        };
    }

    /**
     * Instantiates content assist for every content type
     */
    @Override
    public IContentAssistant getContentAssistant(ISourceViewer sourceViewer)
    {
        ContentAssistant cAssist = new ContentAssistant();

        final FieldsSuggestionProvider fieldSuggestions = new FieldsSuggestionProvider(snapshot);
        
        // classNames
        ContextExtractor classNameExtr = new ClassNameExtractor();
        SuggestionProvider classSuggestions = new ClassesSuggestionProvider(snapshot);
        final OQLContentAssistantProcessor fromProcessor = new OQLContentAssistantProcessor(classSuggestions, classNameExtr);
        cAssist.setContentAssistProcessor(fromProcessor, OQLPartitionScanner.FROM_CLAUSE);
        // TODO: define a better partitioning to correctly treat FROM clauses
        // split by a comment.
        cAssist.setContentAssistProcessor(fromProcessor, IDocument.DEFAULT_CONTENT_TYPE);

        BuiltinSuggestionProvider builtinSuggestions = new BuiltinSuggestionProvider();
        ContextExtractor attributeExtr = new AttributeExtractor();
        PropertySuggestionProvider attributeSuggestions = new PropertySuggestionProvider(snapshot);

        SuggestionProvider allSuggestions = new MultiSuggestionProvider(fieldSuggestions, builtinSuggestions,
                        attributeSuggestions);

        final OQLContentAssistantProcessor selectProcessor = new OQLContentAssistantProcessor(allSuggestions, attributeExtr)
        {
            public char[] getCompletionProposalAutoActivationCharacters()
            {
                return new char[] { '.', '@' };
            }
        };
        cAssist.setContentAssistProcessor(selectProcessor, OQLPartitionScanner.SELECT_CLAUSE);
        cAssist.setContentAssistProcessor(selectProcessor, OQLPartitionScanner.WHERE_CLAUSE);
        /*
         * Listen for a select completion starting. If the last class name for an information pop-up has changed,
         * use it to provide field names.
         */
        cAssist.addCompletionListener(new ICompletionListener() {

            public void assistSessionStarted(ContentAssistEvent event)
            {
                if (event.processor.equals(selectProcessor))
                {
                    IContextInformation lctx[] = fromProcessor.getLastContextInformation();
                    if (lctx != null) {
                        fieldSuggestions.setClassesSuggestions(snapshot, lctx);
                        fromProcessor.setLastContextInformation(null);
                    }
                }
            }

            public void assistSessionEnded(ContentAssistEvent event)
            {
            }

            public void selectionChanged(ICompletionProposal proposal, boolean smartToggle)
            {
            }
            
        });

        cAssist.enableAutoActivation(true);

        cAssist.setAutoActivationDelay(500);
        cAssist.setProposalPopupOrientation(IContentAssistant.CONTEXT_INFO_BELOW);
        cAssist.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_BELOW);

        return cAssist;
    }

    /**
     * Provides syntax highlighting.
     */
    @Override
    public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer)
    {
        PresentationReconciler reconciler = new PresentationReconciler();

        DefaultDamagerRepairer dr = new DefaultDamagerRepairer(new OQLScanner(keyword, OQLPartitionScanner.SELECT_CLAUSE));
        reconciler.setDamager(dr, OQLPartitionScanner.SELECT_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.SELECT_CLAUSE);

        dr = new DefaultDamagerRepairer(new OQLScanner(keyword, OQLPartitionScanner.FROM_CLAUSE));
        reconciler.setDamager(dr, OQLPartitionScanner.FROM_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.FROM_CLAUSE);

        dr = new DefaultDamagerRepairer(new OQLScanner(keyword, OQLPartitionScanner.WHERE_CLAUSE));
        reconciler.setDamager(dr, OQLPartitionScanner.WHERE_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.WHERE_CLAUSE);

        dr = new DefaultDamagerRepairer(new OQLScanner(keyword, OQLPartitionScanner.UNION_CLAUSE));
        reconciler.setDamager(dr, OQLPartitionScanner.UNION_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.UNION_CLAUSE);

        dr = new DefaultDamagerRepairer(new CommentScanner(comment));
        reconciler.setDamager(dr, OQLPartitionScanner.COMMENT_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.COMMENT_CLAUSE);

        return reconciler;
    }

}

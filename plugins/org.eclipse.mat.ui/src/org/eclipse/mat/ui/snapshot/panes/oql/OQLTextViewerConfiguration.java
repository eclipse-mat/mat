/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson - more content assist for fields and methods
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql;

import java.util.List;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.AttributeExtractor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.BuiltinSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ClassNameExtractor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ClassesSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.CommentScanner;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ContentAssistElement;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ContextExtractor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.FieldsSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.MultiSuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.OQLContentAssistantProcessor;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.OQLScanner;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.PropertySuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.SuggestionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning.OQLPartitionScanner;
import org.eclipse.mat.ui.util.ErrorHelper;
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
        SuggestionProvider classSuggestions = null;
        try
        {
            classSuggestions = new ClassesSuggestionProvider(snapshot)
            {
                public List<ContentAssistElement> getSuggestions(String context)
                {
                    List<ContentAssistElement> classSuggestions = super.getSuggestions(context);
                    try
                    {
                        // New class suggestions, so update the field suggestions
                        fieldSuggestions.setClassesSuggestions(snapshot, classSuggestions);
                    }
                    catch (SnapshotException e)
                    {
                        ErrorHelper.logThrowable(e);
                    }
                    return classSuggestions;
                }
            };
        }
        catch (SnapshotException e)
        {
            ErrorHelper.logThrowable(e);
        }
        OQLContentAssistantProcessor fromProcessor = new OQLContentAssistantProcessor(classSuggestions, classNameExtr);
        cAssist.setContentAssistProcessor(fromProcessor, OQLPartitionScanner.FROM_CLAUSE);
        // TODO: define a better partitioning to correctly treat FROM clauses
        // split by a comment.
        cAssist.setContentAssistProcessor(fromProcessor, IDocument.DEFAULT_CONTENT_TYPE);

        final BuiltinSuggestionProvider builtinSuggestions = new BuiltinSuggestionProvider();
        ContextExtractor attributeExtr = new AttributeExtractor();
        final PropertySuggestionProvider attributeSuggestions = new PropertySuggestionProvider(snapshot);

        SuggestionProvider allSuggestions = new MultiSuggestionProvider(fieldSuggestions, builtinSuggestions,
                        attributeSuggestions);

        OQLContentAssistantProcessor selectProcessor = new OQLContentAssistantProcessor(allSuggestions, attributeExtr)
        {
            public char[] getCompletionProposalAutoActivationCharacters()
            {
                return new char[] { '.', '@' };
            }
        };
        cAssist.setContentAssistProcessor(selectProcessor, OQLPartitionScanner.SELECT_CLAUSE);
        cAssist.setContentAssistProcessor(selectProcessor, OQLPartitionScanner.WHERE_CLAUSE);

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

        DefaultDamagerRepairer dr = new DefaultDamagerRepairer(new OQLScanner(keyword));
        reconciler.setDamager(dr, OQLPartitionScanner.SELECT_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.SELECT_CLAUSE);

        reconciler.setDamager(dr, OQLPartitionScanner.FROM_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.FROM_CLAUSE);

        reconciler.setDamager(dr, OQLPartitionScanner.WHERE_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.WHERE_CLAUSE);

        reconciler.setDamager(dr, OQLPartitionScanner.UNION_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.UNION_CLAUSE);

        dr = new DefaultDamagerRepairer(new CommentScanner(comment));
        reconciler.setDamager(dr, OQLPartitionScanner.COMMENT_CLAUSE);
        reconciler.setRepairer(dr, OQLPartitionScanner.COMMENT_CLAUSE);

        return reconciler;
    }

}

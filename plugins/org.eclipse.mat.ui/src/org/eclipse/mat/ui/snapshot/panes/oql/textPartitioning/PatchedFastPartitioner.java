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

import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TypedPosition;
import org.eclipse.jface.text.TypedRegion;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPartitionTokenScanner;

/**
 * Overrides the getPartition to correctly support the case in which provided
 * offset is the position after the last character of the document as
 * ContentAssistant asks.
 * 
 * @author Filippo Pacifici
 */
public class PatchedFastPartitioner extends FastPartitioner
{

    public PatchedFastPartitioner(IPartitionTokenScanner scanner, String[] legalContentTypes)
    {
        super(scanner, legalContentTypes);

    }

    @Override
    /**
     * {@inheritDoc}
     * <p>
     * May be replaced or extended by subclasses.
     * </p>
     */
    public ITypedRegion getPartition(int offset)
    {
        checkInitialization();

        if (fDocument.getLength() <= offset)
        {
            // last character:
            try
            {
                Position[] category = getPositions();
                if (category.length > 0)
                {
                    TypedPosition previous = (TypedPosition) category[category.length - 1];
                    // check if the last partition contains the last character
                    // of the document.
                    if (previous.includes(offset - 1))
                        return new TypedRegion(previous.getOffset(), previous.getLength(), previous.getType());
                    else
                    {
                        int endOffset = previous.getOffset() + previous.getLength();
                        return new TypedRegion(endOffset, fDocument.getLength() - endOffset,
                                        IDocument.DEFAULT_CONTENT_TYPE);
                    }
                }
                else
                {
                    return new TypedRegion(0, fDocument.getLength(), IDocument.DEFAULT_CONTENT_TYPE);
                }
            }
            catch (BadPositionCategoryException e)
            {
                return new TypedRegion(0, fDocument.getLength(), IDocument.DEFAULT_CONTENT_TYPE);
            }
        }
        else
        {
            // normal case
            return super.getPartition(offset);
        }
    }

}

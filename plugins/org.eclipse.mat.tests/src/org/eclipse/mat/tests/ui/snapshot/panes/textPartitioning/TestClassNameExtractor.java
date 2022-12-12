/*******************************************************************************
 * Copyright (c) 2012,2022 Filippo Pacifici and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson (IBM Corporation) - fix deprecated method
 *******************************************************************************/
package org.eclipse.mat.tests.ui.snapshot.panes.textPartitioning;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IAutoIndentStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IEventConsumer;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.IUndoManager;
import org.eclipse.jface.text.IViewportListener;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ClassNameExtractor;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that the classname extractor works properly
 * 
 * @author Filippo Pacifici
 */
public class TestClassNameExtractor
{

    @Test
    public void testClassNameExtractor() throws Exception
    {
        IDocument doc = new Document();
        doc.set("FROM java.util.");

        ITextViewer viewer = new MockTextViewer();
        viewer.setDocument(doc);

        ClassNameExtractor extractor = new ClassNameExtractor();
        String context = extractor.getPrefix(viewer, 9);
        Assert.assertEquals("java", context);

        String context2 = extractor.getPrefix(viewer, 14);
        Assert.assertEquals("java.util", context2);

        String context3 = extractor.getPrefix(viewer, 3);
        Assert.assertEquals("FRO", context3);

        String context4 = extractor.getPrefix(viewer, 4);
        Assert.assertEquals("FROM", context4);

        String context5 = extractor.getPrefix(viewer, 5);
        Assert.assertEquals("", context5);

    }

    private static class MockTextViewer implements ITextViewer
    {

        private IDocument doc = null;

        // public getSelectionProvider(){
        // return null;
        // }

        public void activatePlugins()
        {
            // TODO Auto-generated method stub

        }

        public void addTextInputListener(ITextInputListener arg0)
        {
            // TODO Auto-generated method stub

        }

        public void addTextListener(ITextListener arg0)
        {
            // TODO Auto-generated method stub

        }

        public void addViewportListener(IViewportListener arg0)
        {
            // TODO Auto-generated method stub

        }

        public void changeTextPresentation(TextPresentation arg0, boolean arg1)
        {
            // TODO Auto-generated method stub

        }

        public int getBottomIndex()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        public int getBottomIndexEndOffset()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        public IDocument getDocument()
        {
            return doc;
        }

        public IFindReplaceTarget getFindReplaceTarget()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public Point getSelectedRange()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public ITextOperationTarget getTextOperationTarget()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public StyledText getTextWidget()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public int getTopIndex()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        public int getTopIndexStartOffset()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        public int getTopInset()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        public IRegion getVisibleRegion()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public void invalidateTextPresentation()
        {
            // TODO Auto-generated method stub

        }

        public boolean isEditable()
        {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean overlapsWithVisibleRegion(int arg0, int arg1)
        {
            // TODO Auto-generated method stub
            return false;
        }

        public void removeTextInputListener(ITextInputListener arg0)
        {
            // TODO Auto-generated method stub

        }

        public void removeTextListener(ITextListener arg0)
        {
            // TODO Auto-generated method stub

        }

        public void removeViewportListener(IViewportListener arg0)
        {
            // TODO Auto-generated method stub

        }

        public void resetPlugins()
        {
            // TODO Auto-generated method stub

        }

        public void resetVisibleRegion()
        {
            // TODO Auto-generated method stub

        }

        public void revealRange(int arg0, int arg1)
        {
            // TODO Auto-generated method stub

        }

        public void setAutoIndentStrategy(IAutoIndentStrategy arg0, String arg1)
        {
            // TODO Auto-generated method stub

        }

        public void setDefaultPrefixes(String[] arg0, String arg1)
        {
            // TODO Auto-generated method stub

        }

        public void setDocument(IDocument arg0, int arg1, int arg2)
        {
            // TODO Auto-generated method stub

        }

        public void setDocument(IDocument arg0)
        {
            doc = arg0;

        }

        public void setEditable(boolean arg0)
        {
            // TODO Auto-generated method stub

        }

        public void setEventConsumer(IEventConsumer arg0)
        {
            // TODO Auto-generated method stub

        }

        public void setIndentPrefixes(String[] arg0, String arg1)
        {
            // TODO Auto-generated method stub

        }

        public void setSelectedRange(int arg0, int arg1)
        {
            // TODO Auto-generated method stub

        }

        public void setTextColor(Color arg0, int arg1, int arg2, boolean arg3)
        {
            // TODO Auto-generated method stub

        }

        public void setTextColor(Color arg0)
        {
            // TODO Auto-generated method stub

        }

        public void setTextDoubleClickStrategy(ITextDoubleClickStrategy arg0, String arg1)
        {
            // TODO Auto-generated method stub

        }

        public void setTextHover(ITextHover arg0, String arg1)
        {
            // TODO Auto-generated method stub

        }

        public void setTopIndex(int arg0)
        {
            // TODO Auto-generated method stub

        }

        public void setUndoManager(IUndoManager arg0)
        {
            // TODO Auto-generated method stub

        }

        public void setVisibleRegion(int arg0, int arg1)
        {
            // TODO Auto-generated method stub

        }

        public ISelectionProvider getSelectionProvider()
        {
            // TODO Auto-generated method stub
            return null;
        }

    }

}

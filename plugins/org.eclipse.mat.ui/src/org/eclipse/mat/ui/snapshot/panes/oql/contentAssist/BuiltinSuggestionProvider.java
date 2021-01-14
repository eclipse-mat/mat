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

import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.graphics.Image;

/**
 * Provides the list of built-in functions that start with the provided
 * context String.
 * 
 * @author Andrew Johnson
 */
public class BuiltinSuggestionProvider implements SuggestionProvider
{

    /**
     * Ordered list.
     */
    private TreeSet<ContentAssistElement> orderedList;

    /**
     * Builds this object
     * 
     */
    public BuiltinSuggestionProvider()
    {
        initList();
    }

    /**
	 * Returns the list of ICompletionProposals
	 * It scans the ordered set up to the first valid element.
	 * Once it is found it fills the temporary list with all the elements up to the 
	 * first which is no more valid.
	 * 
	 * At that point it returns.
     */
    public List<ContentAssistElement> getSuggestions(String context)
    {
        LinkedList<ContentAssistElement> tempList = new LinkedList<ContentAssistElement>();
        boolean foundFirst = false;

        for (ContentAssistElement cp : orderedList)
        {
            String cName = cp.getClassName();
            if (cName.startsWith(context))
            {
                foundFirst = true;
                tempList.add(cp);
            }
            else
            {
                if (foundFirst)
                {
                    break;
                }
            }
        }
        return tempList;
    }

    /**
     * Collects built-in functions and puts them ordered into orderedList.
     * 
     */
    private void initList()
    {
        @SuppressWarnings("nls")
        String builtin[] = { "toHex", "toString", "dominators", "dominatorof", "outbounds", "inbounds", "classof" };
        orderedList = new TreeSet<ContentAssistElement>();

        Image im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.INFO);
        for (String func : builtin)
        {
            // instantiate here in order to provide class and packages images.
            ContentAssistElement ce = new ContentAssistElement(func + "()", im); //$NON-NLS-1$
            orderedList.add(ce);
        }
        im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.HEAP);
        ContentAssistElement ce = new ContentAssistElement("${snapshot}", im); //$NON-NLS-1$
        orderedList.add(ce);
    }
}

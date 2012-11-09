/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 * Andrew Johnson - add regular expression matching and images
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.util.PatternUtil;
import org.eclipse.swt.graphics.Image;

/**
 * Provides the list of classnames in the snapshot that starts with the provided
 * context String.
 * 
 * @author Filippo Pacifici
 */
public class ClassesSuggestionProvider implements SuggestionProvider
{

    /**
     * Keeps the status of the class list. Since classes set is filled
     * asynchronously, we do not try to show results as long as the flag is
     * false to avoid ConcurrentModificationException on the iterator.
     */
    boolean ready = false;

    /**
     * Ordered class list.
     */
    private TreeSet<ContentAssistElement> orderedList;

    /**
     * Builds this object passing the snapshot
     * 
     * @param snapshot
     */
    public ClassesSuggestionProvider(ISnapshot snapshot)
    {
        InitializerJob asyncJob = new InitializerJob(snapshot);
        asyncJob.schedule();
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
        if (ready)
        {
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
            // Regular expression matching
            if (context.startsWith("\"")) //$NON-NLS-1$
            {
                String context2 = context.substring(1);
                if (context2.endsWith("\"")) //$NON-NLS-1$
                {
                    // Terminated with double-quote, so expression complete
                    context2 = context2.substring(0, context2.length() - 1);
                }
                else if (context2.endsWith("[")) //$NON-NLS-1$
                {
                    // Partial array name, so complete it now so that the regex is valid
                    context2 = context2 + "].*"; //$NON-NLS-1$
                }
                else if (!context2.endsWith(".*")) //$NON-NLS-1$
                {
                    // Allow anything to end
                    context2 = context2 + ".*"; //$NON-NLS-1$
                }
                // Convert array name to regex safe form
                context2 = PatternUtil.smartFix(context2, false);
                try
                {
                    Pattern p = Pattern.compile(context2);

                    foundFirst = false;
                    for (ContentAssistElement cp : orderedList)
                    {
                        String cName = cp.getClassName();
                        if (p.matcher(cName).matches())
                        {
                            if (!foundFirst)
                            {
                                // Add the regex to the list
                                tempList.add(new ContentAssistElement("\"" + context2 + "\"", null)); //$NON-NLS-1$ //$NON-NLS-2$
                                foundFirst = true;
                            }
                            tempList.add(cp);
                        }
                    }
                }
                catch (PatternSyntaxException e)
                {
                    // Ignore - the user just made a mistake
                }
            }
        }
        return tempList;
    }

    /**
     * Scans class list from the snapshot and put it ordered into orderedList.
     * 
     * @param snapshot
     */
    private void initList(ISnapshot snapshot) throws SnapshotException
    {
        if (snapshot == null)
            throw new IllegalArgumentException("Cannot extract class list from a null snapshot.");

        Collection<IClass> classes = snapshot.getClasses();
        orderedList = new TreeSet<ContentAssistElement>();

        Image im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.CLASS);
        for (IClass c : classes)
        {
            // instantiate here in order to provide class and packages images.
            ContentAssistElement ce = new ContentAssistElement(c.getName(), im);
            orderedList.add(ce);
        }
        ready = true;
    }

    /**
     * Asynchronous job to initialize the completion
     * 
     * @author Filippo Pacifici
     */
    private class InitializerJob extends Job
    {

        ISnapshot snapshot;

        public InitializerJob(ISnapshot snapshot)
        {
            super("Init content assistant");
            this.snapshot = snapshot;
        }

        @Override
        protected IStatus run(IProgressMonitor arg0)
        {
            try
            {
                initList(snapshot);
                return Status.OK_STATUS;
            }
            catch (SnapshotException e)
            {
                ErrorHelper.logThrowable(e);
            }
            return Status.CANCEL_STATUS;
        }

    }

}

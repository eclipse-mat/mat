/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.graphics.Image;

/**
 * Provides the list of classnames in the snapshot that starts with the provided
 * context String.
 */
public class FieldsSuggestionProvider implements SuggestionProvider
{

    private static final String CLASS_SEP = " - "; //$NON-NLS-1$

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
    public FieldsSuggestionProvider(ISnapshot snapshot)
    {
        
    }

    public void setClassesSuggestions(ISnapshot snapshot, IContextInformation[] classSuggestions)
    {
        ready = false;
        // Done synchronously as called when select pop-up appears
        try
        {
            initList(snapshot, classSuggestions);
        }
        catch (SnapshotException e)
        {
            ErrorHelper.logThrowable(e);
        }
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
        }
        return tempList;
    }

    /**
     * Scans class list and adds the fields, ordered, into orderedList.
     * 
     * @param snapshot
     * @param classSuggestions - possible classes to find fields from
     */
    private void initList(ISnapshot snapshot, IContextInformation[] classSuggestions) throws SnapshotException
    {
        if (snapshot == null)
            throw new IllegalArgumentException("Cannot extract class list from a null snapshot.");

        ready = false;
        Collection<IClass>classes = new HashSet<IClass>();
        
        for (IContextInformation el : classSuggestions)
        {
            Collection<IClass> cls = snapshot.getClassesByName(el.getContextDisplayString(), false);
            if (cls != null)
            {
                classes.addAll(cls);
            }
        }
        orderedList = new TreeSet<ContentAssistElement>();

        Image im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.CLASS);
        for (IClass c : classes)
        {
            do
            {
                for (FieldDescriptor fd : c.getFieldDescriptors())
                {
                    // instantiate here in order to provide class and packages
                    // images.
                    String desc = fd.getVerboseSignature() + " " + fd.getName() + CLASS_SEP + c.getName(); //$NON-NLS-1$
                    ContentAssistElement ce = new ContentAssistElement(fd.getName(), im, desc);
                    if (!orderedList.contains(ce))
                        orderedList.add(ce);
                }
                c = c.getSuperClass();
            }
            while (c != null);
        }
        ready = true;
    }

    /**
     * Asynchronous job to initialize the completion
     */
    private class InitializerJob extends Job
    {

        ISnapshot snapshot;
        IContextInformation[] classSuggestions;

        public InitializerJob(ISnapshot snapshot, IContextInformation[] suggestions)
        {
            super("Init content assistant");
            this.snapshot = snapshot;
            this.classSuggestions = suggestions;
        }

        @Override
        protected IStatus run(IProgressMonitor arg0)
        {
            try
            {
                initList(snapshot, classSuggestions);
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

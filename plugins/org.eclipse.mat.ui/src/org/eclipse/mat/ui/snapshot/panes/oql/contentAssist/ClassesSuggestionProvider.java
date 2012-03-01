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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.ui.util.ErrorHelper;

/**
 * Provides the list of classnames in the snapshot that starts with 
 * the provided context String.
 * 
 * @author Filippo Pacifici
 *
 */
public class ClassesSuggestionProvider implements SuggestionProvider {

	/**
	 * Keeps the status of the class list.
	 * Since classes set is filled asynchronously, we do not try to show
	 * results as long as the flag is false to avoit ConcurrentModificationException 
	 * on the iterator.
	 */
	boolean ready = false;
	
	/**
	 * Ordered class list.
	 */
	private TreeSet<ContentAssistElement> orderedList;
	
	/**
	 * Builds this object passing the snapshot
	 * @param snapshot
	 */
	public ClassesSuggestionProvider(ISnapshot snapshot) throws SnapshotException{
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
	public List<ContentAssistElement> getSuggestions(String context) {
		LinkedList<ContentAssistElement> tempList = new LinkedList<ContentAssistElement>();
		boolean foundFirst = false;
		boolean foundLast = false;
		if (ready){
			Iterator<ContentAssistElement> it = orderedList.iterator();
		
			while (it.hasNext() && foundLast == false){
				ContentAssistElement cp = it.next();
				String cName = cp.getClassName();
				if (cName.startsWith(context)){
					foundFirst = true;
					tempList.add(cp);
				}else{
					if (foundFirst){
						foundLast = true;
					}
				}
			}
		}
		
		return tempList;
	}
	
	/**
	 * Scans class list from the snapshot and put it ordered into orderedList. 
	 * @param snapshot
	 */
	private void initList(ISnapshot snapshot) throws SnapshotException{
		if (snapshot == null)
			throw new IllegalArgumentException("Cannot extract class list from a null snapshot.");
		
		Collection<IClass> classes = snapshot.getClasses();
		orderedList = new TreeSet<ContentAssistElement>();
		
		for (IClass c : classes) {
			//instantiate here in order to provide class and packages images.
			ContentAssistElement ce = new ContentAssistElement(c.getName(), null);
			orderedList.add(ce);
		}
		ready = true;
	}
	
	/**
	 * Asynchronous job to initialize the completion
	 * 
	 * @author Filippo Pacifici
	 *
	 */
	private class InitializerJob extends Job {

		ISnapshot snapshot;
		
		public InitializerJob(ISnapshot snapshot) {
			super("Init content assistant");
			this.snapshot = snapshot;
		}

		@Override
		protected IStatus run(IProgressMonitor arg0) {
			try {
				initList(snapshot);
				return Status.OK_STATUS;
			}catch (SnapshotException e){
				ErrorHelper.logThrowable(e);
			}
			return Status.CANCEL_STATUS;
		}
		
	}

}

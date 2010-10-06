/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.extension;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.util.IProgressListener;

/**
 * Interface for a trouble-ticket component resolver. A trouble-ticket component
 * resolver can propose a proper component (e.g. MAT) within a troubleshooting
 * system (e.g. bugzilla) for a specific class or classloader (e.g.
 * org.eclipse.mat.parser.internal.SnapshotImpl).
 * 
 * This information is exposed in the Leak suspects reports. It could help the
 * user who generated the report to open a trouble ticket in the proper
 * component, and this is especially helpful when the user is not familiar with
 * the analyzed coding.
 * 
 * Implementations of this interface need to be
 * registered using the <code>org.eclipse.mat.api.ticketResolver</code> extension point.
 */
public interface ITroubleTicketResolver
{

	/**
	 * Get the trouble tracking system, e.g Bugzilla
	 * 
	 * @return a String identifying the trouble-ticket system
	 */
	public String getTicketSystem();

	/**
	 * Return a proposal for the component (e.g. the bugzilla product MAT) based
	 * on a class.
	 * 
	 * @param clazz
	 *            the class for which the component should be proposed
	 * @param listener
	 *            a progress listener
	 * 
	 * @return a String for the proposed component, or null if no proposal.
	 * @throws SnapshotException
	 */
	public String resolveByClass(IClass clazz, IProgressListener listener) throws SnapshotException;

	/**
	 * Return a proposal for the component (e.g. the bugzilla product MAT) based
	 * on a classloader.
	 * 
	 * @param classLoader
	 *            the class for which the component should be proposed
	 * @param listener
	 *            a progress listener
	 * 
	 * @return a String for the proposed component, or null if no proposal.
	 * @throws SnapshotException
	 */
	public String resolveByClassLoader(IClassLoader classLoader, IProgressListener listener) throws SnapshotException;

}

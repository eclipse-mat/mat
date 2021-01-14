/*******************************************************************************
* Copyright (c) 2012 Filippo Pacifici
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
* Filippo Pacifici - initial API and implementation
*******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import org.eclipse.jface.text.ITextViewer;

/**
 * A ContextExtractor extract the part of the current TextViewer that is the 
 * prefix of of the String.
 * 
 * The String provided by implementations of this interface are used to reduce
 * the amount of results provided by content assist.
 * 
 * @author Filippo Pacifici
 *
 */
public interface ContextExtractor {

	/**
	 * Given the TextViewer and the position, it scans the content backwards to extract 
	 * the prefix for the content assist
	 * @param source
	 * @param currentPosition
	 * @return what has been typed before the current position and should be used to filter the content assistance.
	 */
	public String getPrefix(ITextViewer source, int currentPosition);
}

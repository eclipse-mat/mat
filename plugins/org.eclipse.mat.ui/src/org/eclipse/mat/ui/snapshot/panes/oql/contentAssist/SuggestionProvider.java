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

import java.util.List;

/**
 * The suggestion provider returns the list of suggestion given a context.
 * 
 * @author Filippo Pacifici
 */
public interface SuggestionProvider
{

    /**
	 * Given the context (prefix provided by the user) it returns available suggestions. 
	 * @param context is the prefix to be searched from. It can be an empty String but must not
	 * be null.
     * @return the list of suggestion proposals.
     */
    public List<ContentAssistElement> getSuggestions(String context);
}

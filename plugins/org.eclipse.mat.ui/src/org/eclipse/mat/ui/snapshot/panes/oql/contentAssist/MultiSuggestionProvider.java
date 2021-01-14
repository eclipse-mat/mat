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

import java.util.ArrayList;
import java.util.List;

/**
 * Combines the results of several suggestion providers.
 */
public class MultiSuggestionProvider implements SuggestionProvider
{
    private final SuggestionProvider list[];

    public MultiSuggestionProvider(SuggestionProvider... l)
    {
        this.list = l;
    }

    public List<ContentAssistElement> getSuggestions(String context)
    {
        List<ContentAssistElement> ret = new ArrayList<ContentAssistElement>();
        for (SuggestionProvider sp : list)
        {
            ret.addAll(sp.getSuggestions(context));
        }
        return ret;
    }
}

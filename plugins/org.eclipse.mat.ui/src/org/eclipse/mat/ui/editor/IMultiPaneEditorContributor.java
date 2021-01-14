/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.editor;

import org.eclipse.jface.action.IToolBarManager;

/**
 * Used to contribute items to the tool bar of the editor.
 */
public interface IMultiPaneEditorContributor
{
    /**
     * Called when the editor starts
     */
    void init(MultiPaneEditor editor);

    /**
     * Called to enable the extension to add contributions to the toolbar.
     */
    void contributeToToolbar(IToolBarManager manager);

    /**
     * Called when the editor stops
     */
    void dispose();
}

/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
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
package org.eclipse.mat.ui.internal.views;

import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.views.contentoutline.ContentOutline;

public class SnapshotDetailsView extends ContentOutline
{
    @Override
    protected boolean isImportant(IWorkbenchPart part)
    {
        return (part instanceof MultiPaneEditor) || (part instanceof SnapshotHistoryView);
    }
}

/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
// Copied from org.eclipse.platform.internal
package org.eclipse.mat.ui.internal;

import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.cheatsheets.CheatSheetViewerFactory;
import org.eclipse.ui.cheatsheets.ICheatSheetViewer;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.intro.IIntroPart;
import org.eclipse.ui.intro.config.IStandbyContentPart;

public final class CheatSheetStandbyContent implements IStandbyContentPart {

    private static String MEMENTO_CHEATSHEET_ID_ATT = "cheatsheetId"; //$NON-NLS-1$

    //private IIntroPart introPart;
    private ICheatSheetViewer viewer;
    private Composite container;
    private String input;

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.intro.internal.parts.IStandbyContentPart#init(org.eclipse.ui.intro.IIntroPart)
     */
    public void init(IIntroPart introPart, IMemento memento) {
        //this.introPart = introPart;
        // try to restore last state.
        input = getCachedInput(memento);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.intro.internal.parts.IStandbyContentPart#createControl(org.eclipse.swt.widgets.Composite,
     *      org.eclipse.ui.forms.widgets.FormToolkit)
     */
    public void createPartControl(Composite parent, FormToolkit toolkit) {
        container = toolkit.createComposite(parent);
        FillLayout layout = new FillLayout();
        layout.marginWidth = layout.marginHeight = 0;
        container.setLayout(layout);

        viewer = CheatSheetViewerFactory.createCheatSheetView();
        viewer.createPartControl(container);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.intro.internal.parts.IStandbyContentPart#getControl()
     */
    public Control getControl() {
        return container;
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.intro.internal.parts.IStandbyContentPart#setInput(java.lang.Object)
     */
    public void setInput(Object input) {
        // if the new input is null, use cacched input from momento.
        if (input != null)
            this.input = (String) input;
        viewer.setInput(this.input);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.intro.internal.parts.IStandbyContentPart#setFocus()
     */
    public void setFocus() {
        viewer.setFocus();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.intro.internal.parts.IStandbyContentPart#dispose()
     */
    public void dispose() {

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.intro.config.IStandbyContentPart#saveState(org.eclipse.ui.IMemento)
     */
    public void saveState(IMemento memento) {
        String currentCheatSheetId = viewer.getCheatSheetID();
        if (currentCheatSheetId != null)
            memento.putString(MEMENTO_CHEATSHEET_ID_ATT, currentCheatSheetId);
    }

    /**
     * Tries to create the last content part viewed, based on content part id..
     * 
     * @param memento
     * @return
     */
    private String getCachedInput(IMemento memento) {
        if (memento == null)
            return null;
        return memento.getString(MEMENTO_CHEATSHEET_ID_ATT);

    }

}

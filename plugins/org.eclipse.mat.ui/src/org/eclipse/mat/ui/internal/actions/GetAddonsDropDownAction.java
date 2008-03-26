/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.jface.action.Action;
import org.eclipse.mat.inspections.query.sidecar.AddonResolverRegistry;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.PopupMenu;


public class GetAddonsDropDownAction extends EasyToolBarDropDown
{

    private boolean doDisplayMenu;
    private List<Action> addonActions;

    public GetAddonsDropDownAction(HeapEditor editor)
    {
        super("Extract Additional Heap Dump Info", MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SHOW_ADDONS), editor);

        addonActions = new ArrayList<Action>();
        for (AddonResolverRegistry.AddonRecord addonRecord : AddonResolverRegistry.instance().delegates())
        {
            try
            {
                if (editor.getSnapshotInput().getSnapshot().getSnapshotAddons(addonRecord.getQueryInterface()) == null)
                    continue;
                Action action = new ExecuteQueryAction(editor, addonRecord.getQueryIdentifier());
                action.setText(addonRecord.getName());
                addonActions.add(action);

            }
            catch (InvalidRegistryObjectException e)
            {
                MemoryAnalyserPlugin.log(e);
            }
            catch (ClassNotFoundException e)
            {
                MemoryAnalyserPlugin.log(e);
            }
            catch (InstantiationException e)
            {
                MemoryAnalyserPlugin.log(e);
            }
            catch (IllegalAccessException e)
            {
                MemoryAnalyserPlugin.log(e);
            }
            catch (SnapshotException e)
            {
                MemoryAnalyserPlugin.log(e);
            }
        }
        if (!addonActions.isEmpty())
            doDisplayMenu = true;

    }

    @Override
    public void contribute(PopupMenu menu)
    {

        Collections.sort(addonActions, new Comparator<Action>()
        {
            public int compare(Action o1, Action o2)
            {
                return o1.getText().compareTo(o2.getText());
            }
        });

        for (Action action : addonActions)
            menu.add(action);

    }

    public boolean doDisplayMenu()
    {
        return doDisplayMenu;
    }
}

/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
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
package org.eclipse.mat.ui.rcp.actions;

import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;

public class AddHistoryToMenuAction extends ContributionItem
{
    private IWorkbenchWindow window;
    private boolean dirty = true;
    private IMenuListener menuListener = new IMenuListener()
    {
        public void menuAboutToShow(IMenuManager manager)
        {
            manager.markDirty();
            dirty = true;
        }
    };

    // the maximum length for a file name; must be >= 4
    private static final int MAX_TEXT_LENGTH = 40;
    // only assign mnemonic to the first nine items
    private static final int MAX_MNEMONIC_SIZE = 9;

    /**
     * Create a new instance.
     */
    public AddHistoryToMenuAction(IWorkbenchWindow window)
    {
        super("Reopen Editor"); //$NON-NLS-1$
        this.window = window;
    }

    /**
     * Returns the text for a history item. This may be truncated to fit within
     * the MAX_TEXT_LENGTH.
     */
    private String calcText(int index, SnapshotHistoryService.Entry item)
    {
        StringBuffer sb = new StringBuffer();

        int mnemonic = index + 1;
        sb.append(mnemonic);
        sb.append(" "); //$NON-NLS-1$
        if (mnemonic <= MAX_MNEMONIC_SIZE)
        {
            sb.insert(sb.length() - (mnemonic + "").length(), '&'); //$NON-NLS-1$
        }
        sb.append(" "); //$NON-NLS-1$

        String fileName = new Path(item.getFilePath()).lastSegment();
        String pathName = item.getFilePath();
        if (pathName.equals(fileName))
        {
            pathName = ""; //$NON-NLS-1$
        }
        IPath path = new Path(pathName);
        // if last segment in path is the fileName, remove it
        if (path.segmentCount() > 1 && path.segment(path.segmentCount() - 1).equals(fileName))
        {
            path = path.removeLastSegments(1);
            pathName = path.toString();
        }

        if ((fileName.length() + pathName.length()) <= (MAX_TEXT_LENGTH - 4))
        {
            // entire item name fits within maximum length
            sb.append(fileName);
            if (pathName.length() > 0)
            {
                sb.append("  ["); //$NON-NLS-1$
                sb.append(pathName);
                sb.append("]"); //$NON-NLS-1$
            }
        }
        else
        {
            // need to shorten the item name
            int length = fileName.length();
            if (length > MAX_TEXT_LENGTH)
            {
                // file name does not fit within length, truncate it
                sb.append(fileName.substring(0, MAX_TEXT_LENGTH - 3));
                sb.append("..."); //$NON-NLS-1$
            }
            else if (length > MAX_TEXT_LENGTH - 7)
            {
                sb.append(fileName);
            }
            else
            {
                sb.append(fileName);
                int segmentCount = path.segmentCount();
                if (segmentCount > 0)
                {
                    length += 7; // 7 chars are taken for " [...]"

                    sb.append("  ["); //$NON-NLS-1$

                    // Add first n segments that fit
                    int i = 0;
                    while (i < segmentCount && length < MAX_TEXT_LENGTH)
                    {
                        String segment = path.segment(i);
                        if (length + segment.length() < MAX_TEXT_LENGTH)
                        {
                            sb.append(segment);
                            sb.append(IPath.SEPARATOR);
                            length += segment.length() + 1;
                            i++;
                        }
                        else if (i == 0)
                        {
                            // append at least part of the first segment
                            sb.append(segment.substring(0, MAX_TEXT_LENGTH - length));
                            length = MAX_TEXT_LENGTH;
                            break;
                        }
                        else
                        {
                            break;
                        }
                    }

                    sb.append("..."); //$NON-NLS-1$

                    i = segmentCount - 1;
                    // Add last n segments that fit
                    while (i > 0 && length < MAX_TEXT_LENGTH)
                    {
                        String segment = path.segment(i);
                        if (length + segment.length() < MAX_TEXT_LENGTH)
                        {
                            sb.append(IPath.SEPARATOR);
                            sb.append(segment);
                            length += segment.length() + 1;
                            i--;
                        }
                        else
                        {
                            break;
                        }
                    }

                    sb.append("]"); //$NON-NLS-1$
                }
            }
        }
        return sb.toString();
    }

    /**
     * Fills the given menu with menu items for all windows.
     */
    public void fill(final Menu menu, int index)
    {
        if (window.getActivePage() == null || window.getActivePage().getPerspective() == null)
            return;

        if (getParent() instanceof MenuManager)
            ((MenuManager) getParent()).addMenuListener(menuListener);

        if (!dirty)
            return;

        // Get items.
        List<SnapshotHistoryService.Entry> lastHeaps = SnapshotHistoryService.getInstance().getVisitedEntries();

        // If no items return.
        if (lastHeaps.size() <= 0) { return; }

        final int menuIndex[] = new int[] { index };

        int i = 0;
        for (final SnapshotHistoryService.Entry entry : lastHeaps)
        {
            if (i == 10)
                continue;
            final int historyIndex = i;
            window.getWorkbench().getDisplay().syncExec(new Runnable()
            {
                public void run()
                {
                    final String text = calcText(historyIndex, entry);
                    final MenuItem mi = new MenuItem(menu, SWT.PUSH, menuIndex[0]);
                    ++menuIndex[0];
                    mi.setText(text);
                    mi.addSelectionListener(new SelectionAdapter()
                    {
                        public void widgetSelected(final SelectionEvent e)
                        {
                            try
                            {
                                IDE.openEditor(window.getActivePage(),
                                                new PathEditorInput(new Path(entry.getFilePath())),
                                                entry.getEditorId(), true);
                                if (window.getWorkbench().getIntroManager().getIntro() != null)
                                {
                                    // if this action was called with open
                                    // welcome page - set it to standby mode.
                                    window.getWorkbench().getIntroManager().setIntroStandby(
                                                    window.getWorkbench().getIntroManager().getIntro(), true);
                                }
                            }
                            catch (final PartInitException ex)
                            {
                                throw new RuntimeException(ex);
                            }
                        }
                    });
                }
            });
            i++;
        }
        new MenuItem(menu, SWT.SEPARATOR, i + index);
        dirty = false;
    }

    /**
     * Overridden to always return true and force dynamic menu building.
     */
    public boolean isDirty()
    {
        return dirty;
    }

    /**
     * Overridden to always return true and force dynamic menu building.
     */
    public boolean isDynamic()
    {
        return true;
    }

}

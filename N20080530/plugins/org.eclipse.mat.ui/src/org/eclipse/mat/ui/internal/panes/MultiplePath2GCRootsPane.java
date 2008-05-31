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
package org.eclipse.mat.ui.internal.panes;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.inspections.query.MultiplePath2GCRootsQuery;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.ImageHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.PlatformUI;


public class MultiplePath2GCRootsPane extends QueryResultPane
{
    private MultiplePath2GCRootsQuery.Grouping groupedBy;

    @Override
    public void initWithArgument(Object argument)
    {
        QueryResult queryResult = (QueryResult) argument;
        MultiplePath2GCRootsQuery.Tree tree = (MultiplePath2GCRootsQuery.Tree) queryResult.getSubject();
        groupedBy = tree.getGroupedBy();

        super.initWithArgument(argument);
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        addGroupingOptions(manager);
        super.contributeToToolBar(manager);
    }

    private void addGroupingOptions(IToolBarManager manager)
    {

        Action groupingAction = new EasyToolBarDropDown(
                        "Group result by...", //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.GROUPING),
                        this)
        {

            @Override
            public void contribute(PopupMenu menu)
            {
                for (MultiplePath2GCRootsQuery.Grouping group : MultiplePath2GCRootsQuery.Grouping.values())
                {
                    Action action = new GroupingAction(group);
                    action.setText(group.toString());
                    action.setImageDescriptor(ImageHelper.getImageDescriptor(group.getIcon()));
                    if (groupedBy == group)
                        action.setEnabled(false);
                    menu.add(action);
                }
            }
        };
        
        manager.add(groupingAction);

    }

    class GroupingAction extends Action
    {
        private MultiplePath2GCRootsQuery.Grouping target;

        public GroupingAction(MultiplePath2GCRootsQuery.Grouping group)
        {
            this.target = group;
        }

        public void run()
        {
            if (viewer.getResult().hasActiveFilter())
            {
                StringBuilder buf = new StringBuilder();
                buf.append("The original table is filtered. The WHOLE tree will be grouped.");

                MessageBox msg = new MessageBox(viewer.getControl().getShell(), SWT.OK | SWT.CANCEL);
                msg.setText("Info");
                msg.setMessage(buf.toString());

                if (msg.open() != SWT.OK)
                    return;
            }

            new Job(getText())
            {
                protected IStatus run(IProgressMonitor monitor)
                {
                    MultiplePath2GCRootsQuery.Tree original = (MultiplePath2GCRootsQuery.Tree) viewer.getResult().unwrap();

                    final QueryResult queryResult = new QueryResult(null, "multiple_path -groupBy " + target.name(),
                                    original.groupBy(target));

                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            deactivateViewer();

                            groupedBy = target;

                            RefinedResultViewer v = createViewer(queryResult);

                            activateViewer(v);
                        }

                    });

                    return Status.OK_STATUS;
                }
            }.schedule();
        }
    }
}

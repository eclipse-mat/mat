/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - help after changing grouping
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mat.internal.snapshot.inspections.MultiplePath2GCRootsQuery;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.panes.QueryResultPane;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;

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

        Action groupingAction = new EasyToolBarDropDown(Messages.TableResultPane_GroupResultBy, //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.GROUPING), this)
        {

            @Override
            public void contribute(PopupMenu menu)
            {
                for (MultiplePath2GCRootsQuery.Grouping group : MultiplePath2GCRootsQuery.Grouping.values())
                {
                    Action action = new GroupingAction(group);
                    action.setText(group.toString());
                    action.setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(group.getIcon()));
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
                buf.append(Messages.DominatorPane_WholeTreeWillBeGrouped);

                MessageBox msg = new MessageBox(viewer.getControl().getShell(), SWT.OK | SWT.CANCEL);
                msg.setText(Messages.DominatorPane_Info);
                msg.setMessage(buf.toString());

                if (msg.open() != SWT.OK)
                    return;
            }

            new Job(getText())
            {
                protected IStatus run(IProgressMonitor monitor)
                {
                    MultiplePath2GCRootsQuery.Tree original = (MultiplePath2GCRootsQuery.Tree) viewer.getResult()
                                    .unwrap();

                    final QueryResult queryResult = new QueryResult(QueryRegistry.instance().getQuery("merge_shortest_paths"), "merge_shortest_paths -groupBy " + target.name(), //$NON-NLS-1$ //$NON-NLS-2$
                                    original.groupBy(target));

                    top.getDisplay().asyncExec(new Runnable()
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

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
package org.eclipse.mat.ui.snapshot.panes;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mat.inspections.osgi.BundleRegistryQuery;
import org.eclipse.mat.inspections.osgi.BundleRegistryQuery.BundleTreeResult;
import org.eclipse.mat.inspections.osgi.BundleRegistryQuery.Grouping;
import org.eclipse.mat.inspections.osgi.model.OSGiModel;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.internal.panes.QueryResultPane;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.PopupMenu;

public class BundlesPane extends QueryResultPane
{
    private BundleRegistryQuery.Grouping groupBy;
    private OSGiModel model;

    private class TopLevelAction extends Action
    {
        private BundleRegistryQuery.Grouping target;

        public TopLevelAction(BundleRegistryQuery.Grouping topLevelBy)
        {
            super(topLevelBy.toString(), AS_CHECK_BOX);
            this.setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(topLevelBy.getIcon()));
            this.target = topLevelBy;
        }

        public void run()
        {
            if (!isChecked())// do not run the same action twice - selection
                // was not changed
                return;

            Job job = new AbstractPaneJob(getText(), BundlesPane.this)
            {
                protected IStatus doRun(IProgressMonitor monitor)
                {
                    IResultTree tree = null;
                    switch (target)
                    {
                        case NONE:
                            tree = BundleRegistryQuery.Factory.create(model);
                            break;
                        case BY_SERVICE:
                            tree = BundleRegistryQuery.Factory.servicesOnTop(model);
                            break;
                        case BY_EXTENSION_POINT:
                            tree = BundleRegistryQuery.Factory.extensionPointsOnTop(model);
                            break;
                    }

                    final QueryResult queryResult = new QueryResult(((BundlesPane)getPane()).srcQueryResult.getQuery(), "bundle_registry -groupBy " + target.name(),//$NON-NLS-1$
                                    tree);

                    top.getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            deactivateViewer();

                            groupBy = target;

                            RefinedResultViewer v = createViewer(queryResult);

                            activateViewer(v);
                        }

                    });

                    return Status.OK_STATUS;
                }
            };
            job.setUser(true);
            job.schedule();
        }
    }

    @Override
    public void initWithArgument(Object argument)
    {
        QueryResult queryResult = (QueryResult) argument;
        BundleTreeResult tree = (BundleTreeResult) queryResult.getSubject();
        groupBy = tree.getGroupBy();
        model = tree.getModel();

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
        Action groupingAction = new EasyToolBarDropDown(Messages.BundlesPane_GroupBy, //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.GROUPING), this)
        {
            @Override
            public void contribute(PopupMenu menu)
            {
                for (Grouping choice : Grouping.values())
                {
                    Action action = new TopLevelAction(choice);
                    action.setEnabled(choice != groupBy);
                    action.setChecked(choice == groupBy);
                    menu.add(action);
                }
            }
        };

        manager.add(groupingAction);

    }

}

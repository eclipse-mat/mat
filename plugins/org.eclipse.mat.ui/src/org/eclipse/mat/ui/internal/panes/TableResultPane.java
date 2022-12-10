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
 *    Andrew Johnson (IBM) - add icon
 *******************************************************************************/
package org.eclipse.mat.ui.internal.panes;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.TQuantize;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.internal.viewer.RefinedTableViewer;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;

public class TableResultPane extends QueryResultPane
{
	protected RefinedStructuredResult srcStructured;
    protected TQuantize.Target groupedBy;

    @Override
    public void initWithArgument(Object argument)
    {
        super.initWithArgument(argument);

        srcStructured = viewer.getResult();
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        if (srcStructured instanceof RefinedTable)
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
                Action action = new GroupingAction(null);
                if (groupedBy == null)
                    action.setEnabled(false);
                menu.add(action);

                action = new GroupingAction(TQuantize.Target.CLASSLOADER);
                if (groupedBy == TQuantize.Target.CLASSLOADER)
                    action.setEnabled(false);
                menu.add(action);

                action = new GroupingAction(TQuantize.Target.PACKAGE);
                if (groupedBy == TQuantize.Target.PACKAGE)
                    action.setEnabled(false);
                menu.add(action);
            }

        };

        manager.add(groupingAction);
    }
    
	public RefinedStructuredResult getSrcStructured()
	{
		return srcStructured;
	}

	public TQuantize.Target getGroupedBy()
	{
		return groupedBy;
	}

    class GroupingAction extends Action
    {
        private TQuantize.Target target;

        public GroupingAction(TQuantize.Target target)
        {
            this.target = target;

            if (target == null)
            {
                setText(Messages.TableResultPane_NoGrouping);
            }
            else
            {
                setText(target.getLabel());
                setImageDescriptor(MemoryAnalyserPlugin.getDefault().getImageDescriptor(target.getIcon()));
            }
        }

        public void run()
        {
            if (target == null)
            {
                deactivateViewer();
                groupedBy = target;
                activateViewer(new RefinedTableViewer(getQueryContext(), srcQueryResult, (RefinedTable) srcStructured));
            }
            else
            {
                if (srcStructured.hasActiveFilter())
                {
                    StringBuilder buf = new StringBuilder();
                    buf.append(Messages.TableResultPane_TableIsFiltered);

                    Column[] columns = srcStructured.getColumns();
                    Filter[] filters = srcStructured.getFilter();

                    for (int index = 0; index < filters.length; index++)
                    {
                        if (filters[index].isActive())
                        {
                            buf.append("\n").append( //$NON-NLS-1$
                                    MessageUtil.format(Messages.TableResultPane_onColumn,
                                            filters[index].getCriteria(),
                                            columns[index].getLabel()));
                        }
                    }

                    MessageBox msg = new MessageBox(viewer.getControl().getShell(), SWT.ICON_INFORMATION | SWT.OK | SWT.CANCEL);
                    msg.setText(Messages.TableResultPane_Info);
                    msg.setMessage(buf.toString());

                    if (msg.open() != SWT.OK)
                        return;
                }

                new Job(getText())
                {
                    protected IStatus run(IProgressMonitor monitor)
                    {
                        try
                        {
                            ISnapshot snapshot = (ISnapshot) getQueryContext().get(ISnapshot.class, null);
                            TQuantize t = TQuantize.defaultValueDistribution(snapshot, (RefinedTable) srcStructured,
                                            target);
                            final IResult subject = t.process(new ProgressMonitorWrapper(monitor));

                            top.getDisplay().asyncExec(new Runnable()
                            {
                                public void run()
                                {
                                    deactivateViewer();

                                    QueryResult qr = new QueryResult(srcQueryResult.getQuery(), srcQueryResult
                                                    .getCommand(), subject);

                                    groupedBy = target;

                                    RefinedResultViewer v = createViewer(qr);

                                    activateViewer(v);
                                }

                            });
                        }
                        catch (SnapshotException e)
                        {
                            return ErrorHelper.createErrorStatus(e);
                        }
                        return Status.OK_STATUS;
                    }
                }.schedule(Job.SHORT);
            }
        }
    }
}

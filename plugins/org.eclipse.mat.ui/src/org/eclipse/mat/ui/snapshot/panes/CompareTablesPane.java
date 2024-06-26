/*******************************************************************************
 * Copyright (c) 2010, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - set operations
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.mat.internal.snapshot.inspections.CompareTablesQuery.ComparedColumn;
import org.eclipse.mat.internal.snapshot.inspections.CompareTablesQuery.Mode;
import org.eclipse.mat.internal.snapshot.inspections.CompareTablesQuery.Operation;
import org.eclipse.mat.internal.snapshot.inspections.CompareTablesQuery.TableComparisonResult;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.refined.Filter;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.panes.QueryResultPane;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.PopupMenu;

public class CompareTablesPane extends QueryResultPane
{

    public enum DiffOption
    {
        ABSOLUTE(Messages.CompareTablesPane_AbsoluteValues), DIFF_TO_BASE(
                        Messages.CompareTablesPane_DifferenceToBaseTable), DIFF_RATIO_TO_BASE(
                                        Messages.CompareTablesPane_PercentageDifferenceToBaseTable), DIFF_TO_PREV(
                                                        Messages.CompareTablesPane_DifferenceToPrecedingTable), DIFF_RATIO_TO_PREV(
                                                                        Messages.CompareTablesPane_PercentageDifferenceToPrecedingTable);

        String label;

        private DiffOption(String label)
        {
            this.label = label;
        }

        public String toString()
        {
            return label;
        }
    }

    private DiffOption diffOption;


    public enum SetopOption
    {
        NONE(Messages.CompareTablesPane_Setop_None),
        ALL(Messages.CompareTablesPane_Setop_All),
        INTERSECTION(Messages.CompareTablesPane_Setop_Intersection),
        UNION(Messages.CompareTablesPane_Setop_Union),
        SYMMETRIC_DIFFERENCE(Messages.CompareTablesPane_Setop_SymmetricDifference),
        DIFFERENCE(Messages.CompareTablesPane_Setop_Difference),
        REVERSE_DIFFERENCE(Messages.CompareTablesPane_Setop_ReverseDifference);

        String label;

        private SetopOption(String label)
        {
            this.label = label;
        }

        public String toString()
        {
            return label;
        }
    }

    private SetopOption setopOption;

    @Override
    public void initWithArgument(Object argument)
    {
        super.initWithArgument(argument);

        IResult subject = ((QueryResult) argument).getSubject();
        if (subject instanceof TableComparisonResult)
        {
            Mode mode = ((TableComparisonResult) subject).getMode();
            switch (mode)
            {
                case ABSOLUTE:
                    diffOption = DiffOption.ABSOLUTE;
                    break;
                case DIFF_TO_FIRST:
                    diffOption = DiffOption.DIFF_TO_BASE;
                    break;
                case DIFF_TO_PREVIOUS:
                    diffOption = DiffOption.DIFF_TO_PREV;
                    break;
                case DIFF_RATIO_TO_FIRST:
                    diffOption = DiffOption.DIFF_RATIO_TO_BASE;
                    break;
                case DIFF_RATIO_TO_PREVIOUS:
                    diffOption = DiffOption.DIFF_RATIO_TO_PREV;
                    break;
                default:
                    break;
            }

            Operation op = ((TableComparisonResult) subject).getOperation();
            switch (op)
            {
                case NONE:
                    setopOption = SetopOption.NONE;
                    break;
                case ALL:
                    setopOption = SetopOption.ALL;
                    break;
                case INTERSECTION:
                    setopOption = SetopOption.INTERSECTION;
                    break;
                case UNION:
                    setopOption = SetopOption.UNION;
                    break;
                case SYMMETRIC_DIFFERENCE:
                    setopOption = SetopOption.SYMMETRIC_DIFFERENCE;
                    break;
                case DIFFERENCE:
                    setopOption = SetopOption.DIFFERENCE;
                    break;
                case REVERSE_DIFFERENCE:
                    setopOption = SetopOption.REVERSE_DIFFERENCE;
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        addDiffOptions(manager);

        addSetopOptions(manager);

        addSelectColumns(manager);

        manager.add(new Separator());
        super.contributeToToolBar(manager);

    }

    private void addSelectColumns(IToolBarManager manager)
    {
        final IResult result = viewer.getQueryResult().getSubject();
        if (result instanceof TableComparisonResult)
        {
            Action selectColumnsAction = new EasyToolBarDropDown(Messages.CompareTablesPane_SelectDisplayedColumnsTooltip, //
                            MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SELECT_COLUMN), this) {

                @Override
                public void contribute(PopupMenu menu)
                {
                    List<ComparedColumn> columns = ((TableComparisonResult) result).getComparedColumns();
                    for (ComparedColumn comparedColumn : columns)
                    {
                        menu.add(new SelectColumnAction(comparedColumn));
                    }

                }
            };

            manager.add(selectColumnsAction);
        }

    }

    private void addDiffOptions(IToolBarManager manager)
    {
        Action diffOptionAction = new EasyToolBarDropDown(Messages.CompareTablesPane_ChooseDiffOptionTooltip, //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.GROUPING), this) {
            @Override
            public void contribute(PopupMenu menu)
            {
                for (DiffOption opt : DiffOption.values())
                {
                    Action action = new DiffOptionAction(opt);
                    action.setEnabled(opt != diffOption);
                    action.setChecked(opt == diffOption);
                    menu.add(action);
                }
            }
        };

        manager.add(diffOptionAction);
    }

    private void addSetopOptions(IToolBarManager manager)
    {
        final IResult result = viewer.getQueryResult().getSubject();
        // Test whether set operations make sense
        if (result instanceof TableComparisonResult)
        {
            TableComparisonResult tcr = (TableComparisonResult)result;
            Operation op1 = tcr.getOperation();
            if (op1 == Operation.NONE)
            {
                // Try setting the operation
                Operation op2 = Operation.ALL;
                tcr.setOperation(op2);
                Operation op3 = tcr.getOperation();
                // Restore
                tcr.setOperation(op1);
                // If we can't change the mode, don't give the option to change
                if (op3 != op2)
                    return;
            }
        }
        Action setopOptionAction = new EasyToolBarDropDown(Messages.CompareTablesPane_ChooseOperation, //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SET_SYMMETRIC_DIFFERENCE), this)
        {
            @Override
            public void contribute(PopupMenu menu)
            {
                for (SetopOption opt : SetopOption.values())
                {
                    Action action = new SetopOptionAction(opt);
                    action.setEnabled(opt != setopOption);
                    action.setChecked(opt == setopOption);
                    switch (opt)
                    {
                        case UNION:
                            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SET_UNION));
                            break;
                        case INTERSECTION:
                            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SET_INTERSECTION));
                            break;
                        case SYMMETRIC_DIFFERENCE:
                            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SET_SYMMETRIC_DIFFERENCE));
                            break;
                        case DIFFERENCE:
                            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SET_DIFFERENCE_A));
                            break;
                        case REVERSE_DIFFERENCE:
                            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.SET_DIFFERENCE_B));
                            break;
                        case NONE:
                        case ALL:
                            break;
                    }
                    menu.add(action);
                }
            }
        };

        manager.add(setopOptionAction);
    }

    /**
     * Rebuild the tree / table but preserve existing
     * sort order and filters.
     * @param queryResult
     */
    private void rebuildViewer(final QueryResult queryResult)
    {
        int sortIndex = viewer.getResult().getSortColumn();
        SortDirection direction = viewer.getResult().getSortDirection();
        Column sortCol;
        if (sortIndex >= 0)
        {
            sortCol = viewer.getResult().getColumns()[sortIndex];
        }
        else
        {
            sortCol = null;
        }
        Filter filters[];
        Column cols[];
        boolean hasFilters = viewer.getResult().hasActiveFilter();
        if (hasFilters)
        {
            filters = viewer.getResult().getFilter();
            cols = viewer.getResult().getColumns();
        }
        else
        {
            filters = null;
            cols = null;
        }
        deactivateViewer();
        RefinedResultViewer v = createViewer(queryResult);
        if (sortCol != null)
        {
            // Find the matching sort column
            for (Column col : v.getResult().getColumns())
            {
                if (col.getLabel().equals(sortCol.getLabel())
                    && Objects.equals(col.getFormatter(), sortCol.getFormatter())
                    && Objects.equals(col.getComparator(), sortCol.getComparator())
                   )
                {
                    // Set the corresponding sort column
                    v.getResult().setSortOrder(col, direction);
                    break;
                }
            }
        }
        if (hasFilters)
        {
            Filter filters2[] = v.getResult().getFilter();
            Column cols2[] = v.getResult().getColumns();
            for (int i = 0; i < filters.length; ++i)
            {
                if (filters[i].isActive())
                {
                    for (int j = 0; j < filters2.length; ++j)
                    {
                        // Match on filters and the column
                        if (filters[i].getLabel().equals(filters2[j].getLabel())
                            && filters[i].getClass() == filters2[j].getClass()
                            && cols[i].getLabel().equals(cols2[j].getLabel())
                            && Objects.equals(cols[i].getFormatter(), cols2[j].getFormatter())
                           )
                        {
                            filters2[j].setCriteria(filters[i].getCriteria());
                            v.getResult().filterChanged(filters[j]);
                        }
                    }
                }
            }
        }
        activateViewer(v);
    }

    private class DiffOptionAction extends Action
    {
        private DiffOption diffOption;

        private DiffOptionAction(DiffOption diffOption)
        {
            super(diffOption.toString(), AS_CHECK_BOX);
            this.diffOption = diffOption;
        }

        @Override
        public void run()
        {
            // do not run the same action twice - selection was not changed
            if (!isChecked()) return;

            Mode mode = Mode.ABSOLUTE;
            switch (diffOption)
            {
                case DIFF_TO_PREV:
                    mode = Mode.DIFF_TO_PREVIOUS;

                    break;
                case DIFF_TO_BASE:
                    mode = Mode.DIFF_TO_FIRST;

                    break;

                case DIFF_RATIO_TO_PREV:
                    mode = Mode.DIFF_RATIO_TO_PREVIOUS;

                    break;
                case DIFF_RATIO_TO_BASE:
                    mode = Mode.DIFF_RATIO_TO_FIRST;

                    break;

                default:
                    break;
            }

            IResult result = viewer.getQueryResult().getSubject();
            if (result instanceof TableComparisonResult)
            {
                ((TableComparisonResult) result).setMode(mode);
            }

            final QueryResult queryResult = new QueryResult(viewer.getQueryResult().getQuery(), viewer.getQueryResult().getCommand(), result);

            new Job(getText()) {
                protected IStatus run(IProgressMonitor monitor)
                {

                    if (!top.isDisposed()) top.getDisplay().asyncExec(new Runnable() {
                        public void run()
                        {
                            if (top.isDisposed())
                                return;
                            CompareTablesPane.this.diffOption = diffOption;
                            rebuildViewer(queryResult);
                        }
                    });
                    return Status.OK_STATUS;
                }
            }.schedule();
        }
    }

    private class SelectColumnAction extends Action
    {
        ComparedColumn column;

        public SelectColumnAction(ComparedColumn column)
        {
            super(column.getDescription().getLabel(), AS_CHECK_BOX);
            this.column = column;
            setChecked(column.isDisplayed());
        }

        @Override
        public void run()
        {
            column.setDisplayed(!column.isDisplayed());

            IResult result = viewer.getQueryResult().getSubject();
            if (result instanceof TableComparisonResult)
            {
                ((TableComparisonResult) result).updateColumns();
            }

            final QueryResult queryResult = new QueryResult(viewer.getQueryResult().getQuery(), viewer.getQueryResult().getCommand(), result);

            new Job(getText()) {
                protected IStatus run(IProgressMonitor monitor)
                {

                    if (!top.isDisposed()) top.getDisplay().asyncExec(new Runnable() {
                        public void run()
                        {
                            if (top.isDisposed())
                                return;
                            rebuildViewer(queryResult);
                        }
                    });
                    return Status.OK_STATUS;
                }
            }.schedule();
        }
    }

    private class SetopOptionAction extends Action
    {
        private SetopOption setopOption;

        private SetopOptionAction(SetopOption setopOption)
        {
            super(setopOption.toString(), AS_CHECK_BOX);
            this.setopOption = setopOption;
        }

        @Override
        public void run()
        {
            // do not run the same action twice - selection was not changed
            if (!isChecked())
                return;

            Operation op = Operation.NONE;
            switch (setopOption)
            {
                case ALL:
                    op = Operation.ALL;
                    break;
                case INTERSECTION:
                    op = Operation.INTERSECTION;
                    break;
                case UNION:
                    op = Operation.UNION;
                    break;
                case SYMMETRIC_DIFFERENCE:
                    op = Operation.SYMMETRIC_DIFFERENCE;
                    break;
                case DIFFERENCE:
                    op = Operation.DIFFERENCE;
                    break;
                case REVERSE_DIFFERENCE:
                    op = Operation.REVERSE_DIFFERENCE;
                    break;
                default:
                    break;
            }

            IResult result = viewer.getQueryResult().getSubject();
            if (result instanceof TableComparisonResult)
            {
                ((TableComparisonResult) result).setOperation(op);
            }

            final QueryResult queryResult = new QueryResult(viewer.getQueryResult().getQuery(), viewer.getQueryResult().getCommand(), result);

            new Job(getText())
            {
                protected IStatus run(IProgressMonitor monitor)
                {

                    if (!top.isDisposed()) top.getDisplay().asyncExec(new Runnable()
                    {
                        public void run()
                        {
                            if (top.isDisposed())
                                return;
                            CompareTablesPane.this.setopOption = setopOption;
                            rebuildViewer(queryResult);
                        }
                    });
                    return Status.OK_STATUS;
                }
            }.schedule();
        }
    }
}

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
package org.eclipse.mat.ui.snapshot.panes;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.inspections.Path2GCRootsQuery;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.internal.panes.QueryResultPane;
import org.eclipse.mat.ui.internal.viewer.RefinedTreeViewer;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;


public class Path2GCRootsPane extends QueryResultPane
{
    private Button nextPathButton;
    private Label statusLabel;

    @Override
    public void createPartControl(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.TOP);

        // composite layout
        GridLayout ly = new GridLayout(3, false);
        ly.marginHeight = ly.marginWidth = ly.verticalSpacing = 0;
        composite.setLayout(ly);

        // label Status:
        Label label = new Label(composite, SWT.NONE);
        label.setText(Messages.Path2GCRootsPane_Status);
        GridDataFactory.fillDefaults().grab(false, false).indent(5, 3).applyTo(label);

        // label actual text
        statusLabel = new Label(composite, SWT.NULL);
        GridDataFactory.fillDefaults().grab(true, false).indent(5, 3).applyTo(statusLabel);

        // next button
        nextPathButton = new Button(composite, SWT.NONE);
        nextPathButton.setText(Messages.Path2GCRootsPane_FetchNextPaths);
        nextPathButton.addSelectionListener(new SelectionAdapter()
        {
            public void widgetSelected(SelectionEvent event)
            {
                if (viewer == null || viewer.getControl().isDisposed())
                    return;

                Path2GCRootsQuery.Tree tree = (Path2GCRootsQuery.Tree) viewer.getResult().unwrap();
                new ReadNextPathJob(Path2GCRootsPane.this, tree, 30).schedule();
            }
        });

        // viewer area
        Composite viewerArea = new Composite(composite, SWT.TOP);
        viewerArea.setLayout(new FillLayout());
        GridDataFactory.fillDefaults().grab(true, true).span(3, 1).minSize(100, 100).applyTo(viewerArea);
        super.createPartControl(viewerArea);
    }

    @Override
    public void initWithArgument(Object argument)
    {
        super.initWithArgument(argument);
        updateStatusLabel();
    }

    private void updateStatusLabel()
    {
        Path2GCRootsQuery.Tree tree = (Path2GCRootsQuery.Tree) viewer.getResult().unwrap();
        String message = tree.morePathsAvailable() ? Messages.Path2GCRootsPane_FoundSoFar : Messages.Path2GCRootsPane_NoMorePaths;
        String formatted = MessageFormat.format(message, tree.getNumberOfPaths());
        statusLabel.setText(formatted);
    }

    private class ReadNextPathJob extends AbstractPaneJob implements ISchedulingRule
    {
        private Path2GCRootsQuery.Tree tree;
        private int noToFetch;

        public ReadNextPathJob(AbstractEditorPane pane, Path2GCRootsQuery.Tree root, int noToFetch)
        {
            super(Messages.Path2GCRootsPane_ReadingNext, pane);
            this.tree = root;
            this.noToFetch = noToFetch;

            setUser(true);
            setRule(this);
        }

        @Override
        protected IStatus doRun(IProgressMonitor monitor)
        {
            PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
            {
                public void run()
                {
                    nextPathButton.setEnabled(false);
                }
            });

            monitor.beginTask(Messages.Path2GCRootsPane_FetchingNext, noToFetch);

            try
            {
                for (int count = 0; count < noToFetch && tree.morePathsAvailable(); count++)
                {
                    final List<?> ancestors = tree.addNextPath();

                    if (ancestors != null)
                    {
                        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                        {
                            public void run()
                            {
                                ((RefinedTreeViewer) viewer).refresh(ancestors);
                                updateStatusLabel();
                                viewer.getControl().setFocus();
                            }
                        });
                    }

                    if (monitor.isCanceled())
                        break;
                }
            }
            catch (SnapshotException e)
            {
                return ErrorHelper.createErrorStatus(e);
            }
            finally
            {
                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        if (tree.morePathsAvailable())
                            nextPathButton.setEnabled(true);
                    }
                });

            }

            return Status.OK_STATUS;
        }

        public boolean contains(ISchedulingRule rule)
        {
            return isConflicting(rule);
        }

        public boolean isConflicting(ISchedulingRule rule)
        {
            return rule instanceof ReadNextPathJob && ((ReadNextPathJob) rule).belongsTo(getPane());
        }

    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.viewer;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.ContextDerivedData.DerivedOperation;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.refined.RefinedStructuredResult.ICalculationProgress;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.viewer.RefinedResultViewer.ControlItem;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.swt.widgets.Item;

/* package */abstract class DerivedDataJob extends Job implements ICalculationProgress
{

    protected RefinedResultViewer viewer;
    protected ContextProvider provider;
    protected DerivedOperation operation;
    protected List<?> subjectItems;

    protected DerivedDataJob(RefinedResultViewer viewer, //
                    ContextProvider provider, //
                    DerivedOperation operation, //
                    List<?> subjectItems)
    {
        super(Messages.DerivedDataJob_CalculatingRetainedSizes);
        this.viewer = viewer;
        this.provider = provider;
        this.operation = operation;
        this.subjectItems = subjectItems;

        setUser(true);
        setPriority(Job.LONG);
    }

    @Override
    protected final IStatus run(IProgressMonitor monitor)
    {
        try
        {
            viewer.result.calculate(provider, operation, subjectItems, this, new ProgressMonitorWrapper(monitor));

            publish();

            return Status.OK_STATUS;
        }
        catch (IProgressListener.OperationCanceledException e)
        {
            return Status.CANCEL_STATUS;
        }
        catch (SnapshotException e)
        {
            return ErrorHelper.createErrorStatus(e);
        }
    }

    protected abstract void publish();

    // //////////////////////////////////////////////////////////////
    // Calculate & update a selection of objects
    // //////////////////////////////////////////////////////////////

    /* package */static final class OnSelection extends DerivedDataJob
    {
        List<Item> widgetItems;
        ArrayInt computed = new ArrayInt();

        public OnSelection(RefinedResultViewer viewer, //
                        ContextProvider provider, //
                        DerivedOperation operation, //
                        List<?> subjectItems, //
                        List<Item> widgetItems)
        {
            super(viewer, provider, operation, subjectItems);
            this.widgetItems = widgetItems;
        }

        public void done(int index, Object row)
        {
            computed.add(index);

            if (computed.size() > 5)
                publish();
        }

        @Override
        protected void publish()
        {
            final int[] done = computed.toArray();
            computed.clear();

            viewer.getControl().getDisplay().asyncExec(new Runnable()
            {
                public void run()
                {
                    for (int index : done)
                    {
                        Item item = widgetItems.get(index);
                        if (!item.isDisposed())
                            viewer.applyTextAndImage(item, item.getData());
                        // viewer.adapter.clear(item, false);
                    }
                }
            });
        }

    }

    /* package */static final class OnFullList extends DerivedDataJob
    {
        Item parent;
        ControlItem ctrl;
        ArrayInt computed = new ArrayInt();

        public OnFullList(RefinedResultViewer viewer, //
                        ContextProvider provider, //
                        DerivedOperation operation, //
                        List<?> subjectItems, //
                        Item parent, //
                        ControlItem ctrl)
        {
            super(viewer, provider, operation, subjectItems);
            this.parent = parent;
            this.ctrl = ctrl;
        }

        public void done(int index, Object row)
        {
            if (index < ctrl.totals.getVisibleItems())
                computed.add(index);

            if (computed.size() > 5)
                publish();
        }

        @Override
        protected void publish()
        {
            if (computed.isEmpty())
                return;

            final int[] done = computed.toArray();
            computed.clear();

            viewer.getControl().getDisplay().asyncExec(new Runnable()
            {
                public void run()
                {
                    for (int ii = 0; ii < done.length; ii++)
                    {
                        // correct indexes for root elements -> filter row
                        int index = parent == null ? done[ii] + 1 : done[ii];
                        Item item = viewer.adapter.getItem(parent, index);
                        viewer.applyTextAndImage(item, item.getData());
                        // viewer.adapter.clear(parent, index, false);
                    }
                }
            });
        }
    }
}

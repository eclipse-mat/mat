/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - fix deprecated method
 *******************************************************************************/
package org.eclipse.mat.ui.internal.browser;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public class QueryContextHelp extends PopupDialog
{
    IAnnotatedObjectDescriptor query;

    Rectangle bounds;
    StyledText helpText;
    IQueryContext queryContext;

    public QueryContextHelp(Shell shell, IAnnotatedObjectDescriptor queryDescriptor, Rectangle helpBounds)
    {
        this(shell, queryDescriptor, null, helpBounds);
    }

    public QueryContextHelp(Shell parent, IAnnotatedObjectDescriptor query, IQueryContext queryContext, Rectangle bounds)
    {
        super(parent, /* HOVER_SHELLSTYLE */SWT.NO_FOCUS | SWT.TOOL, false, false, false, false, false, null, null);
        this.query = query;
        this.bounds = bounds;
        this.queryContext = queryContext;
    }

    @Override
    protected Point getInitialLocation(Point initialSize)
    {
        return new Point(bounds.x, bounds.y);
    }

    @Override
    protected Point getInitialSize()
    {
        return new Point(bounds.width, bounds.height);
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite composite = (Composite) super.createDialogArea(parent);
        GridLayoutFactory.fillDefaults().margins(2, 2).applyTo(composite);

        // ugly: the additional composite w/ the form layout is necessary
        // because otherwise the word-wrap flag of the styled text is ignored
        Composite textComposite = new Composite(composite, SWT.NONE);
        textComposite.setLayout(new FormLayout());

        List<StyleRange> ranges = new ArrayList<StyleRange>();

        StringBuilder buf = new StringBuilder(128);
        if (query.getHelp() != null)
            buf.append(query.getHelp());

        boolean first = true;

        for (ArgumentDescriptor argument : query.getArguments())
        {
            String help = argument.getHelp();

            if (help != null)
            {
                if (first)
                {
                    first = false;
                    String heading = "\n\n" + Messages.QueryContextHelp_Arguments + "\n";//$NON-NLS-1$ //$NON-NLS-2$
                    ranges.add(new StyleRange(buf.length(), heading.length(), null, null, SWT.BOLD));
                    buf.append(heading);
                }

                buf.append("\n"); //$NON-NLS-1$
                String name = argument.getFlag() != null ? "-" + argument.getFlag() : argument.getName();//$NON-NLS-1$
                ranges.add(new StyleRange(buf.length(), name.length(), null, null, SWT.BOLD));
                buf.append(name).append("\n");//$NON-NLS-1$
                buf.append(help);
            }
        }
        
        if (queryContext != null)
        {
            String heading = "\n\n" + Messages.QueryContextHelp_Usage + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
            ranges.add(new StyleRange(buf.length(), heading.length(), null, null, SWT.BOLD));
            buf.append(heading);
            String usage = query.getUsage(queryContext);
            buf.append("\n"); //$NON-NLS-1$
            buf.append(usage);
        }

        helpText = new StyledText(textComposite, SWT.WRAP | SWT.READ_ONLY);
        helpText.setText(buf.toString());
        helpText.setStyleRanges(ranges.toArray(new StyleRange[0]));

        Point p = helpText.computeSize(bounds.width - 10, SWT.DEFAULT);
        bounds.height = p.y + 10;

        helpText.setLayoutData(new FormData(bounds.width, bounds.height));
        return textComposite;
    }

    public IAnnotatedObjectDescriptor getQuery()
    {
        return query;
    }

    @Override
    protected void configureShell(Shell shell)
    {
        GridLayoutFactory.fillDefaults().margins(0, 0).spacing(5, 5).applyTo(shell);
    }
 
    public void resize(Rectangle rectangle)
    {
        boolean resizing = false;
        if (bounds.x != rectangle.x || bounds.y != rectangle.y)
        {
            this.bounds.x = rectangle.x;
            this.bounds.y = rectangle.y;
            getShell().setLocation(bounds.x, bounds.y);
            resizing = true;
        }

        if (bounds.width != rectangle.width)
        {
            this.bounds.width = rectangle.width;
            Point p = helpText.computeSize(bounds.width - 10, SWT.DEFAULT);
            bounds.height = p.y + 10;

            helpText.setLayoutData(new FormData(bounds.width, bounds.height));
            getShell().setSize(bounds.width, bounds.height);
            resizing = true;
        }

        if (resizing)
        {
            getShell().layout();
        }
    }
    
    /**
     * Tidy up
     */
    public boolean close()
    {
        // Fix a memory leak back to the snapshot via the query context
        if (helpText != null) 
        {
            // Do we need to do this?
            helpText.dispose();
            helpText = null;
        }
        queryContext = null;
        query = null;
        return super.close();
    }
}

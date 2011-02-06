/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.actions;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.query.registry.CategoryDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.snapshot.ImageHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

public class OpenIconAssistAction extends Action implements IWorkbenchWindowActionDelegate
{
    private IWorkbenchWindow window;
    private Shell shell;

    public void run()
    {
        openIconAssist(PlatformUI.getWorkbench().getActiveWorkbenchWindow());
    }

    public void run(IAction action)
    {
        openIconAssist(window);
    }

    private void openIconAssist(final IWorkbenchWindow window)
    {
        shell = window.getShell();
        PopupTable table = new PopupTable(shell, SWT.RIGHT);
        Rectangle rectangle = shell.getBounds();
        int x = rectangle.x + rectangle.width - 315;
        int y = rectangle.y + rectangle.height - rectangle.height / 2;
        table.open(new Rectangle(x, y, 305, rectangle.height / 2));
    }

    public void selectionChanged(IAction action, ISelection selection)
    {}

    public void dispose()
    {}

    public void init(IWorkbenchWindow window)
    {
        this.window = window;
    }

    // //////////////////////////////////////////////////////////////
    // handler
    // //////////////////////////////////////////////////////////////

    public static class Handler extends AbstractHandler
    {

        public Handler()
        {}

        public Object execute(ExecutionEvent executionEvent)
        {
            new OpenIconAssistAction().run();
            return null;
        }
    }

    // //////////////////////////////////////////////////////////////
    // internal helpers
    // //////////////////////////////////////////////////////////////

    private static class IconAssist
    {
        public List<Icon> icons = new LinkedList<Icon>();

        public IconAssist()
        {
            icons.add(new Icon(null, Messages.OpenIconAssistAction_HeapObjects));
            icons.add(new Icon(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CLASS),
                            Messages.OpenIconAssistAction_InstancesGroupedByClass));
            icons.add(new Icon(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CLASS_MIXED),
                            Messages.OpenIconAssistAction_MixedInstancesGroupedByClass));
            icons.add(new Icon(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CLASS_OLD),
                            Messages.OpenIconAssistAction_OldInstancesGroupedByClass));
            icons.add(new Icon(ImageHelper.getImageDescriptor(ImageHelper.Type.CLASS_INSTANCE),
                            Messages.OpenIconAssistAction_ClassObject));
            icons.add(new Icon(ImageHelper.getImageDescriptor(ImageHelper.Type.CLASSLOADER_INSTANCE),
                            Messages.OpenIconAssistAction_ClassLoaderObject));
            icons.add(new Icon(ImageHelper.getImageDescriptor(ImageHelper.Type.ARRAY_INSTANCE),
                            Messages.OpenIconAssistAction_ArrayObject));
            icons.add(new Icon(ImageHelper.getImageDescriptor(ImageHelper.Type.OBJECT_INSTANCE),
                            Messages.OpenIconAssistAction_OtherObject));
            icons.add(new Icon(ImageHelper.getImageDescriptor(ImageHelper.Type.PACKAGE),
                            Messages.OpenIconAssistAction_Package));

            icons.add(new Icon(null, Messages.OpenIconAssistAction_IndicatorsAdded));
            icons.add(new Icon(ImageHelper.getInboundImageDescriptor(ImageHelper.Type.OBJECT_INSTANCE),
                            Messages.OpenIconAssistAction_ReferenceAbove));
            icons.add(new Icon(ImageHelper.getOutboundImageDescriptor(ImageHelper.Type.OBJECT_INSTANCE),
                            Messages.OpenIconAssistAction_ReferenceBelow));

            icons.add(new Icon(ImageHelper.decorate(ImageHelper.Type.OBJECT_INSTANCE, MemoryAnalyserPlugin
                            .getImageDescriptor("icons/decorations/gc_root.gif")), //$NON-NLS-1$
                            Messages.OpenIconAssistAction_ThisObjectIsGCRoot));

            icons.add(new Icon(null, Messages.OpenIconAssistAction_QueryViews));
            icons.addAll(getQueryIcons());

            icons.add(new Icon(null, Messages.OpenIconAssistAction_Others));
            icons.add(new Icon(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.QUERY),
                            Messages.OpenIconAssistAction_ExecuteQuery));

        }

        private List<Icon> getQueryIcons()
        {
            LinkedList<CategoryDescriptor> categories = new LinkedList<CategoryDescriptor>();
            categories.add(QueryRegistry.instance().getRootCategory());

            List<Icon> answer = new ArrayList<Icon>();

            while (!categories.isEmpty())
            {
                CategoryDescriptor cat = categories.removeFirst();
                categories.addAll(cat.getSubCategories());

                // extrawurst: show only category, if all entries are the same
                // icon

                URL icon = null;

                boolean isFirst = true;
                boolean itemsWithDifferentIcons = false;

                for (QueryDescriptor query : cat.getQueries())
                {
                    URL thisIcon = query.getIcon();

                    if (isFirst)
                    {
                        icon = thisIcon;
                    }
                    else
                    {
                        try
                        {
                            // Use URI for comparisons to avoid blocking
                            // operation
                            if (icon == null ? thisIcon != null : thisIcon == null
                                            || !icon.toURI().equals(thisIcon.toURI()))
                            {
                                itemsWithDifferentIcons = true;
                                break;
                            }
                        }
                        catch (URISyntaxException e)
                        {
                            itemsWithDifferentIcons = true;
                            break;
                        }
                    }

                    isFirst = false;
                }

                if (itemsWithDifferentIcons)
                {
                    for (QueryDescriptor query : cat.getQueries())
                    {
                        icon = query.getIcon();
                        if (icon != null)
                        {
                            String categoryName = cat.getFullName();
                            String label = categoryName != null ? categoryName + " / " + query.getName() : query//$NON-NLS-1$
                                            .getName();
                            answer.add(new Icon(MemoryAnalyserPlugin.getDefault().getImageDescriptor(icon), label));
                        }
                    }
                }
                else if (icon != null)
                {
                    answer.add(new Icon(MemoryAnalyserPlugin.getDefault().getImageDescriptor(icon), cat.getFullName()));
                }
            }

            Collections.sort(answer, new Comparator<Icon>()
            {
                public int compare(Icon o1, Icon o2)
                {
                    return o1.description.compareTo(o2.description);
                }
            });

            return answer;
        }

        public List<Icon> getIcons()
        {
            return icons;
        }
    }

    private static class Icon
    {
        private ImageDescriptor image;
        private String description;

        public Icon(ImageDescriptor image, String description)
        {
            setImageDescriptor(image);
            setDescription(description);
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public ImageDescriptor getImageDescriptor()
        {
            return image;
        }

        public void setImageDescriptor(ImageDescriptor image)
        {
            this.image = image;
        }
    }

    private static class PopupTable
    {
        private Table table;
        Shell shell;

        public PopupTable(Shell parent, int style)
        {
            shell = new Shell(parent, checkStyle(style));
            TableViewer viewer = new TableViewer(shell, SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL);
            table = viewer.getTable();
            TableColumn tc1 = new TableColumn(table, SWT.CENTER);
            TableColumn tc2 = new TableColumn(table, SWT.LEFT);
            tc1.setWidth(25);
            tc2.setWidth(270);
            table.setLinesVisible(true);
            viewer.setContentProvider(new TableContentProvider());
            viewer.setLabelProvider(new IconsLabelProvider(viewer.getTable().getFont()));
            viewer.setInput(new IconAssist().getIcons());

            // close dialog if user selects outside of the shell
            shell.addListener(SWT.Deactivate, new Listener()
            {
                public void handleEvent(Event e)
                {
                    shell.setVisible(false);
                }
            });

            // resize shell when table resizes
            shell.addControlListener(new ControlListener()
            {
                public void controlMoved(ControlEvent e)
                {}

                public void controlResized(ControlEvent e)
                {
                    Rectangle shellSize = shell.getClientArea();
                    table.setSize(shellSize.width, shellSize.height);
                }
            });
        }

        private static int checkStyle(int style)
        {
            int mask = SWT.LEFT_TO_RIGHT | SWT.RIGHT_TO_LEFT;
            return style & mask;
        }

        public void open(Rectangle rect)
        {
            shell.setBounds(new Rectangle(rect.x, rect.y, rect.width + 10, rect.height));
            shell.open();
            table.setFocus();

            Display display = shell.getDisplay();
            while (!shell.isDisposed() && shell.isVisible())
            {
                if (!display.readAndDispatch())
                    display.sleep();
            }

        }
    }

    private static class TableContentProvider implements IStructuredContentProvider
    {
        Object[] elements;

        public Object[] getElements(Object inputElement)
        {
            return elements;
        }

        public void dispose()
        {}

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
        {
            if (newInput instanceof Collection<?>)
            {
                this.elements = ((Collection<?>) newInput).toArray();
            }
            else
            {
                this.elements = (Object[]) newInput;
            }
        }
    }

    private static class IconsLabelProvider implements ITableLabelProvider, IFontProvider
    {

        Font boldFont;

        public IconsLabelProvider(Font defaultFont)
        {
            FontDescriptor fontDescriptor = FontDescriptor.createFrom(defaultFont);
            fontDescriptor = fontDescriptor.setStyle(SWT.BOLD);
            this.boldFont = fontDescriptor.createFont(Display.getDefault());
        }

        public Image getColumnImage(Object element, int columnIndex)
        {
            Icon icon = (Icon) element;

            switch (columnIndex)
            {
                case 0:
                    return MemoryAnalyserPlugin.getDefault().getImage(icon.getImageDescriptor());

                case 1:
                    return null;
            }
            return null;
        }

        public String getColumnText(Object element, int columnIndex)
        {
            Icon icon = (Icon) element;
            switch (columnIndex)
            {
                case 0:
                    return "";//$NON-NLS-1$

                case 1:
                    return icon.getDescription();
            }
            return "";//$NON-NLS-1$
        }

        public void addListener(ILabelProviderListener listener)
        {}

        public void dispose()
        {
            boldFont.dispose();
        }

        public boolean isLabelProperty(Object element, String property)
        {
            return false;
        }

        public void removeListener(ILabelProviderListener listener)
        {}

        public Font getFont(Object element)
        {
            return (((Icon) element).getImageDescriptor() == null) ? boldFont : null;
        }

    }
}

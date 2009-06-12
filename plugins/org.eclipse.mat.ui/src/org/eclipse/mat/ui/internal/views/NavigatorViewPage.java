/*******************************************************************************
 * Copyright (c) 2008 SAP AG. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: SAP AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.mat.ui.internal.views;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.MemoryAnalyserPlugin.ISharedImages;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.EditorPaneRegistry;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.NavigatorState.IStateChangeListener;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;

public class NavigatorViewPage extends Page implements ISelectionProvider, IDoubleClickListener, IStateChangeListener
{
    private class NavigatorLabelProvider extends LabelProvider implements IFontProvider, IColorProvider
    {
        @Override
        public Image getImage(Object element)
        {
            return ((PaneState) element).getImage();
        }

        @Override
        public String getText(Object element)
        {
            return ((PaneState) element).getIdentifier();
        }

        public Font getFont(Object element)
        {
            if (((PaneState) element).isReproducable())
                return null;
            return font;
        }

        public Color getBackground(Object element)
        {
            return null;
        }

        public Color getForeground(Object element)
        {
            if (((PaneState) element).isActive())
                return null;
            return greyColor;
        }
    }

    private class NavigatorContentProvider implements ITreeContentProvider
    {
        private List<PaneState> elements;

        public Object[] getChildren(Object element)
        {
            return ((PaneState) element).getChildren().toArray();
        }

        public Object[] getElements(Object element)
        {
            return elements.toArray();
        }

        public boolean hasChildren(Object element)
        {
            return ((PaneState) element).hasChildren();
        }

        public Object getParent(Object element)
        {
            return ((PaneState) element).getParentPaneState();
        }

        public void dispose()
        {}

        @SuppressWarnings("unchecked")
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
        {
            elements = (List<PaneState>) newInput;
        }
    }

    private TreeViewer treeViewer;
    private MultiPaneEditor editor;
    private Font font;
    private Color greyColor;
    private Action showPaneAction;
    private Action removeWithChildrenAction;
    private Action closePaneAction;
    private Action closeWithChildrenAction;

    public NavigatorViewPage(MultiPaneEditor editor)
    {
        super();
        this.editor = editor;
    }

    public void createControl(Composite parent)
    {
        treeViewer = new TreeViewer(parent);
        createContextMenu(treeViewer.getTree());

        treeViewer.setContentProvider(new NavigatorContentProvider());
        treeViewer.setLabelProvider(new NavigatorLabelProvider());
        treeViewer.addDoubleClickListener(this);
        editor.getNavigatorState().addChangeStateListener(this);
        initializeFont();

        treeViewer.setInput(editor.getNavigatorState().getElements());
        treeViewer.expandAll();

        makeActions();
    }

    private void makeActions()
    {
        showPaneAction = new Action()
        {
            public void run()
            {
                bringToTop((IStructuredSelection) treeViewer.getSelection());
            }
        };
        showPaneAction.setText(Messages.NavigatorViewPage_Activate);
        showPaneAction.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.SHOW_PANE));

        removeWithChildrenAction = new Action()
        {
            public void run()
            {
                close(true, true);
            }
        };
        removeWithChildrenAction.setText(Messages.NavigatorViewPage_RemoveFromList);
        removeWithChildrenAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                        org.eclipse.ui.ISharedImages.IMG_TOOL_DELETE));

        closePaneAction = new Action()
        {
            public void run()
            {
                close(false, false);
            }
        };
        closePaneAction.setText(Messages.NavigatorViewPage_Close);
        closePaneAction.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.CLOSE_PANE));

        closeWithChildrenAction = new Action()
        {
            public void run()
            {
                close(false, true);
            }
        };
        closeWithChildrenAction.setText(Messages.NavigatorViewPage_CloseBranch);
        closeWithChildrenAction.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(ISharedImages.CLOSE_BRANCH));

        Action copyAction = new Action()
        {
            @Override
            public void run()
            {
                Copy.copyToClipboard(treeViewer.getTree());
            }
        };
        getSite().getActionBars().setGlobalActionHandler(ActionFactory.COPY.getId(), copyAction);
        getSite().getActionBars().updateActionBars();

    }

    private void close(boolean remove, boolean recursive)
    {
        TreeItem[] selection = treeViewer.getTree().getSelection();
        List<PaneState> selectedStates = new ArrayList<PaneState>(selection.length);
        for (TreeItem treeItem : selection)
        {
            if (treeItem.isDisposed())
                continue;
            PaneState state = (PaneState) treeItem.getData();
            selectedStates.add(state);
        }
        for (PaneState paneState : selectedStates)
        {
            closePane(paneState, remove, recursive);
        }
    }

    private void closePane(PaneState state, boolean remove, boolean recursive)
    {
        if (state.getType() == PaneType.COMPOSITE_CHILD)
        {
            CompositeHeapEditorPane composite = (CompositeHeapEditorPane) editor.getEditor(state.getParentPaneState());
            if (composite != null)
                composite.closePage(state);
        }
        else
        {
            editor.closePage(state);
        }

        if (remove)
        {
            editor.getNavigatorState().removeEntry(state);
        }

        if (recursive)
        {
            List<PaneState> children = new ArrayList<PaneState>(state.getChildren());
            for (PaneState child : children)
                closePane(child, remove, true);
        }

        // last composite child closes & removes parent too
        if (state.getType() == PaneType.COMPOSITE_CHILD && !state.getParentPaneState().hasActiveChildren())
        {
            closePane(state.getParentPaneState(), remove && !state.getParentPaneState().hasChildren(), false);
        }
    }

    private void initializeFont()
    {
        Font defaultFont = JFaceResources.getDefaultFont();
        FontDescriptor fontDescriptor = FontDescriptor.createFrom(defaultFont);
        fontDescriptor = fontDescriptor.setStyle(SWT.ITALIC);
        this.font = fontDescriptor.createFont(treeViewer.getTree().getDisplay());
        this.greyColor = treeViewer.getTree().getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
    }

    private void createContextMenu(Control control)
    {
        MenuManager menuManager = new MenuManager();
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener(new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager menu)
            {
                IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
                if (selection.size() != 0)
                    editorContextMenuAboutToShow(menu, selection);
            }
        });
        Menu menu = menuManager.createContextMenu(control);
        control.setMenu(menu);
    }

    private void editorContextMenuAboutToShow(IMenuManager menu, IStructuredSelection selection)
    {
        menu.add(showPaneAction);
        menu.add(closePaneAction);
        menu.add(closeWithChildrenAction);
        menu.add(new Separator());
        menu.add(removeWithChildrenAction);

        if (selection.size() > 1)
        {
            boolean enabled = false;
            for (Iterator<?> i = selection.iterator(); i.hasNext();)
            {
                PaneState state = (PaneState) i.next();
                if (state.isActive())
                {
                    enabled = true;
                    break;
                }
            }
            closePaneAction.setEnabled(enabled);
            showPaneAction.setEnabled(false);
            closeWithChildrenAction.setEnabled(false);
        }
        else
        {
            PaneState state = (PaneState) selection.getFirstElement();

            showPaneAction.setEnabled(state.isReproducable() || state.isActive());
            closePaneAction.setEnabled(state.isActive());
            closeWithChildrenAction.setEnabled(state.isActive());
        }
    }

    public Control getControl()
    {
        if (treeViewer == null)
            return null;
        return treeViewer.getControl();
    }

    public void setFocus()
    {
        treeViewer.getControl().setFocus();
    }

    public void update()
    {
        if (treeViewer != null)
        {
            Control control = treeViewer.getControl();
            if (control != null && !control.isDisposed())
            {
                control.setRedraw(false);
                treeViewer.setInput(editor.getNavigatorState().getElements());
                treeViewer.expandAll();
                control.setRedraw(true);
            }
        }
    }

    public void doubleClick(DoubleClickEvent event)
    {
        ISelection selection = event.getSelection();
        if (selection instanceof IStructuredSelection)
        {
            IStructuredSelection elements = (IStructuredSelection) selection;
            if (elements.size() == 1)
            {
                bringToTop(elements);
            }
        }
    }

    private void bringToTop(IStructuredSelection selection)
    {
        PaneState state = (PaneState) selection.getFirstElement();

        if (state.isActive())
        {// bring to top
            if (state.getType() == PaneType.COMPOSITE_CHILD)
                state = state.getParentPaneState();
            editor.bringPageToTop(state);
        }
        else if (state.isReproducable())
        { // reopen
            try
            {
                PaneType type = state.getType();
                switch (type)
                {
                    case EDITOR:
                    {
                        AbstractEditorPane pane = EditorPaneRegistry.instance().createNewPane(state.getIdentifier());
                        pane.setPaneState(state);
                        editor.addNewPage(pane, null, null, null);
                        break;
                    }
                    case QUERY:
                    {
                        QueryExecution.executeAgain(editor, state);
                        break;
                    }
                    case COMPOSITE_CHILD:
                    {
                        AbstractEditorPane parent = editor.getEditor(state.getParentPaneState());
                        if (parent == null)
                        {
                            parent = EditorPaneRegistry.instance().createNewPane(
                                            state.getParentPaneState().getIdentifier());
                            parent.setPaneState(state.getParentPaneState());
                            editor.addNewPage(parent, null, null, null);
                        }

                        parent.initWithArgument(state);
                        break;
                    }
                    case COMPOSITE_PARENT:
                        // not applicable
                    default:
                }
            }
            catch (Exception e)
            {
                ErrorHelper.logThrowableAndShowMessage(e);
            }
        }
    }

    @Override
    public void init(IPageSite pageSite)
    {
        super.init(pageSite);
        pageSite.setSelectionProvider(this);
    }

    public void onStateChanged(PaneState state)
    {
        if (state == null)
        {
            update();
        }
        else
        {// update only this state and its children
            Control control = treeViewer.getControl();
            if (control != null && !control.isDisposed())
            {
                control.setRedraw(false);
                treeViewer.refresh(state);
                treeViewer.expandToLevel(state, -1);
                control.setRedraw(true);
            }
        }
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        treeViewer.addSelectionChangedListener(listener);
    }

    public ISelection getSelection()
    {
        return treeViewer.getSelection();
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        treeViewer.removeSelectionChangedListener(listener);
    }

    public void setSelection(ISelection selection)
    {
        treeViewer.setSelection(selection);
    }

    @Override
    public void dispose()
    {
        editor.getNavigatorState().removeChangeStateListener(this);
        if (font != null)
            font.dispose();
        if (greyColor != null)
            greyColor.dispose();
        super.dispose();
    }
}

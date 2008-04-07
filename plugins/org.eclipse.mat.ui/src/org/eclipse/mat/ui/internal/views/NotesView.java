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
package org.eclipse.mat.ui.internal.views;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.regex.Pattern;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.DefaultHyperlinkPresenter;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.impl.snapshot.notes.NotesManager;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.ISnapshotEditorInput;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.IUpdate;

public class NotesView extends ViewPart implements IPartListener, Observer
{
    private String snapshotPath;
    private static Menu menu;
    private DisposeListener disposeListener;

    Action undo;
    Action redo;

    Font font;
    Color hyperlinkColor;

    HeapEditor heapEditor;

    private TextViewer textViewer;
    private static final int UNDO_LEVEL = 10;
    TextViewerUndoManager undoManager;
    private Map<String, NotesViewAction> actions = new HashMap<String, NotesViewAction>();

    @Override
    public void createPartControl(Composite parent)
    {
        parent.setLayout(new FillLayout());
        disposeListener = new DisposeListener()
        {

            public void widgetDisposed(DisposeEvent e)
            {
                if (undoManager.undoable())
                    saveNotes();
            }
        };
        parent.addDisposeListener(disposeListener);

        textViewer = new TextViewer(parent, SWT.MULTI | SWT.V_SCROLL | SWT.LEFT | SWT.H_SCROLL);
        textViewer.setDocument(new Document());
        textViewer.getControl().setEnabled(false);
        textViewer.getTextWidget().setWordWrap(false);
        font = new Font(parent.getDisplay(), "Courier New", 8, SWT.NORMAL);
        textViewer.getControl().setFont(font);

        hyperlinkColor = new Color(null, new RGB(0, 0, 255));

        getSite().getPage().addPartListener(this);

        undoManager = new TextViewerUndoManager(UNDO_LEVEL);
        undoManager.connect(textViewer);
        textViewer.setUndoManager(undoManager);

        textViewer.addSelectionChangedListener(new ISelectionChangedListener()
        {
            public void selectionChanged(SelectionChangedEvent event)
            {
                updateActions();
            }
        });

        // TODO(en) make it work without keyListener
        textViewer.getTextWidget().addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent event)
            {
                if (event.keyCode == 'z' && (event.stateMask & SWT.MOD1) != 0)
                {
                    undo.run();
                }
                if (event.keyCode == 'y' && (event.stateMask & SWT.MOD1) != 0)
                {
                    redo.run();
                }
            }
        });

        textViewer.addTextListener(new ITextListener()
        {
            public void textChanged(TextEvent event)
            {
                searchForHyperlinks(textViewer.getDocument().get(), 0);
            }
        });

        textViewer.setHyperlinkPresenter(new DefaultHyperlinkPresenter(hyperlinkColor));
        textViewer.setHyperlinkDetectors(new IHyperlinkDetector[] { new ObjectAddressHyperlinkDetector() }, SWT.MOD1);

        makeActions();
        hookContextMenu();
        showBootstrapPart();
        NotesManager.instance().addObserver(this);
        updateActions();
    }

    private void updateActions()
    {
        for (Iterator<NotesViewAction> iterator = actions.values().iterator(); iterator.hasNext();)
        {
            ((IUpdate) iterator.next()).update();
        }
    }

    private void hookContextMenu()
    {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                textEditorContextMenuAboutToShow(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(textViewer.getControl());
        textViewer.getControl().setMenu(menu);
    }

    private void textEditorContextMenuAboutToShow(IMenuManager manager)
    {
        if (textViewer != null)
        {
            undo.setEnabled(undoManager.undoable());
            redo.setEnabled(undoManager.redoable());
            manager.add(undo);
            manager.add(redo);
            manager.add(new Separator());

            manager.add(getAction(ActionFactory.CUT.getId()));
            manager.add(getAction(ActionFactory.COPY.getId()));
            manager.add(getAction(ActionFactory.PASTE.getId()));
            manager.add(new Separator());
            manager.add(getAction(ActionFactory.DELETE.getId()));
            manager.add(getAction(ActionFactory.SELECT_ALL.getId()));
        }
    }

    private NotesViewAction getAction(String actionID)
    {
        return actions.get(actionID);
    }

    private Action addAction(ActionFactory actionFactory, int textOperation, String actionDefinitionId)
    {
        IWorkbenchWindow window = getViewSite().getWorkbenchWindow();
        IWorkbenchAction globalAction = actionFactory.create(window);

        // Create our text action.
        NotesViewAction action = new NotesViewAction(textOperation, actionDefinitionId);
        actions.put(actionFactory.getId(), action);
        // Copy its properties from the global action.
        action.setText(globalAction.getText());
        action.setToolTipText(globalAction.getToolTipText());
        action.setDescription(globalAction.getDescription());
        action.setImageDescriptor(globalAction.getImageDescriptor());

        action.setDisabledImageDescriptor(globalAction.getDisabledImageDescriptor());
        action.setAccelerator(globalAction.getAccelerator());

        action.update();

        // Register our text action with the global action handler.
        IActionBars actionBars = getViewSite().getActionBars();
        actionBars.setGlobalActionHandler(actionFactory.getId(), action);
        return action;
    }

    @Override
    public void setFocus()
    {
        textViewer.getControl().setFocus();
    }

    public void partActivated(IWorkbenchPart part)
    {
        if (!supportsNotes(part))
            return;

        textViewer.getControl().setEnabled(true);
        this.heapEditor = (HeapEditor) part;
        String path = ((ISnapshotEditorInput) heapEditor.getPaneEditorInput()).getPath().toOSString();

        if (!path.equals(this.snapshotPath))
        {
            this.snapshotPath = path;
            this.updateTextViewer();
        }
    }

    public void partBroughtToTop(IWorkbenchPart part)
    {
        if (snapshotPath != null && undoManager.undoable())
            saveNotes();
        partActivated(part);
    }

    public void partClosed(IWorkbenchPart part)
    {
        if (!supportsNotes(part)) { return; }

        HeapEditor heapEditor = (HeapEditor) part;
        String path = ((ISnapshotEditorInput) heapEditor.getPaneEditorInput()).getPath().toOSString();

        if (path.equals(this.snapshotPath))
        {
            if (undoManager.undoable())
                saveNotes();
            this.snapshotPath = null;
            this.updateTextViewer();
        }
    }

    public void partDeactivated(IWorkbenchPart part)
    {}

    public void partOpened(IWorkbenchPart part)
    {}

    private void showBootstrapPart()
    {
        IWorkbenchPage page = getSite().getPage();
        if (page != null)
            partActivated(page.getActiveEditor());
    }

    private void makeActions()
    {
        // Install the standard text actions.
        addAction(ActionFactory.CUT, ITextOperationTarget.CUT, "org.eclipse.ui.edit.cut");
        addAction(ActionFactory.COPY, ITextOperationTarget.COPY, "org.eclipse.ui.edit.copy");
        addAction(ActionFactory.PASTE, ITextOperationTarget.PASTE, "org.eclipse.ui.edit.paste");
        addAction(ActionFactory.DELETE, ITextOperationTarget.DELETE, "org.eclipse.ui.edit.delete");
        addAction(ActionFactory.SELECT_ALL, ITextOperationTarget.SELECT_ALL, "org.eclipse.ui.edit.selectAll");
        undo = addAction(ActionFactory.UNDO, ITextOperationTarget.UNDO, "org.eclipse.ui.edit.undo");
        redo = addAction(ActionFactory.REDO, ITextOperationTarget.REDO, "org.eclipse.ui.edit.redo");
    }

    public void update(Observable o, Object arg)
    {
        String path = (String) arg;

        if (path == null || path.equals(this.snapshotPath))
        {
            updateTextViewer();
        }
    }

    private void updateTextViewer()
    {
        // get notes.txt and if it's not null set is as input
        if (snapshotPath != null)
        {
            StringBuffer buffer = NotesManager.instance().getSnapshotNotes(snapshotPath);
            if (buffer != null)
            {
                Document document = new Document(buffer.toString());
                textViewer.setDocument(document);
                revealEndOfDocument();
            }
            else
                textViewer.setDocument(new Document(""));
        }
        else
        {
            textViewer.setDocument(new Document(""));
            textViewer.getControl().setEnabled(false);
        }
    }

    private void searchForHyperlinks(String allText, int offset)
    {
        if (snapshotPath == null)
            return;
        Pattern addressPattern = Pattern.compile("0x\\p{XDigit}+");//$NON-NLS-1$        
        String[] fields = allText.split("\\W", 0);
        List<IdHyperlink> hyperlinks = new ArrayList<IdHyperlink>();

        for (String field : fields)
        {
            if (addressPattern.matcher(field).matches())
            {
                IRegion idRegion = new Region(offset, field.length());
                IdHyperlink hyperlink = new IdHyperlink(field, heapEditor, idRegion);
                hyperlinks.add(hyperlink);
            }
            offset = offset + field.length() + 1; // length of the splitter
        }
        if (!hyperlinks.isEmpty())
            highlightHyperlinks(hyperlinks);
    }

    private void highlightHyperlinks(List<IdHyperlink> hyperlinks)
    {
        TextPresentation style = new TextPresentation();
        for (IHyperlink hyperlink : hyperlinks)
        {
            int startIndex = hyperlink.getHyperlinkRegion().getOffset();
            int length = hyperlink.getHyperlinkRegion().getLength();
            StyleRange styleRange = new StyleRange(startIndex, length, hyperlinkColor, null, SWT.ITALIC);
            styleRange.underline = true;
            style.addStyleRange(styleRange);
        }
        textViewer.changeTextPresentation(style, true);
    }

    private void saveNotes()
    {
        if (snapshotPath != null)
        {
            String text = textViewer.getDocument().get();
            if (text != null && text.trim() != "")
                NotesManager.instance().saveNotes(snapshotPath, text);
            resetUndoManager();
        }
    }

    @Override
    public void dispose()
    {
        NotesManager.instance().deleteObserver(this);
        undoManager.disconnect();
        getSite().getPage().removePartListener(this);
        getSite().getShell().removeDisposeListener(disposeListener);
        if (font != null)
            font.dispose();
        if (hyperlinkColor != null)
            hyperlinkColor.dispose();
        if (menu != null)
            menu.dispose();
        super.dispose();
    }

    private boolean supportsNotes(IWorkbenchPart part)
    {
        if (part instanceof HeapEditor)
        {
            textViewer.getControl().setEnabled(true);
            return true;
        }
        return false;
    }

    protected void revealEndOfDocument()
    {
        IDocument doc = textViewer.getDocument();
        int docLength = doc.getLength();
        if (docLength > 0)
        {
            textViewer.revealRange(docLength - 1, 1);
            StyledText widget = textViewer.getTextWidget();
            widget.setCaretOffset(docLength);
        }
    }

    public void resetUndoManager()
    {
        undoManager.reset();
    }

    private static final class IdHyperlink implements IHyperlink
    {

        String id;
        ISnapshot snapshot;
        HeapEditor editor;
        IRegion region;

        public IdHyperlink(String id, HeapEditor editor, IRegion region)
        {
            this.id = id;
            this.editor = editor;
            this.snapshot = editor.getSnapshotInput().getSnapshot();
            this.region = region;
        }

        public IRegion getHyperlinkRegion()
        {
            return region;
        }

        public String getHyperlinkText()
        {
            return null;
        }

        public String getTypeLabel()
        {
            return null;
        }

        public void open()
        {
            try
            {
                long objectAddress = new BigInteger(id.substring(2), 16).longValue();

                final int objectId = snapshot.mapAddressToId(objectAddress);

                if (objectId < 0)
                    return;

                QueryContextMenu contextMenu = new QueryContextMenu(editor, new ContextProvider((String) null)
                {
                    @Override
                    public IContextObject getContext(final Object row)
                    {
                        return new IContextObject()
                        {

                            public int getObjectId()
                            {
                                return (Integer) row;
                            }

                        };
                    }
                });

                PopupMenu popupMenu = new PopupMenu();
                contextMenu.addContextActions(popupMenu, new StructuredSelection(objectId));

                if (menu != null && !menu.isDisposed())
                    menu.dispose();

                menu = popupMenu.createMenu(editor.getEditorSite().getActionBars().getStatusLineManager(), PlatformUI
                                .getWorkbench().getDisplay().getActiveShell());
                menu.setVisible(true);
            }
            catch (NumberFormatException ignore)
            {
                // $JL-EXC$
                // shouldn't happen and if it does nothing can be done
            }
            catch (SnapshotException ignore)
            {
                // $JL-EXC$
                // mapToAddress throws exception on illegal values
            }

        }
    }

    private class ObjectAddressHyperlinkDetector extends AbstractHyperlinkDetector
    {

        public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks)
        {
            if (region == null || textViewer == null)
                return null;

            IDocument document = textViewer.getDocument();

            int offset = region.getOffset();
            if (document == null)
                return null;

            IRegion lineInfo;
            String text;
            try
            {
                lineInfo = document.getLineInformationOfOffset(offset);
                text = document.get(lineInfo.getOffset(), lineInfo.getLength());
            }
            catch (BadLocationException ex)
            {
                return null;
            }
            int index = offset - lineInfo.getOffset();
            // to the left from offset
            char ch;
            do
            {
                index--;
                ch = ' ';
                if (index > -1)
                    ch = text.charAt(index);
            }
            while (Character.isLetterOrDigit(ch));
            int startIndex = index + 1;

            // to the right from offset
            index = offset - lineInfo.getOffset() - 1;
            do
            {
                index++;
                if (index >= text.length())
                {
                    break;
                }
                ch = text.charAt(index);
            }
            while (Character.isLetterOrDigit(ch));

            Pattern addressPattern = Pattern.compile("0x\\p{XDigit}+");//$NON-NLS-1$
            String address = text.substring(startIndex, index);
            if (address != null && addressPattern.matcher(address).matches())
            {
                IRegion idRegion = new Region(startIndex, address.length());
                return new IHyperlink[] { new IdHyperlink(address, heapEditor, idRegion) };
            }
            else
                return null;
        }
    }

    class NotesViewAction extends Action implements IUpdate
    {
        private int actionId;

        NotesViewAction(int actionId, String actionDefinitionId)
        {
            this.actionId = actionId;
            this.setActionDefinitionId(actionDefinitionId);
        }

        public boolean isEnabled()
        {
            return textViewer.canDoOperation(actionId);
        }

        public void run()
        {
            textViewer.doOperation(actionId);
        }

        public void update()
        {
            if (super.isEnabled() != isEnabled())
                setEnabled(isEnabled());
        }
    }

}

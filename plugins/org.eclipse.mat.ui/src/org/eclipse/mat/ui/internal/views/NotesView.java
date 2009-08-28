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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.eclipse.jface.resource.JFaceResources;
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
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
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

public class NotesView extends ViewPart implements IPartListener, Observer
{
    private static final int UNDO_LEVEL = 10;

    private File resource;
    private DisposeListener disposeListener;

    private Action undo;
    private Action redo;

    private Menu menu;
    private Font font;
    private Color hyperlinkColor;

    private MultiPaneEditor editor;

    private TextViewer textViewer;
    private TextViewerUndoManager undoManager;
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
        font = new Font(parent.getDisplay(), JFaceResources.getDefaultFont().getFontData()[0].getName(), 8, SWT.NORMAL);
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
        updateActions();
    }

    private void updateActions()
    {
        for (NotesViewAction a : actions.values())
            a.setEnabled(textViewer.canDoOperation(a.actionId));
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
        editor = (MultiPaneEditor) part;
        File path = editor.getResourceFile();

        if (path != null && !path.equals(resource))
        {
            resource = path;
            updateTextViewer();
        }
    }

    public void partBroughtToTop(IWorkbenchPart part)
    {
        if (resource != null && undoManager.undoable())
            saveNotes();
        partActivated(part);
    }

    public void partClosed(IWorkbenchPart part)
    {
        if (!supportsNotes(part)) { return; }

        MultiPaneEditor editor = (MultiPaneEditor) part;
        File resource = editor.getResourceFile();

        if (resource.equals(this.resource))
        {
            if (undoManager.undoable())
                saveNotes();
            this.resource = null;
            this.editor = null;
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
        addAction(ActionFactory.CUT, ITextOperationTarget.CUT, "org.eclipse.ui.edit.cut");//$NON-NLS-1$
        addAction(ActionFactory.COPY, ITextOperationTarget.COPY, "org.eclipse.ui.edit.copy");//$NON-NLS-1$
        addAction(ActionFactory.PASTE, ITextOperationTarget.PASTE, "org.eclipse.ui.edit.paste");//$NON-NLS-1$
        addAction(ActionFactory.DELETE, ITextOperationTarget.DELETE, "org.eclipse.ui.edit.delete");//$NON-NLS-1$
        addAction(ActionFactory.SELECT_ALL, ITextOperationTarget.SELECT_ALL, "org.eclipse.ui.edit.selectAll");//$NON-NLS-1$
        undo = addAction(ActionFactory.UNDO, ITextOperationTarget.UNDO, "org.eclipse.ui.edit.undo");//$NON-NLS-1$
        redo = addAction(ActionFactory.REDO, ITextOperationTarget.REDO, "org.eclipse.ui.edit.redo");//$NON-NLS-1$
    }

    public void update(Observable o, Object arg)
    {
        String path = (String) arg;

        if (path == null || path.equals(this.resource))
        {
            updateTextViewer();
        }
    }

    private void updateTextViewer()
    {
        // get notes.txt and if it's not null set is as input
        if (resource != null)
        {
            String buffer = readNotes(resource);
            if (buffer != null)
            {
                Document document = new Document(buffer);
                textViewer.setDocument(document);
                revealEndOfDocument();
            }
            else
                textViewer.setDocument(new Document(""));//$NON-NLS-1$
        }
        else
        {
            textViewer.setDocument(new Document(""));//$NON-NLS-1$
            textViewer.getControl().setEnabled(false);
        }
    }

    private void searchForHyperlinks(String allText, int offset)
    {
        if (resource == null)
            return;
        Pattern addressPattern = Pattern.compile("0x\\p{XDigit}+");//$NON-NLS-1$        
        String[] fields = allText.split("\\W", 0);//$NON-NLS-1$
        List<IdHyperlink> hyperlinks = new ArrayList<IdHyperlink>();

        for (String field : fields)
        {
            if (addressPattern.matcher(field).matches())
            {
                IRegion idRegion = new Region(offset, field.length());
                IdHyperlink hyperlink = new IdHyperlink(field, editor, idRegion);
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
        if (resource != null)
        {
            String text = textViewer.getDocument().get();
            if (text != null)
                saveNotes(resource, text);
            resetUndoManager();
        }
    }

    @Override
    public void dispose()
    {
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
        if (part instanceof MultiPaneEditor)
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

    private final class IdHyperlink implements IHyperlink
    {

        String id;
        MultiPaneEditor editor;
        IRegion region;

        public IdHyperlink(String id, MultiPaneEditor editor, IRegion region)
        {
            this.id = id;
            this.editor = editor;
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
                final int objectId = editor.getQueryContext().mapToObjectId(id);
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
                contextMenu.addContextActions(popupMenu, new StructuredSelection(objectId), null);

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
                return new IHyperlink[] { new IdHyperlink(address, editor, idRegion) };
            }
            else
                return null;
        }
    }

    private class NotesViewAction extends Action
    {
        private int actionId;

        NotesViewAction(int actionId, String actionDefinitionId)
        {
            this.actionId = actionId;
            this.setActionDefinitionId(actionDefinitionId);
        }

        @Override
        public boolean isEnabled()
        {
            return textViewer.canDoOperation(actionId);
        }

        public void run()
        {
            textViewer.doOperation(actionId);
        }

    }

    // //////////////////////////////////////////////////////////////
    // notes management
    // //////////////////////////////////////////////////////////////

    private static String readNotes(File resourcePath)
    {
        try
        {
            if (resourcePath != null)
            {
                File notesFile = getDefaultNotesFile(resourcePath);
                if (notesFile.exists())
                {
                    FileReader fileReader = new FileReader(getDefaultNotesFile(resourcePath));

                    BufferedReader myInput = new BufferedReader(fileReader);

                    try
                    {
                        String s;
                        StringBuffer b = new StringBuffer();
                        while ((s = myInput.readLine()) != null)
                        {
                            b.append(s);
                            b.append("\n");//$NON-NLS-1$
                        }
                        return b.toString();
                    }
                    finally
                    {
                        try
                        {
                            myInput.close();
                        }
                        catch (IOException ignore)
                        {}
                    }
                }
            }

            return null;
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void saveNotes(File resource, String notes)
    {
        OutputStream fout = null;

        try
        {
            File notesFile = getDefaultNotesFile(resource);

            fout = new FileOutputStream(notesFile);
            OutputStreamWriter out = new OutputStreamWriter(new BufferedOutputStream(fout), "UTF8");//$NON-NLS-1$
            out.write(notes);
            out.flush();
            out.close();
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            try
            {
                if (fout != null)
                    fout.close();
            }
            catch (IOException ignore)
            {}
        }
    }

    private static File getDefaultNotesFile(File resource)
    {
        String filename = resource.getAbsolutePath();
        int p = filename.lastIndexOf('.');
        return new File(filename.substring(0, p + 1) + "notes.txt");//$NON-NLS-1$
    }

}

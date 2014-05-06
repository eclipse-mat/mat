/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - improved undo
 *******************************************************************************/
package org.eclipse.mat.ui.internal.views;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.JFacePreferences;
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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.part.ViewPart;

public class NotesView extends ViewPart implements IPartListener, Observer, ISaveablePart, ISaveablePart2
{
    private static final String NOTES_ENCODING = "UTF8"; //$NON-NLS-1$

    private static final int UNDO_LEVEL = 25;

    private File resource;

    private Action undo;
    private Action redo;

    private Menu menu;
    private Font font;
    private Color hyperlinkColor;

    private MultiPaneEditor editor;

    private TextViewer textViewer;
    private TextViewerUndoManager undoManager;
    private Map<String, NotesViewAction> actions = new HashMap<String, NotesViewAction>();
    long hash;
    boolean modified;

    @Override
    public void createPartControl(Composite parent)
    {
        parent.setLayout(new FillLayout());
        // No need for a dispose listener - the SaveablePart will save it

        textViewer = new TextViewer(parent, SWT.MULTI | SWT.V_SCROLL | SWT.LEFT | SWT.H_SCROLL);
        textViewer.setDocument(new Document());
        textViewer.getControl().setEnabled(false);
        textViewer.getTextWidget().setWordWrap(false);
        font = JFaceResources.getFont("org.eclipse.mat.ui.notesfont"); //$NON-NLS-1$
        textViewer.getControl().setFont(font);

        hyperlinkColor = JFaceResources.getColorRegistry().get(JFacePreferences.HYPERLINK_COLOR);

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
                modified = true;
                searchForHyperlinks(textViewer.getDocument().get(), 0);
                firePropertyChange(PROP_DIRTY); 
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
            if (isDirty())
                saveNotes();
            resource = path;
            updateTextViewer();
        }
    }

    public void partBroughtToTop(IWorkbenchPart part)
    {
        partActivated(part);
    }

    public void partClosed(IWorkbenchPart part)
    {
        if (!supportsNotes(part)) { return; }

        MultiPaneEditor editor = (MultiPaneEditor) part;
        File resource = editor.getResourceFile();

        if (resource.equals(this.resource))
        {
            // Saving usually done as SaveablePart except when snapshot is closed
            if (isDirty())
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

    private long hash() {
        // Used to detect if document has changed.
        CRC32 crc = new CRC32();
        try
        {
            crc.update(textViewer.getDocument().get().getBytes(NOTES_ENCODING));
        }
        catch (UnsupportedEncodingException e)
        {
            // Won't happen
            return textViewer.getDocument().get().hashCode();
        }
        return crc.getValue();
    }
    
    public void update(Observable o, Object arg)
    {
        String path = (String) arg;

        if (path == null || new File(path).equals(this.resource))
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
            {
                textViewer.setDocument(new Document(""));//$NON-NLS-1$
            }
        }
        else
        {
            textViewer.setDocument(new Document(""));//$NON-NLS-1$
            textViewer.getControl().setEnabled(false);
        }
        hash = hash();
        modified = false;
        firePropertyChange(PROP_DIRTY);
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
            hash = hash();
            modified = false;
        }
    }

    @Override
    public void dispose()
    {
        undoManager.disconnect();
        getSite().getPage().removePartListener(this);
        // The parent composite has been disposed, so there is no need to remove the disposeListener.
        if (menu != null)
            menu.dispose();
        super.dispose();
    }

    private boolean supportsNotes(IWorkbenchPart part)
    {
        if (part instanceof MultiPaneEditor)
        {
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

                menu = popupMenu.createMenu(getViewSite().getActionBars().getStatusLineManager(), PlatformUI
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
                    FileInputStream fileInput = new FileInputStream(getDefaultNotesFile(resourcePath));
                    try
                    {
                        BufferedReader myInput = new BufferedReader(new InputStreamReader(fileInput, NOTES_ENCODING));

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
                    finally
                    {
                        fileInput.close();
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
            OutputStreamWriter out = new OutputStreamWriter(new BufferedOutputStream(fout), NOTES_ENCODING);
            try
            {
                out.write(notes);
                out.flush();
            }
            finally
            {
                out.close();
            }
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
        String filename = resource.getName();
        int p = filename.lastIndexOf('.');
        if (p >= 0)
            filename = filename.substring(0, p);
        return new File(resource.getParentFile(), filename + ".notes.txt");//$NON-NLS-1$
    }

    public void doSave(IProgressMonitor monitor)
    {
        saveNotes();
        firePropertyChange(PROP_DIRTY); 
    }

    public void doSaveAs()
    {
    }

    public boolean isDirty()
    {
        return undoManager.undoable() || modified && hash != hash();
    }

    public boolean isSaveAsAllowed()
    {
        return false;
    }

    public boolean isSaveOnCloseNeeded()
    {
        return true;
    }

    public int promptToSaveOnClose()
    {
        return ISaveablePart2.YES;
    }

}

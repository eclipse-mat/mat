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
package org.eclipse.mat.ui.internal.actions;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.regex.Pattern;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.ISnapshotEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;


public class OpenObjectByIdAction extends Action
{

    public OpenObjectByIdAction()
    {
        super("Find object by address", MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.FIND));
    }

    @Override
    public void run()
    {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IEditorPart part = page == null ? null : page.getActiveEditor();

        if (part instanceof HeapEditor)
        {
            HeapEditor editor = (HeapEditor)part;

            String value = askForAddress();
            
            if (value != null)
            {
                retrieveObjectAndOpenPane(editor, value);
            }
        }
    }

    private void retrieveObjectAndOpenPane(HeapEditor editor, String value)
    {
        String errorMessage = null;

        try
        {
            // Long.parseLong works only for positive hex
            long objectAddress = new BigInteger(value.substring(2), 16).longValue();
            ISnapshot snapshot = ((ISnapshotEditorInput)editor.getPaneEditorInput()).getSnapshot();
            if (snapshot == null)
            {
                errorMessage = "Error getting heap dump. Not yet loaded?";
            }
            else
            {
                int objectId = snapshot.mapAddressToId(objectAddress);
                if (objectId < 0)
                {
                    errorMessage = MessageFormat.format("No object with address {0} found.",
                                    new Object[] { value });
                }
                else
                {
                    QueryExecution.execute(editor, "list_objects " + value);
                }
            }
        }
        catch (NumberFormatException e)
        {
            // $JL-EXC$
            errorMessage = "Address is not a hexadecimal number.";
        }
        catch (SnapshotException e)
        {
            // $JL-EXC$
            errorMessage = MessageFormat.format("Error reading object: {0}", new Object[] { e.getMessage() });
        }

        if (errorMessage != null)
        {
            MessageDialog.openError(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                            "Error opening object", errorMessage);
        }
    }

    private String askForAddress()
    {
        final Pattern pattern = Pattern.compile("^0x\\p{XDigit}+$");

        InputDialog dialog = new InputDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                        "Find object by address", "Object address:", "0x", new IInputValidator()
                        {

                            public String isValid(String newText)
                            {
                                return !pattern.matcher(newText).matches() ? "Address must be a hex number, e.g. 0x6b93d8"
                                                : null;
                            }

                        });

        int result = dialog.open();

        String value = dialog.getValue();
        if (result == IDialogConstants.CANCEL_ID)
            return null;
        return value;
    }
}

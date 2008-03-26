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
package org.eclipse.mat.ui.internal;

import java.io.File;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;


public class OpenSnapshot
{
    private static final String LAST_DIRECTORY_KEY = OpenSnapshot.class.getName() + ".lastDir";
    private static final Pattern SPECIAL_HPROF_NAMES = Pattern.compile(".*(\\.[Hh][Pp][Rr][Oo][Ff]\\.[0-9]*)");
    private static final Pattern HPROF_PATTERN = Pattern.compile(".*\\"
                    + MemoryAnalyserPlugin.FileExtensions.HEAPDUMP_EXTENSION);

    
    public static abstract class Visitor
    {
        public abstract void visit(IFileStore fileStore);
        
        public boolean go(Shell shell)
        {
            Preferences prefs = MemoryAnalyserPlugin.getDefault().getPluginPreferences();
            String lastDirectory = prefs.getString(LAST_DIRECTORY_KEY);

            FileDialog dialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
            dialog.setText("Open Snapshot");
            dialog.setFilterExtensions(new String[] { "*.hprof;*.hprof.*",
                            "*.hprof;*.hprof.*;*.index" });
            dialog.setFilterNames(new String[] { "All Snapshots (*.hprof.*;*.index)",
                            "All Snapshots (including index files)" });

            if (lastDirectory != null && lastDirectory.length() > 0)
                dialog.setFilterPath(lastDirectory);
            else
                dialog.setFilterPath(System.getProperty("user.home")); //$NON-NLS-1$

            dialog.open();
            String[] names = dialog.getFileNames();
            if (names != null)
            {
                final String filterPath = dialog.getFilterPath();
                prefs.setValue(LAST_DIRECTORY_KEY, filterPath);

                int numberOfFilesNotFound = 0;
                StringBuffer notFound = new StringBuffer();
                for (int i = 0; i < names.length; i++)
                {

                    // check if it is one of the automatically generated
                    // hprof files with time stamp extension

                    if (SPECIAL_HPROF_NAMES.matcher(names[i]).matches())
                    {
                        names[i] = askForRename(filterPath, names[i]);
                        if (names[i] == null)
                            continue;
                    }
                    
                    IFileStore fileStore = EFS.getLocalFileSystem().getStore(new Path(filterPath));
                    fileStore = fileStore.getChild(names[i]);
                    if (!fileStore.fetchInfo().isDirectory() && fileStore.fetchInfo().exists())
                    {
                        visit(fileStore);
                    }
                    else
                    {
                        if (++numberOfFilesNotFound > 1)
                            notFound.append('\n');
                        notFound.append(fileStore.getName());
                    }
                }

                if (numberOfFilesNotFound > 0)
                {
                    String msgFmt = numberOfFilesNotFound == 1 ? "File {0} not found" : "Files {0} not found";
                    String msg = MessageFormat.format(msgFmt, notFound.toString());
                    MessageDialog.openError(shell, "Internal Error", msg);
                }
            }
            
            if (names != null && names.length == 0)
                return false;
            else
                return true;

        }
    }

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////
    
    private static String askForRename(final String path, final String filename)
    {
        IInputValidator inputValidator = new IInputValidator()
        {

            public String isValid(String newText)
            {
                if (!HPROF_PATTERN.matcher(newText).matches()) { return MessageFormat.format(
                                "The file must have the extension {0}",
                                new Object[] { MemoryAnalyserPlugin.FileExtensions.HEAPDUMP_EXTENSION }); }

                File f = new File(path, newText);
                if (f.exists()) { return MessageFormat.format("The file {0} already exists", new Object[] { path
                                + File.separator + newText }); }

                return null;
            }

        };

        InputDialog inputDialog = new InputDialog(
                        PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                        "Rename HPROF file",
                        "Due to technical reasons, a HPROF file must have the extension 'hprof'. Please choose a new name.",
                        filename + MemoryAnalyserPlugin.FileExtensions.HEAPDUMP_EXTENSION, inputValidator)
        {

            @Override
            protected void createButtonsForButtonBar(Composite parent)
            {
                super.createButtonsForButtonBar(parent);

                Matcher matcher = SPECIAL_HPROF_NAMES.matcher(filename);
                if (matcher.matches())
                    getText().setSelection(
                                    matcher.start(1) + MemoryAnalyserPlugin.FileExtensions.HEAPDUMP_EXTENSION.length(),
                                    matcher.end(1) + MemoryAnalyserPlugin.FileExtensions.HEAPDUMP_EXTENSION.length());
            }

        };

        inputDialog.open();

        String newName = inputDialog.getValue();

        if (newName != null)
        {
            new File(path + File.separator + filename).renameTo(new File(path + File.separator + newName));
        }

        return newName;
    }
}

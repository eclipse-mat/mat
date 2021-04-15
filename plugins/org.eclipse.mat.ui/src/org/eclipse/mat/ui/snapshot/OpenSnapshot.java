/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.SnapshotFormat;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public class OpenSnapshot
{
    private static final String LAST_DIRECTORY_KEY = OpenSnapshot.class.getName() + ".lastDir"; //$NON-NLS-1$
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(.*\\.)([a-zA-Z]*)\\.([0-9]*)");//$NON-NLS-1$

    public static abstract class Visitor
    {
        public abstract void visit(IFileStore fileStore);

        public boolean go(Shell shell)
        {
            IPreferenceStore prefs = MemoryAnalyserPlugin.getDefault().getPreferenceStore();
            String lastDirectory = prefs.getString(LAST_DIRECTORY_KEY);

            FileDialog dialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
            dialog.setText(Messages.OpenSnapshot_OpenSnapshot);

            applyFilter(dialog);

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
                for (int ii = 0; ii < names.length; ii++)
                {
                    // Sometimes the heap dump file as the time stamp as an
                    // extension. This causes problems with Eclipse as it works
                    // on file extensions only. Ask the user to rename the file.

                    Matcher matcher = TIMESTAMP_PATTERN.matcher(names[ii]);
                    if (matcher.matches())
                    {
                        names[ii] = askForRename(filterPath, names[ii], matcher.group(2)); // extension
                        if (names[ii] == null)
                            continue;
                    }

                    IFileStore fileStore = EFS.getLocalFileSystem().getStore(new Path(filterPath));
                    fileStore = fileStore.getChild(names[ii]);
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
                    String msgFmt = numberOfFilesNotFound == 1 ? Messages.OpenSnapshot_FileNotFound
                                    : Messages.OpenSnapshot_FilesNotFound;
                    String msg = MessageUtil.format(msgFmt, notFound.toString());
                    MessageDialog.openError(shell, Messages.ErrorHelper_InternalError, msg);
                }
            }

            if (names != null && names.length == 0)
                return false;
            else
                return true;

        }

        private void applyFilter(FileDialog dialog)
        {
            List<SnapshotFormat> types = SnapshotFactory.getSupportedFormats();
            // Arrange types in order and remove duplicates
            SortedSet<SnapshotFormat> sortedTypes = new TreeSet<SnapshotFormat>(new Comparator<SnapshotFormat>()
            {
                public int compare(SnapshotFormat f1, SnapshotFormat f2)
                {
                    int ret = f1.getName().compareTo(f2.getName());
                    if (ret == 0)
                    {
                        // Sort by extensions if the name is the same
                        String exts1[] = f1.getFileExtensions();
                        String exts2[] = f2.getFileExtensions();
                        ret = Arrays.toString(exts1).compareTo(Arrays.toString(exts2));
                    }
                    return ret;
                }
            });
            sortedTypes.addAll(types);
            types = new ArrayList<SnapshotFormat>(sortedTypes);

            // Extensions length is the number of types
            // plus 1 for "All Known Formats"
            // plus 1 for the * wildcard (on Linux *.* doesn't find files without extensions)
            String[] filterExtensions = new String[types.size() + 2];
            String[] filterNames = new String[types.size() + 2];

            // first element: all heap dump formats
            filterExtensions[0] = null;
            filterNames[0] = Messages.OpenSnapshot_AllKnownFormats;

            for (int ii = 0; ii < types.size(); ii++)
            {
                SnapshotFormat snapshotFormat = types.get(ii);

                StringBuilder e = new StringBuilder();
                String[] fileExtensions = snapshotFormat.getFileExtensions();
                for (int jj = 0; jj < fileExtensions.length; jj++)
                {
                    if (jj > 0)
                        e.append(";");//$NON-NLS-1$
                    e.append("*.").append(fileExtensions[jj]);//$NON-NLS-1$
                }

                filterExtensions[ii + 1] = e.toString();
                filterNames[ii + 1] = snapshotFormat.getName();

                if (filterExtensions[0] == null)
                    filterExtensions[0] = filterExtensions[ii + 1];
                else
                    filterExtensions[0] += ";" + filterExtensions[ii + 1];//$NON-NLS-1$
            }

            // Add in the wildcard
            filterExtensions[types.size() + 1] = "*"; //$NON-NLS-1$
            filterNames[types.size() + 1] = Messages.OpenSnapshot_AllFiles;

            dialog.setFilterExtensions(filterExtensions);
            dialog.setFilterNames(filterNames);
        }
    }

    // //////////////////////////////////////////////////////////////
    // helper methods
    // //////////////////////////////////////////////////////////////

    private static String askForRename(final String path, final String filename, final String extension)
    {
        final Pattern validFileName = Pattern.compile(".*\\.((?i)" + extension + ")");//$NON-NLS-1$//$NON-NLS-2$
        final String bad[] = new String[1];
        IInputValidator inputValidator = new IInputValidator()
        {

            public String isValid(String newText)
            {
                if (!validFileName.matcher(newText).matches()) { return MessageUtil.format(
                                Messages.OpenSnapshot_FileMustHaveExtension, extension); }

                File f = new File(path, newText);
                if (f.exists())
                    return MessageUtil.format(Messages.OpenSnapshot_FileAlreadyExists, f.getAbsolutePath());

                try
                {
                    File newCanonical = f.getCanonicalFile();
                    // Wrong directory?
                    if (!newCanonical.getParentFile().equals((new File(path)).getCanonicalFile())
                                    || newText.contains(File.separator) || newText.indexOf('/') >= 0
                                    || newText.equals(bad[0]))
                    {
                        // Find the problem
                        String name = newCanonical.getName();
                        for (int i = newText.length() - 1; i >= 0; --i)
                        {
                            if (name.length() - newText.length() + i < 0
                                            || name.charAt(name.length() - newText.length() + i) != newText.charAt(i))
                            {
                                newText = newText.substring(0, i + 1);
                                break;
                            }
                        }
                        return MessageUtil.format(Messages.OpenSnapshot_BadName, newText);
                    }
                }
                catch (IOException e)
                {
                    return e.getLocalizedMessage();
                }
                return null;
            }

        };
        String suggestedFilename = filename + "." + extension; //$NON-NLS-1$
        do {
            String msg = Messages.OpenSnapshot_Warning;
            InputDialog inputDialog = new InputDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                            Messages.OpenSnapshot_RenameHeapDump, //
                            MessageUtil.format(msg, extension), suggestedFilename,
                            inputValidator)
            {

                @Override
                protected void createButtonsForButtonBar(Composite parent)
                {
                    super.createButtonsForButtonBar(parent);

                    Matcher matcher = TIMESTAMP_PATTERN.matcher(filename);
                    if (matcher.matches())
                        getText().setSelection(matcher.start(2) - 1, matcher.end(3));
                }
            };

            if (inputDialog.open() != InputDialog.OK)
                return null;

            String newName = inputDialog.getValue();

            if (newName != null)
            {
                File file = new File(path, filename);
                File newFile = new File(path, newName);
                if (file.renameTo(newFile))
                {
                    return newName;
                }
                // Retry, flagging this name as bad
                bad[0] = newName;
                suggestedFilename = newName;
            }
            else
            {
                return null;
            }
        } while (true);
    }
}

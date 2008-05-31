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
package org.eclipse.mat.ui.internal.viewer;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

public class ExportDialog
{   
    private static final String[] FILTER_NAMES = { "Comma Separated Values Files (*.csv)" };
    // These filter extensions are used to filter which files are displayed.
    private static final String[] FILTER_EXTS = { "*.csv" }; //$NON-NLS-1$
    
    private FileDialog dlg;
    private String[] names;
    private String[] extentions;

    /**
     * ExportCSVDialog constructor
     * 
     * @param shell
     *            the parent shell
     */
    public ExportDialog(Shell shell, String[] names, String[] extentions)
    {
        dlg = new FileDialog(shell, SWT.SAVE);
        this.names = names;
        this.extentions = extentions;
    }
    
    public ExportDialog(Shell shell)
    {
        dlg = new FileDialog(shell, SWT.SAVE);
        this.names = FILTER_NAMES;
        this.extentions = FILTER_EXTS;
    }

    public String open()
    {
        // We store the selected file name in fileName
        String fileName = null;

        // The user has finished when one of the
        // following happens:
        // 1) The user dismisses the dialog by pressing Cancel
        // 2) The selected file name does not exist
        // 3) The user agrees to overwrite existing file
        boolean done = false;

        while (!done)
        {
            dlg.setFilterNames(names);
            dlg.setFilterExtensions(extentions);
            // Open the File Dialog
            fileName = dlg.open();
            if (fileName == null)
            {
                // User has canceled, so quit and return
                done = true;
            }
            else
            {
                // User has selected a file; see if it already exists
                File file = new File(fileName);
                if (file.exists())
                {
                    // The file already exists; asks for confirmation
                    MessageBox mb = new MessageBox(dlg.getParent(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
                    mb.setMessage(MessageFormat.format("{0} already exists. Do you want to replace it?", fileName));

                    // If they click Yes, we're done and we drop out. If
                    // they click No, we redisplay the File Dialog
                    done = mb.open() == SWT.YES;
                }
                else
                {
                    // File does not exist, so drop out
                    done = true;
                }
            }
        }
        return fileName;
    }

    public String getFileName()
    {
        return dlg.getFileName();
    }

    public String[] getFileNames()
    {
        return dlg.getFileNames();
    }

    public String[] getFilterExtensions()
    {
        return dlg.getFilterExtensions();
    }

    public String[] getFilterNames()
    {
        return dlg.getFilterNames();
    }

    public String getFilterPath()
    {
        return dlg.getFilterPath();
    }

    public void setFileName(String string)
    {
        dlg.setFileName(string);
    }

    public void setFilterExtensions(String[] extensions)
    {
        dlg.setFilterExtensions(extensions);
    }

    public void setFilterNames(String[] names)
    {
        dlg.setFilterNames(names);
    }

    public void setFilterPath(String string)
    {
        dlg.setFilterPath(string);
    }

    public Shell getParent()
    {
        return dlg.getParent();
    }

    public int getStyle()
    {
        return dlg.getStyle();
    }

    public String getText()
    {
        return dlg.getText();
    }

    public void setText(String string)
    {
        dlg.setText(string);
    }

}

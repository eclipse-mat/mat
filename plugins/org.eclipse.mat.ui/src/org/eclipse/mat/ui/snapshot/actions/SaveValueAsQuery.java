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
package org.eclipse.mat.ui.snapshot.actions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.PlatformUI;

@Name("10|Save Value To File")
@Category("101|Copy")
@Help("Save the value of char[], String, StringBuffer or StringBuilder into a text file.")
@Icon("/icons/copy.gif")
public class SaveValueAsQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument
    public List<IContextObject> objects;

    @Argument
    public File file;

    @Argument(isMandatory = false)
    public String encoding = System.getProperty("file.encoding");

    public IResult execute(IProgressListener listener) throws Exception
    {
        checkIfFileExists();

        // FIXME
        ExportInfo info = ExportInfo.of(snapshot.getObject(objects.get(0).getObjectId()));
        if (info == null)
            throw new SnapshotException(
                            "Nothing to save. Only char[], String, StringBuffer and StringBuilder currently supported.");

        writeToFile();

        // let the UI ignore this query
        throw new IProgressListener.OperationCanceledException();
    }

    private void checkIfFileExists()
    {
        if (file.exists())
        {
            try
            {
                // message box will popup in background...
                Thread.sleep(500);
            }
            catch (InterruptedException ignore)
            {}

            final boolean[] goAhead = new boolean[1];

            PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
            {
                public void run()
                {
                    MessageBox box = new MessageBox(PlatformUI.getWorkbench().getDisplay().getActiveShell(), //
                                    SWT.YES | SWT.NO);
                    box.setText("Overwrite?");
                    box.setMessage(MessageFormat.format("File {0} exists. Do you want to overwrite?", file
                                    .getAbsolutePath()));

                    int retValue = box.open();
                    goAhead[0] = retValue == SWT.YES;
                }
            });

            if (!goAhead[0])
                throw new IProgressListener.OperationCanceledException();
        }
    }

    private void writeToFile() throws Exception
    {
        FileOutputStream out = null;

        try
        {
            out = new FileOutputStream(file);
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, encoding)));

            boolean isFirst = true;

            for (IContextObject object : objects)
            {
                if (object.getObjectId() < 0)
                    continue;

                if (!isFirst)
                    writer.append('\n');

                IObject subject = snapshot.getObject(object.getObjectId());
                ExportInfo info = ExportInfo.of(subject);
                if (info == null)
                {
                    String name = subject.getClassSpecificName();
                    writer.append(name != null ? name : subject.getTechnicalName());
                }
                else
                {
                    IPrimitiveArray charArray = info.getCharArray();
                    final int length = charArray.getLength();
                    final int end = info.getOffset() + info.getCount();

                    int offset = info.getOffset();

                    while (offset < end)
                    {
                        int read = Math.min(4092, length - offset);
                        char[] array = (char[]) charArray.getValueArray(offset, read);

                        writer.append(new String(array));

                        offset += read;
                    }
                }

                isFirst = false;
            }

            writer.flush();
        }
        finally
        {
            if (out != null)
                out.close();
        }
    }
}

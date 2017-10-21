/*******************************************************************************
 * Copyright (c) 2008, 2017 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - progress indicators
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.actions;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;

@Icon("/icons/copy.gif")
public class SaveValueAsQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument
    public List<IContextObject> objects;

    @Argument(advice = Advice.SAVE)
    public File file;

    @Argument
    public Display display;

    public IResult execute(IProgressListener listener) throws Exception
    {
        checkIfFileExists();

        if (objects.size() > 1)
            writeStringData(listener);
        else if (objects.size() == 1)
            writeRawData(listener);

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

            display.syncExec(new Runnable()
            {
                public void run()
                {
                    MessageBox box = new MessageBox(display.getActiveShell(), //
                                    SWT.YES | SWT.NO);
                    box.setText(Messages.SaveValueAsQuery_Overwrite);
                    box.setMessage(MessageUtil.format(Messages.SaveValueAsQuery_FileExists, file.getAbsolutePath()));

                    int retValue = box.open();
                    goAhead[0] = retValue == SWT.YES;
                }
            });

            if (!goAhead[0])
                throw new IProgressListener.OperationCanceledException();
        }
    }

    private void writeStringData(IProgressListener listener) throws Exception
    {
        FileOutputStream out = new FileOutputStream(file);

        try
        {
            listener.beginTask(Messages.SaveValueAsQuery_Saving, objects.size());
            PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, System
                            .getProperty("file.encoding")))); //$NON-NLS-1$
            try
            {

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
                        final int length = info.getLength();
                        final int end = Math.min(info.getOffset() + info.getCount(), length);

                        int offset = info.getOffset();

                        while (offset < end)
                        {
                            int read = Math.min(4092, end - offset);
                            char[] array = info.getChars(offset, read);

                            writer.append(new String(array));

                            offset += read;
                        }
                    }

                    isFirst = false;
                    listener.worked(1);
                    if (listener.isCanceled())
                        break;
                }

                writer.flush();
            }
            finally
            {
                writer.close();
            }
        }
        finally
        {
            listener.done();
            out.close();
        }
    }

    private void writeRawData(IProgressListener listener) throws Exception
    {
        IContextObject obj = objects.get(0);
        if (obj.getObjectId() < 0)
            return;

        IObject object = snapshot.getObject(obj.getObjectId());
        if (!(object instanceof IPrimitiveArray))
        {
            writeStringData(listener);
            return;
        }

        IPrimitiveArray array = (IPrimitiveArray) object;

        FileOutputStream out = new FileOutputStream(file);

        try
        {
            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(out));
            try 
            {
                int size = array.getLength();
                listener.beginTask(Messages.SaveValueAsQuery_SavingBinaryValue, size);
                int offset = 0;

                while (offset < size)
                {
                    int read = Math.min(4092, size - offset);
                    Object valueArray = array.getValueArray(offset, read);

                    switch (array.getType())
                    {
                        case IObject.Type.BOOLEAN:
                        {
                            boolean[] a = (boolean[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeBoolean(a[ii]);
                            break;
                        }
                        case IObject.Type.BYTE:
                        {
                            byte[] a = (byte[]) valueArray;
                            writer.write(a);
                            break;
                        }
                        case IObject.Type.CHAR:
                        {
                            char[] a = (char[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeChar(a[ii]);
                            break;
                        }
                        case IObject.Type.DOUBLE:
                        {
                            double[] a = (double[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeDouble(a[ii]);
                            break;
                        }
                        case IObject.Type.FLOAT:
                        {
                            float[] a = (float[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeFloat(a[ii]);
                            break;
                        }
                        case IObject.Type.INT:
                        {
                            int[] a = (int[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeInt(a[ii]);
                            break;
                        }
                        case IObject.Type.LONG:
                        {
                            long[] a = (long[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeLong(a[ii]);
                            break;
                        }
                        case IObject.Type.SHORT:
                        {
                            short[] a = (short[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeShort(a[ii]);
                            break;
                        }
                        default:
                            throw new SnapshotException(MessageUtil.format(
                                            Messages.SaveValueAsQuery_UnrecognizedPrimitiveArrayType, array.getType()));
                    }

                    offset += read;
                    listener.worked(1);
                    if (listener.isCanceled())
                        break;
                }

                writer.flush();
            }
            finally
            {
                listener.done();
                writer.close();
            }
        }
        finally
        {
            out.close();
        }
    }
}

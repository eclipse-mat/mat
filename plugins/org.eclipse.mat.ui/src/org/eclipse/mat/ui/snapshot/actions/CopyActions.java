/*******************************************************************************
 * Copyright (c) 2008, 2017 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - updates for Java 9 and progress indicators
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.actions;

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

public abstract class CopyActions implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument
    public List<IContextObject> elements;

    @Argument
    public Display display;

    public IResult execute(IProgressListener listener) throws Exception
    {
        try
        {
            final StringBuilder buf = new StringBuilder(128);
            String lineSeparator = System.getProperty("line.separator"); //$NON-NLS-1$

            listener.beginTask(Messages.CopyActions_CopyingToClipboard, elements.size());
            for (IContextObject argument : elements)
            {
                int objectId = argument.getObjectId();

                if (objectId >= 0)
                {
                    IObject object = snapshot.getObject(objectId);

                    if (buf.length() > 0)
                        buf.append(lineSeparator);

                    appendValue(buf, object);
                }
                listener.worked(1);
                if (listener.isCanceled())
                    break;
            }
            listener.done();

            if (buf.length() > 0)
            {
                display.asyncExec(new Runnable()
                {
                    public void run()
                    {
                        Clipboard clipboard = new Clipboard(display);
                        clipboard.setContents(new Object[] { buf.toString() }, new Transfer[] { TextTransfer
                                        .getInstance() });
                        clipboard.dispose();
                    }
                });
            }

            // let the UI ignore this query
            throw new IProgressListener.OperationCanceledException();
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected abstract void appendValue(StringBuilder buf, IObject object) throws SnapshotException;

    // //////////////////////////////////////////////////////////////
    // several copy actions
    // //////////////////////////////////////////////////////////////

    @Icon("/icons/copy.gif")
    public static class Address extends CopyActions
    {
        protected void appendValue(StringBuilder buf, IObject object)
        {
            buf.append("0x").append(Long.toHexString(object.getObjectAddress()));//$NON-NLS-1$
        }
    }

    @Icon("/icons/copy.gif")
    public static class FQClassName extends CopyActions
    {
        protected void appendValue(StringBuilder buf, IObject object)
        {
            if (object instanceof IClass)
                buf.append(((IClass) object).getName());
            else
                buf.append(object.getClazz().getName());
        }
    }

    @Icon("/icons/copy.gif")
    public static class Value extends CopyActions
    {
        protected void appendValue(StringBuilder buf, IObject object) throws SnapshotException
        {
            ExportInfo info = ExportInfo.of(object);

            if (info != null)
            {
                final int length = info.getLength();
                final int end = Math.min(info.getOffset() + info.getCount(), length);

                int offset = info.getOffset();

                while (offset < end)
                {
                    int read = Math.min(4092, end - offset);
                    char[] array = info.getChars(offset, read);
                    buf.append(new String(array));

                    offset += read;
                }
            }
            else
            {
                String name = object.getClassSpecificName();
                buf.append(name != null ? name : object.getTechnicalName());
            }
        }
    }

}

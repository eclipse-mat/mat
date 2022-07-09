/*******************************************************************************
 * Copyright (c) 2008, 2022 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - updates for Java 9 and progress indicators
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.actions;

import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.util.IProgressListener;
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
                else
                {
                    appendValue(buf, argument);
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
                        Copy.copyToClipboard(buf.toString(), display);
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
    protected void appendValue(StringBuilder buf, IContextObject ctx) throws SnapshotException
    {}


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
        protected void appendValue(StringBuilder buf, IContextObject ctx) throws SnapshotException
        {
            if (ctx instanceof IContextObjectSet)
            {
                IContextObjectSet ctxs = (IContextObjectSet)ctx;
                String oql = ctxs.getOQL();
                if (oql != null && ctxs.getObjectIds().length == 0)
                {
                    String dummy = OQL.forAddress(0x0);
                    if (oql.startsWith(dummy.substring(0, dummy.length() - 3)))
                    {
                        // Special OQL indicating unindexed object
                        String addr = oql.substring(dummy.length() - 3);
                        if (addr.matches("0[xX][0-9A-Fa-f]+")) //$NON-NLS-1$
                        {
                            if (buf.length() > 0)
                                buf.append(System.lineSeparator());
                            buf.append(addr);
                        }
                    }
                }
            }
        }
    }

    static abstract class CopyActions2 extends CopyActions
    {
        protected void appendValue(StringBuilder buf, IContextObject ctx) throws SnapshotException
        {
            if (ctx instanceof IContextObjectSet)
            {
                IContextObjectSet ctxs = (IContextObjectSet)ctx;
                String oql = ctxs.getOQL();
                if (oql != null && ctxs.getObjectIds().length == 0)
                {
                    String dummy = OQL.forAddress(0x0);
                    if (oql.startsWith(dummy.substring(0, dummy.length() - 3)))
                    {
                        // Special OQL indicating unindexed object
                        String addr = oql.substring(dummy.length() - 3);
                        if (addr.matches("0[xX][0-9A-Fa-f]+")) //$NON-NLS-1$
                        {
                            long l = Long.parseUnsignedLong(addr.substring(2), 16);
                            ObjectReference ref = new ObjectReference(snapshot, l);
                            IObject obj = ref.getObject();
                            if (buf.length() > 0)
                                buf.append(System.lineSeparator());
                            appendValue(buf, obj);
                        }
                    }
                }
            }
        }
    }

    @Icon("/icons/copy.gif")
    public static class FQClassName extends CopyActions2
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
    public static class Value extends CopyActions2
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

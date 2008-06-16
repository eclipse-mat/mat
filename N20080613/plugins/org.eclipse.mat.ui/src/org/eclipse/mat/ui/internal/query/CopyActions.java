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
package org.eclipse.mat.ui.internal.query;

import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.ClassSpecificNameResolverRegistry;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.PrettyPrinter;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.ui.PlatformUI;

public abstract class CopyActions extends Action
{
    ISnapshot snapshot;
    List<IContextObject> elements;

    public CopyActions(String name, ISnapshot snapshot, List<IContextObject> elements)
    {
        super(name);
        this.snapshot = snapshot;
        this.elements = elements;
    }

    @Override
    public void run()
    {
        try
        {
            StringBuilder buf = new StringBuilder(128);
            String lineSeparator = System.getProperty("line.separator");

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
            }

            if (buf.length() > 0)
            {
                Clipboard clipboard = new Clipboard(PlatformUI.getWorkbench().getDisplay());
                clipboard.setContents(new Object[] { buf.toString() }, new Transfer[] { TextTransfer.getInstance() });
                clipboard.dispose();
            }

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

    public static class Address extends CopyActions
    {
        public Address(ISnapshot snapshot, List<IContextObject> elements)
        {
            super("Address", snapshot, elements);
            setToolTipText("Copy address of the current object to the clipboard.");
        }

        protected void appendValue(StringBuilder buf, IObject object)
        {
            buf.append("0x").append(Long.toHexString(object.getObjectAddress()));
        }

    }

    public static class FQClassName extends CopyActions
    {
        public FQClassName(ISnapshot snapshot, List<IContextObject> elements)
        {
            super("Class Name", snapshot, elements);
            setToolTipText("Copy the fully qualified class name of the current object to the clipboard.");
        }

        protected void appendValue(StringBuilder buf, IObject object)
        {
            if (object instanceof IClass)
                buf.append(((IClass) object).getName());
            else
                buf.append(object.getClazz().getName());
        }

    }

    public static class Value extends CopyActions
    {
        public Value(ISnapshot snapshot, List<IContextObject> elements)
        {
            super("Value", snapshot, elements);
            setToolTipText("Copy the string value to the clipboard.");
        }

        protected void appendValue(StringBuilder buf, IObject object) throws SnapshotException
        {
            String text = null;
            if ("java.lang.String".equals(object.getClazz().getName()))
            {
                text = PrettyPrinter.objectAsString(object, Integer.MAX_VALUE);
            }
            else if ("char[]".equals(object.getClazz().getName()))
            {
                IPrimitiveArray charArray = (IPrimitiveArray) object;
                text = PrettyPrinter.arrayAsString(charArray, 0, charArray.getLength(), charArray.getLength());
            }
            else
            {
                text = object.getClassSpecificName();
                text = ClassSpecificNameResolverRegistry.resolve(object);
            }

            if (text != null)
                buf.append(text);
            else
                buf.append(object.getTechnicalName());
        }

    }

}

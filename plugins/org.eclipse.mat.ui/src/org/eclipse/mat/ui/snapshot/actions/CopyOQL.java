/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
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
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

/**
 * Combines all the OQL queries associated with the IContextObjectSets into one big query.
 */
@Icon("/icons/copy.gif")
public class CopyOQL implements IQuery
{
    @Argument
    public List<IContextObject> elements;

    @Argument
    public Display display;

    public IResult execute(IProgressListener listener) throws Exception
    {
        String lineSeparator = System.getProperty("line.separator"); //$NON-NLS-1$
        
        final StringBuilder buf = new StringBuilder(128);
        for (IContextObject element : elements)
        {
            if (element instanceof IContextObjectSet)
            {
                String buf1 = ((IContextObjectSet)element).getOQL();
                if (buf1 != null)
                {
                    OQL.union(buf, buf1);
                }
                else
                {
                    int ids[] = ((IContextObjectSet)element).getObjectIds();
                    String oql = OQL.forObjectIds(ids);
                    if (oql != null)
                    {
                        OQL.union(buf, oql);
                    }
                }
            }
            else
            {
                int id = element.getObjectId();
                if (id >= 0)
                {
                    OQL.union(buf, OQL.forObjectId(id));
                }
            }
            if (buf.length() > 60000)
            {
                // A huge OQL statement will take too long to build and execute, so give up now.
                throw new SnapshotException(Messages.CopyOQL_TooBig);
            }
        }

        buf.append(lineSeparator);

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

        // let the UI ignore this query
        throw new IProgressListener.OperationCanceledException();

    }

}

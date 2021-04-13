/*******************************************************************************
 * Copyright (c) 2008, 2021 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - check box for booleans
 *******************************************************************************/
package org.eclipse.mat.ui.internal.query.arguments;

import java.io.File;

import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.internal.query.arguments.CheckBoxEditor.Type;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableItem;

public class TableEditorFactory
{
    public static ArgumentEditor createTableEditor(Composite parent, IQueryContext context,
                    ArgumentDescriptor descriptor, TableItem item)
    {
        if (descriptor.isBoolean())
        {
            //return new BooleanComboEditor(parent, context, descriptor, item);
            return new CheckBoxEditor(parent, context, descriptor, item, Type.GENERAL);
        }
        else if (ISnapshot.class.isAssignableFrom(descriptor.getType()))
        {
            return new SnapshotSelectionEditor(parent, context, descriptor, item);
        }
        else if (File.class.isAssignableFrom(descriptor.getType()))
        {
            return new FileOpenDialogEditor(parent, context, descriptor, item);
        }
        else if (descriptor.isEnum())
        {
            return new EnumComboEditor(parent, context, descriptor, item);
        }
        else
        {
            return new TextEditor(parent, context, descriptor, item);
        }
    }
}

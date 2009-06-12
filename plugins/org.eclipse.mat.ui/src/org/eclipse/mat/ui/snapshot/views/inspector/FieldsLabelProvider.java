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
package org.eclipse.mat.ui.snapshot.views.inspector;

import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.snapshot.views.inspector.FieldsContentProvider.MoreNode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

class FieldsLabelProvider extends LabelProvider implements ITableLabelProvider, ITableFontProvider
{
    private final InspectorView inspectorView;
    private Font italicFont;
    private Font boldFont;

    public FieldsLabelProvider(InspectorView inspectorView, Font defaultFont)
    {
        this.inspectorView = inspectorView;
        FontDescriptor fontDescriptor = FontDescriptor.createFrom(defaultFont);
        this.italicFont = fontDescriptor.setStyle(SWT.ITALIC).createFont(Display.getDefault());
        this.boldFont = fontDescriptor.setStyle(SWT.BOLD).createFont(Display.getDefault());
    }

    public Image getColumnImage(Object element, int columnIndex)
    {
        if (columnIndex != 2)
            return null;

        if (element instanceof MoreNode)
            return MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.PLUS);

        return null;
    }

    public String getColumnText(Object element, int columnIndex)
    {
        if (element instanceof MoreNode)
        {
            switch (columnIndex)
            {
                case 2:
                    return ((MoreNode) element).toString();
            }
        }
        else if (element instanceof FieldNode)
        {
            Field field = ((FieldNode) element).getField();
            switch (columnIndex)
            {
                case 0:
                    return field.getVerboseSignature();
                case 1:
                    return field.getName();
                case 2:
                    return String.valueOf(field.getValue());
            }
        }
        else if (element instanceof NamedReferenceNode)
        {
            NamedReferenceNode node = ((NamedReferenceNode) element);
            switch (columnIndex)
            {
                case 0:
                    return "ref";//$NON-NLS-1$
                case 1:
                    return node.getName();
                case 2:
                    return getObjectLabel(node.getObjectAddress());
            }
        }

        return null;
    }

    private String getObjectLabel(long objectAddress)
    {
        try
        {
            IObject object = this.inspectorView.snapshot.getObject(this.inspectorView.snapshot
                            .mapAddressToId(objectAddress));
            String text = object.getClassSpecificName();
            if (text == null)
                text = object.getTechnicalName();
            return text;
        }
        catch (SnapshotException e)
        {
            // $JL-EXC$
            return "0x" + Long.toHexString(objectAddress);//$NON-NLS-1$
        }
    }

    public Font getFont(Object element, int columnIndex)
    {
        if ((element instanceof NamedReferenceNode && ((NamedReferenceNode) element).isStatic())
                        || (element instanceof FieldNode && ((FieldNode) element).isStatic()))
        {
            return italicFont;
        }
        else if (element instanceof FieldsContentProvider.MoreNode)
        {
            return boldFont;
        }
        else
        {
            return null;
        }
    }

    @Override
    public void dispose()
    {
        italicFont.dispose();
        boldFont.dispose();
    }

}

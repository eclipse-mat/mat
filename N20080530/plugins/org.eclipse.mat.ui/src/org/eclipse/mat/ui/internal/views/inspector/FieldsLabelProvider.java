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
package org.eclipse.mat.ui.internal.views.inspector;

import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.internal.views.inspector.FieldsContentProvider.MoreNode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;


class FieldsLabelProvider extends LabelProvider implements ITableLabelProvider, ITableFontProvider
{
    private final InspectorView inspectorView;
    private Font italicFont;

    public FieldsLabelProvider(InspectorView inspectorView, Font defaultFont)
    {
        this.inspectorView = inspectorView;
        FontData[] fontData = defaultFont.getFontData();
        for (FontData data : fontData)
            data.setStyle(SWT.ITALIC);
        this.italicFont = new Font(null, fontData);

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
                    return "ref";
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
            return "0x" + Long.toHexString(objectAddress);
        }
    }

    public Font getFont(Object element, int columnIndex)
    {
        if ((element instanceof NamedReferenceNode && ((NamedReferenceNode) element).isStatic())
                        || (element instanceof FieldNode && ((FieldNode) element).isStatic()))
        {
            return italicFont;
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
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.views;

import java.io.File;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.ui.accessibility.AccessibleCompositeAdapter;
import org.eclipse.mat.ui.snapshot.editor.ISnapshotEditorInput;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public abstract class SnapshotOutlinePage extends Page implements IContentOutlinePage
{
    public static class HeapEditorOutlinePage extends SnapshotOutlinePage implements
                    ISnapshotEditorInput.IChangeListener
    {
        private ISnapshotEditorInput snapshotInput;

        public HeapEditorOutlinePage(ISnapshotEditorInput input)
        {
            this.snapshotInput = input;
            this.snapshotInput.addChangeListener(this);
        }

        @Override
        protected void createColumns()
        {
            super.createColumns();
            TreeColumn column = new TreeColumn(treeViewer.getTree(), SWT.LEFT);
            column.setText(Messages.baseline);
            column.setWidth(80);
        }

        public void onSnapshotLoaded(ISnapshot snapshot)
        {
            updateSnapshotInput();
        }

        public void onBaselineLoaded(ISnapshot snapshot)
        {
            updateSnapshotInput();
        }

        @Override
        protected SnapshotInfo getBaseline()
        {
            return this.snapshotInput.hasBaseline() ? this.snapshotInput.getBaseline().getSnapshotInfo() : null;
        }

        @Override
        protected SnapshotInfo getSnapshot()
        {
            if (snapshotInput == null)
                return null;
            else
                return snapshotInput.hasSnapshot() ? snapshotInput.getSnapshot().getSnapshotInfo() : null;
        }

        @Override
        protected IPath getSnapshotPath()
        {
            return this.snapshotInput.getPath();
        }

        @Override
        public void dispose()
        {
            if (snapshotInput != null)
            {
                snapshotInput.removeChangeListener(this);
                snapshotInput = null;
            }
            super.dispose();
        }
    }

    class Label
    {
        String text;
        Object snapshotValue, baselineValue;

        public Label(String typeId, Object snapshotValue, Object baselineValue)
        {
            this.text = typeId;
            this.snapshotValue = snapshotValue;
            this.baselineValue = baselineValue;
        }

        public String getText()
        {
            return text;
        }

        public Object getBaselineValue()
        {
            return baselineValue;
        }

        public Object getSnapshotValue()
        {
            return snapshotValue;
        }
    }

    class Category
    {
        String categoryId;
        protected List<Label> children = new ArrayList<Label>();

        public Category(String categoryId)
        {
            this.categoryId = categoryId;
        }

        public String getCategoryId()
        {
            return categoryId;
        }

        public void addChild(Label label)
        {
            children.add(label);
        }
    }

    class OutlineContentProvider implements ITreeContentProvider
    {
        List<Object> elements;

        @SuppressWarnings("unchecked")
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
        {
            elements = (List<Object>) newInput;
        }

        public Object[] getElements(Object inputElement)
        {
            return elements.toArray();
        }

        public Object[] getChildren(Object parentElement)
        {
            return parentElement instanceof Category ? ((Category) parentElement).children.toArray() : null;
        }

        public Object getParent(Object element)
        {
            return null;
        }

        public boolean hasChildren(Object element)
        {
            return element instanceof Category;
        }

        public void dispose()
        {}
    }

    class OutlineLabelProvider extends LabelProvider implements ITableLabelProvider
    {

        public Image getColumnImage(Object element, int columnIndex)
        {
            return null;
        }

        public String getColumnText(Object element, int columnIndex)
        {
            if (element instanceof Category)
            {
                return columnIndex == 0 ? ((Category) element).getCategoryId() : null;
            }
            else if (element instanceof Label)
            {
                Label label = (Label) element;
                if (columnIndex == 0)
                {
                    return label.getText();
                }
                else
                {
                    Object obj = columnIndex == 1 ? label.getSnapshotValue() : label.getBaselineValue();
                    if (obj == null)
                    {
                        return null;
                    }
                    else if (Messages.identifier_size.equals(label.getText()))
                    {
                        int identifierSize = ((Integer) obj).intValue();
                        return MessageUtil.format(Messages.identifier_format, identifierSize);
                    }
                    else if ((obj instanceof Long) || (obj instanceof Integer))
                    {
                        return ((Number) obj).longValue() == 0 ? null : NumberFormat.getInstance().format(obj);
                    }
                    else if (obj instanceof Double)
                    {
                        return new DecimalFormat("#,##0.0 M").format(obj);//$NON-NLS-1$
                    }
                    else if (obj instanceof Date)
                    {
                        return Messages.date.equals(label.getText()) ? DateFormat.getDateInstance().format(obj)
                                        : DateFormat.getTimeInstance().format(obj);
                    }
                    else
                    {
                        return String.valueOf(obj);
                    }
                }
            }

            return null;
        }
    }

    protected TreeViewer treeViewer;

    @Override
    public void createControl(Composite parent)
    {
        treeViewer = new TreeViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);

        createColumns();

        AccessibleCompositeAdapter.access(treeViewer.getTree());

        treeViewer.getTree().setLinesVisible(true);
        treeViewer.getTree().setHeaderVisible(true);

        treeViewer.setContentProvider(new OutlineContentProvider());
        treeViewer.setLabelProvider(new OutlineLabelProvider());

        updateSnapshotInput();
    }

    protected void createColumns()
    {
        TreeColumn column = new TreeColumn(treeViewer.getTree(), SWT.LEFT);
        column.setText(Messages.col_property);
        column.setWidth(160);

        column = new TreeColumn(treeViewer.getTree(), SWT.LEFT);
        column.setText(Messages.col_file);
        column.setWidth(80);
    }

    protected void updateSnapshotInput()
    {
        List<Object> elements = new ArrayList<Object>();

        SnapshotInfo info = getSnapshot();

        if (info != null)
        {
            SnapshotInfo bInfo = getBaseline();

            if (bInfo == null)
                bInfo = new SnapshotInfo(null, null, null, 0, null, 0, 0, 0, 0, 0);

            // resource property (filename)
            int p = info.getPath().lastIndexOf(File.separatorChar);
            String filename = p >= 0 ? info.getPath().substring(p + 1) : info.getPath();
            String bFilename = null;
            if (bInfo.getPath() != null)
            {
                p = bInfo.getPath().lastIndexOf(File.separatorChar);
                bFilename = p >= 0 ? bInfo.getPath().substring(p + 1) : bInfo.getPath();
            }
            elements.add(new Label(Messages.resource, filename, bFilename));

            Category category = new Category(Messages.general_info);
            elements.add(category);

            category.addChild(new Label(Messages.format, info.getProperty("$heapFormat"), bInfo //$NON-NLS-1$
                            .getProperty("$heapFormat"))); //$NON-NLS-1$
            category.addChild(new Label(Messages.jvm_version, info.getJvmInfo(), bInfo.getJvmInfo()));
            category.addChild(new Label(Messages.time, info.getCreationDate(), bInfo.getCreationDate()));
            category.addChild(new Label(Messages.date, info.getCreationDate(), bInfo.getCreationDate()));
            category.addChild(new Label(Messages.identifier_size, info.getIdentifierSize(), bInfo.getIdentifierSize()));
            if (info.getIdentifierSize() == 8) 
            {
                Boolean useCompressedOops = (Boolean) info.getProperty("$useCompressedOops"); //$NON-NLS-1$
                if (useCompressedOops == null)
                {
                    useCompressedOops = false;
                }
                category.addChild(new Label(Messages.use_compressed_oops, useCompressedOops.toString(), "")); // TODO baseline
            }
            category.addChild(new Label(Messages.file_path, info.getPath(), bInfo.getPath()));
            Double fileLength = new Double((double) new File(info.getPath()).length() / (1024 * 1024));
            Double bFileLength = bInfo.getPath() != null ? new Double((double) new File(bInfo.getPath()).length()
                            / (1024 * 1024)) : null;
            category.addChild(new Label(Messages.file_length, fileLength, bFileLength));

            category = new Category(Messages.statistic_info);
            elements.add(category);

            category.addChild(new Label(Messages.heap, info.getUsedHeapSize(), bInfo.getUsedHeapSize()));
            category.addChild(new Label(Messages.number_of_objects, info.getNumberOfObjects(), bInfo
                            .getNumberOfObjects()));
            category.addChild(new Label(Messages.number_of_classes, info.getNumberOfClasses(), bInfo
                            .getNumberOfClasses()));
            category.addChild(new Label(Messages.number_of_classloaders, info.getNumberOfClassLoaders(), bInfo
                            .getNumberOfClassLoaders()));
            category.addChild(new Label(Messages.number_of_gc_roots, info.getNumberOfGCRoots(), bInfo
                            .getNumberOfGCRoots()));
        }
        else if (getSnapshotPath() != null)
        {
            // create tree from file information
            IPath path = getSnapshotPath();
            File osFile = path.toFile();

            elements.add(new Label(Messages.resource, path.lastSegment(), null));

            Category category = new Category(Messages.general_info);
            elements.add(category);
            category.addChild(new Label(Messages.format, null, null));
            category.addChild(new Label(Messages.jvm_version, null, null));
            category.addChild(new Label(Messages.time, new Date(osFile.lastModified()), null));
            category.addChild(new Label(Messages.date, new Date(osFile.lastModified()), null));
            category.addChild(new Label(Messages.identifier_size, null, null));
            category.addChild(new Label(Messages.file_path, path.toOSString(), null));

            final Double fileLength = new Double((double) osFile.length() / (1024 * 1024));
            category.addChild(new Label(Messages.file_length, fileLength, null));

        }

        treeViewer.getTree().setRedraw(false);
        treeViewer.setInput(elements);
        treeViewer.expandAll();
        treeViewer.getTree().setRedraw(true);
    }

    protected abstract IPath getSnapshotPath();

    protected abstract SnapshotInfo getSnapshot();

    protected abstract SnapshotInfo getBaseline();

    @Override
    public Control getControl()
    {
        return treeViewer.getControl();
    }

    @Override
    public void setFocus()
    {
        treeViewer.getControl().setFocus();
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        treeViewer.addSelectionChangedListener(listener);
    }

    public ISelection getSelection()
    {
        return treeViewer.getSelection();
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener)
    {
        treeViewer.removeSelectionChangedListener(listener);
    }

    public void setSelection(ISelection selection)
    {
        treeViewer.setSelection(selection);
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ui.part.IPageBookViewPage#init(org.eclipse.ui.part.IPageSite)
     */
    @Override
    public void init(IPageSite pageSite)
    {
        super.init(pageSite);
        pageSite.setSelectionProvider(this);
    }

}

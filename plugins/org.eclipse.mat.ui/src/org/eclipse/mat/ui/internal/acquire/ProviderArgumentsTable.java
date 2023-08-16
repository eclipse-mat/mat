/*******************************************************************************
 * Copyright (c) 2010, 2023 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - refactor for new/import wizard
 *******************************************************************************/
package org.eclipse.mat.ui.internal.acquire;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.acquire.HeapDumpProviderDescriptor;
import org.eclipse.mat.internal.acquire.VmInfoDescriptor;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;
import org.eclipse.mat.query.registry.AnnotatedObjectArgumentsSet;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.internal.query.arguments.ArgumentEditor;
import org.eclipse.mat.ui.internal.query.arguments.ArgumentEditor.IEditorListener;
import org.eclipse.mat.ui.internal.query.arguments.LinkEditor.Mode;
import org.eclipse.mat.ui.internal.query.arguments.TableEditorFactory;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/**
 * Handles a table of arguments - either for a particular dump or for a particular dump provider type.
 *
 */
public class ProviderArgumentsTable implements IEditorListener/*, ProcessSelectionListener*/
{

    private static final int MIN_EDITOR_WIDTH = 50;

    private static final String ARGUMENT = Messages.ArgumentsTable_Argument;
    private static final String VALUE = Messages.ArgumentsTable_Value;

    private LocalResourceManager resourceManager;

    private Table table;
    private Font boldFont;
    private Font normalFont;
    private int tableRowHeight = SWT.DEFAULT;

    private IAnnotatedObjectDescriptor providerDescriptor;
    private AnnotatedObjectArgumentsSet argumentSet;
    private IQueryContext context;

    private List<ITableListener> listeners = Collections.synchronizedList(new ArrayList<ITableListener>());
    private Map<ArgumentEditor, String> errors = Collections.synchronizedMap(new HashMap<ArgumentEditor, String>());

    public interface ITableListener
    {
        void onInputChanged();

        void onValueChanged();

        void onError(String message);

        void onFocus(String message);
    }

    public ProviderArgumentsTable(Composite parent, int style/*, ProviderArgumentsWizzardPage wizzardPage*/)
    {
        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        parent.setLayout(tableColumnLayout);

        TableViewer tableViewer = new TableViewer(parent, style);
        table = tableViewer.getTable();
        Font parentFont = parent.getFont();
        table.setFont(parentFont);
        table.setLinesVisible(true);
        table.setHeaderVisible(true);

        TableColumn column = new TableColumn(table, SWT.NONE);
        column.setText(ARGUMENT);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(0, 100));

        column = new TableColumn(table, SWT.NONE);
        column.setText(VALUE);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(100, 100));

        ColumnViewerToolTipSupport.enableFor(tableViewer);
        tableViewer.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getToolTipText(Object element)
            {
                if (element instanceof ArgumentEditor)
                    return ((ArgumentEditor) element).getToolTipText();
                return super.getToolTipText(element);
            }
        });

        resourceManager = new LocalResourceManager(JFaceResources.getResources(), table);
        boldFont = resourceManager.createFont(FontDescriptor.createFrom(parentFont).setStyle(SWT.BOLD));
        normalFont = resourceManager.createFont(FontDescriptor.createFrom(parentFont).setStyle(SWT.NORMAL));

        table.addListener(SWT.MeasureItem, new Listener()
        {
            public void handleEvent(Event event)
            {
                event.height = tableRowHeight;
            }
        });
    }

    private void setTableRowHeight(int height)
    {
        if (height > tableRowHeight)
        {
            tableRowHeight = height;
            table.pack();
            table.getParent().pack();
        }
    }

    public AnnotatedObjectArgumentsSet getArgumentSet()
    {
        return argumentSet;
    }

    public IAnnotatedObjectDescriptor getProviderDescriptor()
    {
        return providerDescriptor;
    }

    void createTableContent()
    {
        List<ArgumentDescriptor> argumentDescriptors = providerDescriptor.getArguments();

        for (ArgumentDescriptor descriptor : argumentDescriptors)
        {
            String flag = createArgumentLabel(descriptor);

            Object argumentValue = argumentSet.getArgumentValue(descriptor);

            if (descriptor.isMultiple())
            {
                List<?> values = (List<?>) argumentValue;

                if (values == null) values = (List<?>) descriptor.getDefaultValue();

                if (values == null || values.isEmpty())
                {
                    addEditorRow(descriptor, flag, null, -1);
                }
                else
                {
                    Iterator<?> valueIt = values.iterator();
                    Object firstValue = valueIt.next();
                    addEditorRow(descriptor, flag, firstValue, -1);
                    while (valueIt.hasNext())
                    {
                        Object objValue = valueIt.next();
                        addEditorRow(descriptor, "..\"..", objValue, -1); //$NON-NLS-1$
                    }
                    addEditorRow(descriptor, "..\"..", null, -1); //$NON-NLS-1$
                }
            }
            else
            {
                Object value = argumentValue;
                if (value == null) value = descriptor.getDefaultValue();

                addEditorRow(descriptor, flag, value, -1);
            }
        }

        for (Control control : table.getChildren())
        {
            if (control instanceof ArgumentEditor)
                ((ArgumentEditor) control).addListener(this);
        }
        try
        {
            table.getChildren()[0].setFocus();
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            // $JL-EXC$
            // should not happen as we assume that table should have at least
            // one child.
            // If by any reason the exception occurs, the focus will not be set
        }

        // Get the argument table looking nice with everything lined up
        table.pack();
        table.getParent().pack();
    }

    public void addListener(ITableListener listener)
    {
        this.listeners.add(listener);
    }

    private String createArgumentLabel(ArgumentDescriptor descriptor)
    {
        String flag = descriptor.getFlag();
        if (flag == null) return descriptor.getName();
        else return "-" + flag;//$NON-NLS-1$
    }

    private void addEditorRow(ArgumentDescriptor descriptor, String flag, Object value, int index)
    {
        TableItem item;
        if (index > 0) item = new TableItem(table, SWT.NONE, index);
        else item = new TableItem(table, SWT.NONE);

        item.setText(flag);

        setFont(descriptor, item);

        TableEditor editor = createEditor();

        ArgumentEditor aec = TableEditorFactory.createTableEditor(table, context, descriptor, item);
        aec.setFont(item.getFont());
        aec.setToolTipText(descriptor.getHelp());
        editor.setEditor(aec, item, 1);
        item.setData(aec);
        // Adjust the table height for the editor
        setTableRowHeight(aec.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);

        // listener should be added only to the new rows, for rows with default
        // values listeners are added after the table is created and filled with
        // default values
        if (index > 0)
        {
            aec.addListener(this);

            // ugly: w/o pack, the table does not redraw the editors correctly
            table.pack();
            table.getParent().pack();

            setNewTabOrder();
        }

        try
        {
            if (value != null) aec.setValue(value);
        }
        catch (SnapshotException e)
        {
            // $JL-EXC$
            // leave editor empty
        }
    }

    private void setFont(ArgumentDescriptor descriptor, TableItem item)
    {
        // to make the rows in the table a little higher we set the bigger font
        // to the whole table.
        // and in this method we return the normal size and highlight mandatory
        // arguments.
        if (descriptor.isMandatory())
        {
            // Normal font for whole row
            item.setFont(normalFont);
            // Bold font for first box in row
            item.setFont(0, boldFont);
        }
        else item.setFont(normalFont);
    }

    private void setNewTabOrder()
    {
        // this method is called when the new row was inserted to reassure that
        // the "Tab" button works correct.
        TableItem[] items = table.getItems();
        // we need to include to the TabList only those items that are editors
        Control[] newTabOrder = new Control[table.getChildren().length];
        for (int i = 0, j = 0; i < items.length; i++, j++)
        {
            if (items[i].getData() != null) newTabOrder[j] = (ArgumentEditor) items[i].getData();
            else j--;
        }

        table.setTabList(newTabOrder);
    }

    private TableEditor createEditor()
    {
        TableEditor editor = new TableEditor(table);
        editor.horizontalAlignment = SWT.LEFT;
        editor.grabHorizontal = true;
        editor.minimumWidth = MIN_EDITOR_WIDTH;

        return editor;
    }

    public synchronized void onValueChanged(Object value, ArgumentDescriptor descriptor, TableItem item, ArgumentEditor argEditor)
    {
        int myIndex = table.indexOf(item);

        // remove error message
        onError(argEditor, null);
        onError(null, null);

        boolean isLastOne = descriptor.isMultiple()
                        && (myIndex + 1 == table.getItemCount() || ((ArgumentEditor) table.getItem(myIndex + 1).getData()).getDescriptor() != descriptor);

        // update lists
        if (descriptor.isMultiple())
        {
            List<Object> values = new ArrayList<Object>();

            Control[] children = table.getChildren();
            for (int ii = 0; ii < children.length; ii++)
            {
                if (!(children[ii] instanceof ArgumentEditor)) continue;

                ArgumentEditor editor = (ArgumentEditor) children[ii];
                if (editor.getDescriptor() == descriptor)
                {
                    Object v = editor.getValue();
                    if (v != null) values.add(v);
                }
            }

            if (values.isEmpty()) values = null;

            Object defaultValue = descriptor.getDefaultValue();

            if (defaultValue == null || !defaultValue.equals(values))
            {
                argumentSet.setArgumentValue(descriptor, values);
            }
            else
            {
                argumentSet.removeArgumentValue(descriptor);
            }

            // warn a/b mandatory arguments
            if (descriptor.isMandatory() && values == null) onError(argEditor, MessageUtil.format(Messages.ArgumentsTable_isMandatory, descriptor.getName()));

            // insert new row at myIndex + 1
            if (isLastOne && value != null) addEditorRow(descriptor, "..\"..", null, myIndex + 1);//$NON-NLS-1$
        }
        else
        {
            Object defaultValue = descriptor.getDefaultValue();

            if (defaultValue == null || !defaultValue.equals(value))
            {
                argumentSet.setArgumentValue(descriptor, value);
            }
            else
            {
                argumentSet.removeArgumentValue(descriptor);
            }

            // warn a/b mandatory arguments
            if (descriptor.isMandatory() && value == null) onError(argEditor, MessageUtil.format(Messages.ArgumentsTable_isMandatory, descriptor.getName()));
        }

        if (providerDescriptor instanceof VmInfoDescriptor)
        {
            VmInfoDescriptor vmd = (VmInfoDescriptor)providerDescriptor;
            try {
                AcquireSnapshotAction.AcquireDumpOperation.setupVmInfo(vmd.getVmInfo(), argumentSet);
            } catch (SnapshotException e) {
                // ignore - set later on generating the dump
            }
        }

        // inform about value changes
        fireValueChangedEvent();
    }

    private void fireInputChangedEvent()
    {
        synchronized (listeners)
        {
            for (ITableListener listener : listeners)
                listener.onInputChanged();
        }
    }

    private void fireValueChangedEvent()
    {
        synchronized (listeners)
        {
            for (ITableListener listener : listeners)
                listener.onValueChanged();
        }
    }

    public void onFocus(String message)
    {
        fireFocusChangedEvent(message);
    }

    public void onModeChange(Mode mode, ArgumentDescriptor descriptor)
    {
        // do nothing
    }

    public void onError(ArgumentEditor editor, String message)
    {
        synchronized (errors)
        {
            if (message == null)
            {
                errors.remove(editor);
                // There can be stale argument editors and errors, so remove them.
                // Perhaps better done on leaving a wizard page, but this works.
                for (Iterator<Map.Entry<ArgumentEditor,String>>i = errors.entrySet().iterator(); i.hasNext(); ) {
                    Map.Entry<ArgumentEditor,String>e = i.next();
                    if (e.getKey().isDisposed())
                    {
                        i.remove();
                    }
                }
                if (errors.isEmpty()) fireErrorMessageEvent(null);
                else fireErrorMessageEvent(errors.values().iterator().next());
            }
            else
            {
                errors.put(editor, message);
                fireErrorMessageEvent(message);
            }
        }
    }

    private void fireErrorMessageEvent(String message)
    {
        synchronized (listeners)
        {
            for (ITableListener listener : listeners)
                listener.onError(message);
        }
    }

    public void fireFocusChangedEvent(String message)
    {
        synchronized (listeners)
        {
            for (ITableListener listener : listeners)
                listener.onFocus(message);
        }
    }

    public void providerSelected(AnnotatedObjectArgumentsSet newArgumentsSet)
    {
        if (newArgumentsSet == null)
        {
            // The provider has been deselected
            providerDescriptor = null;
            clearTable();
            argumentSet = null;
            context = null;
            return;
        }
        IAnnotatedObjectDescriptor newProviderDescriptor = newArgumentsSet.getDescriptor();
        if (!newProviderDescriptor.equals(providerDescriptor))
        {
            // Obtain some default values in the table based on the current
            // values of the provider
            for (ArgumentDescriptor ad : newProviderDescriptor.getArguments())
            {
                try
                {
                    Object defaultValue;
                    if (newProviderDescriptor instanceof HeapDumpProviderDescriptor)
                    {
                        defaultValue = ad.getField().get(((HeapDumpProviderDescriptor)newProviderDescriptor).getHeapDumpProvider());
                    }
                    else if (newProviderDescriptor instanceof VmInfoDescriptor)
                    {
                        defaultValue = ad.getField().get(((VmInfoDescriptor)newProviderDescriptor).getVmInfo());
                    }
                    else
                    {
                        // Should never happen
                        defaultValue = null;
                    }
                    if (ad.isArray() && defaultValue != null)
                    {
                        // internally, all multiple values have their values held as arrays
                        // therefore we convert the array once and for all
                        int size = Array.getLength(defaultValue);
                        List<Object> l = new ArrayList<Object>(size);
                        for (int ii = 0; ii < size; ii++)
                        {
                            l.add(Array.get(defaultValue, ii));
                        }
                        Object old = ad.getDefaultValue();
                        ad.setDefaultValue(Collections.unmodifiableList(l));
                        // Has the default value changed, if so then
                        // perhaps the user should also reselect.
                        if (!Objects.deepEquals(old, defaultValue))
                            newArgumentsSet.removeArgumentValue(ad);
                    }
                    else
                    {
                        Object old = ad.getDefaultValue();
                        ad.setDefaultValue(defaultValue);
                        // Has the default value changed, if so then
                        // perhaps the user should also reselect.
                        if (!Objects.deepEquals(old, defaultValue))
                            newArgumentsSet.removeArgumentValue(ad);
                    }
                }
                catch (IllegalAccessException e)
                {}
            }
            providerDescriptor = newProviderDescriptor;

            clearTable();

            //            argumentSet = new ProviderArgumentsSet(providerDescriptor);
            argumentSet = newArgumentsSet;
            context = new ProviderContextImpl();
            createTableContent();
        }
        //        wizzardPage.updateDescription();
        fireInputChangedEvent();
    }

    private void clearTable()
    {
        table.removeAll(); // remove the table items

        /* remove all created editors */
        Control[] controls = table.getChildren();
        for (Control control : controls)
        {
            if (control instanceof ArgumentEditor) control.dispose();
        }
    }
}

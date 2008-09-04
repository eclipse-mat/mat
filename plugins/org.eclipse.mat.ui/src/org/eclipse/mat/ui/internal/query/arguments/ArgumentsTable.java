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
package org.eclipse.mat.ui.internal.query.arguments;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.HeapObjectContextArgument;
import org.eclipse.mat.internal.snapshot.HeapObjectParamArgument;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.ui.internal.query.arguments.LinkEditor.Mode;
import org.eclipse.mat.ui.internal.query.arguments.TextEditor.DecoratorType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

public class ArgumentsTable implements ArgumentEditor.IEditorListener
{
    private static final int MIN_EDITOR_WIDTH = 50;
    private static final String ADDRESS_PREFIX = "0x";

    /**
     * The end of line string for this machine.
     */
    protected static final String EOL = System.getProperty("line.separator", "\n");

    private static final String ARGUMENT = "Argument";
    private static final String VALUE = "Value";

    private LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());

    private Table table;
    private Font boldFont;
    private Font normalFont;

    private List<ITableListener> listeners = Collections.synchronizedList(new ArrayList<ITableListener>());

    private Map<ArgumentEditor, String> errors = Collections.synchronizedMap(new HashMap<ArgumentEditor, String>());
    private Mode mode = Mode.SIMPLE_MODE;
    private Map<ArgumentDescriptor, Mode> modeMap;

    private IQueryContext context;
    private ArgumentSet argumentSet;

    public interface ITableListener
    {
        void onInputChanged();

        void onError(String message);

        void onFocus(String message);

        void onModeChange(Mode mode);
    }

    public ArgumentsTable(Composite parent, int style, IQueryContext context, ArgumentSet argumentSet, Mode mode)
    {
        this.context = context;
        this.argumentSet = argumentSet;
        this.mode = mode;

        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        parent.setLayout(tableColumnLayout);

        table = new Table(parent, style);
        table.setLinesVisible(true);
        table.setHeaderVisible(true);

        TableColumn column = new TableColumn(table, SWT.NONE);
        column.setText(ARGUMENT);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(0, 100));

        column = new TableColumn(table, SWT.NONE);
        column.setText(VALUE);
        tableColumnLayout.setColumnData(column, new ColumnWeightData(100, 100));

        boldFont = resourceManager.createFont(FontDescriptor.createFrom(table.getFont()).setStyle(SWT.BOLD));
        normalFont = resourceManager.createFont(FontDescriptor.createFrom(table.getFont()).setStyle(SWT.NORMAL));
        Font newFont = resourceManager.createFont(FontDescriptor.createFrom(table.getFont()).increaseHeight(2));
        table.setFont(newFont);

        modeMap = new HashMap<ArgumentDescriptor, Mode>(argumentSet.getQueryDescriptor().getArguments().size());
        for (ArgumentDescriptor descriptor : argumentSet.getQueryDescriptor().getArguments())
        {
            if (isHeapObject(descriptor))
                modeMap.put(descriptor, mode);
        }

        createTableContent();

        new DefaultToolTip(table, ToolTip.NO_RECREATE, false)
        {
            private ArgumentDescriptor getEntry(Event event)
            {
                TableItem item = table.getItem(new Point(event.x, event.y));
                if (item != null && item.getData() != null) { return ((ArgumentEditor) item.getData()).getDescriptor(); }
                return null;
            }

            protected String getText(Event event)
            {
                ArgumentDescriptor entry = getEntry(event);
                if (entry != null) { return entry.getHelp(); }
                return null;
            }

            protected boolean shouldCreateToolTip(Event event)
            {
                table.setToolTipText(""); //$NON-NLS-1$
                return getEntry(event) != null && super.shouldCreateToolTip(event);
            }

            protected Object getToolTipArea(Event event)
            {
                return getEntry(event);
            }
        }.activate();

    }

    public void dispose()
    {
        if (resourceManager != null)
        {
            resourceManager.dispose();
            resourceManager = null;
        }
    }

    // //////////////////////////////////////////////////////////////
    // private constructor methods
    // //////////////////////////////////////////////////////////////

    private void createTableContent()
    {
        table.setData(argumentSet);

        for (ArgumentDescriptor descriptor : argumentSet.getQueryDescriptor().getArguments())
        {
            if (context.available(descriptor.getType(), descriptor.getAdvice()))
                continue;

            String flag = createArgumentLabel(descriptor);

            boolean isHeapObject = isHeapObject(descriptor);

            Object argumentValue = argumentSet.getArgumentValue(descriptor);

            if (IContextObject.class.isAssignableFrom(descriptor.getType()))
            {
                TableItem item = new TableItem(table, SWT.NONE);
                item.setFont(normalFont);
                item.setText(new String[] { flag, "selected rows" });
            }
            else if (descriptor.isMultiple() && !isHeapObject)
            {
                List<?> values = (List<?>) argumentValue;

                if (values == null)
                    values = (List<?>) descriptor.getDefaultValue();

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
                        addEditorRow(descriptor, "..\"..", objValue, -1);
                    }
                    addEditorRow(descriptor, "..\"..", null, -1);
                }
            }
            else if (isHeapObject && argumentValue instanceof HeapObjectContextArgument)
            {
                // when query is called for the certain object instance (from
                // the view). In that case hoa cannot be modified
                TableItem item = new TableItem(table, SWT.NONE);
                item.setFont(normalFont);
                item.setText(new String[] { flag, String.valueOf(argumentValue) });
            }
            else if (isHeapObject)
            {
                addHeapObjectTableItems(descriptor, (HeapObjectParamArgument) argumentValue);
            }
            else
            {
                Object value = argumentValue;
                if (value == null)
                    value = descriptor.getDefaultValue();

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
    }

    private boolean isHeapObject(ArgumentDescriptor descriptor)
    {
        boolean isHeapObject = descriptor.getAdvice() == Argument.Advice.HEAP_OBJECT //
                        || IObject.class.isAssignableFrom(descriptor.getType()) //
                        || IHeapObjectArgument.class.isAssignableFrom(descriptor.getType());
        return isHeapObject;
    }

    private String createArgumentLabel(ArgumentDescriptor descriptor)
    {
        String flag = descriptor.getFlag();
        if (flag == null)
            return descriptor.getName();
        else
            return "-" + flag;
    }

    private void addEditorRow(ArgumentDescriptor descriptor, String flag, Object value, int index)
    {
        TableItem item;
        if (index > 0)
            item = new TableItem(table, SWT.NONE, index);
        else
            item = new TableItem(table, SWT.NONE);

        item.setText(flag);

        setFont(descriptor, item);

        TableEditor editor = createEditor();

        ArgumentEditor aec = TableEditorFactory.createTableEditor(table, context, descriptor, item);
        editor.setEditor(aec, item, 1);
        item.setData(aec);

        // listener should be added only to the new rows, for rows with default
        // values listeners are added after the table is created and filled with
        // default values
        if (index > 0)
        {
            aec.addListener(this);

            // ugly: w/o pack, the table does not redraw the editors correctly
            table.getParent().pack();

            setNewTabOrder();
        }

        try
        {
            if (value != null)
                aec.setValue(value);
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
            item.setFont(boldFont);
        else
            item.setFont(normalFont);
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
            if (items[i].getData() != null)
                newTabOrder[j] = (ArgumentEditor) items[i].getData();
            else
                j--;
        }

        table.setTabList(newTabOrder);
    }

    private void addHeapObjectTableItems(ArgumentDescriptor descriptor, int index, TextEditor.DecoratorType decorator)
    {
        TableItem item = new TableItem(table, SWT.NONE, index);
        setFont(descriptor, item);

        TableEditor editor = createEditor();

        ImageTextEditor aec = new ImageTextEditor(table, context, descriptor, item, decorator);
        editor.setEditor(aec, item, 1);
        item.setData(aec);

        aec.addListener(this);
        // ugly: w/o pack, the table does not redraw the editors correctly
        table.getParent().pack();

        setNewTabOrder();

    }

    private void addHeapObjectTableItems(ArgumentDescriptor descriptor, HeapObjectParamArgument initialInput)
    {
        if (initialInput != null && (!initialInput.getAddresses().isEmpty() || !initialInput.getOqls().isEmpty()))
        {
            // check whether Mode.ADVANCED_MODE and switch to it if not the case
            verifyMode();
        }
        for (TextEditor.DecoratorType decorator : TextEditor.DecoratorType.values())
        {
            String label = "";

            if (decorator.equals(TextEditor.DecoratorType.PATTERN))
            {
                label = createArgumentLabel(descriptor);
                // when the mode of the table is switched after a pattern was
                // provided - keep the pattern
                List<Pattern> patterns = null;

                if (initialInput != null)
                {
                    patterns = initialInput.getPatterns();
                }
                // create a new row for each pattern
                if (patterns != null)
                {
                    for (Pattern pattern : patterns)
                    {
                        createHeapObjectRow(descriptor, pattern.toString(), decorator, label);
                        // a work around: to avoid "objects" label for each of
                        // the three editors
                        label = "";
                    }
                }
            }
            else if (decorator.equals(TextEditor.DecoratorType.OBJECT_ADDRESS))
            {
                List<Long> addresses = null;
                if (initialInput != null)
                {
                    addresses = initialInput.getAddresses();
                }
                // create a new row for each pattern
                if (addresses != null)
                {
                    for (Long address : addresses)
                    {
                        createHeapObjectRow(descriptor, ADDRESS_PREFIX + Long.toHexString(address), decorator, label);
                    }
                }
            }
            else
            // TextEditor.DecoratorType.QUERY
            {
                List<String> oqls = null;
                if (initialInput != null)
                {
                    oqls = initialInput.getOqls();
                }
                // create a new row for each pattern
                if (oqls != null)
                {
                    for (String oql : oqls)
                    {
                        createHeapObjectRow(descriptor, oql.toString(), decorator, label);
                    }
                }
            }

            // plus one empty row for this type of decorator in any case
            createHeapObjectRow(descriptor, null, decorator, label);

            if (modeMap.get(descriptor).equals(Mode.SIMPLE_MODE))
                break;

        }
        addCheckBoxRows(descriptor, CheckBoxEditor.Type.INCLUDE_CLASS_INSTANCE, (initialInput != null) ? initialInput
                        .isIncludeClassInstance() : false);

        if (modeMap.get(descriptor) == Mode.ADVANCED_MODE)
        {
            addCheckBoxRows(descriptor, CheckBoxEditor.Type.INCLUDE_SUBCLASSES, (initialInput != null) ? initialInput
                            .isIncludeSubclasses() : false);
            addCheckBoxRows(descriptor, CheckBoxEditor.Type.INTEPRET_AS_CLASSLOADER,
                            (initialInput != null) ? initialInput.isIncludeLoadedInstances() : false);
            addCheckBoxRows(descriptor, CheckBoxEditor.Type.RETAINED, (initialInput != null) ? initialInput
                            .isRetained() : false);
        }

        addLink(descriptor, modeMap.get(descriptor));
        table.getParent().pack();
    }

    private void verifyMode()
    {
        if (mode.equals(Mode.SIMPLE_MODE))
            mode = Mode.ADVANCED_MODE;

    }

    private void createHeapObjectRow(ArgumentDescriptor descriptor, String value, TextEditor.DecoratorType decorator,
                    String label)
    {
        TableItem item = new TableItem(table, SWT.NONE);
        item.setText(label);
        setFont(descriptor, item);

        TableEditor editor = createEditor();

        ImageTextEditor aec = new ImageTextEditor(table, context, descriptor, item, decorator);
        editor.setEditor(aec, item, 1);
        item.setData(aec);

        if (value != null)
        {
            try
            {
                aec.setValue(value);
            }
            catch (SnapshotException e)
            {
                // $JL-EXC$
                // leave editor empty
            }
        }
    }

    private void addLink(ArgumentDescriptor descriptor, Mode mode)
    {
        TableItem item = new TableItem(table, SWT.NONE);
        item.setText("");
        TableEditor editor = createEditor();

        LinkEditor aec = new LinkEditor(table, context, descriptor, item, mode);
        editor.setEditor(aec, item, 1);
        item.setData(aec);
    }

    private TableEditor createEditor()
    {
        TableEditor editor = new TableEditor(table);
        editor.horizontalAlignment = SWT.LEFT;
        editor.grabHorizontal = true;
        editor.minimumWidth = MIN_EDITOR_WIDTH;

        return editor;
    }

    private void addCheckBoxRows(ArgumentDescriptor descriptor, CheckBoxEditor.Type type, boolean selected)
    {
        TableItem item = new TableItem(table, SWT.NONE);
        item.setText("");

        TableEditor editor = createEditor();

        CheckBoxEditor aec = new CheckBoxEditor(table, context, descriptor, item, type);
        editor.setEditor(aec, item, 1);
        item.setData(aec);

        try
        {
            aec.setValue(selected);
        }
        catch (SnapshotException e)
        {
            // $JL-EXC$
            // leave unselected
        }
    }

    public synchronized void onValueChanged(Object value, ArgumentDescriptor descriptor, TableItem item,
                    ArgumentEditor argEditor)
    {
        int myIndex = table.indexOf(item);

        // remove error message
        onError(argEditor, null);
        onError(null, null);

        boolean isHeapObject = isHeapObject(descriptor);

        boolean isLastOne = descriptor.isMultiple()
                        && !isHeapObject
                        && (myIndex + 1 == table.getItemCount() || ((ArgumentEditor) table.getItem(myIndex + 1)
                                        .getData()).getDescriptor() != descriptor);

        // update argument set -- heap objects
        if (isHeapObject)
        {
            // if (value == null)
            // return;

            if (argEditor instanceof ImageTextEditor)
            {
                // verify current modification
                verifyHeapObjectTextEditorInput((ImageTextEditor) argEditor);
            }
            // if there are no errors in all the fields describing the hoa -
            // create HeapObject and add it to the argumentSet
            if (noErrorsInHoa())
            {
                // overwrite hoa on every modification
                argumentSet.removeArgumentValue(descriptor);
                HeapObjectParamArgument hoa = createHeapObjectDefinition(descriptor);

                if (hoa.isComplete())
                {
                    argumentSet.setArgumentValue(descriptor, hoa);
                    // add new row only when there are no errors
                    if ((argEditor instanceof ImageTextEditor)
                                    && isNewRowNeeded(descriptor, ((ImageTextEditor) argEditor).getDecorator()))
                    {
                        addHeapObjectTableItems(descriptor, myIndex + 1, ((ImageTextEditor) argEditor).getDecorator());
                    }
                }
                else if (descriptor.isMandatory())
                {
                    // add error message on null argument
                    onError(null, "Please provide a pattern, an object address or an OQL query");
                }

            }
        }
        // update lists
        else if (descriptor.isMultiple())
        {
            List<Object> values = new ArrayList<Object>();

            Control[] children = table.getChildren();
            for (int ii = 0; ii < children.length; ii++)
            {
                if (!(children[ii] instanceof ArgumentEditor))
                    continue;

                ArgumentEditor editor = (ArgumentEditor) children[ii];
                if (editor.getDescriptor() == descriptor)
                {
                    Object v = editor.getValue();
                    if (v != null)
                        values.add(v);
                }
            }

            if (values.isEmpty())
                values = null;

            Object defaultValue = descriptor.getDefaultValue();
            if (defaultValue == null || !defaultValue.equals(values))
                argumentSet.setArgumentValue(descriptor, values);
            else
                argumentSet.removeArgumentValue(descriptor);

            // warn a/b mandatory arguments
            if (descriptor.isMandatory() && values == null)
                onError(argEditor, MessageFormat.format("''{0}'' is mandatory", descriptor.getName()));

            // insert new row at myIndex + 1
            if (isLastOne && value != null)
                addEditorRow(descriptor, "..\"..", null, myIndex + 1);
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
            if (descriptor.isMandatory() && value == null)
                onError(argEditor, MessageFormat.format("''{0}'' is mandatory", descriptor.getName()));
        }

        // inform about value changes
        fireInputChangedEvent();
    }

    private boolean isNewRowNeeded(ArgumentDescriptor descriptor, DecoratorType decorator)
    {
        boolean notEmpty = true;
        Control[] children = table.getChildren();
        for (Control control : children)
        {
            if ((control instanceof ImageTextEditor) && ((ImageTextEditor) control).getDescriptor().equals(descriptor)
                            && ((ImageTextEditor) control).getDecorator().equals(decorator))
            {
                notEmpty = notEmpty && ((ImageTextEditor) control).getValue() != null
                                && !((ImageTextEditor) control).getValue().equals("");
            }
        }
        return notEmpty;
    }

    private HeapObjectParamArgument createHeapObjectDefinition(ArgumentDescriptor descriptor)
    {
        ISnapshot snapshot = (ISnapshot) context.get(ISnapshot.class, null);
        HeapObjectParamArgument hoa = new HeapObjectParamArgument(snapshot);

        Control[] children = table.getChildren();
        for (Control control : children)
        {
            if (!descriptor.equals(((ArgumentEditor) control).getDescriptor()))
                continue;

            if (control instanceof CheckBoxEditor)
            {
                Boolean value = (Boolean) ((CheckBoxEditor) control).getValue();
                switch (((CheckBoxEditor) control).getType())
                {
                    case INCLUDE_CLASS_INSTANCE:
                    {
                        hoa.setIncludeClassInstance(value);
                        break;
                    }
                    case INCLUDE_SUBCLASSES:
                    {
                        hoa.setIncludeSubclasses(value);
                        break;
                    }
                    case INTEPRET_AS_CLASSLOADER:
                    {
                        hoa.setIncludeLoadedInstances(value);
                        break;
                    }
                    case RETAINED:
                    {
                        hoa.setRetained(value);
                        break;
                    }
                    case VERBOSE:
                    {
                        hoa.setVerbose(value);
                        break;
                    }
                }
            }
            else if (control instanceof ImageTextEditor)
            {
                if (((ImageTextEditor) control).getValue() != null)
                {
                    String line = ((ImageTextEditor) control).getValue().toString().trim();

                    if (line.toLowerCase().startsWith("select"))
                    {
                        hoa.addOql(line);
                    }
                    else if (line.startsWith(ADDRESS_PREFIX))
                    {
                        hoa.addObjectAddress(new BigInteger(line.substring(2), 16).longValue());

                    }
                    else
                    // Pattern
                    {
                        if (!line.equals(""))
                        {
                            hoa.addPattern(Pattern.compile(line));
                        }

                    }
                }
            } // control of other types do not belong to the HeapObject
            // arguments
        }
        return hoa;
    }

    private void verifyHeapObjectTextEditorInput(ImageTextEditor editor)
    {
        TextEditor.DecoratorType decorator = editor.getDecorator();
        if (editor.getValue() != null)
        {
            onError(editor, null);

            String line = editor.getValue().toString().trim();

            if (decorator.equals(TextEditor.DecoratorType.QUERY))
            {
                if (line.equals(""))
                    return;
                try
                {
                    SnapshotFactory.createQuery(line);
                }
                catch (SnapshotException e)
                {
                    // $JL-EXC$

                    // fix: reformat message for proper displaying
                    String msg = e.getMessage();

                    if (msg.startsWith("Encountered"))
                    {
                        int p = msg.indexOf("Was expecting");
                        if (p >= 0)
                            msg = msg.substring(p, msg.length()).replace(EOL, " ");
                    }

                    onError(editor, msg);
                }
            }
            else if (decorator.equals(TextEditor.DecoratorType.OBJECT_ADDRESS))
            {
                if (line.length() > 2 && ADDRESS_PREFIX.equals(line.substring(0, 2)))
                {
                    try
                    {
                        new BigInteger(line.substring(2), 16).longValue();
                    }
                    catch (NumberFormatException e)
                    {
                        // $JL-EXC$
                        onError(editor, "Invalid <address...> " + e.getMessage());
                    }
                }
                else if (line.length() < 2 && !line.startsWith(ADDRESS_PREFIX.substring(0, line.length()))
                                || line.length() >= 2 && !line.startsWith(ADDRESS_PREFIX.substring(0, 2)))
                {

                    onError(editor, "Invalid <address...> " + line);
                }
                else if (line.length() != 0)
                {
                    onError(editor, "<address> is not yet complete");
                }
            }
            else
            // Pattern
            {
                if (!line.equals(""))
                {
                    try
                    {
                        Pattern.compile(line);
                    }
                    catch (PatternSyntaxException e)
                    {
                        // $JL-EXC$
                        onError(editor, e.getMessage());
                    }
                }

            }
        }
    }

    private boolean noErrorsInHoa()
    {
        for (Map.Entry<ArgumentEditor, String> entry : errors.entrySet())
        {
            if (entry.getKey() instanceof ImageTextEditor) { return false; }
        }
        return true;
    }

    public synchronized void onError(ArgumentEditor editor, String message)
    {
        synchronized (errors)
        {
            if (message == null)
            {
                if (errors.remove(editor) != null)
                {

                    if (errors.isEmpty())
                        fireErrorMessageEvent(null);
                    else
                        fireErrorMessageEvent(errors.values().iterator().next());

                }
            }
            else
            {
                errors.put(editor, message);
                fireErrorMessageEvent(message);
            }
        }
    }

    // //////////////////////////////////////////////////////////////
    // table listener implementation
    // //////////////////////////////////////////////////////////////

    public void addListener(ITableListener listener)
    {
        this.listeners.add(listener);
    }

    public void removeListener(ITableListener listener)
    {
        this.listeners.remove(listener);
    }

    private void fireInputChangedEvent()
    {
        synchronized (listeners)
        {
            for (ITableListener listener : listeners)
                listener.onInputChanged();
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

    public void onFocus(String message)
    {
        fireFocusChangedEvent(message);
    }

    public void onModeChange(Mode mode, ArgumentDescriptor descriptor)
    {
        // add new mode for this descriptor to the HashMap to be able to proceed
        // different descriptors independently
        if (modeMap == null)
        {
            modeMap = new HashMap<ArgumentDescriptor, Mode>();
        }

        modeMap.put(descriptor, mode);

        List<ITableListener> copy = new ArrayList<ITableListener>(listeners);
        for (ITableListener listener : copy)
            listener.onModeChange(mode);
        // clear error messages
        errors.clear();
        fireErrorMessageEvent(null);

        table.removeAll();
        Control[] children = table.getChildren();
        for (Control control : children)
        {
            control.dispose();
        }

        this.createTableContent();
    }

}

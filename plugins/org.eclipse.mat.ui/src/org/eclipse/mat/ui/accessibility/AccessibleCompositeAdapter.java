/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/

package org.eclipse.mat.ui.accessibility;

import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/**
 * AccessibleCompositeAdapter Accessibility utility class provides a single
 * encapsulated implementation of accessibility enhancements for Table and Tree
 * Controls within MAT. The implementation is essentially the same for both
 * types of view, adding an Accessible Listener to cause a specially constructed
 * name for each item to be passed to the accessibility client (Screen Reader).
 * 
 * @author Jonathan Lawrence
 */
public class AccessibleCompositeAdapter
{

    // Constants for String construction.
    private static final char space = ' ';

    // Public methods provide interface ensuring only Table or Tree are used.
    /**
     * @param table
     *            The Table to decorate with Accessible.
     */
    public static void access(Table table)
    {
        access(table, ACC.ROLE_TABLE); // Delegate to generic private method.
    }

    /**
     * @param tree
     *            The Tree to decorate with Accessible.
     */
    public static void access(Tree tree)
    {
        access(tree, ACC.ROLE_TREE); // Delegate to generic private method.
    }

    /**
     * @param composite
     *            The Composite (Table/Tree) to add Accessibility
     * @param role
     *            The ACC.ROLE constant representing the type of Composite.
     */
    private static void access(final Composite composite, final int role)
    {
        // Add addAccessibleListener to override getName.
        composite.getAccessible().addAccessibleListener(new AccessibleAdapter()
        {

            @Override
            public void getName(AccessibleEvent e)
            {
                if (e.childID == ACC.CHILDID_SELF)
                {
                    // TODO - provide a suitable name for the Tree/Table.
                }
                else
                { // Name is required for a child of the Composite.
                    // Get the item...
                    Item item = null;
                    if (role == ACC.ROLE_TABLE)
                    {
                        int maxchild = getItemCount(composite, role);
                        if (e.childID >= 0 && e.childID < maxchild)
                        { // Valid range
                          // Get Item
                            item = getItem(composite, role, e.childID);
                        }
                    }
                    else
                    {
                        // TreeItem
                        Widget widget = composite.getDisplay().findWidget(composite, e.childID);
                        if (widget instanceof Item)
                            item = (Item)widget;
                    }

                    // Construct a row of readable text for the Table/TreeItem
                    if (item != null) // Valid item
                    {
                        final boolean nameOnly = role == ACC.ROLE_TABLE;
                        int ncol = getColumnCount(composite, role);
                        /*
                         * For a table:
                         * JAWS 12 reads the name, "list item", then a description based on the
                         * table contents. JAWS 12 can be configured to read certain columns
                         * of the table contents and whether to include the column headers. This
                         * mean we do not need to include the other columns here.
                         * For a tree:
                         * By default, JAWS 12 does not read out the other columns, 
                         * so we need to get all of the column headers and item contents.
                         */
                        if (nameOnly && ncol > 1)
                            ncol = 1;
                        int[] colorder = getColumnOrder(composite, role);
                        StringBuffer rowbuf = new StringBuffer();
                        Item column = null;
                        Image image = null;
                        for (int icol = 0; icol < ncol; icol++) // For each
                                                                // column
                        {
                            int jcol = colorder[icol]; // The index of the
                                                       // column when created.
                            image = getImage(item, role, jcol); // Get image if
                                                                // any
                            if (image != null) // Image exists in this column
                            { // Append the descriptive text for this image
                                rowbuf.append(MemoryAnalyserPlugin.getDefault().getImageText(image));
                                rowbuf.append(space);
                            }
                            String cellText = getText(item, role, jcol);
                            // if cell has any non-white space content...
                            if (cellText.trim().length() > 0)
                            { // Only add text if the cell is non-empty
                                // Only add the column name for trees
                                if (!nameOnly)
                                {
                                    // Get relevant Column
                                    column = getColumn(composite, role, jcol);
                                    // Append column header text
                                    rowbuf.append(column.getText());
                                    rowbuf.append(space);
                                }
                                // Append column cell content
                                rowbuf.append(cellText);
                                rowbuf.append(space);
                            } // if()
                        }
                        e.result = rowbuf.toString();
                    } // if()
                } // if()
            } // getName()
        });

        // Experimentation with JAWS 12 shows that the following is also
        // required to ensure
        // that JAWS will read out the name returned for the Item.
        composite.getAccessible().addAccessibleControlListener(new AccessibleControlAdapter()
        {
            @Override
            public void getRole(AccessibleControlEvent e)
            {
                if (e.childID == ACC.CHILDID_SELF)
                {
                    e.detail = role; // The ACC.ROLE constant for the Control.
                }
                else
                {
                    e.detail = ACC.ROLE_TEXT; // Return TEXT for an Item.
                } // if()
            } // getRole()
        });

    }

    // //////////////////////////////////////////////////////////////
    // Utility methods to map inquiry methods onto the Control-type
    // specific variants for Table or Tree, as required.
    // //////////////////////////////////////////////////////////////

    private static Item getColumn(Composite composite, int role, int index)
    {
        return (role == ACC.ROLE_TABLE) ? ((Table) composite).getColumn(index) : ((Tree) composite).getColumn(index);
    }

    private static int getColumnCount(Composite composite, int role)
    {
        return (role == ACC.ROLE_TABLE) ? ((Table) composite).getColumnCount() : ((Tree) composite).getColumnCount();
    }

    private static int[] getColumnOrder(Composite composite, int role)
    {
        return (role == ACC.ROLE_TABLE) ? ((Table) composite).getColumnOrder() : ((Tree) composite).getColumnOrder();
    }

    private static Item getItem(Composite composite, int role, int index)
    {
        return (role == ACC.ROLE_TABLE) ? ((Table) composite).getItem(index) : ((Tree) composite).getItem(index);
    }

    private static int getItemCount(Composite composite, int role)
    {
        return (role == ACC.ROLE_TABLE) ? ((Table) composite).getItemCount() : ((Tree) composite).getItemCount();
    }

    private static Image getImage(Item item, int role, int index)
    {
        return (role == ACC.ROLE_TABLE) ? ((TableItem) item).getImage(index) : ((TreeItem) item).getImage(index);
    }

    private static String getText(Item item, int role, int index)
    {
        return (role == ACC.ROLE_TABLE) ? ((TableItem) item).getText(index) : ((TreeItem) item).getText(index);
    }

}

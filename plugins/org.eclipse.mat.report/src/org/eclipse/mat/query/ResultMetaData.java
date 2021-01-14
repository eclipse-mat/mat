/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds meta-data of the query result needed to fine-tune the display of the
 * result.
 */
public final class ResultMetaData
{

    /**
     * {@link ResultMetaData} factory
     */
    public static final class Builder
    {
        ResultMetaData data = new ResultMetaData();

        /**
         * Add a named {@link ContextProvider} to display additional context
         * menus.
         * For example, a row may represent two different sets of object(s) and
         * in a query menu it may be useful for the user to choose which set before running
         * the next query on a selection.
         * @param provider the ContextProvider to add to the result
         * @return the original Builder to allow chaining
         */
        public Builder addContext(ContextProvider provider)
        {
            data.contextProviders.add(provider);
            return this;
        }

        /**
         * Add a {@link DetailResultProvider} which can provide enhanced information about a result.
         * @param provider for enhanced information
         * @return the original Builder to allow chaining
         */
        public Builder addDetailResult(DetailResultProvider provider)
        {
            data.resultProviders.add(provider);
            return this;
        }

        /**
         * Indicates that the table or tree is already sorted by the query and
         * (a) prevents sorting by the UI and (b) sets the sort indicators to
         * the right columns.
         * @param columnIndex the column index
         * @param direction how to sort the column
         * @return the original Builder to allow chaining
         */
        public Builder setIsPreSortedBy(int columnIndex, Column.SortDirection direction)
        {
            data.preSortedColumnIndex = columnIndex;
            data.preSortedSortDirection = direction;

            return this;
        }

        /**
         * Extra data for the result
         * @param action the way of adding the extra information
         * @return the original Builder to allow chaining
         */
        public Builder addDerivedData(ContextDerivedData.DerivedOperation action)
        {
            if (data.operations == null)
                data.operations = new HashSet<ContextDerivedData.DerivedOperation>();
            data.operations.add(action);
            return this;
        }

        /**
         * Creates and returns the ResultMetaData object, with
         * the context providers list made unmodifiable.
         * This can only be called once.
         * @return ResultMetaData the extra information for the result which can be attached to
         * the result and be passed around.
         */
        public ResultMetaData build()
        {
            ResultMetaData answer = data;
            data = null;

            answer.contextProviders = Collections.unmodifiableList(answer.contextProviders);

            return answer;
        }
    }

    private List<ContextProvider> contextProviders = new ArrayList<ContextProvider>();
    private List<DetailResultProvider> resultProviders = new ArrayList<DetailResultProvider>();

    private int preSortedColumnIndex;
    private Column.SortDirection preSortedSortDirection;

    private Set<ContextDerivedData.DerivedOperation> operations;

    private ResultMetaData()
    {}

    /**
     * Returns the named context providers.
     * @return a list of the named context providers
     */
    public List<ContextProvider> getContextProviders()
    {
        return contextProviders;
    }

    public List<DetailResultProvider> getDetailResultProviders()
    {
        return resultProviders;
    }

    /**
     * @return true if the result is already sorted.
     */
    public boolean isPreSorted()
    {
        return preSortedSortDirection != null;
    }

    /**
     * The index of the column by which the result is pre-sorted (if it is
     * actually pre-sorted)
     * @return the column index which is pre-sorted
     */
    public int getPreSortedColumnIndex()
    {
        return preSortedColumnIndex;
    }

    /**
     * The direction by which the result is pre-sorted (if it is actually
     * pre-sorted)
     * @return the pre-sorted direction
     */
    public Column.SortDirection getPreSortedDirection()
    {
        return preSortedSortDirection;
    }

    /**
     * Get the operations.
     * @return a set of all the possible operations
     */
    public Collection<ContextDerivedData.DerivedOperation> getDerivedOperations()
    {
        return operations;
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat.query;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

/**
 * Synthetic data columns
 */
public abstract class ContextDerivedData
{
    /**
     * A column of data derived from the existing data.
     * An example might be retained sizes.
     */
    public static final class DerivedColumn
    {
        private final String label;
        private final DerivedOperation[] operations;

        /**
         * A derived column.
         * Operations might include ways of calculating the column, such as approximate or precise retained sizes.
         * @param label a label for the column
         * @param operations ways of calculating the column
         */
        public DerivedColumn(String label, DerivedOperation... operations)
        {
            this.label = label;
            this.operations = operations;
        }

        /**
         * The label for the column.
         * @return the label
         */
        public String getLabel()
        {
            return label;
        }

        /**
         * Possible operations to generate the column values.
         * Do not modify the returned array.
         * @return an array of operations
         */
        public DerivedOperation[] getOperations()
        {
            return operations;
        }
    }

    /**
     * A way of calculating the column values.
     */
    public static final class DerivedOperation
    {
        private final String code;
        private final String label;

        /**
         * Create a way of calculating the column.
         * The code is used for XML queries to specify the operation.
         * @param code a text note of what the operation does (not translated)
         * @param label an explanation of the operation
         */
        public DerivedOperation(String code, String label)
        {
            this.code = code;
            this.label = label;
        }

        /**
         * Get the code.
         * @return a short name used in XML report definitions etc.
         */
        public String getCode()
        {
            return code;
        }

        /**
         * Get the explanation of the operation.
         * @return a readable explanation
         */
        public String getLabel()
        {
            return label;
        }
    }

    /**
     * A way of actually doing the calculations.
     */
    public interface DerivedCalculator
    {
        /**
         * Get the results of the calculation from the row.
         * Allows some caching.
         * @param row the row
         * @return the result, perhaps a Long for retained size
         */
        Object lookup(Object row);

        /**
         * Do the calculation for the row.
         * Save the result in the row or elsewhere, ready for {@link #lookup(Object)}
         * @param operation the operation to do on the row to get the derived data
         * @param row the row
         * @param listener to indicate progress and exceptions
         * @throws SnapshotException if there was a problem with the calculation
         */
        void calculate(DerivedOperation operation, Object row, IProgressListener listener) throws SnapshotException;
    }

    /**
     * Get all the derived columns for the current context (page)
     * Do not modify the returned array.
     * @return an array of columns
     */
    public abstract DerivedColumn[] getDerivedColumns();

    /**
     * Get the label for the extra column
     * @param derivedColumn the extra column
     * @param provider how the column was generated
     * @return the label
     */
    public abstract String labelFor(DerivedColumn derivedColumn, ContextProvider provider);

    /**
     * Get a column ready to use, based on the derived column
     * @param derivedColumn the extra column
     * @param result the original result to be enhanced
     * @param provider the provider of all the data
     * @return the column
     */
    public abstract Column columnFor(DerivedColumn derivedColumn, IResult result, ContextProvider provider);

    /**
     * Find the appropriate column for the requested operation
     * @param operation the operation to generate the column values
     * @return the column
     */
    public final DerivedColumn lookup(DerivedOperation operation)
    {
        for (DerivedColumn column : getDerivedColumns())
            for (DerivedOperation oo : column.getOperations())
            {
                if (oo == operation)
                    return column;
            }

        throw new IllegalArgumentException(MessageUtil.format(Messages.ContextDerivedData_Error_OperationNotFound, operation
                        .getLabel(), this.getClass().getName()));
    }
}

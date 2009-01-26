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
package org.eclipse.mat.query;

import java.text.MessageFormat;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.util.IProgressListener;

public abstract class ContextDerivedData
{
    public static final class DerivedColumn
    {
        private final String label;
        private final DerivedOperation[] operations;

        public DerivedColumn(String label, DerivedOperation... operations)
        {
            this.label = label;
            this.operations = operations;
        }

        public String getLabel()
        {
            return label;
        }

        public DerivedOperation[] getOperations()
        {
            return operations;
        }
    }

    public static final class DerivedOperation
    {
        private final String code;
        private final String label;

        public DerivedOperation(String code, String label)
        {
            this.code = code;
            this.label = label;
        }
        
        public String getCode()
        {
            return code;
        }

        public String getLabel()
        {
            return label;
        }
    }

    public interface DerivedCalculator
    {
        Object lookup(Object row);

        void calculate(DerivedOperation operation, Object row, IProgressListener listener) throws SnapshotException;
    }

    public abstract DerivedColumn[] getDerivedColumns();

    public abstract String labelFor(DerivedColumn derivedColumn, ContextProvider provider);
    
    public abstract Column columnFor(DerivedColumn derivedColumn, IResult result, ContextProvider provider);

    public final DerivedColumn lookup(DerivedOperation operation)
    {
        for (DerivedColumn column : getDerivedColumns())
            for (DerivedOperation oo : column.getOperations())
            {
                if (oo == operation)
                    return column;
            }

        throw new RuntimeException(MessageFormat.format(Messages.ContextDerivedData_Error_OperationNotFound, operation
                        .getLabel(), this.getClass().getName()));
    }
}

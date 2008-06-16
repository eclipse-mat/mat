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
package org.eclipse.mat.snapshot;

/**
 * Exception thrown by the OQL parser. Contains line and column information
 * where exactly the error was found.
 */
public class OQLParseException extends SnapshotException
{
    private static final long serialVersionUID = 1L;

    private int line;
    private int column;

    public OQLParseException(String message, Throwable cause, int line, int column)
    {
        super(message, cause);

        this.line = line;
        this.column = column;
    }

    public OQLParseException(Throwable cause)
    {
        super(cause);
    }

    public int getLine()
    {
        return line;
    }

    public int getColumn()
    {
        return column;
    }

}

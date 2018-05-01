/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation/Andrew Johnson - Javadoc updates
 *******************************************************************************/
package org.eclipse.mat;

/**
 * Exception used to indicate a problem different from the standard Java
 * exceptions while performing an operation on an snapshot.
 */
public class SnapshotException extends Exception
{
    private static final long serialVersionUID = 1L;

    /**
     * Create snapshot exception - should not be used except during
     * deserialization.
     */
    public SnapshotException()
    {}

    /**
     * Create snapshot exception with message and root cause.
     * 
     * @param message the message for the exception
     * @param cause the original exception
     */
    public SnapshotException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Create snapshot exception with message only.
     * 
     * @param message the message for the exception
     */
    public SnapshotException(String message)
    {
        super(message);
    }

    /**
     * Create snapshot exception with root cause only.
     * 
     * @param cause the original exception
     */
    public SnapshotException(Throwable cause)
    {
        super(cause);
    }

    /**
     * Wrap, if necessary, and return a SnapshotException.
     * @param e the original exception
     * @return a new exception to throw
     */
    public static final SnapshotException rethrow(Throwable e)
    {
        if (e instanceof RuntimeException)
        {
            // if we wrap an SnapshotException, pass the snapshot exception on
            if (((RuntimeException) e).getCause() instanceof SnapshotException)
                return (SnapshotException) ((RuntimeException) e).getCause();
            throw (RuntimeException) e;
        }
        else if (e instanceof SnapshotException)
            return (SnapshotException) e;
        else
            return new SnapshotException(e);
    }
}

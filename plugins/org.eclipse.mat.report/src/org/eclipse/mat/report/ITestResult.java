/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.report;

/**
 * The result of a query.
 */
public interface ITestResult
{
    public enum Status
    {
        SUCCESS, WARNING, ERROR;

        public static Status max(Status a, Status b)
        {
            if (a == null)
                return b;
            if (b == null)
                return a;
            return a.ordinal() > b.ordinal() ? a : b;
        }
    }

    Status getStatus();
}

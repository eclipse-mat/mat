/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
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
package org.eclipse.mat.report;

import org.eclipse.mat.report.internal.Messages;

/**
 * The result of a query.
 */
public interface ITestResult
{
    public enum Status
    {
        SUCCESS(Messages.ITestResult_Success), WARNING(Messages.ITestResult_Warning), ERROR(Messages.ITestResult_Error);

        private String label;
        private Status(String label)
        {
            this.label = label;
        }

        /**
         * Translatable name for the status.
         */
        public String toString()
        {
            return label;
        }

        /**
         * Compare two statuses, and return the worst.
         * @param a first status
         * @param b second status
         * @return the worst
         */
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

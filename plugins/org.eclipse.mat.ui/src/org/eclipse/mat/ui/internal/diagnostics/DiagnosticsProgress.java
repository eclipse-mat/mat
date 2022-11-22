/*******************************************************************************
 * Copyright (c) 2022, 2022 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.diagnostics;

/**
 * Mechanism to report diagnostics progress
 */
public interface DiagnosticsProgress
{
    /**
     * Report textual progress and results to the user
     * 
     * @param text
     *            The text to report
     */
    void appendText(String text);

    /**
     * Clear any existing textual progress for the user.
     */
    void clearText();
}

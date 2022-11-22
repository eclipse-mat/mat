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
 * Performs a diagnostic action
 */
public interface DiagnosticsAction
{
    /**
     * Run the diagnostic action
     * 
     * @param progress
     *            Use this to report progress and results to the user
     */
    void run(DiagnosticsProgress progress);
}

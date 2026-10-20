/*******************************************************************************
 * Copyright (c) 2026 SAP AG and IBM Corporation.
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
package org.eclipse.mat.query;

/**
 * Used to give more detailed information for an AI consumer.
 * @since 1.18
 */
public abstract class AIDetailsProvider
{
    /**
     * Output to prefix information sent to an AI consumer.
     * @return output to prefix information sent to an AI consumer.
     */
    public String getOutputPrefix() {
        return null;
    }
    
    /**
     * The depth for a result tree to export to an AI consumer.
     * @return the export depth
     */
    public int getTreeExportDepth()
    {
        return 0;
    }
}

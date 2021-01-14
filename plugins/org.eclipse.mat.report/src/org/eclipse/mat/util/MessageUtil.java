/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.util;

import com.ibm.icu.text.MessageFormat;

/**
 * Substitute replaceable text in a message. 
 * @since 0.8
 */
public final class MessageUtil
{
    public static String format(String message, Object... objects)
    {
        return MessageFormat.format(message, objects);
    }

    private MessageUtil()
    {}
}

/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Filippo Pacifici - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import org.eclipse.swt.graphics.RGB;

/**
 * Contains the definition of colors for syntax highlighting.
 * 
 * @author Filippo Pacifici
 */
public interface ColorProvider
{

    public static RGB COMMENT_COLOR = new RGB(93, 142, 116);
    public static String COMMENT_COLOR_PREF = "org.eclipse.mat.ui.oql_comment"; //$NON-NLS-1$

    public static RGB KEYWORD_COLOR = new RGB(146, 55, 117);
    public static String KEYWORD_COLOR_PREF = "org.eclipse.mat.ui.oql_keyword"; //$NON-NLS-1$
}

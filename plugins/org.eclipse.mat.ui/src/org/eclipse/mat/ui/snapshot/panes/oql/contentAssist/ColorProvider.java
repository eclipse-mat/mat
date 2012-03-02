/*******************************************************************************
 * Copyright (c) 2012 Filippo Pacifici
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
    public static String COMMENT_COLOR_PREF = "comment_color"; //$NON-NLS-1$

    public static RGB KEYWORD_COLOR = new RGB(146, 55, 117);
    public static String KEYWORD_COLOR_PREF = "keyword_color"; //$NON-NLS-1$
}

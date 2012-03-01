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

import java.awt.Image;

/**
 * Javabean to temporarily store classnames for the content assistant in an ordered structure.
 * @author pyppo
 *
 */
public class ContentAssistElement implements Comparable<ContentAssistElement> {

	private String className;
	
	private Image image;

	/**
	 * Uses String compare method.
	 */
	public int compareTo(ContentAssistElement o) {
		return className.compareTo(o.className);
	}

	public String getClassName() {
		return className;
	}

	public Image getImage() {
		return image;
	}

	public ContentAssistElement(String className, Image image) {
		super();
		if (className == null)
			throw new IllegalArgumentException("Cannot be initialized without a class name");
		this.className = className;
		this.image = image;
	}
	
	public String toString() {
		return className; 
	}
	
	
}

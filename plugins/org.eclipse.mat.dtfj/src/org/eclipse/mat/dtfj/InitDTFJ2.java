/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import org.eclipse.ui.IStartup;

/**
 * This is a separate class from InitDTFJ so that the dependency on
 * org.eclipse.ui can be optional, so a command line run of MAT does not require
 * the UI.
 * 
 * @author ajohnson
 */
public class InitDTFJ2 implements IStartup
{

    /**
     * The early start-up code does not need to do anything as the bundle will
     * have already been started, and we register the file extensions then. We
     * do need the early startup extension to ensure that the plugin is called
     * early.
     */
    public void earlyStartup()
    {}

}

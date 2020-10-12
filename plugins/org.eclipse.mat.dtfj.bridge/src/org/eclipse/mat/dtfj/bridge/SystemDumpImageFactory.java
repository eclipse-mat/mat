/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.dtfj.bridge;

/**
 * Concrete factory implementation for system dumps.
 */
public class SystemDumpImageFactory extends DelegatedImageFactory
{
    @Override
    protected String getUnderlyingImageFactoryClass()
    {
        return "com.ibm.dtfj.image.j9.ImageFactory";
    }
}

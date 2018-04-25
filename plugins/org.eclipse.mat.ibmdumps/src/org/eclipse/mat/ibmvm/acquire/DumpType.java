/*******************************************************************************
 * Copyright (c) 2010, 2018 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *    IBM Corporation/Andrew Johnson - add hprof
 *******************************************************************************/
package org.eclipse.mat.ibmvm.acquire;

/**
 * Used for selecting the appropriate dump type
 * @author ajohnson
 *
 */
public enum DumpType
{
    SYSTEM,
    HEAP,
    JAVA,
    HPROF;
}
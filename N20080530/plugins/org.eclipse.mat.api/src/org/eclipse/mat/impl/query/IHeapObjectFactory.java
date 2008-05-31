/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.impl.query;

import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.ISnapshot;

interface IHeapObjectFactory
{
    
    IHeapObjectArgument create(ISnapshot snapshot);

}

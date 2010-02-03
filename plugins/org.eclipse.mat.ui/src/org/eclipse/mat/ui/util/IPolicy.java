/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.util;

import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;

/**
 * This lets the query browser find out which queries will work with the selected arguments
 * then fills in the appropriate arguments for the selected query
 * @noimplement
 *
 */
public interface IPolicy
{

    public abstract boolean accept(QueryDescriptor query);

    public abstract void fillInObjectArguments(ISnapshot snapshot, QueryDescriptor query, ArgumentSet set);
}

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
package org.eclipse.mat.tests.regression.comparator;

import java.io.File;
import java.util.List;

import org.eclipse.mat.tests.regression.Difference;

public interface IComparator
{
    public List<Difference> compare(File baseline, File testFile) throws Exception;
}

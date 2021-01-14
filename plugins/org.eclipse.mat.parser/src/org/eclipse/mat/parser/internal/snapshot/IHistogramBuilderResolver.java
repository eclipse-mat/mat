/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.SnapshotImpl;

/**
 * @deprecated Use {@link HistogramBuilder#toHistogram(SnapshotImpl, boolean)} instead
 */
@Deprecated
public interface IHistogramBuilderResolver
{
    public HistogramBuilderResolverData resolve(int classId) throws SnapshotException;
}

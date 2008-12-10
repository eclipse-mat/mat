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
package org.eclipse.mat.parser.internal.snapshot;

import org.eclipse.mat.SnapshotException;

/**
 * @deprecated Use {@link HistogramBuilder#toHistogram(org.eclipse.mat.internal.snapshot.hprof.HprofSnapshot, boolean) instead}
 */
@Deprecated
public interface IHistogramBuilderResolver
{
    public HistogramBuilderResolverData resolve(int classId) throws SnapshotException;
}

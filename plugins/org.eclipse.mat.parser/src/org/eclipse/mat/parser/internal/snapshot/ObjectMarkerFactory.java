/*******************************************************************************
 * Copyright (c) 2025 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM
 *******************************************************************************/
package org.eclipse.mat.parser.internal.snapshot;

import org.eclipse.mat.parser.index.IIndexReader;
import org.eclipse.mat.util.IProgressListener;

public class ObjectMarkerFactory
{
    private static final boolean useOldMarker = Boolean
                    .getBoolean("org.eclipse.mat.parser.internal.snapshot.ObjectMarkerFactory.useOldMarker");

    public static IObjectMarker getObjectMarker(int[] roots, boolean[] bits, IIndexReader.IOne2ManyIndex outbound,
                    IProgressListener progressListener)
    {
        return useOldMarker ? new ObjectMarkerOld(roots, bits, outbound, progressListener)
                        : new ObjectMarker(roots, bits, outbound, progressListener);
    }

    public static IObjectMarker getObjectMarker(int[] roots, boolean[] bits, IIndexReader.IOne2ManyIndex outbound,
                    long outboundLength, IProgressListener progressListener)
    {
        return useOldMarker ? new ObjectMarkerOld(roots, bits, outbound, outboundLength, progressListener)
                        : new ObjectMarker(roots, bits, outbound, outboundLength, progressListener);
    }
}

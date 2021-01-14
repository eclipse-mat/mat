/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
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
package org.eclipse.mat.query.quantize;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.quantize.Quantize.Function;

/**
 * Simple quantize function which just returns the last value add to the
 * distribution bucket.
 * <p>
 * Its purpose is performance optimization: Assume you want to create a
 * frequency distribution on column value A and you know there is a 1:1
 * relationship to column value B, one can use the LatestValueFunction to
 * display column value B instead of adding B to the composite key (A,B).
 */
public final class LatestValueFunction implements Quantize.Function.Factory
{
    public Function build() throws Exception
    {
        return new Quantize.Function()
        {
            Object latest;

            public void add(Object object)
            {
                latest = object;
            }

            public Object getValue()
            {
                return latest;
            }

        };
    }

    public Column column(String label)
    {
        return new Column(label, Integer.class);
    }
}

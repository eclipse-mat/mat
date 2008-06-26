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
package org.eclipse.mat.query;

import java.util.List;

public interface IResultPie extends IResult
{
    public interface Slice
    {
        String getLabel();
        
        double getValue();
        
        String getDescription();
        
        IContextObject getContext();
    }

    List<? extends Slice> getSlices();
}

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
package org.eclipse.mat.impl.result;

import java.util.List;

import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.VoidProgressListener;


public class RefinedHistogramClassLoaderTree extends RefinedTree
{
    @Override
    protected RetainedSizeCalculator calculatorFor(ContextProvider provider)
    {
        return new RetainedSizeCalculator.AllClasses();
    }

    @Override
    public List<?> getElements()
    {
        List<?> answer = super.getElements();
        
        try
        {
            VoidProgressListener listener = new VoidProgressListener();
            for (Object element : answer)
                ((ClassLoaderHistogramRecord)element).calculateRetainedSize(snapshot, false, true, listener);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
        
        return answer;
    }
    
    @Override
    public List<?> getChildren(Object parent)
    {
        List<?> answer = super.getChildren(parent);
        
        try
        {
            VoidProgressListener listener = new VoidProgressListener();
            for (Object element : answer)
                ((ClassHistogramRecord)element).calculateRetainedSize(snapshot, false, true, listener);
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
        
        return answer;
    }
    
}

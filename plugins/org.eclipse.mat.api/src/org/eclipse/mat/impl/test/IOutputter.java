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
package org.eclipse.mat.impl.test;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import org.eclipse.mat.impl.test.ResultRenderer.RenderingInfo;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.snapshot.ISnapshot;


public interface IOutputter
{
    public interface Context
    {
        ISnapshot getSnapshot();
        
        String getRelativeIconLink(URL icon);

        File getOutputDirectory();
    }
    
    
    String getExtension();

    void embedd(Context context, QueryPart part, IResult result, RenderingInfo renderingInfo, Writer writer) throws IOException;

    void process(Context context, QueryPart part, IResult result, RenderingInfo renderingInfo, Writer writer) throws IOException;

}

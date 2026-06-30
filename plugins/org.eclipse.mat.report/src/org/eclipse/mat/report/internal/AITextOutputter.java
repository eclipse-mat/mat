/*******************************************************************************
 * Copyright (c) 2026 SAP AG and IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.mat.query.AIDetailsProvider;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultPie;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Renderer;

@Renderer(target = "text", result = { IResultTree.class, IResultTable.class, TextResult.class, IResultPie.class })
public class AITextOutputter extends TextOutputter
{
    public AITextOutputter()
    {
    }

    @Override
    public void embedd(Context context, IResult result, Writer writer) throws IOException
    {
        AIDetailsProvider aiDetailsProvider = result.getResultMetaData().getAIDetailsProvider();
        if (aiDetailsProvider != null) {
            String prefix = aiDetailsProvider.getOutputPrefix();
            if (prefix != null) {
                writer.append(prefix);
                writer.append(LINE_SEPARATOR);
                writer.append(LINE_SEPARATOR);
            }
        }
        
        writer.append(Messages.AITextOutputter_Prefix);
        writer.append(LINE_SEPARATOR);
        writer.append(LINE_SEPARATOR);
        
        super.embedd(context, result, writer);
    }
}

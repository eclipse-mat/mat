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
package org.eclipse.mat.inspections.jetty;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@Subject("org.mortbay.jetty.Request")
public class JettyRequestResolver implements IRequestDetailsResolver
{

    public void complement(ISnapshot snapshot, IThreadInfo thread, int[] javaLocals, int thisJavaLocal,
                    IProgressListener listener) throws SnapshotException
    {
        IObject httpRequest = snapshot.getObject(thisJavaLocal);
        IObject requestURI = (IObject) httpRequest.resolveValue("_requestURI");

        if (requestURI == null)
            return;

        CompositeResult answer = new CompositeResult();

        // Summary
        StringBuilder buf = new StringBuilder(256);
        buf.append("The thread is executing an HTTP Request to <strong>") //
                        .append(requestURI.getClassSpecificName()).append("</strong>.");
        String summary = buf.toString();
        QuerySpec spec = new QuerySpec("Summary");
        spec.setCommand("list_objects 0x" + Long.toHexString(httpRequest.getObjectAddress()));
        spec.setResult(new TextResult(summary, true));
        answer.addResult(spec);

        // URI
        IObject uri = (IObject) httpRequest.resolveValue("_uri._raw");
        if (uri != null)
        {
            spec = new QuerySpec("URI");
            spec.setCommand("list_objects 0x" + Long.toHexString(uri.getObjectAddress()));
            spec.setResult(new TextResult(uri.getClassSpecificName()));
            answer.addResult(spec);
        }

        // Parameters
        IObject parameters = (IObject) httpRequest.resolveValue("_parameters");
        if (parameters != null)
        {
            String cmd = "hash_entries 0x" + Long.toHexString(parameters.getObjectAddress());

            spec = new QuerySpec("Parameters");
            spec.setCommand(cmd);
            spec.setResult(SnapshotQuery.parse(cmd, snapshot).execute(listener));
            spec.set(Params.Rendering.HIDE_COLUMN, "Collection");
            answer.addResult(spec);
        }

        thread.addRequest(summary, answer);
    }

}

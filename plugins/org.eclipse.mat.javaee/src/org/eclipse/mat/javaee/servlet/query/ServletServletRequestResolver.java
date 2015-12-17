/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee.servlet.query;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.impl.ServletExtractors;
import org.eclipse.mat.javaee.servlet.api.RequestExtractor;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;


@Subjects(value = {
    "org.apache.catalina.connector.RequestFacade",
    "org.apache.catalina.connector.Request",
    "io.undertow.servlet.spec.HttpServletRequestImpl",
})
public class ServletServletRequestResolver implements IRequestDetailsResolver {
    public void complement(ISnapshot snapshot, IThreadInfo thread,
            int[] javaLocals, int thisJavaLocal, IProgressListener listener)
            throws SnapshotException {
        IObject request = snapshot.getObject(thisJavaLocal);
        RequestExtractor extractor = ServletExtractors.getRequestExtractor(request);
        if (extractor == null) {
            System.out.println("WARN unhandled request type" + request.getClazz().getName());
            return;
        }

        CompositeResult answer = new CompositeResult();

        QuerySpec spec = new QuerySpec("URI", new TextResult(extractor.getRequestUri(request)));
        answer.addResult(spec);

        thread.addRequest("Enterprise Java web worker thread", answer);
    }
}

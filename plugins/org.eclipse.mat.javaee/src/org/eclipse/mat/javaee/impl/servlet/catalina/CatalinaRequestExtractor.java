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
package org.eclipse.mat.javaee.impl.servlet.catalina;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.servlet.api.RequestExtractor;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

public class CatalinaRequestExtractor implements RequestExtractor {
    public String getRequestUri(IObject request) {
        try {
            IObject decodedURI = (IObject)request.resolveValue("coyoteRequest.decodedUriMB");
            String uri = helperMessageBytesToString(decodedURI);
            if (uri != null)
                return uri;

            // not in use
            return null;
        } catch (SnapshotException e) {
            JavaEEPlugin.warning("Unable to resolve URI", e);
            return null;
        }
    }

    public boolean isInUse(IObject request) {
        try {
            IObject context = (IObject)request.resolveValue("context");
            return context != null;
        } catch (SnapshotException e) {
            JavaEEPlugin.warning("Unable to request context", e);
            return false;
        }
    }

    /* helpers*/

    private String helperMessageBytesToString(IObject uri) throws SnapshotException {
        if (((Boolean) uri.resolveValue("hasStrValue")).booleanValue()) {
            return ((IObject)uri.resolveValue("strValue")).getClassSpecificName();
        }

        int type = ((Integer)uri.resolveValue("type")).intValue();
        switch (type) {
        case 3:
            return helperCharChunkToString((IObject) uri.resolveValue("charC"));
        case 2:
            return helperByteChunkToString((IObject) uri.resolveValue("byteC"));
        case 0:
            // unused
            return null;
        default:
            JavaEEPlugin.warning("Unexpected Catalina message bytes type " + type);
            return null;
        }
    }

    private String helperCharChunkToString(IObject chunk) throws SnapshotException {
        Object ex = extractFromChunk(chunk);
        if (ex instanceof String) {
            return (String)ex;
        } else if (ex instanceof char[]) {
            char[] buff = (char[])ex;
            return new String(buff);
        } else {
            JavaEEPlugin.error("Unexpected chunk class " + ex.getClass().getName());
            return null;
        }
    }

    private String helperByteChunkToString(IObject chunk) throws SnapshotException {
        byte[] buff = (byte[])extractFromChunk(chunk);

        IObject enc = (IObject)chunk.resolveValue("enc");
        final String encoding;
        if (enc == null) {
            encoding = "ISO-8859-1";
        } else {
            encoding = enc.getClassSpecificName();
        }

        try {
            return new String(buff, 0, buff.length, encoding);
        } catch (UnsupportedEncodingException e) {
            return "Undecodable buffer (" + encoding + "):" + Arrays.toString(buff);
        }
    }

    private Object extractFromChunk(IObject chunk) throws SnapshotException {
        IPrimitiveArray buff = (IPrimitiveArray)chunk.resolveValue("buff");
        if (buff == null)
            return null;

        int start = (Integer)chunk.resolveValue("start");
        int end = (Integer)chunk.resolveValue("end");

        if (start == end)
            return "";

        return buff.getValueArray(start, end - start);
    }
}

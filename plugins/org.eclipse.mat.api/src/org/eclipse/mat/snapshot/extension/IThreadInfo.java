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
package org.eclipse.mat.snapshot.extension;

import java.util.Collection;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.snapshot.model.IObject;

public interface IThreadInfo
{
    int getThreadId();

    IObject getThreadObject();

    void setValue(Column column, Object value);

    void addKeyword(String keyword);

    void addDetails(String name, IResult details);

    void addRequest(String summary, IResult details);

    CompositeResult getRequests();

    Collection<String> getKeywords();

    int getContextClassLoaderId();
}

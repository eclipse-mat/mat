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

import java.io.File;
import java.text.ParsePosition;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.annotations.Argument;

/**
 * @noimplement
 */
public interface IQueryContext
{
    File getPrimaryFile();

    boolean available(Class<?> type, Argument.Advice advice);

    Object get(Class<?> type, Argument.Advice advice);

    String mapToExternalIdentifier(int objectId) throws SnapshotException;

    int mapToObjectId(String externalIdentifier) throws SnapshotException;

    boolean converts(Class<?> type, Argument.Advice advice);

    String convertToString(Class<?> type, Argument.Advice advice, Object value) throws SnapshotException;

    Object convertToValue(Class<?> type, Argument.Advice advice, String value) throws SnapshotException;

    boolean parses(Class<?> type, Argument.Advice advice);

    Object parse(Class<?> type, Argument.Advice advice, String[] args, ParsePosition pos) throws SnapshotException;

    ContextDerivedData getContextDerivedData();

}

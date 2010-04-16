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
 * The context for a query. Hides the snapshot implementation,
 * and is not tied to the snapshot API.
 * @noimplement
 */
public interface IQueryContext
{
    /**
     * The main file for the snapshot
     * @return the dump
     */
    File getPrimaryFile();

    /**
     * Is this type of data available from the context?
     * @param type the type the data should be converted to
     * @param advice advice such as from the query as to how the value should be converted.
     * @return true if available.
     */
    boolean available(Class<?> type, Argument.Advice advice);

    /**
     * Get this type of data from the context.
     * @param type the type the data should be converted to
     * @param advice advice such as from the query as to how the value should be converted.
     * @return the object of the right type
     */
    Object get(Class<?> type, Argument.Advice advice);

    /**
     * Map an id to a readable form.
     * Reverse of {@link #mapToObjectId}
     * @param objectId
     * @return readable external version
     * @throws SnapshotException
     * @see #mapToObjectId
     */
    String mapToExternalIdentifier(int objectId) throws SnapshotException;

    /**
     * Map readable form to internal id.
     * Reverse of {@link #mapToExternalIdentifier}
     * @param externalIdentifier
     * @return the object id
     * @throws SnapshotException
     */
    int mapToObjectId(String externalIdentifier) throws SnapshotException;

    /**
     * Does the context have a converter for data of this type?
     * @param type
     * @param advice
     * @return true if available and convertible
     */
    boolean converts(Class<?> type, Argument.Advice advice);

    /**
     * Convert the value to a string.
     * For example the converter might be String.valueOf(Integer)
     * @param type
     * @param advice
     * @param value
     * @return the value converted to a String
     * @throws SnapshotException
     */
    String convertToString(Class<?> type, Argument.Advice advice, Object value) throws SnapshotException;

    /**
     * Convert the String to the value based on the type and advice.
     * @param type
     * @param advice
     * @param value
     * @return the String converted to a value
     * @throws SnapshotException
     */
    Object convertToValue(Class<?> type, Argument.Advice advice, String value) throws SnapshotException;

    /**
     * Is special parsing required to get an object of the required type?
     * @param type
     * @param advice
     * @return true if parsing is needed.
     */
    boolean parses(Class<?> type, Argument.Advice advice);

    /**
     * Consume the special data.
     * For example using the ArgumentParser for data from a query wizard. 
     * @param type
     * @param advice
     * @param args
     * @param pos
     * @return the result of parsing the data suitable given the type and advice
     * @throws SnapshotException
     */
    Object parse(Class<?> type, Argument.Advice advice, String[] args, ParsePosition pos) throws SnapshotException;

    /**
     * For example, retained size derived data. 
     * @return the derived data
     */
    ContextDerivedData getContextDerivedData();

}

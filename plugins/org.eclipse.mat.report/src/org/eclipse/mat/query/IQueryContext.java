/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - multiple snapshots in a dump file using separate prefixes
 *    IBM Corporation/Andrew Johnson - Javadoc updates
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
     * The prefix for files generated from snapshot
     * @return the prefix
     * @since 1.3
     */
    String getPrefix();

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
     * For example the hex-address with 0x as a prefix.
     * Reverse of {@link #mapToObjectId}
     * @param objectId The 0-based internal identifier used within MAT.
     * @return readable external version
     * @throws SnapshotException if the objectId does not match to a valid object.
     * @see #mapToObjectId
     */
    String mapToExternalIdentifier(int objectId) throws SnapshotException;

    /**
     * Map readable form to internal id.
     * Reverse of {@link #mapToExternalIdentifier}.
     * @param externalIdentifier as provided by {@link #mapToExternalIdentifier}.
     * @return the object id
     * @throws SnapshotException if the external identifier does not match a known object in the snapshot.
     */
    int mapToObjectId(String externalIdentifier) throws SnapshotException;

    /**
     * Does the context have a converter for data of this type?
     * @param type The Java type of an argument to be supplied with data from this context.
     * @param advice Further details about the argument to be supplied with data.
     * @return true if available and convertible
     */
    boolean converts(Class<?> type, Argument.Advice advice);

    /**
     * Convert the value to a string.
     * For example the converter might be String.valueOf(Integer)
     * @param type The Java type of the argument.
     * @param advice Further details about the argument.
     * @param value The value of the argument held in the context.
     * @return the value converted to a String
     * @throws SnapshotException If there is a problem with the conversion such as the value is not a valid object ID.
     */
    String convertToString(Class<?> type, Argument.Advice advice, Object value) throws SnapshotException;

    /**
     * Convert the String to the value based on the type and advice.
     * @param type The Java type of the argument
     * @param advice Further details about the argument.
     * @param value The readable string value
     * @return the String converted to a value suitable to be stored in the argument.
     * @throws SnapshotException if there is a problem with the conversion, such as an unknown object address.
     */
    Object convertToValue(Class<?> type, Argument.Advice advice, String value) throws SnapshotException;

    /**
     * Is special parsing required to get an object of the required type?
     * @param type The Java type of the argument.
     * @param advice Further details about the argument.
     * @return true if special parsing is needed, for example for a heap object or class object in the heap.
     */
    boolean parses(Class<?> type, Argument.Advice advice);

    /**
     * Consume the special data.
     * For example using the ArgumentParser for data from a query wizard. 
     * @param type The Java type of the destination argument.
     * @param advice Further details about the argument.
     * @param args The source to be converted
     * @param pos Used to index through the array of Strings. 
     * @return the result of parsing the data suitable given the type and advice
     * @throws SnapshotException If there is a problem in the parsing.
     */
    Object parse(Class<?> type, Argument.Advice advice, String[] args, ParsePosition pos) throws SnapshotException;

    /**
     * For example, retained size derived data. 
     * @return the derived data
     */
    ContextDerivedData getContextDerivedData();

}

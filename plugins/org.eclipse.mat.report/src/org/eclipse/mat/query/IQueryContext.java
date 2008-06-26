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

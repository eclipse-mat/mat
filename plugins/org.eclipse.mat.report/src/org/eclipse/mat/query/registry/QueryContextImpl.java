package org.eclipse.mat.query.registry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.Argument.Advice;

public abstract class QueryContextImpl implements IQueryContext
{

    public boolean available(Class<?> type, Advice advice)
    {
        return IQueryContext.class.isAssignableFrom(type);
    }

    public Object get(Class<?> type, Advice advice)
    {
        return IQueryContext.class.isAssignableFrom(type) ? this : null;
    }

    public boolean converts(Class<?> type, Advice advice)
    {
        return Converters.getConverter(type) != null;
    }

    public String convertToString(Class<?> type, Advice advice, Object value) throws SnapshotException
    {
        return Converters.getConverter(type).toString(value);
    }

    public Object convertToValue(Class<?> type, Advice advice, String value) throws SnapshotException
    {
        return Converters.getConverter(type).toObject(value, advice);
    }
}

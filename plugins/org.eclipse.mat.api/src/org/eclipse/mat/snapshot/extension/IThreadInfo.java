/**
 * 
 */
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

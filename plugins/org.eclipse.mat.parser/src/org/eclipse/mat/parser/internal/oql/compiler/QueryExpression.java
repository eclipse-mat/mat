/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.oql.OQLQueryImpl;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;

public class QueryExpression extends Expression
{
    Query query;

    boolean isDependencyCalculated = false;
    boolean isQueryContextDependent;
    Object queryResult;

    public QueryExpression(Query query)
    {
        this.query = query;
    }

    @Override
    public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
    {
        if (!isDependencyCalculated)
        {
            isQueryContextDependent = this.isContextDependent(ctx);
            isDependencyCalculated = true;

            if (!isQueryContextDependent)
            {
                OQLQueryImpl q = new OQLQueryImpl(ctx, query);
                queryResult = q.execute(ctx.getSnapshot(), null);
            }
        }

        if (isQueryContextDependent)
        {
            OQLQueryImpl q = new OQLQueryImpl(ctx, query);
            return q.execute(ctx.getSnapshot(), null);
        }
        else
        {
            return queryResult;
        }
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        Expression whereClause = query.getWhereClause();
        if (whereClause != null && whereClause.isContextDependent(ctx))
            return true;

        for (Query.SelectItem column : query.getSelectClause().getSelectList())
        {
            if (column.getExpression().isContextDependent(ctx))
                return true;
        }

        Expression fromExpression = query.getFromClause().getCall();
        if (fromExpression != null && fromExpression.isContextDependent(ctx))
            return true;

        for (Query union : query.getUnionQueries())
        {
            QueryExpression qe = new QueryExpression(union);
            if (qe.isContextDependent(ctx))
                return true;
        }

        return false;
    }

    @Override
    public String toString()
    {
        return "(" + query.toString() + ")";//$NON-NLS-1$//$NON-NLS-2$
    }

}

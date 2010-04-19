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
package org.eclipse.mat.report;

import org.eclipse.mat.query.IResult;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

/**
 * A container for a result of a query.
 */
public class QuerySpec extends Spec
{
    private String command;
    private IResult result;

    public QuerySpec()
    {}

    public QuerySpec(String name)
    {
        super(name);
    }

    public QuerySpec(String name, IResult result)
    {
        super(name);
        this.result = result;
    }

    public String getCommand()
    {
        return command;
    }

    public void setCommand(String query)
    {
        this.command = query;
    }

    public IResult getResult()
    {
        return result;
    }

    public void setResult(IResult result)
    {
        this.result = result;
    }

    @Override
    public void merge(Spec other)
    {
        if (!(other instanceof QuerySpec))
            throw new RuntimeException(MessageUtil.format(Messages.QuerySpec_Error_IncompatibleTypes, other.getName(),
                            getName()));

        super.merge(other);
        if (command == null)
            command = ((QuerySpec) other).command;

        if (result == null)
            result = ((QuerySpec) other).result;
    }

}

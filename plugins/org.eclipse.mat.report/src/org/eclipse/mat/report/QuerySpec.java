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
 * Allows a link for a command to be executed from the report.
 */
public class QuerySpec extends Spec
{
    private String command;
    private IResult result;

    /**
     * Create a QuerySpec with no title
     */
    public QuerySpec()
    {}

    /**
     * Create a QuerySpec with a title.
     * @param name
     */
    public QuerySpec(String name)
    {
        super(name);
    }

    /**
     * Create a QuerySpec with a title and a result of executing a query.
     * @param name
     * @param result
     */
    public QuerySpec(String name, IResult result)
    {
        super(name);
        this.result = result;
    }

    /**
     * Get the command to be executed by Memory Analyzer when the user clicks on 
     * a link in the report.
     * @return the command
     */
    public String getCommand()
    {
        return command;
    }

    /**
     * Sets a Memory Analyzer command to be executed when the user clicks on an icon in the report.
     * @param query
     */
    public void setCommand(String query)
    {
        this.command = query;
    }

    /**
     * Gets the body of this section which is the result of a query.
     * @return the body of the section
     */
    public IResult getResult()
    {
        return result;
    }

    /**
     * Sets the body of this section to the result of a query.
     * @param result
     */
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

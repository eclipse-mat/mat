package org.eclipse.mat.query.registry;

import java.text.MessageFormat;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.util.IProgressListener;

public class Queries
{

    public static Queries lookup(String name, IQueryContext context) throws SnapshotException
    {
        QueryDescriptor query = QueryRegistry.instance().getQuery(name);
        if (query == null)
            throw new SnapshotException(MessageFormat.format("Query not available: {0}", name));

        if (!query.accept(context))
            throw new SnapshotException(query.explain(context));

        ArgumentSet arguments = query.createNewArgumentSet(context);

        return new Queries(arguments);
    }

    public static Queries parse(String commandLine, IQueryContext context) throws SnapshotException
    {
        ArgumentSet arguments = CommandLine.parse(context, commandLine);
        return new Queries(arguments);
    }

    private final QueryDescriptor query;
    private final ArgumentSet arguments;

    private Queries(ArgumentSet arguments)
    {
        this.query = arguments.getQueryDescriptor();
        this.arguments = arguments;
    }

    public Queries set(String name, Object value) throws SnapshotException
    {
        ArgumentDescriptor argument = query.getArgumentByName(name);
        if (argument == null)
            throw new SnapshotException(MessageFormat.format("Unknown argument: {0} for query {1}", name, query
                            .getIdentifier()));

        arguments.setArgumentValue(argument, value);

        return this;
    }

    public QueryResult execute(IProgressListener listener) throws SnapshotException
    {
        return arguments.execute(listener);
    }

}

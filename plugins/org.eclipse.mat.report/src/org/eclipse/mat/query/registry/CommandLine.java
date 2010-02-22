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
package org.eclipse.mat.query.registry;

import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class CommandLine
{
    public static String[] tokenize(String line)
    {
        List<String> resultBuffer = new ArrayList<String>();

        if (line != null)
        {
            int len = line.length();
            boolean insideQuotes = false;
            StringBuilder buf = new StringBuilder();

            for (int ii = 0; ii < len; ++ii)
            {
                char c = line.charAt(ii);
                if (c == '"')
                {
                    // convert "" into a null value
                    if (insideQuotes && buf.length() == 0)
                        resultBuffer.add(null);
                    else
                        appendToBuffer(resultBuffer, buf);

                    insideQuotes = !insideQuotes;
                }
                else if (c == '\\')
                {
                    if ((len > ii + 1) && ((line.charAt(ii + 1) == '"') || (line.charAt(ii + 1) == '\\')))
                    {
                        buf.append(line.charAt(ii + 1));
                        ++ii;
                    }
                    else
                    {
                        buf.append("\\"); //$NON-NLS-1$
                    }
                }
                else
                {
                    if (insideQuotes)
                    {
                        buf.append(c);
                    }
                    else
                    {
                        if (Character.isWhitespace(c))
                        {
                            appendToBuffer(resultBuffer, buf);
                        }
                        else
                        {
                            buf.append(c);
                        }
                    }
                }
            }

            appendToBuffer(resultBuffer, buf);
        }

        return resultBuffer.toArray(new String[0]);
    }

    public static IResult execute(IQueryContext context, String commandLine, IProgressListener listener)
                    throws SnapshotException
    {
        ArgumentSet set = parse(context, commandLine);

        QueryResult result = set.execute(listener);

        return result != null ? result.getSubject() : null;
    }

    public static ArgumentSet parse(IQueryContext context, String line) throws SnapshotException
    {
        String[] args = CommandLine.tokenize(line);

        if (args.length == 0)
            throw new SnapshotException(MessageUtil.format(Messages.CommandLine_Error_InvalidCommand, line));

        // determine query
        QueryDescriptor descriptor = QueryRegistry.instance().getQuery(args[0].toLowerCase(Locale.ENGLISH));
        if (descriptor == null)
            throw new SnapshotException(MessageUtil.format(Messages.CommandLine_Error_NotFound, args[0]));

        ArgumentSet arguments = descriptor.createNewArgumentSet(context);

        // set all boolean values to false (via the command line, they can only
        // be set to true)
        for (ArgumentDescriptor argDescriptor : descriptor.getArguments())
        {
            if (argDescriptor.getType() == Boolean.class || argDescriptor.getType() == boolean.class)
            {
                arguments.setArgumentValue(argDescriptor, Boolean.FALSE);
            }
        }

        ParsePosition pos = new ParsePosition(1);

        parseArguments(arguments, args, pos);

        return arguments;
    }

    public static void fillIn(ArgumentSet arguments, String line) throws SnapshotException
    {
        String[] args = CommandLine.tokenize(line);

        if (args.length == 0)
            throw new SnapshotException(MessageUtil.format(Messages.CommandLine_Error_InvalidCommand, line));

        ParsePosition pos = new ParsePosition(0);

        parseArguments(arguments, args, pos);
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    private static void parseArguments(ArgumentSet arguments, String[] args, ParsePosition pos)
                    throws SnapshotException
    {
        QueryDescriptor descriptor = arguments.getQueryDescriptor();

        boolean mandatoryUnflaggedArgumentIsSet = false;

        while (pos.getIndex() < args.length)
        {
            String arg = args[pos.getIndex()];

            if (arg != null && arg.charAt(0) == '-' && arg.length() > 1)
            {
                pos.setIndex(pos.getIndex() + 1);

                String flag = arg.substring(1).toLowerCase(Locale.ENGLISH);
                ArgumentDescriptor argDescriptor = descriptor.byFlag(flag);
                if (argDescriptor == null)
                    throw new SnapshotException(MessageUtil.format(Messages.CommandLine_Error_MissingArgument,
                                    descriptor.getName(), flag));

                Object value;

                if (argDescriptor.getType() == Boolean.class || argDescriptor.getType() == boolean.class)
                {
                    value = Boolean.TRUE;
                }
                else if (arguments.getQueryContext().parses(argDescriptor.getType(), argDescriptor.getAdvice()))
                {
                    value = arguments.getQueryContext().parse(argDescriptor.getType(), argDescriptor.getAdvice(), args,
                                    pos);
                }
                else if (argDescriptor.isMultiple())
                {
                    value = consumeMultipleArguments(arguments.getQueryContext(), argDescriptor, args, pos);
                }
                else
                {
                    value = consumeSingleArgument(arguments.getQueryContext(), argDescriptor, args, pos);
                }

                arguments.setArgumentValue(argDescriptor, value);
            }
            else
            {
                // get mandatory non flagged parameter
                ArgumentDescriptor argDescriptor = null;
                for (ArgumentDescriptor a : arguments.getUnsetArguments())
                {
                    if (a.getFlag() == null)
                    {
                        argDescriptor = a;
                        break;
                    }
                }

                if (argDescriptor == null)
                    throw new SnapshotException(MessageUtil.format(Messages.CommandLine_Error_NoUnflaggedArguments,
                                    arg));

                if (mandatoryUnflaggedArgumentIsSet)
                    throw new SnapshotException(MessageUtil.format(Messages.CommandLine_Error_AssignmentFailed,
                                    args[pos.getIndex()], argDescriptor.getName()));

                Object value;

                if (arguments.getQueryContext().parses(argDescriptor.getType(), argDescriptor.getAdvice()))
                {
                    value = arguments.getQueryContext().parse(argDescriptor.getType(), argDescriptor.getAdvice(), args,
                                    pos);
                }
                else if (argDescriptor.isMultiple())
                {
                    value = consumeMultipleArguments(arguments.getQueryContext(), argDescriptor, args, pos);
                }
                else
                {
                    value = consumeSingleArgument(arguments.getQueryContext(), argDescriptor, args, pos);
                }

                arguments.setArgumentValue(argDescriptor, value);

                mandatoryUnflaggedArgumentIsSet = true;
            }
        }
    }

    private static Object consumeSingleArgument(IQueryContext context, ArgumentDescriptor descriptor, String[] args,
                    ParsePosition pos) throws SnapshotException
    {
        if (pos.getIndex() >= args.length)
            throw error(descriptor);

        String value = args[pos.getIndex()];
        pos.setIndex(pos.getIndex() + 1);
        return value == null ? null : context.convertToValue(descriptor.getType(), descriptor.getAdvice(), value);
    }

    private static Object consumeMultipleArguments(IQueryContext context, ArgumentDescriptor descriptor, String[] args,
                    ParsePosition pos) throws SnapshotException
    {
        List<String> arguments = consumeMultipleTokens(args, pos);

        if (descriptor.isMandatory() && arguments.isEmpty())
            throw error(descriptor);

        if (arguments == null)
            return null;

        List<Object> values = new ArrayList<Object>(arguments.size());
        for (String arg : arguments)
        {
            values.add(context.convertToValue(descriptor.getType(), descriptor.getAdvice(), arg));
        }
        return values;
    }

    private static List<String> consumeMultipleTokens(String[] args, ParsePosition pos)
    {
        List<String> arguments = new ArrayList<String>();
        while (pos.getIndex() < args.length)
        {
            String arg = args[pos.getIndex()];

            if (arg != null && arg.length() == 1 && arg.charAt(0) == ';')
                break;
            if (arg != null && arg.length() > 1 && (arg.charAt(0) == '-' || arg.charAt(arg.length() - 1) == ';'))
                break;

            pos.setIndex(pos.getIndex() + 1);
            if (arg != null)
                arguments.add(arg);
        }
        return arguments;
    }

    private static SnapshotException error(ArgumentDescriptor descriptor)
    {
        String flag = descriptor.getFlag() != null ? "( -" + descriptor.getFlag() + " )" : ""; //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
        return new SnapshotException(MessageUtil.format(Messages.CommandLine_Error_MissingValue, //
                        descriptor.getName(), flag));
    }

    private static void appendToBuffer(List<String> resultBuffer, StringBuilder buf)
    {
        if (buf.length() > 0)
        {
            resultBuffer.add(buf.toString());
            buf.setLength(0);
        }
    }

}

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
package org.eclipse.mat.impl.query;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.mat.query.IResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.PatternUtil;


public class CommandLine
{
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x\\p{XDigit}+$");

    static class ParsePosition
    {
        int index;

        public ParsePosition(int index)
        {
            this.index = index;
        }
    }

    private static String[] tokenize(String line)
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
                        buf.append("\\");
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

    public static IResult execute(final ISnapshot snapshot, String commandLine, IProgressListener listener)
                    throws SnapshotException
    {
        ArgumentSet set = parse(new IArgumentContextProvider()
        {

            public ISnapshot getPrimarySnapshot()
            {
                return snapshot;
            }

            public ISnapshot resolveSnapshot(String argument) throws SnapshotException
            {
                return null;
            }

        }, commandLine);

        QueryResult result = set.execute(listener);

        return result != null ? result.getSubject() : null;
    }

    public static HeapObjectParamArgument parseHeapObjectArgument(String line) throws SnapshotException
    {
        String[] args = CommandLine.tokenize(line);
        ParsePosition pos = new ParsePosition(0);
        HeapObjectParamArgument hopa = consumeHeapObjects(null, args, pos);

        if (pos.index < args.length)
        {
            StringBuilder buf = new StringBuilder();
            while (pos.index < args.length)
                buf.append(args[pos.index++]).append(" ");

            throw new SnapshotException(MessageFormat.format("Remaining unparsed line: {0}", new Object[] { buf
                            .toString() }));
        }

        return hopa;
    }

    public static ArgumentSet parse(IArgumentContextProvider contextProvider, String line) throws SnapshotException
    {
        String[] args = CommandLine.tokenize(line);

        if (args.length == 0)
            throw new SnapshotException(MessageFormat.format("Invalid command line: {0}", new Object[] { line }));

        // determine query
        QueryDescriptor descriptor = QueryRegistry.instance().getQuery(args[0].toLowerCase());
        if (descriptor == null)
            throw new SnapshotException(MessageFormat.format("Command {0} not found.", new Object[] { args[0] }));

        ArgumentSet arguments = descriptor.createNewArgumentSet(contextProvider);

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
            throw new SnapshotException(MessageFormat.format("Invalid command line: {0}", new Object[] { line }));

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

        while (pos.index < args.length)
        {
            String arg = args[pos.index];

            if (arg != null && arg.charAt(0) == '-' && arg.length() > 1)
            {
                pos.index++;

                String flag = arg.substring(1).toLowerCase();
                ArgumentDescriptor argDescriptor = descriptor.byFlag(flag);
                if (argDescriptor == null)
                    throw new SnapshotException(MessageFormat.format("Query ''{0}'' has no argument ''{1}''",
                                    new Object[] { descriptor.getName(), flag }));

                Object value;

                if (argDescriptor.getType() == Boolean.class || argDescriptor.getType() == boolean.class)
                {
                    value = Boolean.TRUE;
                }
                else if (argDescriptor.isHeapObject())
                {
                    value = consumeHeapObjects(argDescriptor, args, pos);
                    if (value == null)
                        throw error(argDescriptor, "Missing heap object values");
                }
                else if (argDescriptor.isMultiple())
                {
                    value = consumeMultipleArguments(argDescriptor, args, pos);
                }
                else
                {
                    value = consumeSingleArgument(argDescriptor, args, pos);
                }

                arguments.setArgumentValue(argDescriptor, value);
            }
            else
            {
                // get mandatory non flagged parameter
                ArgumentDescriptor argDescriptor = descriptor.getUnflaggedArgument();

                if (argDescriptor == null)
                    throw new SnapshotException("No unflagged arguments available");

                if (mandatoryUnflaggedArgumentIsSet)
                    throw new SnapshotException(MessageFormat.format(
                                    "''{0}'' cannot be assigned. Argument ''{1}'' is already set.", new Object[] {
                                                    args[pos.index], argDescriptor.getName() }));

                Object value;

                if (argDescriptor.isHeapObject())
                {
                    value = consumeHeapObjects(argDescriptor, args, pos);
                }
                else if (argDescriptor.isMultiple())
                {
                    value = consumeMultipleArguments(argDescriptor, args, pos);
                }
                else
                {
                    value = consumeSingleArgument(argDescriptor, args, pos);
                }

                arguments.setArgumentValue(argDescriptor, value);

                mandatoryUnflaggedArgumentIsSet = true;
            }
        }
    }

    private static HeapObjectParamArgument consumeHeapObjects(ArgumentDescriptor descriptor, String[] args,
                    ParsePosition pos) throws SnapshotException
    {
        if (pos.index >= args.length)
            return null;

        HeapObjectParamArgument proxy = new HeapObjectParamArgument();

        while (pos.index < args.length)
        {
            String arg = args[pos.index];

            if (arg == null)
            {
                pos.index++;
            }
            else if (HeapObjectParamArgument.Flags.VERBOSE.equals(arg))
            {
                pos.index++;

                proxy.setVerbose(true);
            }
            else if (HeapObjectParamArgument.Flags.INCLUDE_SUBCLASSES.equals(arg))
            {
                pos.index++;

                proxy.setIncludeSubclasses(true);
            }
            else if (HeapObjectParamArgument.Flags.INCLUDE_CLASS_INSTANCE.equals(arg))
            {
                pos.index++;

                proxy.setIncludeClassInstance(true);
            }
            else if (HeapObjectParamArgument.Flags.INCLUDE_LOADED_INSTANCES.equals(arg))
            {
                pos.index++;

                proxy.setIncludeLoadedInstances(true);
            }
            else if (HeapObjectParamArgument.Flags.RETAINED.equals(arg))
            {
                pos.index++;

                proxy.setRetained(true);
            }
            else if (arg.charAt(0) == '-')
            {
                break;
            }
            else if (ADDRESS_PATTERN.matcher(arg).matches())
            {
                long address = new BigInteger(arg.substring(2), 16).longValue();
                proxy.addObjectAddress(address);
                pos.index++;
            }
            else if ("select".equals(arg.toLowerCase()))
            {
                StringBuilder query = new StringBuilder(128);
                query.append(arg);
                pos.index++;

                while (pos.index < args.length)
                {
                    arg = args[pos.index];
                    if (arg.length() == 1 && arg.charAt(0) == ';')
                    {
                        pos.index++;
                        break;
                    }
                    else if (arg.charAt(arg.length() - 1) == ';')
                    {
                        query.append(" ").append(arg.substring(0, arg.length() - 1));
                        pos.index++;
                        break;
                    }
                    else if (arg.charAt(0) == '-')
                    {
                        break;
                    }
                    query.append(" ").append(arg);
                    pos.index++;
                }
                proxy.addOql(query.toString());
            }
            else
            {
                try
                {
                    Pattern pattern = Pattern.compile(PatternUtil.smartFix(arg, false));
                    proxy.addPattern(pattern);
                    pos.index++;
                }
                catch (PatternSyntaxException e)
                {
                    throw error(descriptor, MessageFormat.format("Error: ''{0}''", e.getMessage()));
                }
            }
        }

        return proxy;
    }

    private static Object consumeSingleArgument(ArgumentDescriptor descriptor, String[] args, ParsePosition pos)
                    throws SnapshotException
    {
        if (pos.index >= args.length)
            throw error(descriptor, "Missing value");

        String value = args[pos.index];
        pos.index++;
        return value == null ? null : descriptor.stringToValue(value);
    }

    private static Object consumeMultipleArguments(ArgumentDescriptor descriptor, String[] args, ParsePosition pos)
                    throws SnapshotException
    {
        List<String> arguments = consumeMultipleTokens(args, pos);

        if (descriptor.isMandatory() && arguments.isEmpty())
            throw error(descriptor, "Missing argument value");

        if (arguments == null)
            return null;

        List<Object> values = new ArrayList<Object>(arguments.size());
        for (String arg : arguments)
        {
            values.add(descriptor.stringToValue(arg));
        }
        return values;
    }

    private static List<String> consumeMultipleTokens(String[] args, ParsePosition pos)
    {
        List<String> arguments = new ArrayList<String>();
        while (pos.index < args.length)
        {
            String arg = args[pos.index];

            if (arg != null && arg.length() == 1 && arg.charAt(0) == ';')
                break;
            if (arg != null && arg.length() > 1 && (arg.charAt(0) == '-' || arg.charAt(arg.length() - 1) == ';'))
                break;

            pos.index++;
            if (arg != null)
                arguments.add(arg);
        }
        return arguments;
    }

    private static SnapshotException error(ArgumentDescriptor descriptor, String message)
    {
        if (descriptor == null)
        {
            return new SnapshotException(message);
        }
        else
        {
            String flag = descriptor.getFlag() != null ? "( -" + descriptor.getFlag() + " )" : "";
            return new SnapshotException(MessageFormat.format("{0} for argument ''{1}'' {2}", //
                            message, descriptor.getName(), flag));
        }
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

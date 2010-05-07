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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.oql.compiler.CompilerImpl.ConstantExpression;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;

class MethodCallExpression extends Expression
{
    String name;
    List<Expression> parameters;

    public MethodCallExpression(String name, List<Expression> parameters)
    {
        this.name = name;
        this.parameters = parameters;
    }

    @Override
    public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
    {
        Object subject = ctx.getSubject();
        if (subject == null)
            return null;

        if (subject.getClass().isArray())
            subject = PathExpression.asList(subject);

        // compute arguments
        Object[] arguments = new Object[parameters.size()];
        for (int ii = 0; ii < arguments.length; ii++)
            arguments[ii] = parameters.get(ii).compute(ctx);

        // special handling for #toString() and IObjects
        if (subject instanceof IObject && "toString".equals(this.name) && parameters.isEmpty()) //$NON-NLS-1$
        {
            String name = ((IObject) subject).getClassSpecificName();
            return name != null ? name : ((IObject) subject).getTechnicalName();
        }

        // find appropriate method
        Method[] methods = subject.getClass().getMethods();
        for (int ii = 0; ii < methods.length; ii++)
        {
            if (methods[ii].getName().equals(this.name))
            {
                Class<?>[] parameterTypes = methods[ii].getParameterTypes();
                if (parameterTypes.length == arguments.length)
                {
                    for (int jj = 0; jj < arguments.length; jj++)
                    {
                        if (arguments[jj] == ConstantExpression.NULL)
                        {
                            arguments[jj] = null;
                        }
                        if (arguments[jj] != null && !(parameterTypes[jj].isAssignableFrom(arguments[jj].getClass())))
                        {
                            // we do some special magic here...
                            if (parameterTypes[jj].isAssignableFrom(Pattern.class))
                                arguments[jj] = Pattern.compile(PatternUtil.smartFix(String.valueOf(arguments[jj]),
                                                false));
                        }
                    }

                    try
                    {
                        return methods[ii].invoke(subject, arguments);
                    }
                    catch (IllegalArgumentException e)
                    {
                        throw new SnapshotException(e);
                    }
                    catch (IllegalAccessException e)
                    {
                        throw new SnapshotException(e);
                    }
                    catch (InvocationTargetException e)
                    {
                        throw new SnapshotException(e);
                    }
                }
            }
        }

        throw new SnapshotException(MessageUtil.format(Messages.MethodCallExpression_Error_MethodNotFound,
                        new Object[] { this.name, subject }));
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        for (Expression element : this.parameters)
        {
            boolean isContextDependent = element.isContextDependent(ctx);

            if (isContextDependent)
                return true;
        }

        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(256);

        buf.append(name);
        buf.append("(");//$NON-NLS-1$

        for (Iterator<Expression> iter = this.parameters.iterator(); iter.hasNext();)
        {
            Expression element = iter.next();
            buf.append(element);

            if (iter.hasNext())
                buf.append(",");//$NON-NLS-1$
        }

        buf.append(")");//$NON-NLS-1$

        return buf.toString();
    }

}

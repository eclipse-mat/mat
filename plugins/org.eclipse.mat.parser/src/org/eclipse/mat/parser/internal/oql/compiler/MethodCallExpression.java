/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - matching of overloaded methods
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.oql.compiler.CompilerImpl.ConstantExpression;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;

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

        /*
         * Finding the right method is tricky as the arguments have already been boxed.
         * E.g. consider overloaded methods
         * remove(int)
         * remove(Object)
         * with argument Integer(1).
         */
        List<Method> extraMethods = new ArrayList<Method>();
        final Class<? extends Object> subjectClass = subject.getClass();
        Method[] methods;
        methods = subjectClass.getMethods();
        // If we checkMethodAccess then an interface method may be allowed even if the class method isn't
        boolean alwaysInterfaces = true;
        if (!Modifier.isPublic(subjectClass.getModifiers()) || alwaysInterfaces)
        {
            // Non-public class public methods are only accessible via
            // interfaces. For example java.util.Arrays$ArrayList.get()
            for (Class<?> superClass = subjectClass; superClass != null; superClass = superClass.getSuperclass())
            {
                for (Class<?> c : superClass.getInterfaces())
                {
                    firstChoiceMethods(extraMethods, c, arguments);
                }
            }
            firstChoiceMethods(extraMethods, subjectClass, arguments);
            for (Class<?> superClass = subjectClass; superClass != null; superClass = superClass.getSuperclass())
            {
                for (Class<?> c : superClass.getInterfaces())
                {
                    extraMethods.addAll(Arrays.asList(c.getMethods()));
                }
            }
        }
        else
        {
            firstChoiceMethods(extraMethods, subjectClass, arguments);
        }
        // Add static methods if a class object is passed
        if (subject instanceof Class)
        {
            for (Method m : ((Class<?>)subject).getMethods())
            {
                if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()))
                {
                    extraMethods.add(m);
                }
            }
        }
        if (extraMethods.size() > 0)
        {
            // Then add the original methods
            extraMethods.addAll(Arrays.asList(methods));
            // Remove duplicates
            extraMethods = new ArrayList<Method>(new LinkedHashSet<Method>(extraMethods));
            methods = extraMethods.toArray(new Method[extraMethods.size()]);
        }
        SnapshotException deferred = null;
        nextMethod: for (int ii = 0; ii < methods.length; ii++)
        {
            if (methods[ii].getName().equals(this.name))
            {
                Class<?>[] parameterTypes = methods[ii].getParameterTypes();
                if (parameterTypes.length == arguments.length ||
                    methods[ii].isVarArgs() && parameterTypes.length < arguments.length)
                {
                    Object savedArgs[] = null;
                    for (int jj = 0; jj < arguments.length; jj++)
                    {
                        if (arguments[jj] == ConstantExpression.NULL)
                        {
                            arguments[jj] = null;
                        }
                        if (!(methods[ii].isVarArgs() && jj >= parameterTypes.length - 1) &&
                            arguments[jj] != null && !isConvertible(parameterTypes[jj], arguments[jj]))
                        {
                            // we do some special magic here...
                            if (parameterTypes[jj].isAssignableFrom(Pattern.class))
                            {
                                if (savedArgs == null)
                                    savedArgs = new Object[arguments.length];
                                savedArgs[jj] = arguments[jj];
                                arguments[jj] = Pattern.compile(PatternUtil.smartFix(String.valueOf(arguments[jj]),
                                                false));
                            }
                            else
                            {
                                if (savedArgs != null)
                                {
                                    // Restore arguments
                                    for (int ia = 0; ia < savedArgs.length; ++ia)
                                    {
                                        if (savedArgs[ia] != null)
                                            arguments[ia] = savedArgs[ia];
                                    }
                                }
                                continue nextMethod;
                            }
                        }
                    }

                    try
                    {
                        checkMethodAccess(methods[ii]);
                        if (methods[ii].isVarArgs())
                        {
                            Object args2[] = convertVarArgs(parameterTypes, arguments);
                            if (args2 != null)
                                return methods[ii].invoke(subject, args2);
                        }
                        else
                        {
                            return methods[ii].invoke(subject, arguments);
                        }
                    }
                    catch (IllegalArgumentException e)
                    {
                        throw new SnapshotException(Arrays.toString(arguments), e);
                    }
                    catch (IllegalAccessException e)
                    {
                        throw new SnapshotException(methods[ii].toString(), e);
                    }
                    catch (InvocationTargetException e)
                    {
                        throw new SnapshotException(e);
                    }
                    catch (SecurityException e)
                    {
                        // Perhaps another method works
                        deferred = new SnapshotException(methods[ii].toString(), e);
                    }
                }
            }
        }
        if (deferred != null)
            throw deferred;

        StringBuilder argTypes = new StringBuilder();
        for (Object arg : arguments)
        {
            if (argTypes.length() > 0)
                argTypes.append(", "); //$NON-NLS-1$
            if (arg == ConstantExpression.NULL)
                arg = null;
            argTypes.append(arg != null ? unboxedType(arg.getClass()).getName() : null);
        }
        throw new SnapshotException(MessageUtil.format(Messages.MethodCallExpression_Error_MethodNotFound,
                        this.name, argTypes, subject, subjectClass.getName()));
    }

    /**
     * Try for a good match on the method.
     * Also try unboxed arguments.
     */
    private void firstChoiceMethods(List<Method> extraMethods, final Class<? extends Object> subjectClass,
                    Object[] arguments)
    {
        // find appropriate method
        Class<?>[] argumentTypes1 = new Class<?>[arguments.length];
        Class<?>[] argumentTypes2 = new Class<?>[arguments.length];
        int i = 0;
        boolean unbox = false;
        for (Object args : arguments)
        {
            if  (args == ConstantExpression.NULL)
                args = null;
            if (args != null)
                argumentTypes1[i] = args.getClass();
            argumentTypes2[i] = unboxedType(argumentTypes1[i]);
            if (argumentTypes2[i] != argumentTypes1[i])
                unbox = true;
            i++;
        }
        extracted(extraMethods, subjectClass, argumentTypes1);
        if (unbox)
            extracted(extraMethods, subjectClass, argumentTypes2);
    }

    private void extracted(List<Method> extraMethods, final Class<? extends Object> subjectClass,
                    Class<?>[] argumentTypes1)
    {
        try
        {
            // Avoid some NoSuchMethodExceptions by checking the name and number of parms first
            nextMethod: for (Method m2 : subjectClass.getMethods())
            {
                if (!m2.getName().equals(name))
                    continue;
                int parameterCount = m2.getParameterCount();
                if (parameterCount != argumentTypes1.length)
                    continue;
                Class<?>[] parameterTypes = m2.getParameterTypes();
                for (int j = 0; j < parameterCount; ++j)
                {
                    Class<?>pt = parameterTypes[j];
                    if (argumentTypes1[j] != null && !pt.isAssignableFrom(argumentTypes1[j]))
                        continue nextMethod;
                }
                Method m1 = subjectClass.getMethod(name, argumentTypes1);
                extraMethods.add(m1);
                break;
            }
        }
        catch (SecurityException e1)
        {
        }
        catch (NoSuchMethodException e1)
        {
        }
    }

    private Class<?> unboxedType(Class<?>arg)
    {
        if (arg == Boolean.class)
        {
            arg = boolean.class;
        }
        if (arg == Byte.class)
        {
            arg = byte.class;
        }
        else if (arg == Short.class)
        {
            arg = short.class;
        }
        else if (arg == Character.class)
        {
            arg = char.class;
        }
        else if (arg == Integer.class)
        {
            arg = int.class;
        }
        else if (arg == Long.class)
        {
            arg = long.class;
        }
        else if (arg == Float.class)
        {
            arg = float.class;
        }
        else if (arg == Double.class)
        {
            arg = double.class;
        }
        return arg;
    }

    /**
     * Can method invocation convert the argument via unboxing/widening conversion?
     */
    private boolean isConvertible(Class<?>parameterType, Object argument)
    {
        Class<?> argumentType = argument.getClass();
        if (parameterType.isAssignableFrom(argumentType))
            return true;
        if (argumentType == Boolean.class && (
                        parameterType == boolean.class || parameterType == Boolean.class))
            return true;
        if (argumentType == Byte.class && (
                        parameterType == byte.class || parameterType == Byte.class
                        || parameterType == short.class || parameterType == Short.class
                        || parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Short.class && (
                        parameterType == short.class || parameterType == Short.class
                        || parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Character.class && (
                        parameterType == char.class || parameterType == Character.class
                        || parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Integer.class && (
                        parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Long.class && (
                        parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Float.class && (
                        parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Double.class && (
                        parameterType == double.class || parameterType == Double.class))
            return true;
        return false;
    }

    /**
     * Check whether to allow this method call.
     * Syntax for mat.oqlmethodFilter
     * com.package1.* only classes/methods in this package
     * com.package1.** classes/methods in this package or subpackages
     * com.package* classes/methods starting with the prefix
     * com.*#methodname prefix before * and suffix after * must match
     * ! means not allowed
     * ; separates components
     * Throws an exception if not allowed.
     */
    static void checkMethodAccess(Method method)
    {
        /*
         * Default allows a few safe methods.
         */
        String match = System.getProperty("mat.oql.methodFilter", //$NON-NLS-1$
                        "org.eclipse.mat.snapshot.model.*;!org.eclipse.mat.snapshot.ISnapshot#dispose;org.eclipse.mat.snapshot.*;java.util.*;" //$NON-NLS-1$
                        + "!java.lang.ClassLoader#*;!java.lang.Compiler#*;!java.lang.Module*;!java.lang.Process*;!java.lang.Runtime#*;!java.lang.SecurityManager#*;!java.lang.System#*;!java.lang.Thread*;java.lang.*" //$NON-NLS-1$
                        + ";!*"); //$NON-NLS-1$
        String nm = method.getDeclaringClass().getName()+"#"+method.getName(); //$NON-NLS-1$
        for (String pt : match.split(";")) //$NON-NLS-1$
        {
            boolean not = pt.startsWith("!"); //$NON-NLS-1$
            if (not)
                pt = pt.substring(1);
            boolean m;
            if (pt.endsWith(".**")) //$NON-NLS-1$
                m = nm.startsWith(pt.substring(0, pt.length() - 2));
            else if (pt.endsWith(".*")) //$NON-NLS-1$
                m = nm.startsWith(pt.substring(0, pt.length() - 1))
                && !nm.substring(pt.length() - 1).contains("."); //$NON-NLS-1$
            else if (pt.endsWith("*")) //$NON-NLS-1$
                m = nm.startsWith(pt.substring(0, pt.length() - 1));
            else if (pt.contains("*")) //$NON-NLS-1$)
            {
                int i = pt.indexOf("*"); //$NON-NLS-1$)
                m = nm.startsWith(pt.substring(0, i)) && nm.endsWith(pt.substring(i + 1));
            }
            else
                m = nm.equals(pt);
            if (not && m)
                throw new AccessControlException(MessageUtil.format(Messages.MethodCallExpression_Error_MethodProhibited, nm, "!" + pt, match)); //$NON-NLS-1$
            if (m)
                break;
        }
    }

    /**
     * Collect varargs arguments into an array.
     * @param parameterTypes
     * @param arguments
     * @return null if the arguments won't convert
     */
    private Object[] convertVarArgs(Class<?>[] parameterTypes, Object arguments[])
    {
        /*
         *  If there is one var args argument and it looks like it matches the object array,
         *  then don't wrap it.
         */
        if (!(arguments.length == parameterTypes.length
                        && (arguments[arguments.length - 1] == null || parameterTypes[parameterTypes.length - 1]
                                        .isAssignableFrom(arguments[arguments.length - 1].getClass()))))
        {
            Object args2[] = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length - 1; ++i)
            {
                args2[i] = arguments[i];
            }
            Class<?> componentType = parameterTypes[parameterTypes.length - 1].getComponentType();
            Object varargs[] = (Object[]) Array.newInstance(componentType,
                            arguments.length - (parameterTypes.length - 1));
            args2[parameterTypes.length - 1] = varargs;
            for (int i = parameterTypes.length - 1; i < arguments.length; ++i)
            {
                if (arguments[i] != null && !componentType.isAssignableFrom(arguments[i].getClass()))
                    return null;
                varargs[i - (parameterTypes.length - 1)] = arguments[i];
            }
            return args2;
        }
        else
        {
            return arguments;
        }
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

/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - Integer arithmetic as well as Long
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.oql.compiler.CompilerImpl.ConstantExpression;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;

abstract class Operation extends Expression
{
    static final String ERR_NO_COMPARABLE = Messages.Operation_ErrorNoComparable;
    static final String ERR_NOT_A_NUMBER = Messages.Operation_ErrorNotNumber;

    protected Expression args[];

    public Operation(Expression args[])
    {
        this.args = args;
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        for (Expression expression : args)
        {
            if (expression.isContextDependent(ctx))
                return true;
        }

        return false;
    }

    public Expression[] getArguments()
    {
        return args;
    }

    public abstract String getSymbol();

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        buf.append("(");//$NON-NLS-1$

        for (int ii = 0; ii < args.length; ii++)
        {
            if (ii != 0)
                buf.append(' ').append(getSymbol()).append(' ');

            buf.append(args[ii]);
        }
        buf.append(")");//$NON-NLS-1$
        return buf.toString();
    }

    static abstract class RelationalOperation extends Operation
    {
        public RelationalOperation(Expression arg1, Expression arg2)
        {
            super(new Expression[] { arg1, arg2 });
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object obj1 = this.args[0].compute(ctx);
            Object obj2 = this.args[1].compute(ctx);

            if (obj1 == null || obj2 == null)
                return evalNull(obj1, obj2);

            boolean obj1IsNumber = obj1 instanceof Number;
            boolean obj2IsNumber = obj2 instanceof Number;

            if (obj1IsNumber && obj2IsNumber)
            {
                if (obj1 instanceof Double || obj1 instanceof Float || obj2 instanceof Double || obj2 instanceof Float)
                {
                    return eval(((Number) obj1).doubleValue(), ((Number) obj2).doubleValue());
                }
                else
                {
                    return eval(((Number) obj1).longValue(), ((Number) obj2).longValue());
                }
            }
            else
            {
                return eval(obj1, obj2);
            }
        }

        abstract Object evalNull(Object left, Object right);

        abstract Object eval(Object left, Object right);

        abstract Object eval(long left, long right);

        abstract Object eval(double left, double right);

    }

    static class Equal extends RelationalOperation
    {
        public Equal(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object evalNull(Object left, Object right)
        {
            return eval(left, right);
        }

        @Override
        Object eval(double left, double right)
        {
            return left == right;
        }

        @Override
        Object eval(long left, long right)
        {
            return left == right;
        }

        @Override
        Object eval(Object left, Object right)
        {
            if (left == ConstantExpression.NULL)
                left = null;

            if (right == ConstantExpression.NULL)
                right = null;

            if (left == null)
                return right == null;

            if (right == null)
                return false;

            return left.equals(right);
        }

        public String getSymbol()
        {
            return "=";//$NON-NLS-1$
        }
    }

    static class NotEqual extends RelationalOperation
    {
        public NotEqual(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object evalNull(Object left, Object right)
        {
            return eval(left, right);
        }

        @Override
        Object eval(double left, double right)
        {
            return left != right;
        }

        @Override
        Object eval(long left, long right)
        {
            return left != right;
        }

        @Override
        Object eval(Object left, Object right)
        {
            if (left == ConstantExpression.NULL)
                left = null;

            if (right == ConstantExpression.NULL)
                right = null;

            if (left == null)
                return right != null;

            if (right == null)
                return true;

            return !left.equals(right);
        }

        public String getSymbol()
        {
            return "!=";//$NON-NLS-1$
        }
    }

    static class GreaterThan extends RelationalOperation
    {
        public GreaterThan(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object evalNull(Object left, Object right)
        {
            throw new NullPointerException(this.args[left == null ? 0 : 1].toString());
        }

        @Override
        Object eval(double left, double right)
        {
            return left > right;
        }

        @Override
        Object eval(long left, long right)
        {
            return left > right;
        }

        @SuppressWarnings("unchecked")
        @Override
        Object eval(Object left, Object right)
        {
            if (!(left instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[0], left, left
                                .getClass().getName(), getSymbol()));
            if (!(right instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[1], right,
                                right.getClass().getName(), getSymbol()));

            return ((Comparable) left).compareTo(right) > 0;
        }

        public String getSymbol()
        {
            return ">";//$NON-NLS-1$
        }
    }

    static class GreaterThanOrEqual extends RelationalOperation
    {
        public GreaterThanOrEqual(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object evalNull(Object left, Object right)
        {
            throw new NullPointerException(this.args[left == null ? 0 : 1].toString());
        }

        @Override
        Object eval(double left, double right)
        {
            return left >= right;
        }

        @Override
        Object eval(long left, long right)
        {
            return left >= right;
        }

        @SuppressWarnings("unchecked")
        @Override
        Object eval(Object left, Object right)
        {
            if (!(left instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[0], left, left
                                .getClass().getName(), getSymbol()));
            if (!(right instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[1], right,
                                right.getClass().getName(), getSymbol()));

            return ((Comparable) left).compareTo(right) >= 0;
        }

        public String getSymbol()
        {
            return ">=";//$NON-NLS-1$
        }
    }

    static class LessThan extends RelationalOperation
    {
        public LessThan(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object evalNull(Object left, Object right)
        {
            throw new NullPointerException(this.args[left == null ? 0 : 1].toString());
        }

        @Override
        Object eval(double left, double right)
        {
            return left < right;
        }

        @Override
        Object eval(long left, long right)
        {
            return left < right;
        }

        @SuppressWarnings("unchecked")
        @Override
        Object eval(Object left, Object right)
        {
            if (!(left instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[0], left, left
                                .getClass().getName(), getSymbol()));
            if (!(right instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[1], right,
                                right.getClass().getName(), getSymbol()));

            return ((Comparable) left).compareTo(right) < 0;
        }

        public String getSymbol()
        {
            return "<";//$NON-NLS-1$
        }
    }

    static class LessThanOrEqual extends RelationalOperation
    {
        public LessThanOrEqual(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object evalNull(Object left, Object right)
        {
            throw new NullPointerException(this.args[left == null ? 0 : 1].toString());
        }

        @Override
        Object eval(double left, double right)
        {
            return left <= right;
        }

        @Override
        Object eval(long left, long right)
        {
            return left <= right;
        }

        @SuppressWarnings("unchecked")
        @Override
        Object eval(Object left, Object right)
        {
            if (!(left instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[0], left, left
                                .getClass().getName(), getSymbol()));
            if (!(right instanceof Comparable))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NO_COMPARABLE, this.args[1], right,
                                right.getClass().getName(), getSymbol()));

            return ((Comparable) left).compareTo(right) <= 0;
        }

        public String getSymbol()
        {
            return "<=";//$NON-NLS-1$
        }
    }

    static class In extends Operation
    {
        public In(Expression arg1, Expression arg2)
        {
            super(new Expression[] { arg1, arg2 });
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object obj1 = this.args[0].compute(ctx);
            Object obj2 = this.args[1].compute(ctx);

            if (obj1 == null || obj2 == null)
                return Boolean.FALSE;

            if (obj2 instanceof List)
            {
                return ((List<?>) obj2).contains(obj1);
            }
            else if (obj2 instanceof Object[])
            {
                for (Object element : ((Object[]) obj2))
                {
                    if (obj1.equals(element))
                        return Boolean.TRUE;
                }
                return Boolean.FALSE;

            }
            else if (obj2 instanceof int[])
            {
                int leftId = -1;

                if (obj1 instanceof Integer)
                    leftId = ((Integer) obj1).intValue();
                else if (obj1 instanceof IObject)
                    leftId = ((IObject) obj1).getObjectId();
                else
                    throw new UnsupportedOperationException(MessageUtil.format(Messages.Operation_Error_CannotCompare,
                                    new Object[] { obj1.getClass().getName() }));

                for (int objectId : (int[]) obj2)
                {
                    if (leftId == objectId)
                        return Boolean.TRUE;
                }

                return Boolean.FALSE;
            }
            else if (obj2 instanceof IResultTable)
            {
                IResultTable other = (IResultTable) obj2;

                int count = other.getRowCount();

                for (int ii = 0; ii < count; ii++)
                {
                    if (obj1.equals(other.getColumnValue(other.getRow(ii), 0)))
                        return Boolean.TRUE;
                }

                return Boolean.FALSE;
            }
            else
            {
                throw new UnsupportedOperationException(MessageUtil.format(Messages.Operation_Error_ArgumentOfUnknownClass, obj2
                                .getClass().getName()));
            }
        }

        @Override
        public String getSymbol()
        {
            return "IN";//$NON-NLS-1$
        }

    }

    static class NotIn extends Operation
    {
        public NotIn(Expression arg1, Expression arg2)
        {
            super(new Expression[] { arg1, arg2 });
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object obj1 = this.args[0].compute(ctx);
            Object obj2 = this.args[1].compute(ctx);

            if (obj1 == null || obj2 == null)
                return Boolean.FALSE;

            if (obj2 instanceof List)
            {
                return !((List<?>) obj2).contains(obj1);
            }
            else if (obj2 instanceof Object[])
            {
                for (Object element : ((Object[]) obj2))
                {
                    if (obj1.equals(element))
                        return Boolean.FALSE;
                }
                return Boolean.TRUE;

            }
            else if (obj2 instanceof int[])
            {
                int leftId = -1;

                if (obj1 instanceof Integer)
                    leftId = ((Integer) obj1).intValue();
                else if (obj1 instanceof IObject)
                    leftId = ((IObject) obj1).getObjectId();
                else
                    throw new UnsupportedOperationException(MessageUtil.format(Messages.Operation_Error_NotInCannotCompare, obj1
                                    .getClass().getName()));

                for (int objectId : (int[]) obj2)
                {
                    if (leftId == objectId)
                        return Boolean.FALSE;
                }

                return Boolean.TRUE;
            }
            else if (obj2 instanceof IResultTable)
            {
                IResultTable other = (IResultTable) obj2;

                int count = other.getRowCount();

                for (int ii = 0; ii < count; ii++)
                {
                    if (obj1.equals(other.getColumnValue(other.getRow(ii), 0)))
                        return Boolean.FALSE;
                }

                return Boolean.TRUE;
            }
            else
            {
                throw new UnsupportedOperationException(MessageUtil.format(Messages.Operation_Error_NotInArgumentOfUnknownClass,
                                obj2.getClass().getName()));
            }
        }

        @Override
        public String getSymbol()
        {
            return "NOT IN";//$NON-NLS-1$
        }

    }

    static class And extends Operation
    {

        public And(Expression[] args)
        {
            super(args);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            for (Expression ex : this.args)
            {
                if (!(booleanValue(ex.compute(ctx))))
                    return Boolean.FALSE;
            }

            return Boolean.TRUE;
        }

        public String getSymbol()
        {
            return "and";//$NON-NLS-1$
        }
    }

    static class Or extends Operation
    {

        public Or(Expression[] args)
        {
            super(args);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            for (Expression ex : this.args)
            {
                if (booleanValue(ex.compute(ctx)))
                    return Boolean.TRUE;
            }

            return Boolean.FALSE;
        }

        public String getSymbol()
        {
            return "or";//$NON-NLS-1$
        }
    }

    static class Like extends Operation
    {
        Pattern pattern;

        public Like(Expression arg1, String regex)
        {
            super(new Expression[] { arg1 });
            this.pattern = Pattern.compile(PatternUtil.smartFix(regex, false));
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object obj1 = this.args[0].compute(ctx);

            if (obj1 == null)
                return Boolean.FALSE;

            return this.pattern.matcher(String.valueOf(obj1)).matches();

        }

        public String getSymbol()
        {
            return "LIKE";//$NON-NLS-1$
        }

        @Override
        public String toString()
        {
            return new StringBuilder().append('(').append(args[0]).append(' ').append(getSymbol()).append(' ').append('"').
                            append(pattern.toString()).append('"').append(')').toString();
        }

    }

    static class NotLike extends Operation
    {
        Pattern pattern;

        public NotLike(Expression arg1, String regex)
        {
            super(new Expression[] { arg1 });
            this.pattern = Pattern.compile(PatternUtil.smartFix(regex, false));
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object obj1 = this.args[0].compute(ctx);

            if (obj1 == null)
                return Boolean.FALSE;

            return !this.pattern.matcher(String.valueOf(obj1)).matches();
        }

        public String getSymbol()
        {
            return "NOT LIKE";//$NON-NLS-1$
        }

        @Override
        public String toString()
        {
            return new StringBuilder().append('(').append(args[0]).append(' ').append(getSymbol()).append(' ').append('"').
                            append(pattern.toString()).append('"').append(')').toString();
        }

    }

    static class InstanceOf extends Operation
    {
        private static Map<String, Set<String>> class2intf = new HashMap<String, Set<String>>();

        String className;

        public InstanceOf(Expression arg1, String className)
        {
            super(new Expression[] { arg1 });
            this.className = className;
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object obj1 = this.args[0].compute(ctx);

            if (obj1 == null)
                return Boolean.FALSE;

            if (obj1 instanceof Integer)
                obj1 = ctx.getSnapshot().getObject(((Integer) obj1).intValue());

            Set<String> interfaces = getIntf(obj1.getClass());

            return interfaces.contains(className) ? Boolean.TRUE : Boolean.FALSE;
        }

        public String getSymbol()
        {
            return "implements";//$NON-NLS-1$
        }

        @Override
        public String toString()
        {
            return new StringBuilder().append('(').append(args[0]).append(' ').append(getSymbol()).append(' ').append(className).append(')')
                            .toString();
        }

        private static Set<String> getIntf(Class<?> context)
        {
            Set<String> intf = class2intf.get(context.getName());
            if (intf == null)
            {
                class2intf.put(context.getName(), intf = new HashSet<String>());
                inspect(intf, context);
            }
            return intf;
        }

        private static void inspect(Set<String> types, Class<?> clasz)
        {
            if (clasz == null || Object.class.equals(clasz))
                return;

            if (!types.add(clasz.getName()))
                return;

            Class<?>[] classes = clasz.getInterfaces();
            for (Class<?> c : classes)
                inspect(types, c);

            inspect(types, clasz.getSuperclass());
        }

    }

    static abstract class NumberOperation extends Operation
    {

        public NumberOperation(Expression arg1, Expression arg2)
        {
            super(new Expression[] { arg1, arg2 });
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object obj1 = this.args[0].compute(ctx);
            Object obj2 = this.args[1].compute(ctx);

            if (!(obj1 instanceof Number) || !(obj2 instanceof Number))
                return calculate(obj1, obj2);

            if (obj1 instanceof Double || obj1 instanceof Float || obj2 instanceof Double || obj2 instanceof Float)
            {
                return calculate(((Number) obj1).doubleValue(), ((Number) obj2).doubleValue());
            }
            else if (obj1 instanceof Long || obj2 instanceof Long)
            {
                return calculate(((Number) obj1).longValue(), ((Number) obj2).longValue());
            }
            else
            {
                return calculate(((Number) obj1).intValue(), ((Number) obj2).intValue());
            }
        }

        abstract Object calculate(int left, int right);

        abstract Object calculate(long left, long right);

        abstract Object calculate(double left, double right);
        
        Object calculate(Object obj1, Object obj2)
        {
            if (!(obj1 instanceof Number))
                throw new UnsupportedOperationException(MessageUtil.format(ERR_NOT_A_NUMBER,
                                new Object[] { args[0], obj1,
                                obj1 != null ? obj1.getClass().getName() : Messages.Function_unknown,
                                                getSymbol() }));

            throw new UnsupportedOperationException(MessageUtil.format(ERR_NOT_A_NUMBER,
                            new Object[] { args[1], obj2,
                            obj2 != null ? obj2.getClass().getName() : Messages.Function_unknown,
                                            getSymbol() }));
        }

    }

    static class Plus extends NumberOperation
    {

        public Plus(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object calculate(int left, int right)
        {
            return left + right;
        }

        @Override
        Object calculate(long left, long right)
        {
            return left + right;
        }

        @Override
        Object calculate(double left, double right)
        {
            return left + right;
        }

        @Override
        Object calculate(Object left, Object right)
        {
            // Allow String concatenation
            if (left instanceof String)
                return (String)left + right;
            else
                return super.calculate(left, right);
        }

        @Override
        public String getSymbol()
        {
            return "+";//$NON-NLS-1$
        }
    }

    static class Minus extends NumberOperation
    {

        public Minus(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }

        @Override
        Object calculate(int left, int right)
        {
            return left - right;
        }

        @Override
        Object calculate(long left, long right)
        {
            return left - right;
        }

        @Override
        Object calculate(double left, double right)
        {
            return left - right;
        }

        @Override
        public String getSymbol()
        {
            return "-";//$NON-NLS-1$
        }
    }

    static class Multiply extends NumberOperation
    {

        public Multiply(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }


        @Override
        Object calculate(int left, int right)
        {
            return left * right;
        }

        @Override
        Object calculate(long left, long right)
        {
            return left * right;
        }

        @Override
        Object calculate(double left, double right)
        {
            return left * right;
        }

        @Override
        public String getSymbol()
        {
            return "*";//$NON-NLS-1$
        }
    }

    static class Divide extends NumberOperation
    {

        public Divide(Expression arg1, Expression arg2)
        {
            super(arg1, arg2);
        }


        @Override
        Object calculate(int left, int right)
        {
            return (double) left / (double) right;
        }

        @Override
        Object calculate(long left, long right)
        {
            return (double) left / (double) right;
        }

        @Override
        Object calculate(double left, double right)
        {
            return left / right;
        }

        @Override
        public String getSymbol()
        {
            return "/";//$NON-NLS-1$
        }
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    static boolean booleanValue(Object object)
    {
        if (object instanceof Number)
        {
            double value = ((Number) object).doubleValue();
            // NaN -> false. Is this expected?
            return value != 0 && !Double.isNaN(value);
        }
        else if (object instanceof Boolean)
        {
            return ((Boolean) object).booleanValue();
        }
        else if (object instanceof String)
        {
            return ((String) object).length() != 0;
        }
        else if (object == null) { return false; }
        return true;
    }

    static Object numberValue(Object object)
    {
        if (object instanceof Integer)
            return ((Integer) object).longValue();
        if (object instanceof Float)
            return ((Float) object).doubleValue();
        return object;
    }

}

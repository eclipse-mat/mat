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
package org.eclipse.mat.parser.internal.oql.compiler;

import java.text.MessageFormat;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.registry.ClassSpecificNameResolverRegistry;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;


abstract class Function extends Expression
{
    static final String ERR_NO_FUNCTION = "''{0}'' yields ''{1}'' of type ''{2}'' which is not supported by the build-in function ''{3}''.";

    Expression argument;

    public Function(Expression argument)
    {
        this.argument = argument;
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        return this.argument.isContextDependent(ctx);
    }

    public abstract String getSymbol();

    @Override
    public String toString()
    {
        return getSymbol() + "(" + argument + ")";
    }

    static class ToHex extends Function
    {

        public ToHex(Expression argument)
        {
            super(argument);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object s = this.argument.compute(ctx);

            if (!(s instanceof Number))
                throw new RuntimeException("toHex needs Number as input");

            return "0x" + Long.toHexString(((Number) s).longValue());
        }

        @Override
        public String getSymbol()
        {
            return "toHex";
        }
    }

    static class ToString extends Function
    {

        public ToString(Expression argument)
        {
            super(argument);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object s = this.argument.compute(ctx);

            if (s instanceof IObject)
            {
                String name = ClassSpecificNameResolverRegistry.resolve((IObject) s);
                return name != null ? name : "";
            }
            else
            {
                return String.valueOf(s);
            }
        }

        @Override
        public String getSymbol()
        {
            return "toString";
        }

    }

    static class Outbounds extends Function
    {

        public Outbounds(Expression argument)
        {
            super(argument);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object s = this.argument.compute(ctx);

            if (s instanceof IObject)
            {
                return ctx.getSnapshot().getOutboundReferentIds(((IObject) s).getObjectId());
            }
            else if (s instanceof Integer)
            {
                return ctx.getSnapshot().getOutboundReferentIds(((Integer) s).intValue());
            }
            else
            {
                throw new SnapshotException(MessageFormat.format(ERR_NO_FUNCTION, argument, s, s != null ? s.getClass()
                                .getName() : "unknown", getSymbol()));
            }
        }

        @Override
        public String getSymbol()
        {
            return "outbounds";
        }

    }

    static class Inbounds extends Function
    {

        public Inbounds(Expression argument)
        {
            super(argument);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object s = this.argument.compute(ctx);

            if (s instanceof IObject)
            {
                return ctx.getSnapshot().getInboundRefererIds(((IObject) s).getObjectId());
            }
            else if (s instanceof Integer)
            {
                return ctx.getSnapshot().getInboundRefererIds(((Integer) s).intValue());
            }
            else
            {
                throw new SnapshotException(MessageFormat.format(ERR_NO_FUNCTION, argument, s, s != null ? s.getClass()
                                .getName() : "unknown", getSymbol()));
            }
        }

        @Override
        public String getSymbol()
        {
            return "inbounds";
        }

    }

    static class Dominators extends Function
    {

        public Dominators(Expression argument)
        {
            super(argument);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object s = this.argument.compute(ctx);

            if (s instanceof IObject)
            {
                return ctx.getSnapshot().getImmediateDominatedIds(((IObject) s).getObjectId());
            }
            else if (s instanceof Integer)
            {
                return ctx.getSnapshot().getImmediateDominatedIds(((Integer) s).intValue());
            }
            else
            {
                throw new SnapshotException(MessageFormat.format(ERR_NO_FUNCTION, argument, s, s != null ? s.getClass()
                                .getName() : "unknown", getSymbol()));
            }
        }

        @Override
        public String getSymbol()
        {
            return "dominators";
        }

    }

    static class ClassOf extends Function
    {

        public ClassOf(Expression argument)
        {
            super(argument);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object s = this.argument.compute(ctx);

            if (s instanceof IObject)
            {
                return ((IObject) s).getClazz();
            }
            else if (s instanceof Integer)
            {
                return ctx.getSnapshot().getClassOf(((Integer) s).intValue());
            }
            else
            {
                throw new SnapshotException(MessageFormat.format(ERR_NO_FUNCTION, argument, s, s != null ? s.getClass()
                                .getName() : "unknown", getSymbol()));
            }
        }

        @Override
        public String getSymbol()
        {
            return "classof";
        }

    }

    static class DominatorOf extends Function
    {

        public DominatorOf(Expression argument)
        {
            super(argument);
        }

        @Override
        public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException
        {
            Object s = this.argument.compute(ctx);

            int dominatorId = -1;

            if (s instanceof IObject)
            {
                dominatorId = ctx.getSnapshot().getImmediateDominatorId(((IObject) s).getObjectId());
            }
            else if (s instanceof Integer)
            {
                dominatorId = ctx.getSnapshot().getImmediateDominatorId(((Integer) s).intValue());
            }
            else
            {
                throw new SnapshotException(MessageFormat.format(ERR_NO_FUNCTION, argument, s, s != null ? s.getClass()
                                .getName() : "unknown", getSymbol()));
            }

            return dominatorId >= 0 ? ctx.getSnapshot().getObject(dominatorId) : null;
        }

        @Override
        public String getSymbol()
        {
            return "dominatorof";
        }

    }

}

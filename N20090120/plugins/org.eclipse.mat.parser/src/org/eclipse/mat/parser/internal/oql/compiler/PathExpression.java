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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;


class PathExpression extends Expression
{
    private List<Object> attributes;

    public PathExpression(List<Object> attributes)
    {
        this.attributes = attributes;
    }

    @Override
    public Object compute(EvaluationContext ctx) throws SnapshotException
    {
        try
        {
            Object current = null;
            int index = 0;

            // check for alias
            Object firstItem = attributes.get(0);
            if (firstItem instanceof Attribute)
            {
                Attribute firstAttribute = (Attribute) firstItem;
                current = !firstAttribute.isNative() ? ctx.getAlias(firstAttribute.getName()) : null;
            }

            if (current == null)
                current = ctx.getSubject();
            else
                index++;

            for (; index < this.attributes.size(); index++)
            {
                Object element = this.attributes.get(index);

                if (element != null && element.getClass().isArray())
                    element = asList(element);

                if (element instanceof Attribute)
                {
                    Attribute attribute = (Attribute) element;
                    if (attribute.isNative() || !(current instanceof IObject))
                    {
                        // special: we support the 'length' property for arrays
                        if (current.getClass().isArray())
                        {
                            if ("length".equals(attribute.getName()))
                            {
                                current = Array.getLength(current);
                            }
                            else
                            {
                                throw new SnapshotException(MessageFormat.format(
                                                "The array of type {0} has no property {1}", new Object[] {
                                                                current.getClass().getComponentType().getName(),
                                                                attribute.name }));
                            }
                        }
                        else
                        {
                            boolean didFindProperty = false;

                            BeanInfo info = Introspector.getBeanInfo(current.getClass());
                            PropertyDescriptor[] descriptors = info.getPropertyDescriptors();

                            for (PropertyDescriptor descriptor : descriptors)
                            {
                                if (attribute.getName().equals(descriptor.getName()))
                                {
                                    current = descriptor.getReadMethod().invoke(current, (Object[]) null);
                                    didFindProperty = true;
                                    break;
                                }
                            }

                            if (!didFindProperty) { throw new SnapshotException(MessageFormat.format(
                                            "Type {0} has no property {1}", new Object[] {
                                                            current.getClass().getName(), attribute.name })); }
                        }

                    }
                    else
                    {
                        IObject c = (IObject) current;
                        current = c.resolveValue(attribute.getName());
                    }

                }
                else if (element instanceof Expression)
                {
                    EvaluationContext methodCtx = new EvaluationContext(ctx);
                    methodCtx.setSubject(current);
                    current = ((Expression) element).compute(methodCtx);
                }
                else
                {
                    throw new SnapshotException(MessageFormat.format("Unknown element in path {0}",
                                    new Object[] { element }));
                }

                if (current == null)
                    break;
            }

            return current;
        }
        catch (Exception e)
        {
            throw SnapshotException.rethrow(e);
        }
    }

    protected static List<?> asList(Object element)
    {
        int size = Array.getLength(element);

        List<Object> answer = new ArrayList<Object>(size);
        for (int ii = 0; ii < size; ii++)
            answer.add(Array.get(element, ii));

        return answer;
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        Object firstItem = attributes.get(0);
        if (firstItem instanceof Attribute)
        {
            Attribute firstAttribute = (Attribute) firstItem;
            return !firstAttribute.isNative() && ctx.isAlias(firstAttribute.getName());
        }
        else if (firstItem instanceof Expression)
        {
            return ((Expression) firstItem).isContextDependent(ctx);
        }
        else
        {
            return false;
        }
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(256);

        for (Iterator<Object> iter = this.attributes.iterator(); iter.hasNext();)
        {
            Object element = iter.next();
            buf.append(element);

            if (iter.hasNext())
                buf.append(".");
        }

        return buf.toString();
    }

}

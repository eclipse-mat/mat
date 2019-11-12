/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - array indexing and wrapping
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

class PathExpression extends Expression
{
    private static class ArrayWrapper extends AbstractList<Object> {
        private final Object element;
        public ArrayWrapper(Object element) {
            this.element = element;
        }
        @Override
        public Object get(int i)
        {
            return Array.get(element, i);
        }

        @Override
        public int size()
        {
            return Array.getLength(element);
        }
        
        /*
         * Could override toString as but it can be useful to see the contents
         * element.getClass().getComponentType().getSimpleName()+'['+size()+']';
         */
    };
    
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
                if (current != null || !firstAttribute.isNative() && ctx.isAlias(firstAttribute.getName()))
                {
                    // We retrieved an alias (or snapshot) from the attribute, even if it was null, so advance
                    index++;
                }
            }

            if (index == 0)
                current = ctx.getSubject();

            for (; index < this.attributes.size(); index++)
            {
                if (current == null)
                    break;
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
                            if ("length".equals(attribute.getName())) //$NON-NLS-1$
                            {
                                current = Array.getLength(current);
                            }
                            else
                            {
                                throw new SnapshotException(MessageUtil.format(
                                                Messages.PathExpression_Error_ArrayHasNoProperty, new Object[] {
                                                                current.getClass().getComponentType().getName(),
                                                                attribute.name }));
                            }
                        }
                        else
                        {
                            boolean didFindProperty = false;

                            if (!attribute.isNative())
                            {
                                // Used for table rows from sub-select
                                if (current instanceof  Map<?,?>)
                                {
                                    Map<?,?> map = (Map<?,?>)current;
                                    current = map.get(attribute.getName());
                                    didFindProperty = true;
                                }
                            }

                            if (!didFindProperty)
                            {
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
                            }

                            if (!didFindProperty) { throw new SnapshotException(MessageUtil.format(
                                            Messages.PathExpression_Error_TypeHasNoProperty, new Object[] {
                                                            current.getClass().getName(), attribute.name })); }
                        }

                    }
                    else
                    {
                        IObject c = (IObject) current;
                        // Performance optimization - check that the field exists first
                        boolean found = false;
                        field: for (IClass cls = c.getClazz(); cls != null; cls = cls.getSuperClass())
                        {
                            for (FieldDescriptor fd : cls.getFieldDescriptors())
                            {
                                if (fd.getName().equals(attribute.getName()))
                                {
                                    found = true;
                                    break field;
                                }
                            }
                        }
                        if (found)
                        {
                            current = c.resolveValue(attribute.getName());
                        }
                        else
                        {
                            if (current instanceof IClass)
                            {
                                field: for (IClass cls = (IClass)current; cls != null; cls = cls.getSuperClass())
                                {
                                    for (Field f : cls.getStaticFields()) {
                                        if (f.getName().equals(attribute.getName())) {
                                            current = f.getValue();
                                            found = true;
                                            break field;
                                        }
                                    }
                                }
                            }
                            if (!found)
                            {
                                current = null;
                            }
                        }
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
                    throw new SnapshotException(MessageUtil.format(Messages.PathExpression_Error_UnknownElementInPath,
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

    protected static List<?> asList(final Object element)
    {
        int size = Array.getLength(element);
        final boolean wrap = true;
        List<Object> answer;

        if (wrap)
        {
            // Wrap the original array
            answer = new ArrayWrapper(element);
        }
        else
        {
            // Make a copy of the array
            answer = new ArrayList<Object>(size);
            for (int ii = 0; ii < size; ii++)
                answer.add(Array.get(element, ii));
        }

        return answer;
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx)
    {
        Object firstItem = attributes.get(0);
        if (firstItem instanceof Attribute)
        {
            Attribute firstAttribute = (Attribute) firstItem;
            if (!firstAttribute.isNative() && ctx.isAlias(firstAttribute.getName()))
                return true;
        }
        else if (firstItem instanceof Expression)
        {
            if (((Expression) firstItem).isContextDependent(ctx))
                return true;
        }
        for (int i = 1; i < attributes.size(); ++i)
        {
            Object nextItem = attributes.get(i);
            if (nextItem instanceof Expression)
                if (((Expression) nextItem).isContextDependent(ctx))
                    return true;
        }
        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder(256);

        boolean first = true;
        for (Iterator<Object> iter = this.attributes.iterator(); iter.hasNext();)
        {
            Object element = iter.next();
            if (!first && !(element instanceof ArrayIndexExpression))
                buf.append(".");//$NON-NLS-1$
            first = false;
            buf.append(element);
        }

        return buf.toString();
    }

}

/*******************************************************************************
 * Copyright (c) 2008, 2011 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - moving to new location and policy for drop-down menu
 *******************************************************************************/
package org.eclipse.mat.ui.internal.browser;

import java.util.Collections;
import java.util.List;

import org.eclipse.mat.internal.snapshot.HeapObjectContextArgument;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.ui.util.IPolicy;

public class Policy implements IPolicy
{
    private final boolean singleRowSelection;
    private final boolean multiRowSelection;
    private final boolean multiObjectSelection;
    private Class<? extends IContextObject> type;
    private List<IContextObject> context;
    private String label;

    /**
     * Determine whether to show this query:
     * Global context (no selection or heap available)
     *  any context arg - disallow
     * Local context
     *  context arg doesn't match available context - disallow
     *  no context arg and no heap arg - disallow (no point in operating on a selection)
     * @param menuContext
     * @param selectionLabel
     */
    public Policy(List<IContextObject> menuContext, String selectionLabel)
    {
        singleRowSelection = menuContext.size() == 1;
        multiRowSelection = menuContext.size() > 1;
        multiObjectSelection = multiRowSelection || singleRowSelection && menuContext.get(0) instanceof IContextObjectSet;

        type = IContextObjectSet.class;
        for (IContextObject obj : menuContext)
        {
            if (!IContextObjectSet.class.isAssignableFrom(obj.getClass()))
            {
                type = IContextObject.class;
                break;
            }
        }
        context = menuContext;
        label = selectionLabel;
    }

    public Policy()
    {
        this(Collections.<IContextObject>emptyList(),"");
    }

    public boolean accept(QueryDescriptor query)
    {
        boolean heapObjectArgExists = false;
        boolean heapObjectArgIsMultiple = false;

        boolean contextObjectArgExists = false;
        boolean contextObjectArgIsMultiple = false;

        for (ArgumentDescriptor argument : query.getArguments())
        {
            if (isHeapObject(argument))
            {
                heapObjectArgExists = true;
                heapObjectArgIsMultiple = heapObjectArgIsMultiple || argument.isMultiple()
                                || IHeapObjectArgument.class.isAssignableFrom(argument.getType());
            }
            else if (IContextObject.class.isAssignableFrom(argument.getType()))
            {
                contextObjectArgExists = true;
                contextObjectArgIsMultiple = contextObjectArgIsMultiple || argument.isMultiple();
                if (!argument.getType().isAssignableFrom(type))
                {
                    return false;
                }
            }
        }

        if (!heapObjectArgExists && !contextObjectArgExists && (singleRowSelection || multiRowSelection))
        {
            return false;
        }
        if (heapObjectArgExists && !heapObjectArgIsMultiple && multiObjectSelection)
        {
            return false;
        }
        if (contextObjectArgExists && (multiRowSelection ? !contextObjectArgIsMultiple : !singleRowSelection))
        {
            return false;
        }
        return true;
    }

    private boolean isHeapObject(ArgumentDescriptor argument)
    {
        Class<?> argType = argument.getType();

        if (argType.isAssignableFrom(int.class) && argument.getAdvice() == Argument.Advice.HEAP_OBJECT)
            return true;
        if (argType.isAssignableFrom(IObject.class))
            return true;
        if (argType.isAssignableFrom(IHeapObjectArgument.class))
            return true;

        return false;
    }
    
    public void fillInObjectArguments(ISnapshot snapshot, QueryDescriptor query, ArgumentSet set)
    {
        // avoid JavaNCSS parsing error
        final Class<?> intClass = int.class;

        boolean doneHeap = false;
        for (ArgumentDescriptor argument : query.getArguments())
        {
            if ((intClass.isAssignableFrom(argument.getType()) && argument.getAdvice() == Argument.Advice.HEAP_OBJECT) //
                            || IObject.class.isAssignableFrom(argument.getType()) //
                            || IHeapObjectArgument.class.isAssignableFrom(argument.getType()))
            {
                if (!doneHeap) {
                    set.setArgumentValue(argument, new HeapObjectContextArgument(snapshot, context, label));
                    doneHeap = true;
                }
            }
            else if (IContextObjectSet.class.isAssignableFrom(argument.getType()))
            {
                if (argument.isMultiple())
                {
                    set.setArgumentValue(argument, context);
                }
                else
                {
                    set.setArgumentValue(argument, context.get(0));
                }
            }
            else if (IContextObject.class.isAssignableFrom(argument.getType()))
            {
                if (argument.isMultiple())
                {
                    set.setArgumentValue(argument, context);
                }
                else
                {
                    set.setArgumentValue(argument, context.get(0));
                }
            }
        }
    }

}
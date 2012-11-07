/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.contentAssist;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.snapshot.ImageHelper;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.swt.graphics.Image;

/**
 * Provides the list of properties in the snapshot that starts with the provided
 * context String.
 */
public class PropertySuggestionProvider implements SuggestionProvider
{

    /**
     * Keeps the status of the property list. Since property set is filled
     * asynchronously, we do not try to show results as long as the flag is
     * false to avoid ConcurrentModificationException on the iterator.
     */
    boolean ready = false;

    /**
     * Ordered class list.
     */
    private TreeSet<ContentAssistElement> orderedList;

    /**
     * Builds this object passing the snapshot
     * 
     * @param snapshot
     */
    public PropertySuggestionProvider(ISnapshot snapshot)
    {
        InitializerJob asyncJob = new InitializerJob(snapshot);
        asyncJob.schedule();
    }

    /**
	 * Returns the list of ICompletionProposals
	 * It scans the ordered set up to the first valid element.
	 * Once it is found it fills the temporary list with all the elements up to the 
	 * first which is no more valid.
	 * 
	 * At that point it returns.
     */
    public List<ContentAssistElement> getSuggestions(String context)
    {
        LinkedList<ContentAssistElement> tempList = new LinkedList<ContentAssistElement>();
        boolean foundFirst = false;
        if (ready)
        {
            for (ContentAssistElement cp : orderedList)
            {
                String cName = cp.getClassName();
                if (cName.startsWith(context))
                {
                    foundFirst = true;
                    tempList.add(cp);
                }
                else
                {
                    if (foundFirst)
                    {
                        break;
                    }
                }
            }
        }
        return tempList;
    }

    /**
     * Scans the list of key classes, extracting methods and beans and put it ordered into orderedList.
     * 
     * @param snapshot
     */
    private void initList(ISnapshot snapshot) throws SnapshotException
    {
        Class<?> classes[] = {IObject.class, IInstance.class, IClassLoader.class, IClass.class, IArray.class, IPrimitiveArray.class, IObjectArray.class, ISnapshot.class};
        orderedList = new TreeSet<ContentAssistElement>();

        for (Class<?> c : classes)
        {
            Image im = imageForClass(c);
            
            for (Method m : c.getMethods()) {
                // Build an example and description of the method
                StringBuilder sb1 = new StringBuilder();
                StringBuilder sb2 = new StringBuilder();
                sb1.append("(");
                sb2.append("(");
                int i = 1;
                for (Class<?>parm : m.getParameterTypes()) {
                    if (sb1.length() > 1)
                        sb1.append(", ");
                    if (sb2.length() > 1)
                        sb2.append(", ");
                    String nm = parm.getSimpleName().toLowerCase(Locale.ENGLISH)+(i++);
                    sb1.append(nm);
                    sb2.append(parm.getName());
                    sb2.append(" ");
                    sb2.append(nm);
                }
                sb1.append(")");
                sb2.append(")");
                sb2.append(": ");
                sb2.append(m.getReturnType().getName());
                ContentAssistElement ce = new ContentAssistElement(m.getName()+sb1.toString(), im, m.getName()+sb2.toString());
                if (!orderedList.contains(ce))
                    orderedList.add(ce);
            }
        }
        for (Class<?> c : classes)
        {
            Image im = imageForClass(c);
            BeanInfo info;
            try
            {
                info = Introspector.getBeanInfo(c);
            }
            catch (IntrospectionException e)
            {
                ErrorHelper.logThrowable(e);
                continue;
            }
            PropertyDescriptor[] descriptors = info.getPropertyDescriptors();

            for (PropertyDescriptor descriptor : descriptors)
            {
                if (descriptor.getReadMethod() != null)
                {
                    // instantiate here in order to provide class and packages images.
                    ContentAssistElement ce = new ContentAssistElement("@"+descriptor.getName(), im, descriptor.getName());
                    if (!orderedList.contains(ce))
                        orderedList.add(ce);
                    // Remove getter methods as we use the bean instead
                    ce = new ContentAssistElement(descriptor.getReadMethod().getName()+"()", im);
                    orderedList.remove(ce);
                }
            }
        }
        ready = true;
    }

    private Image imageForClass(Class<?> c)
    {
        Image im;
        if (c == IObject.class)
        {
            // No image so we can distinguish from the IInstance case
            im = null;
        }
        else if (c == IInstance.class)
        {
            im = ImageHelper.getImage(ImageHelper.Type.OBJECT_INSTANCE);
        }
        else if (c == IClassLoader.class)
        {
            im = ImageHelper.getImage(ImageHelper.Type.CLASSLOADER_INSTANCE);
        }
        else if (c == IClass.class)
        {
            im = ImageHelper.getImage(ImageHelper.Type.CLASS_INSTANCE);
        }
        else if (c == IArray.class || c == IPrimitiveArray.class || c == IObjectArray.class)
        {
            im = ImageHelper.getImage(ImageHelper.Type.ARRAY_INSTANCE);
        }
        else if (c == ISnapshot.class)
        {
            im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.HEAP);
        }
        else
        {
            im = null;
        }
        return im;
    }

    /**
     * Asynchronous job to initialize the completion
     */
    private class InitializerJob extends Job
    {

        ISnapshot snapshot;

        public InitializerJob(ISnapshot snapshot)
        {
            super("Init content assistant");
            this.snapshot = snapshot;
        }

        @Override
        protected IStatus run(IProgressMonitor arg0)
        {
            try
            {
                initList(snapshot);
                return Status.OK_STATUS;
            }
            catch (SnapshotException e)
            {
                ErrorHelper.logThrowable(e);
            }
            return Status.CANCEL_STATUS;
        }

    }
}

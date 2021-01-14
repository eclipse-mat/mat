/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.mat.snapshot.SnapshotInfo;
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

    private static final String PROPERTY_TAG = "@"; //$NON-NLS-1$
    private static final String START_METHOD = "("; //$NON-NLS-1$
    private static final String PARM_SEP = ", "; //$NON-NLS-1$
    private static final String ARG_SEP = " "; //$NON-NLS-1$
    private static final String END_METHOD = ")"; //$NON-NLS-1$
    private static final String RETURN_SEP = " : "; //$NON-NLS-1$
    private static final String CLASS_SEP = " - "; //$NON-NLS-1$

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

    private String buildDescription(Method method, String parms, Class<?> retType, Class<?> declaringClass)
    {
        return method.getName() + START_METHOD + parms + END_METHOD + RETURN_SEP + retType.getSimpleName() + CLASS_SEP + declaringClass.getSimpleName();
    }
    /**
     * Scans the list of key classes, extracting methods and beans and put it ordered into orderedList.
     * 
     * @param snapshot
     */
    private void initList(ISnapshot snapshot) throws SnapshotException
    {
        Class<?> classes[] = {IObject.class, IInstance.class, IClassLoader.class, IClass.class, IArray.class, IPrimitiveArray.class, IObjectArray.class, ISnapshot.class, Object.class, SnapshotInfo.class, List.class};
        orderedList = new TreeSet<ContentAssistElement>();

        for (Class<?> c : classes)
        {
            nextMethod: for (Method m : c.getMethods()) {
                if (!isSuitableMethod(m)) continue;
                Image im = imageForClass(m.getDeclaringClass());
                // Build an example and description of the method
                StringBuilder sb1 = new StringBuilder();
                StringBuilder sb2 = new StringBuilder();
                int i = 1;
                for (Class<?>parm : m.getParameterTypes()) {
                    if (!isSuitableParameter(parm))
                        continue nextMethod;
                    if (sb1.length() > 1)
                        sb1.append(PARM_SEP);
                    if (sb2.length() > 1)
                        sb2.append(PARM_SEP);
                    String nm = parm.getSimpleName().replaceAll("\\[\\]", "").toLowerCase(Locale.ENGLISH)+(i++); //$NON-NLS-1$ //$NON-NLS-2$
                    sb1.append(nm);
                    sb2.append(parm.getSimpleName());
                    sb2.append(ARG_SEP);
                    sb2.append(nm);
                }
                ContentAssistElement ce = new ContentAssistElement(m.getName() + START_METHOD + sb1 + END_METHOD, im,
                                buildDescription(m, sb2.toString(), m.getReturnType(), m.getDeclaringClass()));
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
                    StringBuilder desc = new StringBuilder();
                    desc.append(descriptor.getName());
                    desc.append(RETURN_SEP);
                    desc.append(descriptor.getPropertyType().getSimpleName());
                    desc.append(CLASS_SEP);
                    desc.append(descriptor.getReadMethod().getDeclaringClass().getSimpleName());
                    ContentAssistElement ce = new ContentAssistElement(PROPERTY_TAG+descriptor.getName(), im, desc.toString());
                    if (!orderedList.contains(ce))
                        orderedList.add(ce);
                    // Remove getter methods as we use the bean instead
                    // E.g. AbstractCollection.isEmpty
                    String desc1 = buildDescription(descriptor.getReadMethod(), "", descriptor.getPropertyType(), descriptor.getReadMethod().getDeclaringClass()); //$NON-NLS-1$
                    ce = new ContentAssistElement(descriptor.getReadMethod().getName() + START_METHOD + END_METHOD, im, desc1);
                    orderedList.remove(ce);
                    // E.g. ArrayList.isEmpty
                    String desc2 = buildDescription(descriptor.getReadMethod(), "", descriptor.getPropertyType(), c); //$NON-NLS-1$
                    ce = new ContentAssistElement(descriptor.getReadMethod().getName() + START_METHOD + END_METHOD, im, desc2);
                    orderedList.remove(ce);
                }
            }
        }
        ready = true;
    }

    @SuppressWarnings("nls")
    static final String[] methods = {
        "contains", //(object1) : contains(Object object1) : boolean - List
        "containsAll", //(collection1) : containsAll(Collection collection1) : boolean - List
        "doesExtend", //(string1) : doesExtend(String string1) : boolean - IClass
        "equals", //(object1) : equals(Object object1) : boolean - List
        "equals", //(object1) : equals(Object object1) : boolean - Object
        "get", //(int1) : get(int int1) : Object - List
        "getClassOf", //(int1) : getClassOf(int int1) : IClass - ISnapshot
        "getClassesByName", //(pattern1, boolean2) : getClassesByName(Pattern pattern1, boolean boolean2) : Collection - ISnapshot
        "getClassesByName", //(string1, boolean2) : getClassesByName(String string1, boolean boolean2) : Collection - ISnapshot
        "getField", //(string1) : getField(String string1) : Field - IInstance
        "getGCRootInfo", //(int1) : getGCRootInfo(int int1) : GCRootInfo[] - ISnapshot
        "getHeapSize", //(int1) : getHeapSize(int int1) : long - ISnapshot
        "getHeapSize", //(int1) : getHeapSize(int[] int1) : long - ISnapshot
        "getImmediateDominatedIds", //(int1) : getImmediateDominatedIds(int int1) : int[] - ISnapshot
        "getImmediateDominatorId", //(int1) : getImmediateDominatorId(int int1) : int - ISnapshot
        "getInboundRefererIds", //(int1) : getInboundRefererIds(int int1) : int[] - ISnapshot
        "getMultiplePathsFromGCRoots", //(int1, map2) : getMultiplePathsFromGCRoots(int[] int1, Map map2) : IMultiplePathsFromGCRootsComputer - ISnapshot
        "getObject", //(int1) : getObject(int int1) : IObject - ISnapshot
        "getOutboundReferentIds", //(int1) : getOutboundReferentIds(int int1) : int[] - ISnapshot
        "getPathsFromGCRoots", //(int1, map2) : getPathsFromGCRoots(int int1, Map map2) : IPathsFromGCRootsComputer - ISnapshot
        "getProperty", //(string1) : getProperty(String string1) : Serializable - SnapshotInfo
        "getReferenceArray", //(int1, int2) : getReferenceArray(int int1, int int2) : long[] - IObjectArray
        "getRetainedHeapSize", //(int1) : getRetainedHeapSize(int int1) : long - ISnapshot
        "getSnapshotAddons", //(class1) : getSnapshotAddons(Class class1) : Object - ISnapshot
        "getThreadStack", //(int1) : getThreadStack(int int1) : IThreadStack - ISnapshot
        "getValueArray", //(int1, int2) : getValueArray(int int1, int int2) : Object - IPrimitiveArray
        "getValueAt", //(int1) : getValueAt(int int1) : Object - IPrimitiveArray
        "hasSuperClass", //() : hasSuperClass() : boolean - IClass
        "hashCode", //() : hashCode() : int - List
        "hashCode", //() : hashCode() : int - Object
        "indexOf", //(object1) : indexOf(Object object1) : int - List
        "isArray", //(int1) : isArray(int int1) : boolean - ISnapshot
        "isClass", //(int1) : isClass(int int1) : boolean - ISnapshot
        "isClassLoader", //(int1) : isClassLoader(int int1) : boolean - ISnapshot
        "isGCRoot", //(int1) : isGCRoot(int int1) : boolean - ISnapshot
        "lastIndexOf", //(object1) : lastIndexOf(Object object1) : int - List
        "mapAddressToId", //(long1) : mapAddressToId(long long1) : int - ISnapshot
        "mapIdToAddress", //(int1) : mapIdToAddress(int int1) : long - ISnapshot
        "resolveValue", //(string1) : resolveValue(String string1) : Object - IObject
        "size", //() : size() : int - List
        "subList", //(int1, int2) : subList(int int1, int int2) : List - List
        "toArray", //() : toArray() : Object[] - List
        "toArray", //(object1) : toArray(Object[] object1) : Object[] - List
        "toString", //() : toString() : String - Object
        "toString", //() : toString() : String - SnapshotInfo
    };
    private boolean isSuitableMethod(Method meth)
    {
        for (String m : methods) {
            if (meth.getName().equals(m))
                return true;
        }
        return false;
    }
    @SuppressWarnings("nls")
    static final String[] parmTypes = {
        "java.lang.Object",
        "java.lang.String",
        "java.lang.Class",
        "int",
        "char",
        "byte",
        "short",
        "float",
        "double",
        "long",
        "boolean",
        "java.util.Map",
        "java.util.Collection"
    };
    private boolean isSuitableParameter(Class<?> parm)
    {
        if (parm.isArray())
            parm = parm.getComponentType();
        for (String tp : parmTypes) {
            if (tp.equals(parm.getName())) {
                return true;
            }
        }
        return false;
    }

    private Image imageForClass(Class<?> c)
    {
        Image im;
        if (c == IObject.class)
        {
            im = ImageHelper.getImage(ImageHelper.Type.OBJECT_INSTANCE);
        }
        else if (c == IInstance.class)
        {
            im = ImageHelper.getImage(ImageHelper.Type.OBJECT_INSTANCE);
            im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.SIZE);
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
        else if (c == SnapshotInfo.class)
        {
            im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.HEAP_INFO);
        }
        else if (c == List.class)
        {
            im = MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.GROUPING);
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

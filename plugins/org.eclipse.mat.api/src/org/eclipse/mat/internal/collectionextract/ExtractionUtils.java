/*******************************************************************************
 * Copyright (c) 2008, 2016 SAP AG, IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *    James Livingston - expose collection utils as API
 *                       introduce CollectionExtractor extension
 *******************************************************************************/
package org.eclipse.mat.internal.collectionextract;

import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM14;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM15;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM16;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM17;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM18;
import static org.eclipse.mat.snapshot.extension.JdkVersion.IBM19;
import static org.eclipse.mat.snapshot.extension.JdkVersion.JAVA18;
import static org.eclipse.mat.snapshot.extension.JdkVersion.JAVA19;
import static org.eclipse.mat.snapshot.extension.JdkVersion.SUN;

import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.JdkVersion;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

public class ExtractionUtils
{
    public static Integer toInteger(Object i)
    {
        if (i != null && i instanceof Number)
            return ((Number) i).intValue();
        else
            return null;
    }

    public static int getNumberOfNotNullArrayElements(IObjectArray arrayObject)
    {
        // Fast path using referentIds for arrays with same number of outbounds
        // (+class id) as length
        // or no outbounds other than the class
        ISnapshot snapshot = arrayObject.getSnapshot();
        try
        {
            final int[] outs = snapshot.getOutboundReferentIds(arrayObject.getObjectId());
            if (outs.length == 1 || outs.length == arrayObject.getLength() + 1) { return outs.length - 1; }
        }
        catch (SnapshotException e)
        {}

        return getNumberOfNotNullArrayElements(arrayObject.getReferenceArray());
    }

    public static int getNumberOfNotNullArrayElements(long[] addresses)
    {
        int result = 0;
        for (int i = 0; i < addresses.length; i++)
        {
            if (addresses[i] != 0)
                result++;
        }
        return result;
    }

    public static int getNumberOfNotNullArrayElements(int[] ids)
    {
        int result = 0;
        for (int i = 0; i < ids.length; i++)
        {
            if (ids[i] != 0)
                result++;
        }
        return result;
    }

    public static int[] referenceArrayToIds(ISnapshot snapshot, long[] referenceArray) throws SnapshotException
    {
        ArrayInt arr = new ArrayInt();
        for (int i = 0; i < referenceArray.length; i++)
        {
            if (referenceArray[i] != 0)
                arr.add(snapshot.mapAddressToId(referenceArray[i]));
        }
        return arr.toArray();

    }


    /**
     * Get the only non-array object field from the object.
     * For example used for finding the HashMap from the HashSet
     *
     * @param obj
     * @return null if no non-array, or duplicates found
     * @throws SnapshotException
     */
    public static IInstance followOnlyNonArrayOutgoingReference(IObject obj) throws SnapshotException
    {
        final ISnapshot snapshot = obj.getSnapshot();
        IInstance ret = null;
        for (int i : snapshot.getOutboundReferentIds(obj.getObjectId()))
        {
            if (!snapshot.isArray(i) && !snapshot.isClass(i))
            {
                IObject o = snapshot.getObject(i);
                if (o instanceof IInstance)
                {
                    if (ret != null)
                    {
                        ret = null;
                        break;
                    }
                    ret = (IInstance) o;
                }
            }
        }
        return ret;
    }


    /**
     * Walks the only non-array object field from the object,
     *  stopping at the second-last.
     *
     * @param obj
     * @return null if no non-array, or duplicates found
     * @throws SnapshotException
     */
    public static IObject followOnlyOutgoingReferencesExceptLast(String field, IObject obj) throws SnapshotException
    {
        int j = field.lastIndexOf('.');
        if (j >= 0)
        {
            Object ret = obj.resolveValue(field.substring(0, j));
            if (ret instanceof IObject) { return (IObject) ret; }
        }
        // Find out how many fields to chain through to find the array
        IObject next = obj;
        // Don't do the last as that is the array field
        for (int i = field.indexOf('.'); i >= 0 && next != null; i = field.indexOf('.', i + 1))
        {
            next = followOnlyNonArrayOutgoingReference(next);
        }
        return next;
    }

    /**
     * Get the only array field from the object.
     *
     * @param obj
     * @return null if no non-array, or duplicates found
     * @throws SnapshotException
     */
    public static IObjectArray getOnlyArrayField(IObject obj) throws SnapshotException
    {
        IObjectArray ret = null;
        // Look for the only object array field
        final ISnapshot snapshot = obj.getSnapshot();
        for (int i : snapshot.getOutboundReferentIds(obj.getObjectId()))
        {
            if (snapshot.isArray(i))
            {
                IObject o = snapshot.getObject(i);
                if (o instanceof IObjectArray)
                {
                    // Have we already found a possible return type?
                    // If so, things are uncertain and so give up.
                    if (ret != null)
                        return null;
                    ret = (IObjectArray) o;
                }
            }
        }
        return ret;
    }

    @SuppressWarnings("nls")
    public static JdkVersion resolveVersion(ISnapshot snapshot) throws SnapshotException
    {
        Collection<IClass> classes;

        // Previously this code only checked the existence of certain IBM
        // classes for the IBM JVM version, but this is dangerous because we
        // will often backport stuff into older versions. Instead we will check
        // the JVM info, but I'm not sure if we can really depend on that being
        // there, so we'll leave the old checks as fallback.

        String jvmInfo = snapshot.getSnapshotInfo().getJvmInfo();
        if (jvmInfo != null)
        {
            // Example from IBM Java 6:
            // Java(TM) SE Runtime Environment(build pxi3260sr9ifx-20110208_02
            // (SR9))
            // IBM J9 VM(JRE 1.6.0 IBM J9 2.4 Linux x86-32
            // jvmxi3260sr9-20101209_70480 (JIT enabled, AOT enabled)
            // J9VM - 20101209_070480
            // JIT - r9_20101028_17488ifx3
            // GC - 20101027_AA)

            // Example from IBM Java 7 (for some reason doesn't include "IBM"
            // anymore):
            // JRE 1.7.0 Linux amd64-64 build 20130205_137358
            // (pxa6470sr4ifix-20130305_01(SR4+IV37419) )
            // JRE 1.7.0 Windows 7 amd64-64 build (pwa6470_27sr3fp10-20150708_01(SR3 FP10) )
            // JRE 1.8.0 Windows 7 amd64-64 (build 8.0.5.10 - pwa6480sr5fp10-20180214_01(SR5 FP10))

            if (jvmInfo.contains("IBM") || jvmInfo.contains("build "))
            {
                int jreIndex = jvmInfo.indexOf("JRE ");
                if (jreIndex != -1)
                {
                    String jreVersion = jvmInfo.substring(jreIndex + 4);
                    if (jreVersion.length() >= 3)
                    {
                        jreVersion = jreVersion.split(" ", 2)[0];
                        if (jreVersion.equals("9") || jreVersion.startsWith("9."))
                            return IBM19;
                        else if (jreVersion.equals("10") || jreVersion.startsWith("10."))
                            return IBM19;
                        else if (jreVersion.equals("11") || jreVersion.startsWith("11."))
                            return IBM19;
                        else if (jreVersion.equals("12") || jreVersion.startsWith("12."))
                            return IBM19;
                        else if (jreVersion.startsWith("1.8"))
                            return IBM18;
                        else if (jreVersion.startsWith("1.7"))
                        {
                            if (jvmInfo.matches(".*70sr.*\\(SR[1-3][^0-9].*") || jvmInfo.matches(".*\\(GA") && !jvmInfo.matches(".*70_27.*"))
                            {
                                // Harmony based collections
                                return IBM16;
                            }
                            // SR4 and later switches to Oracle
                            return IBM17;
                        }
                        else if (jreVersion.startsWith("1.6"))
                            return IBM16;
                        else if (jreVersion.startsWith("1.5"))
                            return IBM15;
                        else if (jreVersion.startsWith("1.4"))
                            return IBM14;
                    }
                }
            }
        }

        if ((classes = snapshot.getClassesByName("com.ibm.misc.JavaRuntimeVersion", false)) != null && !classes.isEmpty())return IBM15; //$NON-NLS-1$
        else if ((classes = snapshot.getClassesByName("com.ibm.oti.vm.BootstrapClassLoader", false)) != null && !classes.isEmpty())
        {
            // com.ibm.oti.util.Msg
            if ((classes = snapshot.getClassesByName("com.ibm.oti.util.Msg", false)) != null && !classes.isEmpty()) return IBM19;
            // java.lang.Integer$IntegerCache java 8, com/ibm/oti/util/WeakReferenceNode
            if ((classes = snapshot.getClassesByName("com/ibm/oti/util/WeakReferenceNode", false)) != null && !classes.isEmpty()) return IBM18;
            // com.ibm.oti.vm.VMLangAccess java 7
            if ((classes = snapshot.getClassesByName("com.ibm.oti.vm.VMLangAccess", false)) != null && !classes.isEmpty()) return IBM17;
            return IBM16; //$NON-NLS-1$
        }
        else if ((classes = snapshot.getClassesByName("com.ibm.jvm.Trace", false)) != null && !classes.isEmpty())return IBM14; //$NON-NLS-1$

        classes = snapshot.getClassesByName("sun.misc.Version", false);
        if (classes != null && classes.size() > 0)
        {
            Object ver = classes.iterator().next().resolveValue("java_version");
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("1.8.")) { return JAVA18; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("9.")) { return JAVA18; }
        }
        classes = snapshot.getClassesByName("java.lang.VersionProps", false);
        if (classes != null && classes.size() > 0)
        {
            Object ver = classes.iterator().next().resolveValue("java_version");
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("9.")) { return JAVA19; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("9-")) { return JAVA19; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("10.")) { return JAVA19; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("10-")) { return JAVA19; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("11.")) { return JAVA19; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("11-")) { return JAVA19; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("12.")) { return JAVA19; }
            if (ver instanceof IObject && ((IObject) ver).getClassSpecificName().startsWith("12-")) { return JAVA19; }
            // Lots of new Java versions planned
            return JAVA19;
        }
        return SUN;
    }
}

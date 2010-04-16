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
package org.eclipse.mat.snapshot.query;

import java.net.URL;

import org.eclipse.mat.snapshot.ISnapshot;

/**
 * A factory class for well-known icons, such as object, class and class loader.
 */
@SuppressWarnings("nls")
public final class Icons
{
    private static final String PREFIX = "/META-INF/icons/heapobjects/";

    /**
     * A Java class icon.
     */
    public static final URL CLASS = build("class");

    /** inbound reference to a class */
    public static final URL CLASS_IN = build("in/class");

    /** outbound reference from a class */
    public static final URL CLASS_OUT = build("out/class");

    /**
     * A Java class grey inbound icon. Used to show that all the instances of the class have already been seen.
     */
    public static final URL CLASS_IN_OLD = build("in/class_in_old");

    /**
     * A Java class grey outbound icon. Used to show that all the instances of the class have already been seen.
     */
    public static final URL CLASS_OUT_OLD = build("out/class_out_old");

    /**
     * A Java class grey/green inbound icon. Used to show that some of the instances of the class have already been seen.
     */
    public static final URL CLASS_IN_MIXED = build("in/class_mixed");

    /**
     * A Java class grey/green outbound icon. Used to show that some of the instances of the class have already been seen.
     */
    public static final URL CLASS_OUT_MIXED = build("out/class_mixed");

    /**
     * A Java object.
     */
    public static final URL OBJECT_INSTANCE = build("instance_obj");

    /**
     * An instance of java.lang.Class
     */
    public static final URL CLASS_INSTANCE = build("class_obj");

    /**
     * An array instance.
     */
    public static final URL ARRAY_INSTANCE = build("array_obj");

    /**
     * A class loader instance.
     */
    public static final URL CLASSLOADER_INSTANCE = build("classloader_obj");

    /**
     * A Java object decorated as Garbage Collection Root.
     */
    public static final URL OBJECT_INSTANCE_AS_GC_ROOT = build("instance_obj_gc_root");

    /**
     * An instance of java.lang.Class decorated as Garbage Collection Root.
     */
    public static final URL CLASS_INSTANCE_AS_GC_ROOT = build("class_obj_gc_root");

    /**
     * An array instance decorated as Garbage Collection Root.
     */
    public static final URL ARRAY_INSTANCE_AS_GC_ROOT = build("array_obj_gc_root");

    /**
     * A class loader instance decorated as Garbage Collection Root.
     */
    public static final URL CLASSLOADER_INSTANCE_AS_GC_ROOT = build("classloader_obj_gc_root");

    /**
     * A Java package.
     */
    public static final URL PACKAGE = build("package");

    /**
     * A Java superclass.
     */
    public static final URL SUPERCLASS = build("superclass");

    /**
     * Construct an icon URL for the current object pointing to the right image
     * and containing the right GC decoration.
     * 
     * @param snapshot the snapshot
     * @param objectId the object
     * @return URL of the icon
     */
    public static final URL forObject(ISnapshot snapshot, int objectId)
    {
        boolean isGCRoot = snapshot.isGCRoot(objectId);

        if (snapshot.isArray(objectId))
            return isGCRoot ? ARRAY_INSTANCE_AS_GC_ROOT : ARRAY_INSTANCE;
        else if (snapshot.isClass(objectId))
            return isGCRoot ? CLASS_INSTANCE_AS_GC_ROOT : CLASS_INSTANCE;
        else if (snapshot.isClassLoader(objectId))
            return isGCRoot ? CLASSLOADER_INSTANCE_AS_GC_ROOT : CLASSLOADER_INSTANCE;
        else
            return isGCRoot ? OBJECT_INSTANCE_AS_GC_ROOT : OBJECT_INSTANCE;
    }

    /**
     * Construct an inbound reference icon URL for the current object pointing to the right image
     * and containing the right GC decoration.
     * @param snapshot the snapshot
     * @param objectId the object
     * @return URL of the icon
     */
    public static final URL inbound(ISnapshot snapshot, int objectId)
    {
        boolean isGCRoot = snapshot.isGCRoot(objectId);

        if (snapshot.isArray(objectId))
            return isGCRoot ? ARRAY_INSTANCE_IN_GC : ARRAY_INSTANCE_IN;
        else if (snapshot.isClass(objectId))
            return isGCRoot ? CLASS_INSTANCE_IN_GC : CLASS_INSTANCE_IN;
        else if (snapshot.isClassLoader(objectId))
            return isGCRoot ? CLASSLOADER_INSTANCE_IN_GC : CLASSLOADER_INSTANCE_IN;
        else
            return isGCRoot ? OBJECT_INSTANCE_IN_GC : OBJECT_INSTANCE_IN;
    }

    /**
     * Construct an outbound reference icon URL for the current object pointing to the right image
     * and containing the right GC decoration.
     * @param snapshot the snapshot
     * @param objectId the object
     * @return URL of the icon
     */
    public static final URL outbound(ISnapshot snapshot, int objectId)
    {
        boolean isGCRoot = snapshot.isGCRoot(objectId);

        if (snapshot.isArray(objectId))
            return isGCRoot ? ARRAY_INSTANCE_OUT_GC : ARRAY_INSTANCE_OUT;
        else if (snapshot.isClass(objectId))
            return isGCRoot ? CLASS_INSTANCE_OUT_GC : CLASS_INSTANCE_OUT;
        else if (snapshot.isClassLoader(objectId))
            return isGCRoot ? CLASSLOADER_INSTANCE_OUT_GC : CLASSLOADER_INSTANCE_OUT;
        else
            return isGCRoot ? OBJECT_INSTANCE_OUT_GC : OBJECT_INSTANCE_OUT;
    }

    // this class must not have any dependency on AWT or SWT to be able to run
    // on any (linux) server. Hence, overlay is no option.

    private static final URL OBJECT_INSTANCE_IN = build("in/instance_obj");
    private static final URL OBJECT_INSTANCE_IN_GC = build("in/instance_obj_gc_root");
    private static final URL OBJECT_INSTANCE_OUT = build("out/instance_obj");
    private static final URL OBJECT_INSTANCE_OUT_GC = build("out/instance_obj_gc_root");

    private static final URL CLASS_INSTANCE_IN = build("in/class_obj");
    private static final URL CLASS_INSTANCE_IN_GC = build("in/class_obj_gc_root");
    private static final URL CLASS_INSTANCE_OUT = build("out/class_obj");
    private static final URL CLASS_INSTANCE_OUT_GC = build("out/class_obj_gc_root");

    private static final URL ARRAY_INSTANCE_IN = build("in/array_obj");
    private static final URL ARRAY_INSTANCE_IN_GC = build("in/array_obj_gc_root");
    private static final URL ARRAY_INSTANCE_OUT = build("out/array_obj");
    private static final URL ARRAY_INSTANCE_OUT_GC = build("out/array_obj_gc_root");

    private static final URL CLASSLOADER_INSTANCE_IN = build("in/classloader_obj");
    private static final URL CLASSLOADER_INSTANCE_IN_GC = build("in/classloader_obj_gc_root");
    private static final URL CLASSLOADER_INSTANCE_OUT = build("out/classloader_obj");
    private static final URL CLASSLOADER_INSTANCE_OUT_GC = build("out/classloader_obj_gc_root");

    private static URL build(String name)
    {
        return Icons.class.getResource(PREFIX + name + ".gif");
    }

    /**
     * Retrieve a URL for a named image.
     * @param imageName the image
     * @return the URL used to retrieve the icon.
     */
    public static URL getURL(String imageName)
    {
        return Icons.class.getResource("/META-INF/icons/" + imageName);
    }
}

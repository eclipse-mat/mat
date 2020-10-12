/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.dtfj.bridge;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.mat.dtfj.bridge.api.DTFJBridgeConnector;
import org.eclipse.mat.util.MessageUtil;

/**
 * Search for DTFJ classes in explicit directories, a DTFJ plugin, and/or the
 * running JVM depending on preferences and availability.
 */
public class CustomClassLoader extends URLClassLoader
{
    private final boolean isDebuggingClassloading = "true"
                    .equals(Platform.getDebugOption("org.eclipse.mat.dtfj.bridge/classloading/debug"));
    private final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
    private final boolean someExplicitJARs;
    private boolean foundDTFJ;
    private boolean foundJ9DDR;

    public CustomClassLoader(ClassLoader parent)
    {
        super(new URL[] {}, parent);

        String preferencesBundleSymbolicName = "org.eclipse.mat.dtfj";
        IPreferencesService prefs = Platform.getPreferencesService();
        String dtfjDirectories = prefs.getString(preferencesBundleSymbolicName,
                        DTFJBridgeConnector.DTFJ_PARENT_DIRECTORIES, null, null);
        boolean skipDtfjPlugin = prefs.getBoolean(preferencesBundleSymbolicName, DTFJBridgeConnector.DTFJ_SKIP_PLUGIN,
                        false, null);

        if (isDebuggingClassloading)
            System.out.println(this + " dtfjDirectories: " + dtfjDirectories + ", skipDtfjPlugin: " + skipDtfjPlugin
                            + ", JVM info: " + getJVMInfo());

        // If the user has specified explicit Java 8 directories
        // containing dtfj.jar and j9ddr.jar, then use those.
        if (dtfjDirectories != null && dtfjDirectories.length() > 0)
        {
            try
            {
                findJARs(dtfjDirectories);
            }
            catch (IOException e)
            {
                DTFJBridgePlugin.getDefault().log(IStatus.ERROR,
                                MessageUtil.format(Messages.CustomClassLoader_error_searching_multiple, dtfjDirectories,
                                                e.getLocalizedMessage()),
                                e);
            }
        }

        // If the user hasn't specified explicit directories and hasn't
        // selected the skip DTFJ plugin option, then search for dtfj.jar
        // and j9ddr.jar within the platform.
        else if (!skipDtfjPlugin)
        {
            try
            {
                File platform = new File(Platform.getInstallLocation().getURL().toURI());

                DTFJBridgePlugin.getDefault().log(IStatus.INFO, MessageUtil.format(
                                Messages.CustomClassLoader_searching_install, platform.getAbsolutePath()), null);

                try
                {
                    recurseDirectory(platform);
                }
                catch (IOException e)
                {
                    DTFJBridgePlugin.getDefault().log(IStatus.ERROR,
                                    MessageUtil.format(Messages.CustomClassLoader_error_searching,
                                                    platform.getAbsolutePath(), e.getLocalizedMessage()),
                                    e);
                }
            }
            catch (URISyntaxException e)
            {
                DTFJBridgePlugin.getDefault()
                                .log(IStatus.ERROR,
                                                MessageUtil.format(Messages.CustomClassLoader_error_resolving_platform,
                                                                Platform.getInstallLocation(), e.getLocalizedMessage()),
                                                e);
            }
        }

        // We haven't found anything yet, so for Java 8, find the
        // dtfj.jar and j9ddr.jar within the JVM. This won't exist for
        // Java > 8, but that's okay because then we'll just defer to
        // the system classloader below.
        if (getURLs().length == 0)
        {
            if (skipDtfjPlugin)
            {
                DTFJBridgePlugin.getDefault().log(IStatus.INFO,
                                MessageUtil.format(Messages.CustomClassLoader_searching_jvm_skipdtfj, getJVMInfo()),
                                null);
            }
            else
            {
                DTFJBridgePlugin.getDefault().log(IStatus.INFO,
                                MessageUtil.format(Messages.CustomClassLoader_searching_jvm, getJVMInfo()), null);
            }

            String jvmDirectory = System.getProperty("java.home");

            if (jvmDirectory != null && jvmDirectory.length() > 0)
            {
                File jvmHome = new File(jvmDirectory);
                try
                {
                    recurseDirectory(jvmHome);
                }
                catch (IOException e)
                {
                    DTFJBridgePlugin.getDefault()
                                    .log(IStatus.ERROR,
                                                    MessageUtil.format(Messages.CustomClassLoader_error_searching,
                                                                    jvmHome.getAbsolutePath(), e.getLocalizedMessage()),
                                                    e);

                }
            }
        }

        someExplicitJARs = getURLs().length > 0;

        // If no explicit JARs were found (e.g. Java >= 9), then we'll just
        // be deferring to the system classloader
        if (!someExplicitJARs)
        {
            if (skipDtfjPlugin)
            {
                DTFJBridgePlugin.getDefault().log(IStatus.INFO,
                                MessageUtil.format(Messages.CustomClassLoader_deferring_jvm_skipdtfj, getJVMInfo()),
                                null);
            }
            else
            {
                DTFJBridgePlugin.getDefault().log(IStatus.INFO,
                                MessageUtil.format(Messages.CustomClassLoader_deferring_jvm, getJVMInfo()), null);
            }
        }

    }

    private String getJVMInfo()
    {
        String jvmInfo = System.getProperty("java.vendor") + " " + System.getProperty("java.version") + " @ "
                        + System.getProperty("java.home");
        return jvmInfo;
    }

    private void findJARs(String dtfjDirectories) throws IOException
    {
        String[] directories = dtfjDirectories.split(File.pathSeparator);
        for (String directory : directories)
        {
            File file = new File(directory);
            if (file.exists())
            {
                recurseDirectory(file);
            }
            else
            {
                DTFJBridgePlugin.getDefault().log(IStatus.WARNING,
                                MessageUtil.format(Messages.CustomClassLoader_nodir, file.getAbsolutePath()), null);
            }
        }
    }

    private void recurseDirectory(File file) throws IOException
    {
        for (File child : file.listFiles())
        {
            if (child.isDirectory())
            {
                recurseDirectory(child);
            }
            else
            {
                String fileName = child.getName();
                if (fileName.equals("dtfj.jar") || fileName.equals("j9ddr.jar"))
                {
                    if (isDebuggingClassloading)
                        System.out.println(this + " recurseDirectory found: " + child.getAbsolutePath());

                    boolean doAdd = true;

                    // Don't bother adding either of the JARs twice.
                    // First JAR found is the one that will be used.
                    if (fileName.equals("dtfj.jar") && foundDTFJ)
                    {
                        doAdd = false;
                    }
                    else if (fileName.equals("j9ddr.jar") && foundJ9DDR)
                    {
                        doAdd = false;
                    }

                    if (doAdd)
                    {
                        addURL(child.toURI().toURL());

                        DTFJBridgePlugin.getDefault().log(IStatus.INFO, MessageUtil
                                        .format(Messages.CustomClassLoader_addedjar, child.getAbsolutePath()), null);

                        if (fileName.equals("dtfj.jar"))
                        {
                            foundDTFJ = true;
                        }
                        else if (fileName.equals("j9ddr.jar"))
                        {
                            foundJ9DDR = true;
                        }
                    }
                }
            }
        }
    }

    @Override
    protected Class<?> loadClass(String className, boolean resolveClass) throws ClassNotFoundException
    {
        // We need to override this method because the default behavior
        // first checks the parent classloader:
        // https://docs.oracle.com/javase/8/docs/api/java/lang/ClassLoader.html#loadClass-java.lang.String-boolean-
        // However, we may be configured to
        // defer first to a set of explicit directories or a DTFJ plugin, so
        // we want to handle that logic ourselves in findClass
        Class<?> result = findLoadedClass(className);
        if (result == null)
        {
            result = findClass(className);
        }
        return result;
    }

    @Override
    protected Class<?> findClass(String className) throws ClassNotFoundException
    {
        Class<?> result = null;

        if (className.equals("com.ibm.dtfj.runtime.ManagedRuntime") || className.equals("com.ibm.dtfj.java.JavaMonitor")
                        || className.equals("com.ibm.dtfj.java.JavaLocation")
                        || className.equals("com.ibm.dtfj.java.JavaThread")
                        || className.equals("com.ibm.dtfj.java.JavaMember")
                        || className.equals("com.ibm.dtfj.java.JavaHeap")
                        || className.equals("com.ibm.dtfj.java.JavaClass")
                        || className.equals("com.ibm.dtfj.java.JavaVMOption")
                        || className.equals("com.ibm.dtfj.java.JavaReference")
                        || className.equals("com.ibm.dtfj.java.JavaVMInitArgs")
                        || className.equals("com.ibm.dtfj.java.JavaRuntimeMemorySection")
                        || className.equals("com.ibm.dtfj.java.JavaStackFrame")
                        || className.equals("com.ibm.dtfj.java.JavaObject")
                        || className.equals("com.ibm.dtfj.java.JavaRuntime")
                        || className.equals("com.ibm.dtfj.java.JavaClassLoader")
                        || className.equals("com.ibm.dtfj.java.JavaRuntimeMemoryCategory")
                        || className.equals("com.ibm.dtfj.java.JavaField")
                        || className.equals("com.ibm.dtfj.java.JavaMethod")
                        || className.equals("com.ibm.dtfj.image.ImageThread")
                        || className.equals("com.ibm.dtfj.image.ImagePointer")
                        || className.equals("com.ibm.dtfj.image.ImageRegister")
                        || className.equals("com.ibm.dtfj.image.ImageProcess")
                        || className.equals("com.ibm.dtfj.image.CorruptDataException")
                        || className.equals("com.ibm.dtfj.image.DataUnavailable")
                        || className.equals("com.ibm.dtfj.image.DTFJException")
                        || className.equals("com.ibm.dtfj.image.ImageStackFrame")
                        || className.equals("com.ibm.dtfj.image.Image")
                        || className.equals("com.ibm.dtfj.image.MemoryAccessException")
                        || className.equals("com.ibm.dtfj.image.ImageSection")
                        || className.equals("com.ibm.dtfj.image.ImageSymbol")
                        || className.equals("com.ibm.dtfj.image.ImageModule")
                        || className.equals("com.ibm.dtfj.image.ImageFactory")
                        || className.equals("com.ibm.dtfj.image.CorruptData")
                        || className.equals("com.ibm.dtfj.image.ImageAddressSpace"))
        {
            // For the interfaces and exceptions that MAT uses, we must load
            // from ourselves so as to not get ClassCastExceptions

            if (isDebuggingClassloading)
                System.out.println(this + " loading " + className + " from parent1 " + this.getParent());

            result = this.getParent().loadClass(className);

            if (isDebuggingClassloading)
                System.out.println(this + " loaded " + className + " from parent1 " + this.getParent());

        }

        if (result == null)
        {
            if (someExplicitJARs)
            {
                try
                {
                    // Try all the URLs registered for this ClassLoader
                    if (isDebuggingClassloading)
                        System.out.println(this + " loading " + className + " from CustomClassLoader");

                    result = super.findClass(className);

                    if (isDebuggingClassloading)
                        System.out.println(this + " loaded " + className + " from CustomClassLoader");
                }
                catch (ClassNotFoundException cnfe)
                {
                    result = findFromJVM(className);
                }
            }
            else
            {
                result = findFromJVM(className);
            }
        }

        if (result == null)
        { throw new ClassNotFoundException(className); }
        return result;
    }

    private Class<?> findFromJVM(String className) throws ClassNotFoundException
    {
        Class<?> result;
        if (isDebuggingClassloading)
            System.out.println(this + " loading " + className + " from parent2 " + systemClassLoader);

        result = systemClassLoader.loadClass(className);

        if (isDebuggingClassloading)
            System.out.println(this + " loaded " + className + " from parent2 " + systemClassLoader);
        return result;
    }
}

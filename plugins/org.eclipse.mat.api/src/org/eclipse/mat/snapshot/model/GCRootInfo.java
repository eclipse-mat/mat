/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import java.io.Serializable;

import org.eclipse.mat.internal.Messages;

/**
 * Describes a garbage collection root.
 */
abstract public class GCRootInfo implements Serializable
{
    private static final long serialVersionUID = 2L;

    /**
     * Reasons why an heap object is a garbage collection root.
     * @noimplement
     */
    public interface Type
    {
        /**
         * GC root of unknown type, or a type not matching any of the other declared types
         */
        int UNKNOWN = 1;
        /**
         * Class loaded by system class loader, e.g. java.lang.String
         */
        int SYSTEM_CLASS = 2;
        /**
         * Local variable in native code
         */
        int NATIVE_LOCAL = 4;
        /**
         * Global variable in native code
         */
        int NATIVE_STATIC = 8;
        /**
         * Started but not stopped threads
         * @see #THREAD_OBJ
         */
        int THREAD_BLOCK = 16;
        /**
         * Everything you have called wait() or notify() on or you have
         * synchronized on
         */
        int BUSY_MONITOR = 32;
        /**
         * Local variable, i.e. method input parameters or locally created
         * objects of methods still on the stack of a thread
         */
        int JAVA_LOCAL = 64;
        /**
         * In or out parameters in native code; frequently seen as some methods
         * have native parts and the objects handled as method parameters become
         * GC roots, e.g. parameters used for file/network I/O methods or
         * reflection
         */
        int NATIVE_STACK = 128;
        /**
         * Running or blocked Java threads
         */
        int THREAD_OBJ = 256;
        /**
         * An object which is a queue awaiting its finalizer to be run
         * @see #THREAD_BLOCK
         */
        int FINALIZABLE = 512;
        /**
         * An object which has a finalize method, but has not been finalized and
         * is not yet on the finalizer queue
         */
        int UNFINALIZED = 1024;
        /**
         * An object which is unreachable from any other root, but has been 
         * marked as a root by MAT to retain objects which otherwise would not
         * be included in the analysis
         * @since 1.0
         */
        int UNREACHABLE = 2048;
        /**
         * A Java stack frame containing references to Java locals
         * @since 1.0
         */
        int JAVA_STACK_FRAME = 4096;
    }

    private final static String[] TYPE_STRING = new String[] { Messages.GCRootInfo_Unkown, //
                    Messages.GCRootInfo_SystemClass, //
                    Messages.GCRootInfo_JNILocal, //
                    Messages.GCRootInfo_JNIGlobal, //
                    Messages.GCRootInfo_ThreadBlock, //
                    Messages.GCRootInfo_BusyMonitor, //
                    Messages.GCRootInfo_JavaLocal, //
                    Messages.GCRootInfo_NativeStack, //
                    Messages.GCRootInfo_Thread, //
                    Messages.GCRootInfo_Finalizable, //
                    Messages.GCRootInfo_Unfinalized,
                    Messages.GCRootInfo_Unreachable,
                    Messages.GCRootInfo_JavaStackFrame};

    protected int objectId;
    private long objectAddress;
    protected int contextId;
    private long contextAddress;
    private int type;

    /**
     * Create a description of a Garbage Collection root
     * @param objectAddress the object which is retained
     * @param contextAddress the source of the retention - e.g. a thread address, or 0 for none
     * @param type the reason the object is retained {@link Type}
     */
    public GCRootInfo(long objectAddress, long contextAddress, int type)
    {
        this.objectAddress = objectAddress;
        this.contextAddress = contextAddress;
        this.type = type;
    }

    /**
     * The object id of the retained object
     * @return the target object
     */
    public int getObjectId()
    {
        return objectId;
    }

    /**
     * The object address of the retained object
     * @return the target object address
     */
    public long getObjectAddress()
    {
        return objectAddress;
    }

    /**
     * The object address of the source of the root
     * @return the source object address, or 0 if none
     */
    public long getContextAddress()
    {
        return contextAddress;
    }

    /**
     * The object id of the source of the root, if there is a source
     * @return the source object id
     */
    public int getContextId()
    {
        return contextId;
    }

    /**
     * The reason for the root
     * @return A number representing the type as {@link Type}
     */
    public int getType()
    {
        return type;
    }

    /**
     * A printable version of the type
     * @param type as {@link Type}
     * @return the printable version of the type
     * @see Type
     */
    public static String getTypeAsString(int type)
    {
        for (int i = 0; i < TYPE_STRING.length; i++)
            if (((1 << i) & type) != 0)
                return TYPE_STRING[i];

        return null;
    }

    /**
     * A combined representation of the types of several roots.
     * The types are currently separated by commas, but this could change
     * e.g. for NLS reasons?
     * @param roots an array of roots to get the combined type from
     * @return A combined type
     * @see Type
     */
    public static String getTypeSetAsString(GCRootInfo[] roots)
    {
        int typeSet = 0;
        for (GCRootInfo info : roots)
        {
            typeSet |= info.getType();
        }

        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < TYPE_STRING.length; i++)
        {
            if (((1 << i) & typeSet) != 0)
            {
                if (!first)
                {
                    buf.append(", "); //$NON-NLS-1$
                }
                else
                {
                    // Performance optimization - if there is only one bit set
                    // return the type string without building a new string.
                    if ((1 << i) == typeSet)
                    {
                        return TYPE_STRING[i];
                    }
                    first = false;
                }
                buf.append(TYPE_STRING[i]);
            }
        }
        return buf.toString();

    }

}

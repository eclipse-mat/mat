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
package org.eclipse.mat.snapshot;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.util.IProgressListener;

/**
 * {@link ISnapshot} factory
 */
public final class SnapshotFactory
{
    /**
     * Describes the snapshot factory implementation.
     * Implemented in the parser plugin.
     * @noimplement
     */
    public interface Implementation
    {
        /**
         * Opens a snapshot
         * @param file the dump file
         * @param arguments extra arguments to change the indexing of the dump
         * @param listener to show progress and errors
         * @return the snapshot
         * @throws SnapshotException
         */
        ISnapshot openSnapshot(File file, Map<String, String> arguments, IProgressListener listener)
                        throws SnapshotException;

        /**
         * Free resources when the snapshot is no longer needed.
         * @param snapshot
         */
        void dispose(ISnapshot snapshot);

        /**
         * Run an OQL query
         * @param queryString the OQL query
         * @return the result
         * @throws OQLParseException
         * @throws SnapshotException
         */
        IOQLQuery createQuery(String queryString) throws OQLParseException, SnapshotException;

        /**
         * Show which parsers the factory handles
         * @return a list of snapshot types
         */
        List<SnapshotFormat> getSupportedFormats();
    }

    private static Implementation factory;

    static
    {
        try
        {
            IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(
                            MATPlugin.PLUGIN_ID + ".factory"); //$NON-NLS-1$
            if (extensionPoint != null)
            {
                for (IExtension extension : extensionPoint.getExtensions())
                {
                    factory = (Implementation) extension.getConfigurationElements()[0]
                                    .createExecutableExtension("impl"); //$NON-NLS-1$
                    break;
                }
            }
        }
        catch (InvalidRegistryObjectException e)
        {
            Logger.getLogger(SnapshotFactory.class.getName()).log(Level.SEVERE,
                            Messages.SnapshotFactory_ErrorMsg_FactoryCreation, e);
        }
        catch (CoreException e)
        {
            Logger.getLogger(SnapshotFactory.class.getName()).log(Level.SEVERE,
                            Messages.SnapshotFactory_ErrorMsg_FactoryCreation, e);
        }
        if (factory == null)
            Logger.getLogger(SnapshotFactory.class.getName()).log(Level.SEVERE,
                        Messages.SnapshotFactory_ErrorMsg_FactoryCreation);
    }

    /**
     * Create a snapshot Object from a file representation of a snapshot.
     * 
     * @param file
     *            file from which the snapshot will be constructed (type will be
     *            derived from the file name extension)
     * @param listener
     *            progress listener informing about the current state of
     *            execution
     * @return snapshot
     * @throws SnapshotException
     */
    public static ISnapshot openSnapshot(File file, IProgressListener listener) throws SnapshotException
    {
        return openSnapshot(file, new HashMap<String, String>(0), listener);
    }

    /**
     * Create a snapshot Object from a file representation of a snapshot.
     * 
     * @param file
     *            file from which the snapshot will be constructed (type will be
     *            derived from the file name extension)
     * @param arguments
     *            parsing arguments
     * @param listener
     *            progress listener informing about the current state of
     *            execution
     * @return snapshot
     * @throws SnapshotException
     */
    public static ISnapshot openSnapshot(File file, Map<String, String> arguments, IProgressListener listener)
                    throws SnapshotException
    {
        return factory.openSnapshot(file, arguments, listener);
    }

    /**
     * Dispose the whole snapshot.
     * <p>
     * Please call this method prior to dropping the last reference to the
     * snapshot as this method ensures the proper return of all resources (e.g.
     * main memory, file and socket handles...) when the last user has disposed
     * it through the snapshot factory. After calling this method the snapshot
     * can't be used anymore.
     * 
     * @param snapshot
     *            snapshot which should be disposed
     */
    public static void dispose(ISnapshot snapshot)
    {
        factory.dispose(snapshot);
    }

    /**
     * Factory to create an OQL Query.
     * 
     * @throws OQLParseException
     *             if the OQL contains parsing errors
     */
    public static IOQLQuery createQuery(String queryString) throws OQLParseException, SnapshotException
    {
        return factory.createQuery(queryString);
    }

    /**
     * Get the types of the parsers.
     * @return list of formats that the parsers can understand
     */
    public static List<SnapshotFormat> getSupportedFormats()
    {
        return factory.getSupportedFormats();
    }

    private SnapshotFactory()
    {}
}

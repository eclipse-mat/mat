/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.index;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.mat.parser.internal.snapshot.RetainedSizeCache;
import org.eclipse.mat.util.MessageUtil;

/**
 * Handles the indexes into the snapshot.
 */
public class IndexManager
{
    /**
     * The different index types.
     */
    public enum Index
    {
        /** Inbounds: object id to N outbound object ids */
        INBOUND("inbound", IndexReader.InboundReader.class), //$NON-NLS-1$
        /** Outbounds: object id to N inbound object ids */
        OUTBOUND("outbound", IndexReader.IntIndex1NSortedReader.class), //$NON-NLS-1$
        /** Object to class: object id to 1 class id */
        O2CLASS("o2c", IndexReader.IntIndexReader.class), //$NON-NLS-1$
        /** Index to address: object id to address (as a long) */
        IDENTIFIER("idx", IndexReader.LongIndexReader.class), //$NON-NLS-1$
        /** Array to size: array (or non-default sized object) id to size (as an encoded int) */
        A2SIZE("a2s", IndexReader.SizeIndexReader.class), //$NON-NLS-1$
        /** Dominated: object id to N dominated object ids */
        DOMINATED("domOut", IndexReader.IntIndex1NReader.class), //$NON-NLS-1$
        /** Object to retained size: object in dominator tree to retained size (as a long) */
        O2RETAINED("o2ret", IndexReader.LongIndexReader.class), //$NON-NLS-1$
        /** Dominator of: object id to the id of its dominator */
        DOMINATOR("domIn", IndexReader.IntIndexReader.class), //$NON-NLS-1$
        /**
         * Retained size cache.
         * Retained size cache for a class: class+all instances.
         * Retained size cache for a class loader: loader+all classes+all instances. 
         * @since 1.2
         */
        I2RETAINED("i2sv2", RetainedSizeCache.class); //$NON-NLS-1$
        /*
         * Other indexes:
         * i2s
         *  Old version of class retained size cache
         * threads
         *  text file holding details of thread stacks and local variables
         * index - master index
         */

        /**
         * The file name for the index reader
         */
        public String filename;
        /**
         * The index reader for the index and file name
         */
        Class<? extends IIndexReader> impl;

        private Index(String filename, Class<? extends IIndexReader> impl)
        {
            this.filename = filename;
            this.impl = impl;
        }

        /**
         * Create a {@link File} descriptor based on the prefix and filename 
         * @param prefix the prefix based on the snapshot name
         * @return the file to use
         */
        public File getFile(String prefix)
        {
            return new File(new StringBuilder(prefix).append(filename).append(".index").toString());//$NON-NLS-1$
        }

    }

    /**
     * The index for objects which refer to each object
     */
    public IIndexReader.IOne2ManyObjectsIndex inbound;
    /**
     * The index for objects showing what they refer to 
     */
    public IIndexReader.IOne2ManyIndex outbound;
    /**
     * The index from object ID to the type as a class ID
     */
    public IIndexReader.IOne2OneIndex o2c;
    /**
     * The index from object ID to its address
     */
    public IIndexReader.IOne2LongIndex idx;
    /**
     * The index from object ID to its size, for arrays and variable sized objects
     */
    public IIndexReader.IOne2SizeIndex a2s;
    /**
     * The dominator tree index from an object to the objects it dominates
     */
    public IIndexReader.IOne2ManyIndex domOut;
    /**
     * Index from object ID to its retained size
     */
    public IIndexReader.IOne2LongIndex o2ret;
    /** The dominator tree index from an object to the object which dominates it.
     * 
     */
    public IIndexReader.IOne2OneIndex domIn;
    /**
     * The cache for the retained size from the object ID
     * @noreference This field is not intended to be referenced by clients.
     */
    public RetainedSizeCache i2sv2;

    /**
     * Add index reader corresponding to the index to the index manager
     * @param index the index to set
     * @param reader the source for the index
     */
    public void setReader(final Index index, final IIndexReader reader)
    {
        try
        {
            this.getClass().getField(index.filename).set(this, reader);
        }
        catch (RuntimeException e)
        {
            throw new RuntimeException(e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the reader corresponding to the index
     * @param index the index
     * @return the reader
     */
    public IIndexReader getReader(final Index index)
    {
        try
        {
            return (IIndexReader) this.getClass().getField(index.filename).get(this);
        }
        catch (RuntimeException e)
        {
            throw new RuntimeException(e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Populate all the index readers
     * @param prefix the prefix of the snapshot
     * @throws IOException if a problem occurred reading the indices
     */
    public void init(final String prefix) throws IOException
    {
        new Visitor()
        {

            @Override
            void visit(Index index, IIndexReader reader) throws IOException
            {
                if (reader != null)
                    return;

                try
                {
                    File indexFile = index.getFile(prefix);
                    if (indexFile.exists())
                    {
                        Constructor<?> constructor = index.impl.getConstructor(new Class[] { File.class });
                        reader = (IIndexReader) constructor.newInstance(new Object[] { indexFile });
                        setReader(index, reader);
                    }
                }
                catch (NoSuchMethodException e)
                {
                    throw new RuntimeException(e);
                }
                catch (InstantiationException e)
                {
                    throw new RuntimeException(e);
                }
                catch (IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }
                catch (InvocationTargetException e)
                {
                    Throwable cause = e.getCause();
                    IOException ioe = new IOException(MessageUtil.format("{0}: {1}", cause.getClass().getName(), //$NON-NLS-1$
                                    cause.getMessage()));
                    ioe.initCause(cause);
                    throw ioe;
                }
                catch (RuntimeException e)
                {
                    // re-wrap runtime exceptions caught during index processing
                    // into IOExceptions -> trigger reparsing of hprof dump
                    IOException ioe = new IOException();
                    ioe.initCause(e);
                    throw ioe;
                }
            }

        }.doIt();
    }

    /**
     * The inbounds index for each object to its inbound references.
     * @return the index reader
     */
    public IIndexReader.IOne2ManyIndex inbound()
    {
        return inbound;
    }

    /**
     * The inbounds index for each object to its outbound references.
     * @return the index reader
     */
    public IIndexReader.IOne2ManyIndex outbound()
    {
        return outbound;
    }

    /**
     * The index for each object to class.
     * @return the index reader
     */
    public IIndexReader.IOne2OneIndex o2class()
    {
        return o2c;
    }

    /**
     * The index from a class to its instances
     * @return the index reader
     */
    public IIndexReader.IOne2ManyObjectsIndex c2objects()
    {
        return inbound;
    }

    /**
     * The index from object ID to the object address
     * @return the index reader for the object address
     */
    public IIndexReader.IOne2LongIndex o2address()
    {
        return idx;
    }

    /**
     * The index from array object ID and variable sized objects to the size in bytes.
	 * @since 1.0
	 */
    public IIndexReader.IOne2SizeIndex a2size()
    {
        return a2s;
    }

    /**
     * The index reader from each object to the objects it dominates
     * @return the index reader
     */
    public IIndexReader.IOne2ManyIndex dominated()
    {
        return domOut;
    }

    /**
     * The index reader for each object to its retained size
     * @return the index reader
     */
    public IIndexReader.IOne2LongIndex o2retained()
    {
        return o2ret;
    }

    /**
     * The index reader for each object to the object which dominates it
     * @return the index reader
     */
    public IIndexReader.IOne2OneIndex dominator()
    {
        return domIn;
    }

    /**
     * Closes all the index reader files
     * @throws IOException if there is a problem closing the files
     */
    public void close() throws IOException
    {
        new Visitor()
        {

            @Override
            void visit(Index index, IIndexReader reader) throws IOException
            {
                if (reader == null)
                    return;

                reader.close();
                setReader(index, null);
            }

        }.doIt();
    }

    /**
     * Delete all the index files
     * @throws IOException if there is a problem deleting the files
     */
    public void delete() throws IOException
    {
        new Visitor()
        {

            @Override
            void visit(Index index, IIndexReader reader) throws IOException
            {
                if (reader == null)
                    return;

                reader.close();
                reader.delete();
                setReader(index, null);
            }

        }.doIt();
    }

    private abstract class Visitor
    {
        abstract void visit(Index index, IIndexReader reader) throws IOException;

        void doIt() throws IOException
        {
            try
            {
                for (Index index : Index.values())
                {
                    IIndexReader reader = getReader(index);
                    visit(index, reader);
                }
            }
            catch (IOException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}

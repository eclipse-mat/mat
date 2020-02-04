/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation (Andrew Johnson) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.zip.GZIPInputStream;

import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;

/**
 * Uses a temporary file for caching the unzipped version of the original file.
 * Faster than {@link CompressedRandomAccessFile} but uses lots of disk space.
 * Package class.
 * Do not call any methods other than
 * {@link #seek(long)}
 * {@link #getFilePointer()}
 * {@link #length()} - not necessarily accurate
 * {@link #read(byte[])}
 * {@link #read(byte[], int, int)}
 * {@link #close()}
 */
class FileCacheCompressedRandomAccessFile extends RandomAccessFile
{
    ImageInputStream iis;
    /**
     * Create an unzipped view of the gzipped file, using a file cache for the
     * uncompressed data.
     * @param file
     * @throws IOException
     */
    public FileCacheCompressedRandomAccessFile(File file) throws IOException
    {
        super(file, "r"); //$NON-NLS-1$
        iis = new FileCacheImageInputStream(new GZIPInputStream(Channels.newInputStream(getChannel())), null);
    }

    @Override
    public void seek(long pos) throws IOException
    {
        iis.seek(pos);
    }

    @Override
    public long getFilePointer() throws IOException
    {
        return iis.getStreamPosition();
    }

    /**
     * Unknown length is Long.MAX_VALUE
     */
    @Override
    public long length()
    {
        return Long.MAX_VALUE;
    }

    @Override
    public int read(byte buf[]) throws IOException
    {
        return iis.read(buf);
    }

    @Override
    public int read(byte buf[], int off, int len) throws IOException
    {
        return iis.read(buf, off, len);
    }

    public void close() throws IOException
    {
        iis.close();
        super.close();
    }

    /**
     * Rough estimate if there is enough disk space to unzip the file.
     * @param f The gzipped file.
     * @param len the unzipped length, or -1 if unknown
     * @return true if probably enough space.
     * @throws IOException
     */
    public static boolean isDiskSpace(File f, long len) throws IOException
    {
        long estlen = len >= 0 ? len : CompressedRandomAccessFile.estimatedLength(f);
        String tempDirName = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
        File tempDir = new File(tempDirName);
        return tempDir.getUsableSpace() > estlen;
    }
}

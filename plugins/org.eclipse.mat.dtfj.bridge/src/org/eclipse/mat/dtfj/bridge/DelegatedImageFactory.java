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
import java.net.URI;

import javax.imageio.stream.ImageInputStream;

import org.eclipse.core.runtime.Platform;

import com.ibm.dtfj.image.Image;
import com.ibm.dtfj.image.ImageFactory;

/**
 * Base class for the system dump and PHD factories. This just defers to the
 * CustomClassLoader.
 */
public abstract class DelegatedImageFactory implements ImageFactory
{
    private final boolean isDebuggingClassloading = "true"
                    .equals(Platform.getDebugOption("org.eclipse.mat.dtfj.bridge/classloading/debug"));
    protected ImageFactory underlyingImageFactory;

    public DelegatedImageFactory()
    {
        try
        {
            if (isDebuggingClassloading)
            {
                System.out.println(this + " creating CustomClassLoader");
            }
            CustomClassLoader ccl = new CustomClassLoader(this.getClass().getClassLoader());
            Class<?> imageFactory = ccl.loadClass(getUnderlyingImageFactoryClass());
            underlyingImageFactory = (ImageFactory) imageFactory.newInstance();
        }
        catch (Throwable t)
        {
            throw new RuntimeException(t);
        }
    }

    protected abstract String getUnderlyingImageFactoryClass();

    public int getDTFJMajorVersion()
    {
        return underlyingImageFactory.getDTFJMajorVersion();
    }

    public int getDTFJMinorVersion()
    {
        return underlyingImageFactory.getDTFJMinorVersion();
    }

    public int getDTFJModificationLevel()
    {
        return underlyingImageFactory.getDTFJModificationLevel();
    }

    public Image getImage(File arg0) throws IOException
    {
        return underlyingImageFactory.getImage(arg0);
    }

    public Image getImage(ImageInputStream arg0, URI arg1) throws IOException
    {
        return underlyingImageFactory.getImage(arg0, arg1);
    }

    public Image getImage(File arg0, File arg1) throws IOException
    {
        return underlyingImageFactory.getImage(arg0, arg1);
    }

    public Image getImage(ImageInputStream arg0, ImageInputStream arg1, URI arg2) throws IOException
    {
        return underlyingImageFactory.getImage(arg0, arg1, arg2);
    }

    public Image[] getImagesFromArchive(File arg0, boolean arg1) throws IOException
    {
        return underlyingImageFactory.getImagesFromArchive(arg0, arg1);
    }
}

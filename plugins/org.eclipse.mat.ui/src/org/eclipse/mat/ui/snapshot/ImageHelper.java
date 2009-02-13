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
package org.eclipse.mat.ui.snapshot;

import org.eclipse.jface.resource.CompositeImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;

public class ImageHelper
{
    public interface Type
    {
        int CLASS_INSTANCE = 0;
        int OBJECT_INSTANCE = 1;
        int CLASSLOADER_INSTANCE = 2;
        int ARRAY_INSTANCE = 3;

        int CLASS_INSTANCE_GC_ROOT = 4;
        int OBJECT_INSTANCE_GC_ROOT = 5;
        int CLASSLOADER_INSTANCE_GC_ROOT = 6;
        int ARRAY_INSTANCE_GC_ROOT = 7;

        int CLASS = 8;
        int PACKAGE = 9;
    }

    public interface Decorations
    {
        String GC_ROOT = PREFIX_OVERLAY + "gc_root.gif"; //$NON-NLS-1$
    }

    private static final String[] IMAGES = new String[] { "class_obj", "instance_obj", "classloader_obj", "array_obj", //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
                    "class_obj_gc_root", "instance_obj_gc_root", "classloader_obj_gc_root", "array_obj_gc_root", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    "class", "package" }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String PREFIX_OVERLAY = "icons/decorations/"; //$NON-NLS-1$

    private static final String PREFIX = "icons/heapobjects/"; //$NON-NLS-1$

    private static HashMapIntObject<ImageDescriptor> IMAGES_BY_TYPE = new HashMapIntObject<ImageDescriptor>();

    public static int getType(IObject object)
    {
        boolean isGCRoot = false;
        try
        {
            isGCRoot = object.getGCRootInfo() != null;
        }
        catch (SnapshotException ignore)
        {
            // $JL-EXC$
            // exception should not stop us from displaying the any icon
        }

        if (object == null)
        {
            // do we need an "unknown" type?
            return isGCRoot ? Type.CLASS_INSTANCE_GC_ROOT : Type.CLASS_INSTANCE;
        }
        else if (object instanceof IClass)
        {
            return isGCRoot ? Type.CLASS_INSTANCE_GC_ROOT : Type.CLASS_INSTANCE;
        }
        else if (object instanceof IClassLoader)
        {
            return isGCRoot ? Type.CLASSLOADER_INSTANCE_GC_ROOT : Type.CLASSLOADER_INSTANCE;
        }
        else if (object.getClazz().isArrayType())
        {
            return isGCRoot ? Type.ARRAY_INSTANCE_GC_ROOT : Type.ARRAY_INSTANCE;
        }
        else
        {
            return isGCRoot ? Type.OBJECT_INSTANCE_GC_ROOT : Type.OBJECT_INSTANCE;
        }
    }

    public static ImageDescriptor getImageDescriptor(int type)
    {
        ImageDescriptor id = IMAGES_BY_TYPE.get(type);
        if (id == null)
        {
            IMAGES_BY_TYPE.put(type, id = MemoryAnalyserPlugin.getImageDescriptor(PREFIX + IMAGES[type] + ".gif")); //$NON-NLS-1$
        }
        return id;
    }

    public static Image getImage(int type)
    {
        return MemoryAnalyserPlugin.getDefault().getImage(getImageDescriptor(type));
    }

    public static ImageDescriptor getInboundImageDescriptor(int type)
    {
        ImageDescriptor id = IMAGES_BY_TYPE.get(type + 10);
        if (id == null)
        {
            id = createOverlay(IMAGES[type], "in.gif"); //$NON-NLS-1$
            IMAGES_BY_TYPE.put(type + 10, id);
        }
        return id;
    }

    public static Image getInboundImage(int type)
    {
        return MemoryAnalyserPlugin.getDefault().getImage(getInboundImageDescriptor(type));
    }

    public static ImageDescriptor getOutboundImageDescriptor(int type)
    {
        ImageDescriptor id = IMAGES_BY_TYPE.get(type + 20);
        if (id == null)
        {
            id = createOverlay(IMAGES[type], "out.gif"); //$NON-NLS-1$
            IMAGES_BY_TYPE.put(type + 20, id);
        }
        return id;
    }

    public static Image getOutboundImage(int type)
    {
        return MemoryAnalyserPlugin.getDefault().getImage(getOutboundImageDescriptor(type));
    }

    public static ImageDescriptor decorate(int type, ImageDescriptor overlay)
    {
        Image base = getImage(type);
        Image over = MemoryAnalyserPlugin.getDefault().getImage(overlay);

        return new OverlayImageDescriptor(base, over);
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    private static ImageDescriptor createOverlay(String baseName, String overlayName)
    {
        Image base = MemoryAnalyserPlugin.getImage(PREFIX + baseName + ".gif"); //$NON-NLS-1$
        Image overlay = MemoryAnalyserPlugin.getImage(PREFIX_OVERLAY + overlayName);

        return new OverlayImageDescriptor(base, overlay);
    }

    static class OverlayImageDescriptor extends CompositeImageDescriptor
    {
        ImageData base;
        ImageData overlay;

        public OverlayImageDescriptor(Image base, Image overlay)
        {
            this.base = base.getImageData();
            this.overlay = overlay.getImageData();
        }

        @Override
        protected void drawCompositeImage(int width, int height)
        {
            drawImage(base, 0, 0);
            drawImage(overlay, 0, height - overlay.height);
        }

        @Override
        protected Point getSize()
        {
            return new Point(16, 16);
        }

    }

    public static class ImageImageDescriptor extends ImageDescriptor
    {

        private Image fImage;

        public ImageImageDescriptor(Image image)
        {
            fImage = image;
        }

        public ImageData getImageData()
        {
            return fImage.getImageData();
        }

        public boolean equals(Object obj)
        {
            return (obj != null) && getClass().equals(obj.getClass())
                            && fImage.equals(((ImageImageDescriptor) obj).fImage);
        }

        public int hashCode()
        {
            return fImage.hashCode();
        }

    }

}

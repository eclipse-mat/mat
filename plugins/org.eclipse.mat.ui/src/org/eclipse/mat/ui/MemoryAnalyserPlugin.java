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
package org.eclipse.mat.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.ui.internal.ErrorLogHandler;
import org.eclipse.mat.ui.snapshot.ImageHelper.ImageImageDescriptor;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class MemoryAnalyserPlugin extends AbstractUIPlugin
{
    public static final String PLUGIN_ID = "org.eclipse.mat.ui"; //$NON-NLS-1$
    public static final String EDITOR_ID = "org.eclipse.mat.ui.editors.HeapEditor"; //$NON-NLS-1$

    public interface ISharedImages
    {
        String HEAP = "icons/heapobjects/heapdump16.gif"; //$NON-NLS-1$        
        String OPEN_SNAPSHOT = "icons/open_snapshot.gif";//$NON-NLS-1$
        String CONSOLE = "icons/console.gif"; //$NON-NLS-1$
        String CONSOLE_PLUS = "icons/console_plus.gif"; //$NON-NLS-1$
        String CONSOLE_REMOVE = "icons/remove_console.gif"; //$NON-NLS-1$
        String COPY = "icons/copy.gif"; //$NON-NLS-1$
        String PLUS = "icons/plus.gif"; //$NON-NLS-1$
        String EXPERT_SYSTEM = "icons/expert.gif"; //$NON-NLS-1$

        String REFRESH = "icons/refresh.gif"; //$NON-NLS-1$       
        String THREAD = "icons/thread.gif"; //$NON-NLS-1$       

        String RETAINED_SET = "icons/retainedSet.gif"; //$NON-NLS-1$
        String PACKAGE = "icons/package.gif"; //$NON-NLS-1$

        String SYNCED = "icons/synced.gif"; //$NON-NLS-1$
        String SYNCED_DISABLED = "icons/synced_disabled.gif"; //$NON-NLS-1$

        String ID = "icons/id.gif"; //$NON-NLS-1$
        String SIZE = "icons/size.gif"; //$NON-NLS-1$

        String CLASS = "icons/heapobjects/class.gif"; //$NON-NLS-1$
        String SUPERCLASS = "icons/heapobjects/superclass.gif"; //$NON-NLS-1$
        String NOTEPAD = "icons/notepad.gif"; //$NON-NLS-1$
        String ARGUMENTS_WIZARD = "icons/fill_arguments_wiz.gif"; //$NON-NLS-1$

        String QUERY = "icons/query_browser.gif"; //$NON-NLS-1$
        String QUERY_DISABLED = "icons/query_disabled.gif"; //$NON-NLS-1$
        String MISSING_IMAGE = "icons/missing_image.gif"; //$NON-NLS-1$
        String OQL = "icons/oql.gif"; //$NON-NLS-1$

        String IMPORT_REPORT = "icons/import_report.gif"; //$NON-NLS-1$
        String EXPORT_MENU = "icons/export.gif"; //$NON-NLS-1$
        String EXPORT_HTML = "icons/export_html.gif"; //$NON-NLS-1$
        String EXPORT_CSV = "icons/export_csv.gif"; //$NON-NLS-1$
        String EXPORT_TXT = "icons/export_txt.gif"; //$NON-NLS-1$

        String REFRESHING = "icons/refreshing.gif"; //$NON-NLS-1$

        String CALCULATOR = "icons/calculator.gif";//$NON-NLS-1$

        String FILTER = "icons/filter.gif"; //$NON-NLS-1$

        String GROUPING = "icons/grouping.gif"; //$NON-NLS-1$

        String COMPARE = "icons/compare.gif"; //$NON-NLS-1$
        String PERCENTAGE = "icons/percentage.gif"; //$NON-NLS-1$

        String INFO = "icons/info.gif"; //$NON-NLS-1$

        String FIND = "icons/find.gif"; //$NON-NLS-1$  
        String EXECUTE_QUERY = "icons/execute_query.gif"; //$NON-NLS-1$
        String SHOW_AS_HISTOGRAM = "icons/as_histogram.gif"; //$NON-NLS-1$  
        String EXPLORE = "icons/explore.gif"; //$NON-NLS-1$  

        String SHOW_PANE = "icons/show_pane.gif"; //$NON-NLS-1$  
        String CLOSE_PANE = "icons/close_pane.gif"; //$NON-NLS-1$  
        String CLOSE_BRANCH = "icons/close_branch.gif"; //$NON-NLS-1$  
        
        String PINNED = "icons/pinned.gif"; //$NON-NLS-1$  
    }

    private static MemoryAnalyserPlugin plugin;

    private Map<ImageDescriptor, Image> imageCache = new HashMap<ImageDescriptor, Image>(20);
    private Map<URL, ImageDescriptor> imagePathCache = new HashMap<URL, ImageDescriptor>(20);
    private IExtensionTracker tracker;

    public MemoryAnalyserPlugin()
    {
        plugin = this;
    }

    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);

        tracker = new ExtensionTracker(Platform.getExtensionRegistry());

        // redirect logging from the analysis core into the Eclipse logging
        // facility
        Logger logger = Logger.getLogger("org.eclipse.mat");//$NON-NLS-1$
        logger.setUseParentHandlers(false);
        logger.addHandler(new ErrorLogHandler());
    }

    public void stop(BundleContext context) throws Exception
    {
        plugin = null;

        tracker.close();

        for (Image image : imageCache.values())
            image.dispose();
        imageCache.clear();

        super.stop(context);
    }

    public static MemoryAnalyserPlugin getDefault()
    {
        return plugin;
    }

    // //////////////////////////////////////////////////////////////
    // image handling
    // //////////////////////////////////////////////////////////////

    public static ImageDescriptor getImageDescriptor(String path)
    {
        return AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, path); 
    }

    public static Image getImage(String name)
    {
        return MemoryAnalyserPlugin.getDefault().getImage(getImageDescriptor(name));
    }
    
    public Image getImage(ImageDescriptor descriptor)
    {
        Image image = imageCache.get(descriptor);
        if (image == null && descriptor != null)
        {
            image = descriptor.createImage();
            imageCache.put(descriptor, image);
        }
        return image;
    }

    public ImageDescriptor getImageDescriptor(URL path)
    {
        ImageDescriptor descriptor = imagePathCache.get(path);
        if (descriptor == null)
        {
            Image image = loadImage(path);
            if (image == null)
                descriptor = getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.MISSING_IMAGE);
            else
                descriptor = new ImageImageDescriptor(image);

            imagePathCache.put(path, descriptor);
        }

        return descriptor;
    }
    
    public Image getImage(URL path)
    {
        return getImage(getImageDescriptor(path));
    }

    private Image loadImage(URL path)
    {
        InputStream stream = null;
        try
        {
            stream = path.openStream();
            return new Image(PlatformUI.getWorkbench().getDisplay(), stream);
        }
        catch (SWTException e)
        {
            MemoryAnalyserPlugin.log(e);
            return null;
        }
        catch (IOException e)
        {
            MemoryAnalyserPlugin.log(e);
            return null;
        }
        finally
        {
            try
            {
                if (stream != null)
                    stream.close();
            }
            catch (IOException ignore)
            {
                // $JL-EXC$
            }
        }
    }
    
    public ImageDescriptor getImageDescriptor(QueryDescriptor query)
    {
        URL url = query != null ? query.getIcon() : null;
        return url != null ? getImageDescriptor(url) : null;
    }

    public Image getImage(QueryDescriptor query)
    {
        ImageDescriptor imageDescriptor = getImageDescriptor(query);
        return imageDescriptor == null ? null : getImage(imageDescriptor);
    }

    public IExtensionTracker getExtensionTracker()
    {
        return tracker;
    }

    // //////////////////////////////////////////////////////////////
    // logging
    // //////////////////////////////////////////////////////////////

    public static void log(IStatus status)
    {
        getDefault().getLog().log(status);
    }

    public static void log(Throwable e)
    {
        log(e, Messages.MemoryAnalyserPlugin_InternalError);
    }

    public static void log(Throwable e, String message)
    {
        log(new Status(IStatus.ERROR, PLUGIN_ID, message, e));
    }
}

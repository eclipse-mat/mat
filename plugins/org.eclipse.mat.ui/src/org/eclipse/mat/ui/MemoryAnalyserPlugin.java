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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.ui.internal.ErrorLogHandler;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;


public class MemoryAnalyserPlugin extends AbstractUIPlugin
{
    public static final String PLUGIN_ID = "org.eclipse.mat.ui"; //$NON-NLS-1$

    public interface ISharedImages
    {
        String HEAP = "icons/heapobjects/heapdump16.gif"; //$NON-NLS-1$        
        String OPEN_SNAPSHOT = "icons/open_snapshot.gif";
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
        String SHOW_ADDONS = "icons/heapdump_addons.gif"; //$NON-NLS-1$  
        String FATAL_ERROR = "icons/fatalerror.gif"; //$NON-NLS-1$  
        String EXPLORE = "icons/explore.gif"; //$NON-NLS-1$  
    }

    public interface FileExtensions
    {
        final static String HEAPDUMP_EXTENSION = ".hprof"; //$NON-NLS-1$
    }

    private static MemoryAnalyserPlugin plugin;

    private Map<ImageDescriptor, Image> imageCache = new HashMap<ImageDescriptor, Image>(20);

    public MemoryAnalyserPlugin()
    {
        plugin = this;
    }

    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);

        // redirect logging from the analysis core into the Eclipse logging
        // facility
        Logger logger = Logger.getLogger("org.eclipse.mat");
        logger.setUseParentHandlers(false);
        logger.addHandler(new ErrorLogHandler());
    }

    public void stop(BundleContext context) throws Exception
    {
        plugin = null;

        for (Image image : imageCache.values())
            image.dispose();
        imageCache.clear();

        super.stop(context);
    }

    public static MemoryAnalyserPlugin getDefault()
    {
        return plugin;
    }

    public static ImageDescriptor getImageDescriptor(String path)
    {
        return AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, path); //$NON-NLS-1$
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

    public static Image getImage(String name)
    {
        return MemoryAnalyserPlugin.getDefault().getImage(MemoryAnalyserPlugin.getImageDescriptor(name));
    }

    public static void log(IStatus status)
    {
        getDefault().getLog().log(status);
    }

    public static void log(Throwable e)
    {
        log(e, "Internal Error");
    }

    public static void log(Throwable e, String message)
    {
        log(new Status(IStatus.ERROR, PLUGIN_ID, message, e));
    }

    public static boolean isRunningAsRCP()
    {
        return "org.eclipse.mat.ui.rcp.application".equals(Platform.getProduct().getApplication());
    }
}

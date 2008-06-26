package org.eclipse.mat.report.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.osgi.framework.BundleContext;

public class ReportPlugin extends Plugin
{
    public static final String PLUGIN_ID = "org.eclipse.mat.report";

    private static ReportPlugin plugin;

    private IExtensionTracker tracker;

    public ReportPlugin()
    {}

    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        tracker = new ExtensionTracker(Platform.getExtensionRegistry());
        plugin = this;
    }

    public void stop(BundleContext context) throws Exception
    {
        plugin = null;
        tracker.close();
        super.stop(context);
    }

    public static ReportPlugin getDefault()
    {
        return plugin;
    }

    public IExtensionTracker getExtensionTracker()
    {
        return tracker;
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

    public static void log(int status, String message)
    {
        log(new Status(status, PLUGIN_ID, message));
    }

}

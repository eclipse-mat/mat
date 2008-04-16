package org.eclipse.mat.ui.rcp;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class RCPPlugin extends AbstractUIPlugin
{

    public static final String PLUGIN_ID = "org.eclipse.mat.ui.rcp";

    private static RCPPlugin plugin;

    private IExtensionTracker tracker;

    public RCPPlugin()
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

    public static RCPPlugin getDefault()
    {
        return plugin;
    }

    public IExtensionTracker getExtensionTracker()
    {
        return tracker;
    }
}

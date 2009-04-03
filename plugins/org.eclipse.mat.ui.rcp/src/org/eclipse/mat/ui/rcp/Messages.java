package org.eclipse.mat.ui.rcp;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.ui.rcp.messages"; //$NON-NLS-1$
    public static String ApplicationActionBarAdvisor_Edit;
    public static String ApplicationActionBarAdvisor_File;
    public static String ApplicationActionBarAdvisor_Help;
    public static String ApplicationActionBarAdvisor_NotRunningAsAProduct;
    public static String ApplicationActionBarAdvisor_Window;
    public static String OpenHelp_HelpContents;
    public static String OpenPreferenceAction_Preferences;
    public static String ShowViewMenu_NoApplicableView;
    public static String SnapshotHistoryIntroContentProvider_ErrorCreatingImage;
    public static String SnapshotHistoryIntroContentProvider_HistoryIsEmpty;
    public static String SnapshotHistoryIntroContentProvider_PleaseWait;
	public static String ApplicationWorkbenchWindowAdvisor_Eclipse_Memory_Analyzer;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

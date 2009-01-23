package org.eclipse.mat.jdt;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.jdt.messages"; //$NON-NLS-1$
    public static String OpenSourceFileJob_LookingFor;
    public static String OpenSourceFileJob_NotFound;
    public static String OpenSourceFileJob_SelectFile;
    public static String OpenSourceFileJob_SelectFileToOpen;
    public static String OpenSourceFileJob_TypeNotFound;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}

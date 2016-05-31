/*******************************************************************************
 * Copyright (c) 2008, 2015 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Benjamin Maskalla - patch for 318618, use createFromURL
 *    IBM Corporation - icon labels
 *******************************************************************************/
package org.eclipse.mat.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.ui.internal.ErrorLogHandler;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class MemoryAnalyserPlugin extends AbstractUIPlugin
{
    public static final String PLUGIN_ID = "org.eclipse.mat.ui"; //$NON-NLS-1$
    public static final String EDITOR_ID = "org.eclipse.mat.ui.editors.HeapEditor"; //$NON-NLS-1$

    private static final String PREFIX = "$nl$/icons/"; //$NON-NLS-1$
    private static final String HEAPPREFIX = PREFIX + "heapobjects/"; //$NON-NLS-1$

    public interface ISharedImages
    {
        String HEAP = HEAPPREFIX + "heapdump16.gif"; //$NON-NLS-1$
        String HEAP_INFO = PREFIX + "heapdump_details.gif"; //$NON-NLS-1$
        String OPEN_SNAPSHOT = PREFIX + "open_snapshot.gif";//$NON-NLS-1$
        String CONSOLE = PREFIX + "console.gif"; //$NON-NLS-1$
        String CONSOLE_PLUS = PREFIX + "console_plus.gif"; //$NON-NLS-1$
        String CONSOLE_REMOVE = PREFIX + "remove_console.gif"; //$NON-NLS-1$
        String COPY = PREFIX + "copy.gif"; //$NON-NLS-1$
        String PLUS = PREFIX + "plus.gif"; //$NON-NLS-1$
        String EXPERT_SYSTEM = PREFIX + "expert.gif"; //$NON-NLS-1$

        String REFRESH = PREFIX + "refresh.gif"; //$NON-NLS-1$       
        String THREAD = PREFIX + "thread.gif"; //$NON-NLS-1$       

        String RETAINED_SET = PREFIX + "retainedSet.gif"; //$NON-NLS-1$
        String PACKAGE = PREFIX + "package.gif"; //$NON-NLS-1$

        String SYNCED = PREFIX + "synced.gif"; //$NON-NLS-1$
        String SYNCED_DISABLED = PREFIX + "synced_disabled.gif"; //$NON-NLS-1$

        String ID = PREFIX + "id.gif"; //$NON-NLS-1$
        String SIZE = PREFIX + "size.gif"; //$NON-NLS-1$

        String CLASS = HEAPPREFIX + "class.gif"; //$NON-NLS-1$
        String CLASS_MIXED = HEAPPREFIX + "class_mixed.gif"; //$NON-NLS-1$
        String CLASS_OLD = HEAPPREFIX + "class_old.gif"; //$NON-NLS-1$
        String SUPERCLASS = HEAPPREFIX + "superclass.gif"; //$NON-NLS-1$
        String NOTEPAD = PREFIX + "notepad.gif"; //$NON-NLS-1$
        String ARGUMENTS_WIZARD = PREFIX + "fill_arguments_wiz.gif"; //$NON-NLS-1$

        String QUERY = PREFIX + "query_browser.gif"; //$NON-NLS-1$
        String QUERY_DISABLED = PREFIX + "query_disabled.gif"; //$NON-NLS-1$
        String OQL = PREFIX + "oql.gif"; //$NON-NLS-1$

        String IMPORT_REPORT = PREFIX + "import_report.gif"; //$NON-NLS-1$
        String EXPORT_MENU = PREFIX + "export.gif"; //$NON-NLS-1$
        String EXPORT_HTML = PREFIX + "export_html.gif"; //$NON-NLS-1$
        String EXPORT_CSV = PREFIX + "export_csv.gif"; //$NON-NLS-1$
        String EXPORT_TXT = PREFIX + "export_txt.gif"; //$NON-NLS-1$

        String REFRESHING = PREFIX + "refreshing.gif"; //$NON-NLS-1$

        String CALCULATOR = PREFIX + "calculator.gif";//$NON-NLS-1$

        String FILTER = PREFIX + "filter.gif"; //$NON-NLS-1$

        String GROUPING = PREFIX + "grouping.gif"; //$NON-NLS-1$

        String COMPARE = PREFIX + "compare.gif"; //$NON-NLS-1$
        String PERCENTAGE = PREFIX + "percentage.gif"; //$NON-NLS-1$

        String INFO = PREFIX + "info.gif"; //$NON-NLS-1$
        String HELP = PREFIX + "help.png"; //$NON-NLS-1$

        String FIND = PREFIX + "find.gif"; //$NON-NLS-1$  
        String EXECUTE_QUERY = PREFIX + "execute_query.gif"; //$NON-NLS-1$
        String SHOW_AS_HISTOGRAM = PREFIX + "as_histogram.gif"; //$NON-NLS-1$  
        String EXPLORE = PREFIX + "explore.gif"; //$NON-NLS-1$  

        String SHOW_PANE = PREFIX + "show_pane.gif"; //$NON-NLS-1$  
        String CLOSE_PANE = PREFIX + "close_pane.gif"; //$NON-NLS-1$  
        String CLOSE_BRANCH = PREFIX + "close_branch.gif"; //$NON-NLS-1$  

        String PINNED = PREFIX + "pinned.gif"; //$NON-NLS-1$

        String MOVE_UP = PREFIX + "move_up.gif"; //$NON-NLS-1$
        String MOVE_DOWN = PREFIX + "move_down.gif"; //$NON-NLS-1$
        String REMOVE = PREFIX + "remove.gif"; //$NON-NLS-1$
        String REMOVE_ALL = PREFIX + "removeall.gif"; //$NON-NLS-1$
        String SELECT_COLUMN = PREFIX + "select_table.gif"; //$NON-NLS-1$
    }

    private static MemoryAnalyserPlugin plugin;

    private Map<ImageDescriptor, Image> imageCache = new HashMap<ImageDescriptor, Image>(20);
    private Map<URI, ImageDescriptor> imagePathCache = new HashMap<URI, ImageDescriptor>(20);
    private IExtensionTracker tracker;
    private Logger logger;
    private ErrorLogHandler errorLogHandler;
    private boolean useParentHandlers;

    // Mappings to permit textual descriptions of Images to be recovered from
    // Images.
    private Map<Image, String> imageTextMap = new HashMap<Image, String>(20);
    private Map<ImageDescriptor, String> descriptorTextMap = new HashMap<ImageDescriptor, String>(20);

    public MemoryAnalyserPlugin()
    {}

    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;

        tracker = new ExtensionTracker(Platform.getExtensionRegistry());

        // redirect logging from the analysis core into the Eclipse logging
        // facility
        logger = Logger.getLogger("org.eclipse.mat");//$NON-NLS-1$
        useParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        errorLogHandler = new ErrorLogHandler();
        logger.addHandler(errorLogHandler);
    }

    public void stop(BundleContext context) throws Exception
    {
        plugin = null;

        tracker.close();

        for (Image image : imageCache.values())
            image.dispose();
        imageCache.clear();
        // Clear mappings from Image/Descriptor to descriptive text.
        imageTextMap.clear();
        descriptorTextMap.clear();

        logger.removeHandler(errorLogHandler);
        logger.setUseParentHandlers(useParentHandlers);
        logger = null;
        errorLogHandler = null;

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
        // Use singleton instance so that ImageDescriptor can be mapped to text.
        return MemoryAnalyserPlugin.getDefault().getPluginImageDescriptor(path);
    }

    public static Image getImage(String name)
    {
        return MemoryAnalyserPlugin.getDefault().getImage(getImageDescriptor(name));
    }

    private ImageDescriptor getPluginImageDescriptor(String path)
    {
        ImageDescriptor descriptor = AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, path);
        if (descriptor != null)
        { // Add map entry for new descriptor to appropriate text.
          // This should not result in a memory leak, assuming that two
          // equivalent ImageDescriptors match under equals().
          // This is already assumed in the usage of imageCache.
            descriptorTextMap.put(descriptor, getIconString(path));
        }
        return descriptor;
    }

    public Image getImage(ImageDescriptor descriptor)
    {
        Image image = imageCache.get(descriptor);
        if (image == null && descriptor != null)
        {
            image = descriptor.createImage();
            imageCache.put(descriptor, image);
            // Map new Image to descriptive text.
            // Should not cause memory leak as this must be a new descriptor.
            imageTextMap.put(image, descriptorTextMap.get(descriptor));
        }
        return image;
    }

    public ImageDescriptor getImageDescriptor(URL path)
    {
        // Use URI for maps to avoid blocking equals operation
        URI pathKey;
        try
        {
            pathKey = path.toURI();
        }
        catch (URISyntaxException e)
        {
            // Will cause a missing image to be used instead
            pathKey = null;
        }
        ImageDescriptor descriptor = imagePathCache.get(pathKey);
        if (descriptor == null)
        {
            descriptor = ImageDescriptor.createFromURL(path);
            imagePathCache.put(pathKey, descriptor);
            // Map new descriptor to descriptive text for the Image.
            // Should not cause a memory leak as this is a new descriptor,
            // and equivalent descriptors should overwrite existing entries.
            descriptorTextMap.put(descriptor, getIconString(path));
        }

        return descriptor;
    }

    public Image getImage(URL path)
    {
        return getImage(getImageDescriptor(path));
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

    /**
     * @param url
     *            URL of image file for which a description is required.
     * @return String with meaningful description of image given by input url.
     */
    private String getIconString(URL url)
    {
        // Delegate lookup based on path element of URL.
        return getIconString(url.getPath());
    }

    /**
     * @param path
     *            String representing the path to an image file for which a
     *            description is needed.
     * @return String with meaningful description of image located at input
     *         path. NLS enabled as the string is obtained from a properties
     *         file.
     */
    private String getIconString(String path)
    {
        // Construct system independent string representing path below "icons"
        // This is then used to map to a NLS enabled textual description of the
        // image.
        File imageFile = new File(path); // Full path
        String[] iconPath = parseIconPath(imageFile); // Split into elements
        String iconKey = buildIconKey(iconPath); // Construct key for property
        String iconLabel = IconLabels.getString(iconKey); // Obtain NLS value
        return iconLabel;
    }

    /**
     * @param imageFile
     *            File representing the path to the image. This is converted
     *            into a String[] by splitting the path into elements below the
     *            /icons directory and stripping off the suffix. Returns null if
     *            the file is not below an /icons directory.
     * @return String[] representing the path split into elements as above.
     */
    private static String[] parseIconPath(File imageFile)
    {
        String[] iconPath = null; // Initial and default value to return.
        ArrayList<String> pathList = new ArrayList<String>(); // Accumulator
        // Strip off file suffix.
        pathList.add(imageFile.getName().split("\\.")[0]); //$NON-NLS-1$

        // Iterate backwards up the path, inserting the directory names at the
        // front of the ArrayList.
        // This results in a sequence matching the original order of the path.
        // Do not include the common parent directory "/icons" or ancestors.
        while (imageFile != null)
        { // iterate up the path
            imageFile = imageFile.getParentFile();
            if (imageFile != null) // There was a parent to include.
            {
                String fileName = imageFile.getName();
                if (fileName.equals("icons")) // Iteration complete. //$NON-NLS-1$
                { // Convert ArrayList to array for return.
                    iconPath = pathList.toArray(new String[0]);
                    imageFile = null; // terminate loop
                }
                else
                { // More to do - prepend the name of parent to sequence.
                    pathList.add(0, fileName); // add parent to front of list
                }
            }
        }
        return iconPath; // Return parsed path, or null if unexpected error.
    }

    /**
     * @param iconPath
     *            String[] representing path to icon file below /icons
     * @return String A mangled version of the path with path separators
     *         replaced with '-' to use as a key into the properties file
     *         containing the textual descriptions of the icons. This utility is
     *         used offline to build the properties file, and at runtime to look
     *         up the icon labels from the NLS properties file(s).
     */
    private static String buildIconKey(String[] iconPath)
    {
        if (iconPath == null)
            return IconLabels.UNKNOWN_ICON_KEY;
        // Initialize key with common prefix from IconLabels class.
        StringBuffer propertyBuf = new StringBuffer(IconLabels.ICON_KEY_PREFIX);
        // Iterate through iconPath appending each element after '-'
        for (String pathStr : iconPath)
        {
            propertyBuf.append('-');
            propertyBuf.append(pathStr);
        }
        return propertyBuf.toString(); // Return constructed key.
    }

    /**
     * @param image
     *            The Image for which descriptive text is to be retrieved.
     * @return Descriptive text for the Image object, retrieved from
     *         imageTextMap, or text indicating "unknown image" if not found.
     */
    public String getImageText(Image image)
    {
        String text = imageTextMap.get(image); // May be null
        // Return default string if image not in map.
        return (text == null) ? IconLabels.getString(IconLabels.UNKNOWN_ICON_KEY) : text;
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

    // ///////////////////////////////////////////////////////////////////
    // Main program and associated methods (all offline code)
    // to generate icon label properties from icon file names.
    // Not used in MAT runtime environment.
    // Offline code to generate English labels for icons.
    // The standard required for error handling robustness in this code is
    // lower than would be expected for MAT runtime code, as it is run only
    // as an offline generation utility (by MAT developers only, not by users).
    // ///////////////////////////////////////////////////////////////////

    /**
     * @param args
     *            Input arguments are ignored. This Java program generates a
     *            properties file "iconlabels.properties" automatically, based
     *            on the content of the MAT icons directories. The locations of
     *            the /icons directories are hardcoded to be those under
     *            org.eclipse.mat.api and org.eclipse.mat.ui, relative to the
     *            current working directory which is assumed to be a project in
     *            the workspace. This is the case if this program is
     *            "Run as Java Application" within Eclipse, using the default
     *            working directory org.eclipse.mat.ui. The output is written to
     *            "iconlabels.properties" in the current working directory,
     *            which can then be copied to the required location for the
     *            properties file, org.eclipse.mat.ui/src/org/eclipse/mat/ui/.
     *            Error handling is coarse-grained: any Exception is caught and
     *            details are printed to System.out. Some other diagnostics are
     *            written to System.out if errors occur.
     */
    @SuppressWarnings("nls")
    public static void main(String[] args)
    {
        /*
         * Note that the file output uses \r\n as the line separator, as this is
         * required by the IBM NLS translation tools. This corresponds to 0x0D0A
         * in ASCII/UTF-8 encoding. Hence explicit line separators of \r\n are
         * used throughout the generator code.
         */
        // File for output properties:
        final String propsFilename = "icon_labels.properties";
        // Header string required for translation tooling.
        final String nlsHeaders = "# NLS_MESSAGEFORMAT_NONE\r\n" + "# NLS_ENCODING=UNICODE\r\n" + "# \r\n";
        // Header comment for generated properties file, referring back to this
        // program.
        final String autoComment = "# This file is automatically generated by org.eclipse.mat.ui.MemoryAnalyserPlugin.main().\r\n"
                        + "# Refer to the documentation/comments for this method for usage instructions.\r\n"
                        + "# \r\n"
                        + "# Any manual modifications to this file will need to be reapplied if the file is regenerated.\r\n"
                        + "# Therefore it is preferable if such modifications are kept to a minimum, or preferably\r\n"
                        + "# achieved by amending the label generation code in MemoryAnalyserPlugin.buildIconLabel().\r\n"
                        + "# \r\n";
        // String to include special property to denote the "unknown icon"
        // value.
        final String unknownIconProperty = "# Icon label property to be used for an unknown icon:\r\n"
                        + IconLabels.UNKNOWN_ICON_KEY + "=" + buildIconLabel(null) + "\r\n";
        // String to indicate start of auto-generated label properties.
        final String autoIconsComment = "# Automatically generated icon label properties:";
        final String[] iconDirs = { // UI draws icons from several locations
        // Assume the following two top-level icons directories relative to
        // current directory.
                        "../org.eclipse.mat.api/META-INF/icons", "../org.eclipse.mat.ui/icons", "../org.eclipse.mat.jdt/icons" };
        try
        // Trap any Exceptions at the outermost level.
        {
            // Use a sorted map for the properties so that the ordering is
            // reproducible.
            Map<String, String> iconMap = new TreeMap<String, String>();
            for (String iconDir : iconDirs) // For each /icons directory
                                            // (currently 3).
            {
                File iconDirFile = new File(iconDir);
                if (iconDirFile.isDirectory()) // Check input is valid directory
                { // Generate properties for the directory and add to map.
                    generateIconProps(iconDirFile, iconMap);
                }
                else
                { // Error case - report to user.
                    System.out.println("Input is not a directory: " + iconDir);
                }

            }
            // Now write out iconlabels.properties
            File iconLabelsFile = new File(propsFilename);
            PrintStream iconLabelsStream = null;
            // Properties files are always encoded in ISO-8859-1
            iconLabelsStream = new PrintStream(new FileOutputStream(iconLabelsFile), false, "ISO-8859-1");
            // Print NLS headers required for translation
            // Use printPropertyLine() to insert \r\n separator.
            printPropertyLine(iconLabelsStream, nlsHeaders);
            // Print header comment referring to this generator code.
            printPropertyLine(iconLabelsStream, autoComment);
            // Print special label to be used for unknown icons.
            printPropertyLine(iconLabelsStream, unknownIconProperty);
            // Print special label to be used for unknown icons.
            printPropertyLine(iconLabelsStream, autoIconsComment);
            // Print out iconMap entries, in collation sequence for
            // reproducibility.
            for (Entry<String, String> mapEntry : iconMap.entrySet())
            {
                iconLabelsStream.print(mapEntry.getKey());
                iconLabelsStream.print('=');
                printPropertyLine(iconLabelsStream, mapEntry.getValue());
            }
            iconLabelsStream.close();
            // Print completion message to console
            System.out.println("Icon label properties written to file: " + iconLabelsFile.getAbsolutePath());
        }
        catch (Exception e) // Catch all exceptions and print details to
                            // System.out
        {
            System.out.println(e.toString());
            e.printStackTrace(System.out);
        }
    }

    /**
     * @param stream
     *            PrintStream to print line to.
     * @param line
     *            String to append to PrintStream. Writes line to stream (just
     *            like println()) but using "\r\n" as line terminator to satisfy
     *            requirements of IBM translation tools.
     */
    private static void printPropertyLine(PrintStream stream, String line)
    {
        stream.print(line);
        stream.print("\r\n"); // Use MS-DOS line termination (0D0A) for
    }

    /**
     * @param iconDir
     *            File representing a directory to search for icon files and to
     *            add the generated key/value pairs to iconMap.
     * @param iconMap
     *            Map to add the generated key/value pairs for the icons. This
     *            should be a sorted Map to ensure reproducible ordering of
     *            output. Recursively invoked method to write out generated
     *            labels for icons in the form of a properties file. The
     *            recursion excludes hidden files & directories
     */
    private static void generateIconProps(File iconDir, Map<String, String> iconMap)
    { // precondition: iconDir is a directory.
        File[] fileList = iconDir.listFiles(); // Should not be null, may be
                                               // empty.
        for (File file : fileList) // Iterate over directory contents.
        {
            if (!file.isHidden())
            { // Exclude hidden files eg .svn directories
                if (file.isDirectory())
                { // Directory, so recurse into child directory.
                    generateIconProps(file, iconMap);
                }
                else
                { // File, so add key/value pair to Map.
                    String[] iconPath = parseIconPath(file);
                    String key = buildIconKey(iconPath);
                    String label = buildIconLabel(iconPath);
                    iconMap.put(key, label); // Duplicate key will overwrite
                }
            } // if()
        }
    }

    /**
     * @param iconPath
     *            path of icon below "/icons", without file qualifier(s), parsed
     *            into String[] by
     * @return We simply return a line representing a textual description for
     *         the icon, based on it's file location & name. This automated
     *         process can be adjusted to produce any desired result, provided
     *         the resulting line is a valid Java property value. Performance is
     *         not important as this is non-runtime code. It's more important
     *         that the results should be readily tailorable.
     */
    @SuppressWarnings("nls")
    private static String buildIconLabel(String[] iconPath)
    {
        // Input is path of icon below "/icons", without file qualifier(s).
        if (iconPath == null) // Invalid path, icon may not exist.
            return "unknown icon"; // Special case label.
        StringBuffer labelBuf = new StringBuffer(); // Initially empty
        // Split base file name of icon into tokens delimited by '_'
        String[] iconName = iconPath[iconPath.length - 1].split("_");
        for (String nameElem : iconName)
        { // For each component token, perform required tailoring.
          // Expand common abbreviations
            if (nameElem.equals("obj"))
                nameElem = "object";
            if (nameElem.equals("frgmt"))
                nameElem = "fragment";
            if (nameElem.equals("frgmts"))
                nameElem = "fragments";
            if (nameElem.equals("attr"))
                nameElem = "attribute";
            if (nameElem.equals("ext"))
                nameElem = "extension";
            if (nameElem.equals("mpaths"))
                nameElem = "merge paths";
            // Add to buffer
            labelBuf.append(nameElem);
            labelBuf.append(' '); // Add space between elements
        }
        // Iterate backwards up path, adding parent directory names to text.
        for (int ipath = iconPath.length - 2; ipath >= 1; ipath--)
        { // omit top-level directory if there is one (index 0 not included).
            labelBuf.append(iconPath[ipath]);
            labelBuf.append(' '); // Appends trailing blank which is OK for
                                  // properties
        }
        return labelBuf.toString(); // Convert to String and return.
    }

}

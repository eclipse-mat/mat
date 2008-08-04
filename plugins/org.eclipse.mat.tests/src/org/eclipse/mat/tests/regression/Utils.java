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
package org.eclipse.mat.tests.regression;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Utils
{
    static final String BASELINE_EXTENSION = "_baseline";
    static final String TEST_EXTENSION = "_test";
    static final String RESULT_FILENAME = "result.xml";
    
    private static FileFilter filter = new FileFilter()
    {
        public boolean accept(File file)
        {
            return (file.getName().endsWith(".hprof") || file.getName().endsWith(".dtfj"));
        }
    };
    
    public static FilenameFilter cleanupFilter = new FilenameFilter()
    {
        public boolean accept(File dir, String name)
        {
            if (new File(dir, name).isDirectory())
                return false;
            Pattern hprofPattern = Pattern.compile(name.substring(0, name.lastIndexOf('.')) + "\\.hprof");           
            return !hprofPattern.matcher(name).matches() && !name.endsWith(BASELINE_EXTENSION);
        }
    };

    protected static void removeFile(File file)
    {
        // delete old index and report files, throw exception if fails
        if (!file.delete())
        {

            System.err.println("ERROR> Failed to remove file " + file.getName() + " from the directory "
                            + file.getParent());
        }
        else
        {
            System.out.println("OUTPUT> File " + file.getName() + " deleted.");
        }
    }

    protected static List<File> collectDumps(File dumpsFolder)
    {
        List<File> dumpList = new ArrayList<File>();
        // check whether sub-folders contain heap dumps
        File[] directories = dumpsFolder.listFiles(new FileFilter()
        {

            public boolean accept(File file)
            {
                return file.isDirectory();
            }
        });

        for (File dir : directories)
        {
            File[] dumps = dir.listFiles(filter);
            if (dumps.length > 0)
            {
                for (File dumpFile : dumps)
                {
                    dumpList.add(dumpFile);
                }
            }
        }
        File[] dumps = dumpsFolder.listFiles(filter);
        for (File file : dumps)
        {
            dumpList.add(file);
        }
        return dumpList;
    }   
   
}

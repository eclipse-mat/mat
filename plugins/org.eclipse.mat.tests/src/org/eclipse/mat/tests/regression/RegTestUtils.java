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
import java.util.List;
import java.util.regex.Pattern;

public class RegTestUtils
{
    static final String BASELINE_EXTENSION = "_baseline";
    static final String TEST_EXTENSION = "_test";
    static final String RESULT_FILENAME = "result.xml";
    public static final String SEPARATOR = ";";

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
            Pattern hprofPattern = Pattern.compile(".*\\.hprof");
            Pattern dtfjPattern = Pattern.compile(".*\\.dtfj");
            Pattern resultFilePattern = Pattern.compile("performanceResults.*\\.csv");
            return !hprofPattern.matcher(name).matches() && !name.endsWith(BASELINE_EXTENSION)
                            && !dtfjPattern.matcher(name).matches() && !resultFilePattern.matcher(name).matches();
        }
    };

    protected static void removeFile(File file)
    {
        // delete old index and report files, throw exception if fails
        if (!file.delete())
        {

            System.err.println("ERROR: Failed to remove file " + file.getName() + " from the directory "
                            + file.getParent());
        }
    }

    protected static List<File> collectDumps(File dumpsFolder, List<File> dumpList)
    {        
        File[] dumps = dumpsFolder.listFiles(filter);
        for (File file : dumps)
        {
            dumpList.add(file);
        }

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
            collectDumps(dir, dumpList);
        }

        return dumpList;
    }
   
}

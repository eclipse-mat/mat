/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - CSV changes
 *******************************************************************************/
package org.eclipse.mat.tests.regression;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.List;
import java.util.regex.Pattern;

import com.ibm.icu.text.DecimalFormatSymbols;

public class RegTestUtils
{
    static final String BASELINE_EXTENSION = "_baseline";
    static final String TEST_EXTENSION = "_test";
    static final String RESULT_FILENAME = "result.xml";
    public static final String SEPARATOR = new DecimalFormatSymbols().getDecimalSeparator() == ',' ? ";" : ",";

    private static FileFilter filter = new FileFilter()
    {
        public boolean accept(File file)
        {
            return (file.getName().endsWith(".hprof") || file.getName().endsWith(".dtfj") || file.getName().endsWith(".dmp.zip"));
        }
    };

    public static final FilenameFilter cleanupFilter = new FilenameFilter()
    {
        public boolean accept(File dir, String name)
        {
            Pattern hprofPattern = Pattern.compile(".*\\.hprof");
            Pattern dtfjPattern = Pattern.compile(".*\\.dtfj|.*\\.dmp.zip|.*\\.phd|javacore.*\\.txt");
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

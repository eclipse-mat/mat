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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.SpecFactory;

public class Application implements IApplication
{
    public Object start(IApplicationContext context) throws Exception
    {
        String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);

        if (args == null || args.length < 2)
        {
            printUsage("Missing arguments.");
            return -1;
        }

        String application = args[0];
        if ("-parse".equals(application))
            return launchParsing(args);

        File dumpDir = new File(args[1]);
        if (!dumpDir.exists() || !dumpDir.isDirectory())
        {
            printUsage(MessageFormat.format("{0} is not a directory", dumpDir.getAbsolutePath()));
            return -1;
        }

        if ("-cleanAll".equals(application))
        {
            new CleanAllApplication(dumpDir).run();
            return IApplication.EXIT_OK;
        }
        else if ("-newBaseline".equals(application))
        {
            new ForceNewBaselineApplication(dumpDir).run();
            return IApplication.EXIT_OK;
        }
        else if ("-test".equals(application))
        {
            return launchTestApp(dumpDir, args, "regression", true);
        }
        else if ("-performance".equals(application))
        {
            return launchTestApp(dumpDir, args, "performance", false);
        }
        else
        {
            printUsage(MessageFormat.format("Unknown application: {0}", application));
            return IApplication.EXIT_RELAUNCH;
        }
    }

    private int launchTestApp(File dumpDir, String[] args, String report, boolean isRegressionTest) throws Exception
    {
        if (args.length != 3)
        {
            printUsage("Missing <jvmArgs> parameter.");
            return -1;
        }

        new TestApplication(dumpDir, args[2], report, isRegressionTest).run();

        return IApplication.EXIT_OK;
    }

    private int launchParsing(String[] args) throws FileNotFoundException, IOException, SnapshotException
    {
        if (args.length != 3)
        {
            printUsage("Missing arguments.");
            return -1;
        }

        File snapshotFile = new File(args[1]);
        if (!snapshotFile.exists())
            throw new FileNotFoundException(snapshotFile.getAbsolutePath());

        Spec report = SpecFactory.instance().create(args[2]);
        if (report == null)
            throw new SnapshotException(MessageFormat.format("Report not found: {0}", args[2]));

        new TestParseApp(snapshotFile, report).run();

        return IApplication.EXIT_OK;
    }

    private void printUsage(String errorMessage)
    {
        System.err.println(errorMessage);
        System.err.println("Usage: <application> <arguments>\n\n" + "where:\n" //
                        + "  <application> one of\n" //
                        + "  -test <folder> <jvmargs> : run regression tests on snapshots in given folder\n" //
                        + "  -performance <folder> <jvmargs> : run performance tests on snapshots in given folder\n" //
                        + "  -cleanAll <folder> : clean index files and test results\n" //
                        + "  -newBaseline <folder> : overwrite existing base line with the last test results\n" //
                        + "  -parse <snaphost> <report> : parse heap dump and print times\n");
    }

    public void stop()
    {}
}

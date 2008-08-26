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
import java.text.MessageFormat;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class Application implements IApplication
{
    public Object start(IApplicationContext context) throws Exception
    {
        String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);

        if (args.length != 3)
        {
            printUsage("Missing parameters.");
            return IApplication.EXIT_RELAUNCH;
        }

        String application = args[0];

        File dumpDir = new File(args[1]);
        if (!dumpDir.isDirectory())
        {
            printUsage(MessageFormat.format("{0} is not a directory", dumpDir.getAbsolutePath()));
            return IApplication.EXIT_RELAUNCH;
        }
        String jvmFlags = args[2];

        if ("-cleanAll".equals(application))
        { // removes index files and last test results
            new CleanAllApplication(dumpDir).run();
        }
        else if ("-newBaseline".equals(application))
        {
            new ForceNewBaselineApplication(dumpDir).run();
        }
        else if ("-test".equals(application))
        {
            new TestApplication(dumpDir, jvmFlags, "regression", true).run();
        }
        else if ("-performance".equals(application))
        {
            new TestApplication(dumpDir, jvmFlags, "performance", false).run();
        }
        else
        {
            printUsage(MessageFormat.format("Unknown application: {0}", application));
            return IApplication.EXIT_RELAUNCH;
        }

        return IApplication.EXIT_OK;
    }

    private void printUsage(String errorMessage)
    {
        System.err.println(errorMessage);
        System.err
                        .println("Usage: <application> <dumpFolder> <jvmargs>\n\n"
                                        + "where:\n" //
                                        + "  <application> one of -test : run regression tests\n" //
                                        + "                       -cleanAll : clean index files and test results\n" //
                                        + "                       -newBaseline : overwrite existing base line with the last test results\n" //
                                        + "                       -performance : run performance tests\n" //
                                        + "  <dumpFolder>  a directory with a set of heap dumps\n"
                                        + "  <jvmargs>     the arguments passed through to the VM parsing the heap dumps.");
    }

    public void stop()
    {}
}

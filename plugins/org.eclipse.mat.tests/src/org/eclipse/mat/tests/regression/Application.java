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

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class Application implements IApplication
{
    public Object start(IApplicationContext context) throws Exception
    {
        String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
       
        if (args.length !=3)
        {
            System.err.println("Missing parameters.\r\n  Usage: <application> [dumpFolder] [jvmargs]");
            return IApplication.EXIT_RELAUNCH;
        }
            
        String application = args[0];

        String[] appArgs = new String[args.length - 1];
        System.arraycopy(args, 1, appArgs, 0, appArgs.length);

        if ("-cleanAll".equals(application))
        { // removes index files and last test results
            new CleanAllApplication(appArgs).run();
        }
        else if ("-newBaseline".equals(application))
        {
            new ForceNewBaselineApplication(appArgs).run();
        }    
        else if ("-test".equals(application))
        {
            new RegressionTestApplication(appArgs).run();
        }
        else
        {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                            MessageFormat.format("Unknown application: {0}", application));
            return IApplication.EXIT_RELAUNCH;
        }      
      
        return IApplication.EXIT_OK;
    }   

    public void stop()
    {}    
}

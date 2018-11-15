/*******************************************************************************
 * Copyright (c) 2008, 2018 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - multiple snapshots
 *******************************************************************************/
package org.eclipse.mat.internal.apps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.SpecFactory;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.MultipleSnapshotsException;
import org.eclipse.mat.snapshot.MultipleSnapshotsException.Context;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.ConsoleProgressListener;
import org.eclipse.mat.util.MessageUtil;

/**
 * Opens and parses a dump into a snapshot and runs reports on the snapshot.
 */
public class ParseSnapshotApp implements IApplication
{

    public Object start(IApplicationContext context) throws Exception
    {
        String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);

        if (args == null || args.length < 1)
            throw new IllegalArgumentException(Messages.ParseSnapshotApp_Usage);

        SpecFactory factory = SpecFactory.instance();

        File file = null;
        Map<String, String> options = new HashMap<String, String>();
        List<Spec> reports = new ArrayList<Spec>();

        for (int ii = 0; ii < args.length; ii++)
        {
            if (args[ii].length() > 0 && args[ii].charAt(0) == '-')
            {
                int p = args[ii].indexOf('=');
                if (p < 0)
                    options.put(args[ii].substring(1), Boolean.TRUE.toString());
                else
                    options.put(args[ii].substring(1, p), args[ii].substring(p + 1));
            }
            else if (file == null)
            {
                file = new File(args[ii]);
                if (!file.exists())
                    throw new FileNotFoundException(MessageUtil.format(Messages.ParseSnapshotApp_ErrorMsg_FileNotFound,
                                    file.getAbsolutePath()));
            }
            else
            {
                Spec spec = null;

                File specFile = new File(args[ii]);
                if (specFile.exists())
                {
                    spec = factory.create(specFile);
                }
                else
                {
                    spec = factory.create(args[ii]);
                }

                if (spec != null)
                {
                    // Allow command line options to control reports
                    spec.putAll(options);
                    factory.resolve(spec);
                    reports.add(spec);
                }
                else
                {
                    System.err.println(MessageUtil.format(Messages.ParseSnapshotApp_ErrorMsg_ReportNotFound, args[ii]));
                }
            }

        }

        try
        {
            parse(file, options, reports);
        }
        catch (MultipleSnapshotsException mre)
        {
            System.err.println(Messages.ParseSnapshotApp_MultipleSnapshotsDetected);
            List<MultipleSnapshotsException.Context> runtimes = mre.getRuntimes();

            Iterator<MultipleSnapshotsException.Context> it = runtimes.listIterator();
            while (it.hasNext())
            {
                Context runtime = it.next();
                System.err.println(MessageUtil.format(Messages.ParseSnapshotApp_MultipleSnapshotsDetail,
                                runtime.getRuntimeId(), runtime.getVersion()));
            }
        }

        return IApplication.EXIT_OK;
    }

    public void stop()
    {}

    private void parse(File file, Map<String, String> arguments, List<Spec> reports) throws SnapshotException
    {
        ConsoleProgressListener listener = new ConsoleProgressListener(System.out);
        ISnapshot snapshot;
        try
        {
            snapshot = SnapshotFactory.openSnapshot(file, arguments, listener);
        }
        catch (MultipleSnapshotsException mre)
        {
            throw mre;
        }
        catch (SnapshotException e)
        {
            // CoreExceptions can have subcauses which get lost by printStackTrace
            for (Throwable t = e.getCause(); t != null; t = t.getCause())
            {
                if (t instanceof CoreException)
                {
                    CoreException ce = (CoreException)t;
                    IStatus is = ce.getStatus();
                    MATPlugin.log(is);
                    break;
                }
            }
            throw e;
        }
        finally
        {
            listener.done();
        }

        try
        {
            for (Spec report : reports)
            {
                try
                {
                    runReport(snapshot, report);
                }
                catch (SnapshotException e)
                {
                    MATPlugin.log(e);
                }
                catch (IOException e)
                {
                    MATPlugin.log(e);
                }
            }
        }
        finally
        {
            SnapshotFactory.dispose(snapshot);
        }
    }

    private void runReport(ISnapshot snapshot, Spec report) throws SnapshotException, IOException
    {
        TestSuite suite = new TestSuite.Builder(report) //
                        .build(new SnapshotQueryContext(snapshot));

        ConsoleProgressListener listener = new ConsoleProgressListener(System.out);
        suite.execute(listener);
        listener.done();
    }

}

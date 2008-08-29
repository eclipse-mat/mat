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
import java.io.IOException;
import java.io.PrintStream;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.IProgressListener;

@SuppressWarnings("restriction")
public class TestParseApp
{
    private File snapshotFile;
    private Spec report;

    public TestParseApp(File snapshotFile, Spec report)
    {
        this.snapshotFile = snapshotFile;
        this.report = report;
    }

    public void run()
    {
        IProgressListener listener = new ClockedProgressListener(snapshotFile, System.out);

        ISnapshot snapshot = null;

        try
        {
            snapshot = SnapshotFactory.openSnapshot(snapshotFile, listener);
            
            TestSuite suite = new TestSuite.Builder(report) //
                            .build(new SnapshotQueryContext(snapshot));

            suite.execute(listener);
        }
        catch (SnapshotException e)
        {
            e.printStackTrace(System.err);
        }
        catch (IOException e)
        {
            e.printStackTrace(System.err);
        }
        finally
        {
            SnapshotFactory.dispose(snapshot);
        }

        listener.done();
    }

    private static class ClockedProgressListener implements IProgressListener
    {
        private PrintStream out;

        private int count = 0;
        private String absolutePath;
        private String fileName;

        private long timestamp = 0;
        private String lastMessage = null;

        /* package */ClockedProgressListener(File snapshot, PrintStream out)
        {
            this.out = out;
            this.timestamp = System.currentTimeMillis();

            this.absolutePath = snapshot.getParentFile().getAbsolutePath();
            this.fileName = snapshot.getName();

            int p = this.fileName.lastIndexOf('.');
            if (p >= 0)
                this.fileName = this.fileName.substring(0, p);
        }

        public void beginTask(String name, int totalWork)
        {
            subTask(name);
        }

        public void subTask(String name)
        {
            long now = System.currentTimeMillis();

            if (lastMessage != null)
            {
                int p = lastMessage.indexOf(absolutePath);
                if (p >= 0)
                    lastMessage = lastMessage.substring(0, p) + "DIR"
                                    + lastMessage.substring(p + absolutePath.length());

                p = lastMessage.indexOf(fileName);
                if (p >= 0)
                    lastMessage = lastMessage.substring(0, p) + "DUMP" + lastMessage.substring(p + fileName.length());

                out.println(String.format("Task: [%02d] %s %s ms", ++count, lastMessage, String
                                .valueOf(now - timestamp)));
            }

            this.lastMessage = name;
            this.timestamp = System.currentTimeMillis();
        }

        public void done()
        {
            subTask(null);
        }

        public boolean isCanceled()
        {
            return false;
        }

        public void sendUserMessage(Severity severity, String message, Throwable exception)
        {
            System.out.println(message);
            if (exception != null)
                exception.printStackTrace(System.out);
        }

        public void setCanceled(boolean value)
        {}

        public void worked(int work)
        {}

    }

}

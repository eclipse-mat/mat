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
package org.eclipse.mat.tests;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* Disable as a test for Athena builds
import junit.framework.AssertionFailedError;
import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestListener;
import junit.framework.TestResult;
*/

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class JUnit4TestRunner implements IApplication
{
    private String bundleName;
    private String suiteClassName;

    private List<JUnitResultFormatter> formatters = new ArrayList<JUnitResultFormatter>();

    PrintStream savedOut;
    PrintStream savedErr;

    public Object start(IApplicationContext context) throws Exception
    {

        try
        {
            parseArguments((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));

            savedOut = System.out;
            savedErr = System.err;

            ByteArrayOutputStream errStrm = new ByteArrayOutputStream();
            ByteArrayOutputStream outStrm = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outStrm));
            System.setErr(new PrintStream(errStrm));

            long start = System.currentTimeMillis();

            Class<?> testClass = loadSuiteClass();

            JUnitTest antTest = new JUnitTest(suiteClassName);
            antTest.setProperties(System.getProperties());

            for (JUnitResultFormatter f : formatters)
                f.startTestSuite(antTest);

            /* Disable as a test for Athena builds
            TestResult junitTestResult = new TestResult();
            for (JUnitResultFormatter f : formatters)
                junitTestResult.addListener(wrap(f));

            Test suite = new JUnit4TestAdapter(testClass);
            suite.run(junitTestResult);

            antTest.setCounts(junitTestResult.runCount(), junitTestResult.failureCount(), junitTestResult.errorCount());
            */
            antTest.setRunTime(System.currentTimeMillis() - start);

            sendOutAndErr(new String(outStrm.toByteArray()), new String(errStrm.toByteArray()));

            errStrm.close();
            outStrm.close();

            for (JUnitResultFormatter f : formatters)
                f.endTestSuite(antTest);

            return 0;
        }
        catch (Throwable t)
        {
            t.printStackTrace(System.out);
            return -1;
        }
        finally
        {
            if (savedOut != null)
                System.setOut(savedOut);
            if (savedErr != null)
                System.setErr(savedErr);
        }
    }

    /* Disable as a test for Athena builds
    private TestListener wrap(final JUnitResultFormatter delegate)
    {
        return new TestListener()
        {
            public void addError(Test test, Throwable t)
            {
                try
                {
                    delegate.addError(test, t);
                }
                catch (Throwable e)
                {
                    if (savedErr != null)
                        t.printStackTrace(savedErr);
                    else
                        t.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            public void addFailure(Test test, AssertionFailedError t)
            {
                try
                {
                    delegate.addFailure(test, t);
                }
                catch (Throwable e)
                {
                    if (savedErr != null)
                        t.printStackTrace(savedErr);
                    else
                        t.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            public void endTest(Test test)
            {
                try
                {
                    delegate.endTest(test);
                }
                catch (Throwable t)
                {
                    if (savedErr != null)
                        t.printStackTrace(savedErr);
                    else
                        t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }

            public void startTest(Test test)
            {
                try
                {
                    delegate.startTest(test);
                }
                catch (Throwable t)
                {
                    if (savedErr != null)
                        t.printStackTrace(savedErr);
                    else
                        t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }
        };
    }
    */

    private void sendOutAndErr(String out, String err)
    {
        for (JUnitResultFormatter f : formatters)
        {
            f.setSystemOutput(out);
            f.setSystemError(err);
        }
    }

    private void parseArguments(String[] args) throws Exception
    {
        for (int ii = 0; ii < args.length; ii++)
        {
            if ("-classname".equals(args[ii].toLowerCase()))
            {
                if (ii < args.length - 1)
                    suiteClassName = args[ii + 1];
                ii++;
            }
            else if ("-testpluginname".equals(args[ii].toLowerCase(Locale.ENGLISH)))
            {
                if (ii < args.length - 1)
                    bundleName = args[ii + 1];
                ii++;
            }
            else if (args[ii].startsWith("formatter="))
            {
                createAndStoreFormatter(args[ii].substring(10));
            }
        }

        if (suiteClassName == null)
            throw new Exception("Missing paramter: -classname");
    }

    // //////////////////////////////////////////////////////////////
    // test suite
    // //////////////////////////////////////////////////////////////

    private Class<?> loadSuiteClass() throws ClassNotFoundException
    {
        if (bundleName == null)
            return Class.forName(suiteClassName);

        Bundle bundle = Platform.getBundle(bundleName);
        if (bundle == null)
            throw new ClassNotFoundException(suiteClassName, new Exception(MessageUtil.format(
                            "Could not find bundle \"{0}\"", bundleName)));

        String hostHeader = (String) bundle.getHeaders().get("Fragment-Host");
        if (hostHeader != null)
        {
            try
            {
                ManifestElement[] hostElement = ManifestElement.parseHeader("Fragment-Host", hostHeader);
                bundle = Platform.getBundle(hostElement[0].getValue());
            }
            catch (BundleException e)
            {
                throw new RuntimeException(MessageUtil.format("Could not find host for fragment: {0}", bundleName), e);
            }
        }

        return bundle.loadClass(suiteClassName);
    }

    public void stop()
    {}

    // //////////////////////////////////////////////////////////////
    // formatter
    // //////////////////////////////////////////////////////////////

    private void createAndStoreFormatter(String line) throws BuildException
    {
        String formatterClassName = null;
        File formatterFile = null;

        int p = line.indexOf(',');
        if (p == -1)
        {
            formatterClassName = line;
        }
        else
        {
            formatterClassName = line.substring(0, p);
            formatterFile = new File(line.substring(p + 1));
        }

        formatters.add(createFormatter(formatterClassName, formatterFile));
    }

    private JUnitResultFormatter createFormatter(String classname, File outfile) throws BuildException
    {
        if (classname == null)
            throw new BuildException("Missing class name of formatter.");

        try
        {
            Class<?> clazz = getClass().getClassLoader().loadClass(classname);
            Object instance = clazz.newInstance();
            if (!(instance instanceof JUnitResultFormatter))
                throw new BuildException(MessageUtil.format("{0} is not a JUnitResultFormatter", classname));

            JUnitResultFormatter formatter = (JUnitResultFormatter) instance;
            formatter.setOutput(outfile != null ? new FileOutputStream(outfile) : System.out);

            return formatter;
        }
        catch (ClassNotFoundException e)
        {
            throw new BuildException(e);
        }
        catch (InstantiationException e)
        {
            throw new BuildException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new BuildException(e);
        }
        catch (java.io.IOException e)
        {
            throw new BuildException(e);
        }
    }
}

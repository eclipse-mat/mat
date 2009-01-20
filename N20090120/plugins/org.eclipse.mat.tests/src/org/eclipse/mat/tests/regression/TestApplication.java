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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.tests.regression.comparator.BinaryComparator;
import org.eclipse.mat.tests.regression.comparator.CSVComparator;
import org.eclipse.mat.tests.regression.comparator.IComparator;
import org.eclipse.mat.util.SimpleStringTokenizer;
import org.osgi.framework.Bundle;
import org.xml.sax.helpers.AttributesImpl;

public class TestApplication
{
    private File dumpDir;
    private String jvmFlags;
    private String report;
    private boolean compare;

    private static Map<String, IComparator> comparators = new HashMap<String, IComparator>(2);
    static
    {
        comparators.put("csv", new CSVComparator());
        comparators.put("bin", new BinaryComparator());
    }

    private class StreamGobbler extends Thread
    {
        InputStream is;
        PrintStream os;
        String type;
        private List<String> lines = new ArrayList<String>();

        StreamGobbler(InputStream is, PrintStream out, String type)
        {
            this.is = is;
            this.os = out;
            this.type = type;
        }

        @Override
        public void run()
        {
            try
            {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null)
                {
                    lines.add(line);
                    os.println(type + ">" + line);
                }
            }
            catch (IOException ioe)
            {
                // $JL-EXC$
                ioe.printStackTrace();
            }
        }

        public String getMessage()
        {
            StringBuilder buf = new StringBuilder();
            for (String line : lines)
                buf.append(line).append("\n");
            return buf.toString();
        }

        public List<String> getLines()
        {
            return lines;
        }
    }

    public TestApplication(File dumpDir, String jvmFlags, String report, boolean compare)
    {
        this.dumpDir = dumpDir;
        this.jvmFlags = jvmFlags;
        this.report = report;
        this.compare = compare;
    }

    public void run() throws Exception
    {
        List<File> dumpList = RegTestUtils.collectDumps(dumpDir, new ArrayList<File>());

        if (dumpList.isEmpty())
            throw new IOException(MessageFormat.format("{0} contains no heap dumps", dumpDir.getAbsolutePath()));

        List<TestSuiteResult> testResults = new ArrayList<TestSuiteResult>(dumpList.size());
        for (File dump : dumpList)
        {
            TestSuiteResult result = new TestSuiteResult(dump);
            testResults.add(result);

            try
            {
                // prepare test environment
                cleanIndexFiles(dump, result, true);
            }
            catch (Exception e)
            {
                // skip test suite for this heap dump
                continue;
            }

            try
            {
                // parse the heap dump and execute the test suite
                parse(dump, jvmFlags, result, !compare);
            }
            catch (Exception e)
            {
                System.err.println("ERROR: " + e.getMessage());
                result.addErrorMessage(e.getMessage());
                continue;
            }

            // process the result (compare to the baseline)
            if (compare)
                processResults(dump, result);

            // do the cleanup only if all the tests succeeded
            boolean succeed = true;
            for (SingleTestResult entry : result.getTestData())
            {
                if (entry.getResult().equals("Failed"))
                {
                    succeed = false;
                    break;
                }
            }
            if (succeed && result.getErrorMessages().isEmpty())
                cleanIndexFiles(dump, result, false);

        }

        if (!testResults.isEmpty())
        {
            System.out.println("-------------------------------------------------------------------");

            if (compare)
                generateXMLReport(testResults);
            else
                generatePerformanceReport(testResults);

            System.out.println("-------------------------------------------------------------------");

            boolean isSuccessful = true;
            for (int ii = 0; isSuccessful && ii < testResults.size(); ii++)
                isSuccessful = testResults.get(ii).isSuccessful();

            if (isSuccessful)
                System.out.println("Tests finished successfully");
            else
                throw new IOException("Tests failed with errors.");
        }
        else
        {
            throw new IOException("No test results collected.");
        }
    }

    private static final String URI = "http://www.eclipse.org/mat/regtest/";

    private interface Parameter
    {
        String NAME = "name";
        String TEST_SUITE = "testSuite";
        String HEAP_DUMP = "heapDump";
        String ERROR = "error";
        String TEST = "test";
        String TEST_NAME = "testName";
        String RESULT = "result";
        String PROBLEM = "problem";
        String DIFFERENCE = "difference";
        String LINE = "line";
        String BASELINE = "baseLine";
        String TESTLINE = "testLine";
    }

    private void generateXMLReport(List<TestSuiteResult> testResults)
    {
        try
        {
            File resultFile = new File(dumpDir, RegTestUtils.RESULT_FILENAME);
            PrintWriter out = new PrintWriter(resultFile);

            StreamResult streamResult = new StreamResult(out);
            SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();

            TransformerHandler handler = tf.newTransformerHandler();
            Transformer serializer = handler.getTransformer();
            serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            // serializer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM);
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            handler.setResult(streamResult);
            handler.startDocument();

            AttributesImpl atts = new AttributesImpl();
            handler.startElement(URI, Parameter.TEST_SUITE, Parameter.TEST_SUITE, atts);

            for (TestSuiteResult testSuiteResult : testResults)
            {
                atts.clear();
                atts.addAttribute(URI, Parameter.NAME, Parameter.NAME, "CDATA", testSuiteResult.getDumpName());
                handler.startElement(URI, Parameter.HEAP_DUMP, Parameter.HEAP_DUMP, atts);
                atts.clear();

                List<String> errors = testSuiteResult.getErrorMessages();
                for (String error : errors)
                {
                    atts.clear();
                    handler.startElement(URI, Parameter.ERROR, Parameter.ERROR, atts);
                    handler.characters(error.toCharArray(), 0, error.length());
                    handler.endElement(URI, Parameter.ERROR, Parameter.ERROR);
                }

                List<SingleTestResult> tests = testSuiteResult.getTestData();
                for (SingleTestResult singleTestResult : tests)
                {
                    atts.clear();
                    handler.startElement(URI, Parameter.TEST, Parameter.TEST, atts);

                    atts.clear();
                    handler.startElement(URI, Parameter.TEST_NAME, Parameter.TEST_NAME, atts);
                    handler.characters(singleTestResult.getTestName().toCharArray(), 0, singleTestResult.getTestName()
                                    .length());
                    handler.endElement(URI, Parameter.TEST_NAME, Parameter.TEST_NAME);

                    atts.clear();
                    handler.startElement(URI, Parameter.RESULT, Parameter.RESULT, atts);
                    handler.characters(singleTestResult.getResult().toCharArray(), 0, singleTestResult.getResult()
                                    .length());
                    handler.endElement(URI, Parameter.RESULT, Parameter.RESULT);

                    List<Difference> differences = singleTestResult.getDifferences();
                    for (Difference difference : differences)
                    {
                        atts.clear();
                        if (difference.getProblem() != null)
                        {
                            handler.startElement(URI, Parameter.DIFFERENCE, Parameter.DIFFERENCE, atts);
                            handler.startElement(URI, Parameter.PROBLEM, Parameter.PROBLEM, atts);
                            handler.characters(difference.getProblem().toCharArray(), 0, difference.getProblem()
                                            .length());
                            handler.endElement(URI, Parameter.PROBLEM, Parameter.PROBLEM);
                        }
                        else
                        {
                            atts.addAttribute(URI, Parameter.LINE, Parameter.LINE, "", difference.getLineNumber());
                            handler.startElement(URI, Parameter.DIFFERENCE, Parameter.DIFFERENCE, atts);

                            atts.clear();
                            handler.startElement(URI, Parameter.BASELINE, Parameter.BASELINE, atts);
                            handler.characters(difference.getBaseline().toCharArray(), 0, difference.getBaseline()
                                            .length());
                            handler.endElement(URI, Parameter.BASELINE, Parameter.BASELINE);

                            atts.clear();
                            handler.startElement(URI, Parameter.TESTLINE, Parameter.TESTLINE, atts);
                            handler.characters(difference.getTestLine().toCharArray(), 0, difference.getTestLine()
                                            .length());
                            handler.endElement(URI, Parameter.TESTLINE, Parameter.TESTLINE);
                        }

                        handler.endElement(URI, Parameter.DIFFERENCE, Parameter.DIFFERENCE);
                    }
                    handler.endElement(URI, Parameter.TEST, Parameter.TEST);
                }
                handler.endElement(URI, Parameter.HEAP_DUMP, Parameter.HEAP_DUMP);
            }
            handler.endElement(URI, Parameter.TEST_SUITE, Parameter.TEST_SUITE);
            handler.endDocument();
            out.close();
            System.out.println("Report is generated in: " + resultFile.getAbsolutePath());
        }
        catch (FileNotFoundException e)
        {
            System.err.println("ERROR: File not found " + dumpDir.getAbsolutePath()
                            + "result.xml. Failed to generate the report");

        }
        catch (Exception e)
        {
            System.err.println("ERROR: Failed to generate the report. ");
            e.printStackTrace(System.err);
        }

    }

    private void generatePerformanceReport(List<TestSuiteResult> results) throws IOException
    {
        File report = new File(dumpDir, String.format("performanceResults_%1$tY%1$tm%1$td%1$tH%1$tM.csv", new Date()));

        PrintStream out = null;

        try
        {
            out = new PrintStream(new FileOutputStream(report));

            // add heading
            out.append("Heap Dump").append(RegTestUtils.SEPARATOR) //
                            .append("Test Name").append(RegTestUtils.SEPARATOR) //
                            .append("Date").append(RegTestUtils.SEPARATOR) //
                            .append("Time").append(RegTestUtils.SEPARATOR) //
                            .append("Build Version").append("\n");

            Bundle bundle = Platform.getBundle("org.eclipse.mat.api");
            String buildId = (bundle != null) ? bundle.getHeaders().get("Bundle-Version").toString()
                            : "Unknown version";
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());

            for (TestSuiteResult result : results)
            {
                String path = result.getSnapshot().getAbsolutePath();
                String relativePath = path.substring(dumpDir.getAbsolutePath().length() + 1);

                for (PerfData record : result.getPerfData())
                {
                    out.append(relativePath).append(RegTestUtils.SEPARATOR) //
                                    .append(record.getTestName()).append(RegTestUtils.SEPARATOR) //
                                    .append(date).append(RegTestUtils.SEPARATOR) //
                                    .append(record.getTime()).append(RegTestUtils.SEPARATOR) //
                                    .append(buildId).append("\n");
                }
            }

        }
        finally
        {
            if (out != null)
            {
                out.flush();
                out.close();
            }
        }

        System.out.println(MessageFormat.format("Saved performance data to {0}", report.getAbsolutePath()));
    }

    private void processResults(File dump, TestSuiteResult result) throws Exception
    {
        // check if the baseline exists. Baseline is placed in the
        // sub-folder, named <heapDumpName>_baseline
        File baselineDir = new File(dump.getAbsolutePath() + RegTestUtils.BASELINE_EXTENSION);
        if (baselineDir.exists() && baselineDir.isDirectory() && baselineDir.listFiles().length > 0)
        {
            // create folder, unzip result in it
            File resultDir = new File(dump.getAbsolutePath() + RegTestUtils.TEST_EXTENSION);
            if (resultDir.exists())
            {
                File[] oldFiles = resultDir.listFiles();
                for (File file : oldFiles)
                {
                    file.delete();
                }
                resultDir.delete();
            }
            resultDir.mkdir();
            unzipTestResults(resultDir, dump, result);

            // verify that all the baseline results have corresponding result
            // files in test folder
            File[] baselineFiles = baselineDir.listFiles();
            for (final File baselineFile : baselineFiles)
            {
                File[] matchingFiles = resultDir.listFiles(new FileFilter()
                {

                    public boolean accept(File file)
                    {
                        return file.getName().equals(baselineFile.getName());
                    }

                });
                if (matchingFiles.length == 0)
                {
                    String errorMessage = MessageFormat.format(
                                    "ERROR: Baseline result {0} has no corresponding test result", baselineFile);

                    System.err.println(errorMessage);
                    result.addErrorMessage(errorMessage);
                }
            }

            // for each result file compare
            File[] results = resultDir.listFiles();

            System.out.println("-------------------------------------------------------------------");
            for (final File testResultFile : results)
            {
                File[] matchingFiles = baselineDir.listFiles(new FileFilter()
                {

                    public boolean accept(File file)
                    {
                        return file.getName().equals(testResultFile.getName());
                    }

                });
                if (matchingFiles.length == 1)
                {
                    String fileExtention = testResultFile.getName().substring(
                                    testResultFile.getName().lastIndexOf('.') + 1, testResultFile.getName().length());
                    IComparator comparator = comparators.get(fileExtention);
                    List<Difference> differences = comparator.compare(matchingFiles[0], testResultFile);
                    if (differences == null || differences.isEmpty())
                        result.addTestData(new SingleTestResult(testResultFile.getName(), "Ok", null));
                    else
                        result.addTestData(new SingleTestResult(testResultFile.getName(), "Failed", differences));
                }
                else
                {
                    // this must be a new test - place its results to the
                    // baseline folder
                    File newBaselineFile = new File(baselineDir, testResultFile.getName());
                    testResultFile.renameTo(newBaselineFile);

                    System.out.println("Info: New baseline was added for " + testResultFile.getName());
                    result.addTestData(new SingleTestResult(testResultFile.getName(), "New baseline was added", null));
                }
            }
        }
        else
        {
            // create baseline folder and copy the result of the tests in it
            baselineDir.mkdir();
            // unzip only baseline results (.csv, etc) delete zip file
            unzipTestResults(baselineDir, dump, result);
            // report new baseline creation
            File[] baseline = baselineDir.listFiles();
            for (File baselineFile : baseline)
            {
                SingleTestResult singleTestResult = new SingleTestResult(baselineFile.getName(),
                                "New baseline was added", null);
                result.addTestData(singleTestResult);
                System.out.println("Info: New baseline was added for " + baselineFile.getName());
            }
        }
    }

    private void cleanIndexFiles(File file, TestSuiteResult result, boolean throwExcepionFlag) throws Exception
    {
        System.out.println("Cleanup: Cleaning the indexes and old result files for " + file.getName());
        File dir = file.getParentFile();

        String[] indexFiles = dir.list(RegTestUtils.cleanupFilter);

        for (String indexFile : indexFiles)
        {
            File f = new File(dir, indexFile);
            if (f.exists() && !f.isDirectory())
            {
                // delete old index and report files, throw exception if fails
                if (!f.delete())
                {
                    String message = MessageFormat.format("Failed removing file {0} from the file system", //
                                    f.getAbsolutePath());
                    result.addErrorMessage(message);
                    System.err.println(message);

                    if (throwExcepionFlag)
                        throw new Exception(message);
                }
            }
        }

    }

    private void unzip(File zipfile, File targetDir, TestSuiteResult result) throws Exception
    {
        int BUFFER = 512;// 1024;
        BufferedOutputStream dest = null;
        FileInputStream fis;
        try
        {
            fis = new FileInputStream(zipfile);
        }
        catch (FileNotFoundException e)
        {
            // is not possible
            return;
        }
        ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(fis));
        ZipEntry entry;

        Pattern baselinePattern = Pattern.compile("(.*\\.)?csv");
        Pattern domTreePattern = Pattern.compile("(.*\\.)?bin");
        try
        {
            while ((entry = zipInputStream.getNextEntry()) != null)
            {
                // unzip only baseline files
                if (entry.isDirectory()
                                || (!baselinePattern.matcher(entry.getName()).matches() && !domTreePattern.matcher(
                                                entry.getName()).matches()))
                    continue;

                int count;
                byte data[] = new byte[BUFFER];

                File outputFile = new File(targetDir, entry.getName());
                outputFile.getParentFile().mkdirs();

                FileOutputStream fos = new FileOutputStream(outputFile);
                dest = new BufferedOutputStream(fos, BUFFER);
                while ((count = zipInputStream.read(data, 0, BUFFER)) != -1)
                {
                    dest.write(data, 0, count);
                }
                dest.flush();
                dest.close();
            }
            zipInputStream.close();
        }
        catch (FileNotFoundException e)
        {
            result.addErrorMessage("File not found: " + e.getMessage());
            System.err.println("ERROR: File not found" + e.getMessage());
        }
        catch (IOException e)
        {
            result.addErrorMessage(e.getMessage());
            System.err.println("ERROR: " + e.getMessage());
        }

    }

    private void unzipTestResults(File baselineDir, File dumpFile, TestSuiteResult result) throws Exception
    {
        // get result file name
        String resultsFileName = dumpFile.getAbsolutePath().substring(0, dumpFile.getAbsolutePath().lastIndexOf('.'))
                        + "_Regression_Tests.zip";
        System.out.println("Unzip: unziping test result file " + resultsFileName);

        File originFile = new File(resultsFileName);
        File targetFile = new File(baselineDir, originFile.getName());
        boolean succeed = originFile.renameTo(targetFile);
        if (succeed)
        {
            // unzip
            unzip(targetFile, baselineDir, result);
            targetFile.delete();
        }
        else
        {
            String message = "ERROR: Failed coping test results file " + resultsFileName
                            + " to the destination folder " + baselineDir;
            result.addErrorMessage(message);
            System.err.println(message);
        }

    }

    private void parse(File dump, String jvmFlags, TestSuiteResult result, boolean extractTime) throws Exception
    {
        Properties p = System.getProperties();
        String cp = p.getProperty("java.class.path");
        String osgiDev = p.getProperty("osgi.dev");
        String osgiInstallArea = p.getProperty("osgi.install.area");
        String osgiInstanceArea = p.getProperty("osgi.instance.area");
        String osgiConfiguration = p.getProperty("osgi.configuration.area");

        List<String> cmdArray = new ArrayList<String>();

        cmdArray.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");

        for (String s : SimpleStringTokenizer.split(jvmFlags, ' '))
            cmdArray.add(s);

        cmdArray.add("-jar");
        cmdArray.add(cp);

        if (osgiDev != null)
        {
            cmdArray.add("-dev");
            cmdArray.add(osgiDev);
        }

        if (osgiInstallArea != null)
        {
            cmdArray.add("-install");
            cmdArray.add(osgiInstallArea);
        }

        if (osgiConfiguration != null)
        {
            cmdArray.add("-configuration");
            cmdArray.add(osgiConfiguration);
        }

        if (osgiInstanceArea != null)
        {
            cmdArray.add("-data");
            cmdArray.add(osgiInstanceArea);
        }

        cmdArray.add("-application");
        cmdArray.add("org.eclipse.mat.tests.application");
        cmdArray.add("-parse");
        cmdArray.add(dump.getAbsolutePath());
        cmdArray.add("org.eclipse.mat.tests:" + report);

        System.out.println("Starting: ");
        for (String s : cmdArray)
        {
            System.out.print("   ");
            System.out.println(s);
        }

        Process process = Runtime.getRuntime().exec(cmdArray.toArray(new String[0]));
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), System.err, "ERROR");
        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), System.out, "OUTPUT");

        errorGobbler.start();
        outputGobbler.start();

        // any error???
        int status = process.waitFor();
        if (status != 0) // something went wrong
        {
            System.err.println(MessageFormat.format("ERROR: Exit Status {0}", status));
            throw new IOException(MessageFormat.format("Parsing finished with exit status {0}.\nOutput:\n{1}\n\n {2}", //
                            status, outputGobbler.getMessage(), errorGobbler.getMessage()));
        }
        // extract parsing time
        if (extractTime)
        {
            Pattern pattern = Pattern.compile("Task: (.*) ([0-9]*) ms");
            for (String line : outputGobbler.getLines())
            {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches())
                    result.addPerfData(new PerfData(matcher.group(1), matcher.group(2)));
            }
        }
    }
}

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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
import org.osgi.framework.Bundle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class TestApplication
{
    private String[] args;
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
        StringBuilder builder = new StringBuilder();

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
                    builder.append(line).append("\n");
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
            return builder.toString();
        }
    }

    private class PerfData
    {
        String testName;
        String queryTime;
        String time;

        public PerfData(String testName, String queryTime, String time)
        {
            this.queryTime = queryTime;
            this.testName = testName;
            this.time = time;
        }

    }

    private class TOCHandler extends DefaultHandler
    {

        List<PerfData> data = new ArrayList<PerfData>();

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException
        {
            if (name.equalsIgnoreCase("part"))
            {
                if (attributes.getValue("query-time") != null && attributes.getValue("time") != null)
                {
                    data.add(new PerfData(attributes.getValue("name"), attributes.getValue("query-time"), attributes
                                    .getValue("time")));
                }
            }
        }

        public List<PerfData> getData()
        {
            return data;
        }
    }

    public TestApplication(String[] args, String report, boolean compare)
    {
        this.args = args;
        this.report = report;
        this.compare = compare;
    }

    public void run() throws Exception
    {

        File dumpsFolder = new File(args[0]);
        if (!dumpsFolder.exists())
        {
            System.err.println("Provided directory does not exist");
            return;
        }

        String jvmFlags = args[1];

        List<File> dumpList = RegTestUtils.collectDumps(dumpsFolder, new ArrayList<File>());

        if (dumpList.isEmpty())
        {
            System.err.println("This directory contains no heap dumps");
            // $JL-SYS_OUT_ERR$
            System.err.println();
            return;
        }
        else
        {
            List<TestSuiteResult> testResults = new ArrayList<TestSuiteResult>(dumpList.size());
            for (File dump : dumpList)
            {
                TestSuiteResult result = new TestSuiteResult(dump.getName());
                testResults.add(result);

                try
                {
                    // prepare test environment
                    cleanIndexFiles(dump, result, true);
                }
                catch (Exception e)
                {
                    //skip test suite for this heap dump                     
                    continue;
                }

                try
                {
                    // parse the heap dump and execute the test suite
                    parse(dump, jvmFlags, result, !compare);
                }
                catch (Exception e)
                {
                    System.err.println("ERROR> " + e.getMessage());
                    result.addErrorMessage(e.getMessage());
                    continue;
                }
                if (compare)
                {
                    // process the result (compare to the baseline)
                    processResults(dump, result);
                }
                else
                {
                    // record performance times
                    recordTimes(dump, result, dumpsFolder);
                }

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
                if (compare)
                {
                    // generate XML report for regression tests
                    System.out.println("-------------------------------------------------------------------");
                    generateXMLReport(dumpsFolder, testResults);
                }
                System.out.println("-------------------------------------------------------------------");

                boolean succeed = true;
                for (TestSuiteResult entry : testResults)
                {
                    if (!entry.getErrorMessages().isEmpty())
                    {
                        succeed = false;
                        break;
                    }
                    for (SingleTestResult singleTestResult : entry.getTestData())
                    {
                        if (!singleTestResult.getDifferences().isEmpty())
                        {
                            succeed = false;
                            break;
                        }
                    }
                    if (!succeed)
                        break;
                }
                if (succeed)
                    System.out.println("Tests finished successfully");
                else
                    System.out.println("Tests finished with errors");
            }
            else
            {
                System.out.println("Failed");
            }
        }
    }

    private void recordTimes(File dump, TestSuiteResult result, File dumpsFolder) throws FileNotFoundException
    {
        // get result file name
        String resultsFileName = dump.getAbsolutePath().substring(0, dump.getAbsolutePath().lastIndexOf('.'))
                        + "_Performance_Tests.zip";
        String relativePath = dump.getAbsolutePath().substring(dumpsFolder.getAbsolutePath().length() + 1,
                        dump.getAbsolutePath().length());

        if (!new File(resultsFileName).exists())
            throw new FileNotFoundException(MessageFormat.format("Performance test result zip file not found: {0}",
                            resultsFileName));
        System.out.println("OUTPUT> Task: extracting performance time records from " + resultsFileName);
        PrintStream out = null;
        ZipFile zipFile = null;
        try
        {

            File resultReport = new File(dumpsFolder, "performanceResults.csv");
            try
            {
                out = new PrintStream(new FileOutputStream(resultReport, true));
            }
            catch (FileNotFoundException e)
            {
                // file is in use -> create a new one
                File[] resultFiles = dumpsFolder.listFiles(new FilenameFilter()
                {

                    public boolean accept(File dir, String name)
                    {
                        Pattern pattern = Pattern.compile("performanceResults.*\\.csv");
                        return pattern.matcher(name).matches();
                    }
                });
                String newResultFileName = "performanceResults(" + resultFiles.length + ").csv";
                System.out.println("WARNING: performance.csv file is in use. Creating a new result file: "
                                + newResultFileName);
                resultReport = new File(dumpsFolder, newResultFileName);
                out = new PrintStream(new FileOutputStream(resultReport, true));
            }
            if (!resultReport.exists() || resultReport.length() == 0)
            {
                // add heading
                StringBuilder builder = new StringBuilder();

                builder.append("Heap Dump").append(RegTestUtils.SEPARATOR).append("Test Name")
                                //
                                .append(RegTestUtils.SEPARATOR).append("Date").append(RegTestUtils.SEPARATOR).append(
                                                "Query time, ns")
                                //
                                .append(RegTestUtils.SEPARATOR).append("Time, ns").append(RegTestUtils.SEPARATOR)
                                .append("Build Version");

                out.println(builder.toString());
            }

            zipFile = new ZipFile(resultsFileName);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                // we are interested in toc.xml only, as it contains the time
                // info
                if (entry.isDirectory() || !entry.getName().equals("toc.xml"))
                    continue;

                XMLReader parser = XMLReaderFactory.createXMLReader();
                TOCHandler handler = new TOCHandler();
                parser.setContentHandler(handler);
                parser.parse(new InputSource(zipFile.getInputStream(entry)));
                List<PerfData> data = handler.getData();

                Bundle bundle = Platform.getBundle("org.eclipse.mat.api");
                String buildId = (bundle != null) ? bundle.getHeaders().get("Bundle-Version").toString()
                                : "Unknown version";
                String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.GERMANY)
                                .format(new Date());

                String parsingTime = result.getParsingTime();
                StringBuilder builder = new StringBuilder();

                builder.append(relativePath).append(RegTestUtils.SEPARATOR).append("Parsing")
                                //
                                .append(RegTestUtils.SEPARATOR).append(date).append(RegTestUtils.SEPARATOR)
                                //
                                .append(RegTestUtils.SEPARATOR).append(parsingTime).append(RegTestUtils.SEPARATOR)
                                .append(buildId);

                out.println(builder.toString());

                for (PerfData perfData : data)
                {
                    builder = new StringBuilder();

                    builder.append(relativePath).append(RegTestUtils.SEPARATOR).append(perfData.testName)//
                                    .append(RegTestUtils.SEPARATOR).append(date).append(RegTestUtils.SEPARATOR)//
                                    .append(perfData.queryTime).append(RegTestUtils.SEPARATOR)//
                                    .append(perfData.time).append(RegTestUtils.SEPARATOR).append(buildId);

                    out.println(builder.toString());
                }
                // there is only one toc.xml
                break;
            }
            out.flush();
            out.close();
            zipFile.close();
        }
        catch (IOException e)
        {
            result.addErrorMessage(e.getMessage());
            System.err.println("ERROR> " + e.getMessage());
        }
        catch (SAXException e)
        {
            result.addErrorMessage(e.getMessage());
            System.err.println("ERROR> " + e.getMessage());
        }
        finally
        {
            try
            {
                if (out != null)
                {
                    out.flush();
                    out.close();
                }
                if (zipFile != null)
                    zipFile.close();
            }
            catch (IOException e)
            {
                // ignore
            }
        }

    }

    private void generateXMLReport(File targetFolder, List<TestSuiteResult> testResults)
    {
        try
        {
            File resultFile = new File(targetFolder, RegTestUtils.RESULT_FILENAME);
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
            handler.startElement("", "", "testSuite", atts);

            for (TestSuiteResult testSuiteResult : testResults)
            {
                atts.clear();
                atts.addAttribute("", "", "name", "CDATA", testSuiteResult.getDumpName());
                handler.startElement("", "", "heapDump", atts);
                atts.clear();

                List<String> errors = testSuiteResult.getErrorMessages();
                for (String error : errors)
                {
                    atts.clear();
                    handler.startElement("", "", "error", atts);
                    handler.characters(error.toCharArray(), 0, error.length());
                    handler.endElement("", "", "error");
                }

                List<SingleTestResult> tests = testSuiteResult.getTestData();
                for (SingleTestResult singleTestResult : tests)
                {
                    atts.clear();
                    handler.startElement("", "", "test", atts);

                    atts.clear();
                    handler.startElement("", "", "testName", atts);
                    handler.characters(singleTestResult.getTestName().toCharArray(), 0, singleTestResult.getTestName()
                                    .length());
                    handler.endElement("", "", "testName");

                    atts.clear();
                    handler.startElement("", "", "result", atts);
                    handler.characters(singleTestResult.getResult().toCharArray(), 0, singleTestResult.getResult()
                                    .length());
                    handler.endElement("", "", "result");

                    List<Difference> differences = singleTestResult.getDifferences();
                    for (Difference difference : differences)
                    {
                        atts.clear();
                        if (difference.getProblem() != null)
                        {
                            handler.startElement("", "", "difference", atts);
                            handler.startElement("", "", "problem", atts);
                            handler.characters(difference.getProblem().toCharArray(), 0, difference.getProblem()
                                            .length());
                            handler.endElement("", "", "problem");
                        }
                        else
                        {
                            atts.addAttribute("", "", "line", "", difference.getLineNumber());
                            handler.startElement("", "", "difference", atts);

                            atts.clear();
                            handler.startElement("", "", "baseline", atts);
                            handler.characters(difference.getBaseline().toCharArray(), 0, difference.getBaseline()
                                            .length());
                            handler.endElement("", "", "baseline");

                            atts.clear();
                            handler.startElement("", "", "testLine", atts);
                            handler.characters(difference.getTestLine().toCharArray(), 0, difference.getTestLine()
                                            .length());
                            handler.endElement("", "", "testLine");
                        }

                        handler.endElement("", "", "difference");
                    }
                    handler.endElement("", "", "test");
                }
                handler.endElement("", "", "heapDump");
            }
            handler.endElement("", "", "testSuite");
            handler.endDocument();
            out.close();
            System.out.println("Report is generated in: " + targetFolder);
        }
        catch (FileNotFoundException e)
        {
            System.err.println("ERROR> File not found " + targetFolder.getAbsolutePath()
                            + "result.xml. Failed to generate the report");

        }
        catch (Exception e)
        {
            System.err.println("ERROR> Failed to generate the report. ");
            e.printStackTrace(System.err);
        }

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
                                    "ERROR> Baseline result {0} has no corresponding test result", baselineFile);

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

                    System.out.println("OUTPUT> New baseline was added for " + testResultFile.getName());
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
                System.out.println("OUTPUT> New baseline was added for " + baselineFile.getName());
            }
        }
    }

    private void cleanIndexFiles(File file, TestSuiteResult result, boolean throwExcepionFlag) throws Exception
    {
        System.out.println("OUTPUT>Task: Cleaning the indexes and old result files for " + file.getName());
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
                else
                {
                    System.out.println("OUTPUT>File " + f.getName() + " deleted.");
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
            System.err.println("ERROR> File not found" + e.getMessage());
        }
        catch (IOException e)
        {
            result.addErrorMessage(e.getMessage());
            System.err.println("ERROR> " + e.getMessage());
        }

    }

    private void unzipTestResults(File baselineDir, File dumpFile, TestSuiteResult result) throws Exception
    {
        // get result file name
        String resultsFileName = dumpFile.getAbsolutePath().substring(0, dumpFile.getAbsolutePath().lastIndexOf('.'))
                        + "_Regression_Tests.zip";
        System.out.println("OUTPUT> Task: unziping test result file " + resultsFileName);

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
            String message = "ERROR> Failed coping test results file " + resultsFileName
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

        StringBuilder cmd = new StringBuilder();

        cmd.append("\"").append(System.getProperty("java.home")).append(File.separator).append("bin").append(
                        File.separator).append("java\"");
        cmd.append(" ").append(jvmFlags);
        cmd.append(" -jar \"").append(cp).append("\"");
        cmd.append(" -dev \"").append(osgiDev).append("\"");
        cmd.append(" -install \"").append(osgiInstallArea).append("\"");
        cmd.append(" -configuration \"").append(osgiConfiguration).append("\"");
        cmd.append(" -data \"").append(osgiInstanceArea).append("\"");
        cmd.append(" -application org.eclipse.mat.api.parse");
        cmd.append(" \"").append(dump.getAbsolutePath()).append("\"");
        cmd.append(" org.eclipse.mat.tests:" + report);

        System.out.println("Starting: " + cmd.toString());

        Process process = Runtime.getRuntime().exec(cmd.toString());
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), System.err, "ERROR");
        // any output?
        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), System.out, "OUTPUT");

        // kick them off
        errorGobbler.start();
        outputGobbler.start();

        // any error???
        int status = process.waitFor();
        if (status != 0) // something went wrong
        {
            System.err.println("Exit Status: FAILED");
            throw new IOException(MessageFormat.format("Exception while running the report: {0}\n\n {1}", outputGobbler
                            .getMessage(), errorGobbler.getMessage()));
        }
        // extract parsing time
        if (extractTime)
        {
            Pattern pattern = Pattern.compile("Time to parse: ([0-9]*)");
            Matcher matcher = pattern.matcher(outputGobbler.getMessage());
            if (matcher.find())
                result.setParsingTime(matcher.group(1));
        }
        System.out.println("Exit Status: OK");
    }
}

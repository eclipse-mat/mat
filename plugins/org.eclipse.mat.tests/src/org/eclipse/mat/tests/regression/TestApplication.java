/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - Xmx and thread numbers
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
import com.ibm.icu.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.eclipse.mat.util.MessageUtil;
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
            throw new IOException(MessageUtil.format("{0} contains no heap dumps", dumpDir.getAbsolutePath()));

        List<TestSuiteResult> testResults = new ArrayList<TestSuiteResult>(dumpList.size());
        fileloop: for (File dump : dumpList)
        {
            // Multiple -Djava.util.concurrent.ForkJoinPool.common.parallelism= means
            // run with every value
            // Also allow -XX:ActiveProcessorCount=
            String propname1 = "java.util.concurrent.ForkJoinPool.common.parallelism";
            String prop1 = "-D" + propname1 + "=";
            String propPattern1 = Pattern.quote(prop1);
            String propname2 = "XX:ActiveProcessorCount";
            String prop2 = "-" + propname2 + "=";
            String propPattern2 = Pattern.quote(prop2);
            String propPattern = propPattern1 + "|" + propPattern2;
            // Two instances of property, match first and last, consume space before mid
            Pattern threadPattern = Pattern.compile("(.*?)("+propPattern+")(\\d+) ?" + 
                                                     "(.*)\\2(\\d+)" + "(.*)");
            int minThreads = 1;
            int maxThreads = 1;
            Matcher threadMatch = threadPattern.matcher(jvmFlags);
            if (threadMatch.matches())
            {
                minThreads = Integer.parseInt(threadMatch.group(3));
                maxThreads = Integer.parseInt(threadMatch.group(5));
            }
            for (int th = minThreads; th <= maxThreads; ++th)
            {
                String jvmFlags1 = this.jvmFlags;
                if (threadMatch.matches())
                {
                    String prefix = threadMatch.group(1);
                    String propmatch = threadMatch.group(2);
                    String mid = threadMatch.group(4);
                    String suffix = threadMatch.group(6);
                    jvmFlags1 = prefix + mid + propmatch + th + suffix;
                }

                // Multiple -Xmx settings means for us to find the smallest which works
                String memArg="-Xmx";
                String memArgPattern = Pattern.quote(memArg);
                String unitPattern="[kKmMgG]?";
                // Two instances of Xmx, choose first and last, consume space before mid
                Pattern mXpattern = Pattern.compile("(.*?)(" + memArgPattern + ")(\\d+)(" + unitPattern + ") ?" +
                                                     "(.*)\\2(\\d+)(" + unitPattern + ")" +
                                                     "(.*)");
                long min = 1;
                long max = 1;
                String prefix="",mxmatch="",mid="",unit="",suffix="";
                Matcher m = mXpattern.matcher(jvmFlags1);
                if (m.matches())
                {
                    // Variable heap size e.g. 
                    // -Xmx64M -Xms1024M
                    // -Xmx64m -Xmx1G
                    prefix = m.group(1);
                    mxmatch = m.group(2);
                    min = Long.parseLong(m.group(3));
                    String unitMin = m.group(4);
                    mid = m.group(5);
                    max = Long.parseLong(m.group(6));
                    String unitMax = m.group(7);
                    suffix = m.group(8);
                    // Normalize units
                    unit = unitMin;
                    if (!unitMin.equals(unitMax))
                    {
                        unit = normalizedUnit(unitMin, unitMax);
                        min = normalizeToUnit(min, unitMin, unit);
                        max = normalizeToUnit(max, unitMax, unit);
                    }
                }
                boolean success = false;
                /*
                 * Binary search
                 */
                do
                {
                    long mx = (min + max) >>> 1;
                    String jvmFlags = m.matches() ? prefix + mid + mxmatch + mx + unit + suffix : jvmFlags1;

                    TestSuiteResult result = new TestSuiteResult(dump, jvmFlags);
                    testResults.add(result);
                    try
                    {
                        // prepare test environment
                        cleanIndexFiles(dump, result, true);
                    }
                    catch (Exception e)
                    {
                        // skip test suite for this heap dump
                        continue fileloop;
                    }

                    try
                    {
                        // parse the heap dump and execute the test suite
                        parse(dump, jvmFlags, result, !compare);
                        max = mx - 1;
                        success = true;
                    }
                    catch (Exception e)
                    {
                        min = mx + 1;
                        if (min > max && !success)
                        {
                            System.err.println("ERROR: " + e.getMessage());
                            result.addErrorMessage(e.getMessage());
                        }
                        else
                        {
                            // Expected failure with a too small Xmx
                            testResults.remove(result);
                        }
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

                } while (min <= max);
            }
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

    /**
     * Convert a value to the new units.
     * @param value
     * @param fromUnit
     * @param toUnit
     * @return the new value expressed as toUnit
     */
    private long normalizeToUnit(long value, String fromUnit, String toUnit)
    {
        String u1 = (toUnit+fromUnit).toUpperCase(Locale.ENGLISH);
        if (u1.equals("K") || u1.equals("KM") || u1.equals("MG") || u1.equals("GT"))
        {
            value *= 1024;
        }
        else if (u1.equals("M") || u1.equals("KG") || u1.equals("MT"))
        {
            value *= 1024 * 1024;
        }
        else if (u1.equals("G") || u1.equals("KT"))
        {
            value *= 1024 * 1024 * 1024;
        }
        else if (u1.equals("T"))
        {
            value *= 1024L * 1024 * 1024 * 1024;
        }
        return value;
    }

    /**
     * Choose the smaller of the two units.
     * @param unitMin
     * @param unitMax
     * @return the smaller unit
     */
    private String normalizedUnit(String unitMin, String unitMax)
    {
        String unit;
        if (unitMin.equals(unitMax))
            unit = unitMin;
        else if (unitMin.equals(""))
            unit = unitMin;
        else if (unitMax.equals(""))
            unit = unitMax;
        else if ("tT".contains(unitMin))
            unit = unitMax;
        else if ("tT".contains(unitMax))
            unit = unitMin;
        else if ("gG".contains(unitMin))
            unit = unitMax;
        else if ("gG".contains(unitMax))
            unit = unitMin;
        else if ("mM".contains(unitMin))
            unit = unitMax;
        else if ("mM".contains(unitMax))
            unit = unitMin;
        else if ("kK".contains(unitMin))
            unit = unitMax;
        else if ("kK".contains(unitMax))
            unit = unitMin;
        else
            unit = unitMin;
        return unit;
    }

    private static final String URI = "http://www.eclipse.org/mat/regtest/";

    private interface Parameter
    {
        String NAME = "name";
        String JVM_FLAGS = "jvmFlags";
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
                atts.addAttribute(URI, Parameter.JVM_FLAGS, Parameter.JVM_FLAGS, "CDATA", testSuiteResult.getJVMflags());
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

    /**
     * Escape CSV fields according to RFC 4180.
     * @param data
     * @param sep separator e.g. comma, semicolon
     * @return escaped data
     */
    private String escapeCSVField(String data, String sep)
    {
        boolean hasSeparator = data.indexOf(sep) >= 0;
        boolean hasQuote = data.indexOf('"') >= 0;
        boolean hasNewLine = data.indexOf('\n') >= 0 || data.indexOf('\r') >= 0 && data.indexOf('\f') >= 0;

        if (hasSeparator || hasQuote || hasNewLine)
        {
            if (hasQuote)
            {
                data = data.replace("\"", "\"\""); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return "\"" + data + "\""; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return data;
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
                            .append("Build Version").append(RegTestUtils.SEPARATOR) //
                            .append("JVM flags").append(RegTestUtils.SEPARATOR) //
                            .append("Used memory").append(RegTestUtils.SEPARATOR) //
                            .append("Free memory").append(RegTestUtils.SEPARATOR) //
                            .append("Total memory").append(RegTestUtils.SEPARATOR) //
                            .append("Maximum memory") //
                            .append("\n");

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
                    out.append(escapeCSVField(relativePath, RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(record.getTestName(), RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(date, RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(record.getTime(), RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(buildId, RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(result.getJVMflags(), RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(record.getUsedMem(), RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(record.getFreeMem(), RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(record.getTotalMem(), RegTestUtils.SEPARATOR)).append(RegTestUtils.SEPARATOR) //
                       .append(escapeCSVField(record.getMaxMem(), RegTestUtils.SEPARATOR)) //
                       .append("\n");
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

        System.out.println(MessageUtil.format("Saved performance data to {0}", report.getAbsolutePath()));
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
                    String errorMessage = MessageUtil.format(
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
                    String message = MessageUtil.format("Failed removing file {0} from the file system", //
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
        System.out.println("Unzip: unzipping test result file " + resultsFileName);

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
            String message = "ERROR: Failed copying test results file " + resultsFileName
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
            System.err.println(MessageUtil.format("ERROR: Exit Status {0}", status));
            throw new IOException(MessageUtil.format("Parsing finished with exit status {0}.\nOutput:\n{1}\n\n {2}", //
                            status, outputGobbler.getMessage(), errorGobbler.getMessage()));
        }
        // extract parsing time
        if (extractTime)
        {
            Pattern pattern = Pattern.compile("Task: (.*) ([0-9]+) ms used ([0-9]+) free ([0-9]+) total ([0-9]+) max ([0-9]+)");
            for (String line : outputGobbler.getLines())
            {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches())
                    result.addPerfData(new PerfData(matcher.group(1), matcher.group(2), 
                        matcher.group(3), matcher.group(4), matcher.group(5), matcher.group(6)));
            }
        }
    }
}

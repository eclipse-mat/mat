/*******************************************************************************
 * Copyright (c) 2010,2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *    Andrew Johnson - test class specific name for Strings etc.
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.collection.IsEmptyCollection.emptyCollectionOf;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.internal.snapshot.SnapshotQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.registry.QueryObjectLink;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.GCRootInfo.Type;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(value = Parameterized.class)
public class GeneralSnapshotTests
{
    enum Methods {
        NONE,
        FRAMES_ONLY,
        RUNNING_METHODS,
        ALL_METHODS
    }
    final Methods hasMethods;
    enum Stacks {
        NONE,
        FRAMES,
        FRAMES_AND_OBJECTS
    };
    final Stacks stackInfo;

    @Parameters(name="{index}: Snapshot={0} options={1}")
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
            {TestSnapshots.SUN_JDK6_32BIT, Stacks.NONE},
            {TestSnapshots.SUN_JDK5_64BIT, Stacks.NONE},
            {TestSnapshots.SUN_JDK6_18_32BIT, Stacks.FRAMES_AND_OBJECTS},
            {TestSnapshots.SUN_JDK6_18_64BIT, Stacks.FRAMES_AND_OBJECTS},
            {TestSnapshots.SUN_JDK5_13_32BIT, Stacks.NONE},
            {TestSnapshots.IBM_JDK6_32BIT_HEAP, Stacks.NONE},
            {TestSnapshots.IBM_JDK6_32BIT_JAVA, Stacks.FRAMES},
            {TestSnapshots.IBM_JDK6_32BIT_HEAP_AND_JAVA, Stacks.FRAMES},
            {TestSnapshots.IBM_JDK6_32BIT_SYSTEM, Stacks.FRAMES_AND_OBJECTS},
            {"allMethods", Stacks.FRAMES_AND_OBJECTS},
            {"runningMethods", Stacks.FRAMES_AND_OBJECTS},
            {"framesOnly", Stacks.FRAMES_AND_OBJECTS},
            {"noMethods", Stacks.FRAMES_AND_OBJECTS},
            {TestSnapshots.IBM_JDK142_32BIT_HEAP, Stacks.NONE},
            {TestSnapshots.IBM_JDK142_32BIT_JAVA, Stacks.FRAMES},
            {TestSnapshots.IBM_JDK142_32BIT_HEAP_AND_JAVA, TestSnapshots.DTFJreadJavacore142 ? Stacks.FRAMES : Stacks.NONE},
            {TestSnapshots.IBM_JDK142_32BIT_SYSTEM, Stacks.FRAMES},
            {TestSnapshots.ORACLE_JDK7_21_64BIT, Stacks.FRAMES_AND_OBJECTS},
            {TestSnapshots.ORACLE_JDK8_05_64BIT, Stacks.FRAMES_AND_OBJECTS},
            {TestSnapshots.ORACLE_JDK9_01_64BIT, Stacks.FRAMES_AND_OBJECTS},
            {TestSnapshots.ADOPTOPENJDK_HOTSPOT_JDK11_0_4_11_64BIT, Stacks.FRAMES_AND_OBJECTS},
        });
    }

    public GeneralSnapshotTests(String snapshotname, Stacks s)
    {
        if (snapshotname.equals("allMethods")) {
            snapshot = snapshot2(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, "all");
            hasMethods = Methods.ALL_METHODS;
        }
        else if (snapshotname.equals("runningMethods")) {
            snapshot = snapshot2(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, "running");
            hasMethods = Methods.RUNNING_METHODS;
        }
        else if (snapshotname.equals("framesOnly")) {
            snapshot = snapshot2(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, "frames");
            hasMethods = Methods.FRAMES_ONLY;
        }
        else if (snapshotname.equals("noMethods")) {
            snapshot = snapshot2(TestSnapshots.IBM_JDK6_32BIT_SYSTEM, "none");
            hasMethods = Methods.NONE;
        }
        else
        {
            snapshot = TestSnapshots.getSnapshot(snapshotname, false);
            hasMethods = Methods.NONE;
        }
        stackInfo = s;
    }

    /**
     * Create a snapshot with the methods as classes option
     */
    public ISnapshot snapshot2(String snapshotname, String includeMethods)
    {
        final String dtfjPlugin = "org.eclipse.mat.dtfj";
        final String key = "methodsAsClasses";
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(dtfjPlugin);
        String prev = preferences.get(key, null);
        preferences.put(key, includeMethods);
        try {
            // Tag the snapshot name so we don't end up with the wrong version
            ISnapshot ret = TestSnapshots.getSnapshot(snapshotname+";#"+includeMethods, false);
            return ret;
        } finally {
            if (prev != null)
                preferences.put(key, prev);
            else
                preferences.remove(key);
        }
    }

    final ISnapshot snapshot;

    @Test
    public void stacks1() throws SnapshotException
    {
        int frames = 0;
        int foundTop = 0;
        int foundNotTop = 0;
        SetInt objs = new SetInt();
        Collection<IClass>tClasses = snapshot.getClassesByName("java.lang.Thread", true);
        if (tClasses != null) for (IClass thrdcls : tClasses)
        {
            for (int o : thrdcls.getObjectIds())
            {
                objs.add(o);
            }
        }
        /*
         *  PHD+javacore sometimes doesn't mark javacore threads as type Thread as
         *  javacore thread id is not a real object id.
         */
        for (int o : snapshot.getGCRoots())
        {
            for (GCRootInfo g : snapshot.getGCRootInfo(o)) {
                if (g.getType() == Type.THREAD_OBJ) {
                    objs.add(o);
                }
            }
        }
        for (int o : objs.toArray())
        {
            IThreadStack stk = snapshot.getThreadStack(o);
            if (stk != null)
            {
                int i = 0;
                for (IStackFrame frm : stk.getStackFrames())
                {
                    int os[] = frm.getLocalObjectsIds();
                    if (os != null)
                    {
                        if (i == 0)
                            foundTop += os.length;
                        else
                            foundNotTop += os.length;
                    }
                    ++i;
                    ++frames;
                }
            }
        }
        // If there were some frames, and some frames had some objects
        // then a topmost frame should have some objects
        if (frames > 0 && foundNotTop > 0)
        {
            assertTrue("Expected some objects on top of stack", foundTop > 0);
        }
        if (this.stackInfo != Stacks.NONE)
        {
            assertTrue(frames > 0);
            if (this.stackInfo == Stacks.FRAMES_AND_OBJECTS)
                assertTrue(foundNotTop > 0 || foundTop > 0);
        }
    }

    @Test
    public void totalClasses() throws SnapshotException
    {
        int nc = snapshot.getClasses().size();
        int n = snapshot.getSnapshotInfo().getNumberOfClasses();
        assertEquals("Total classes", n, nc);
    }

    @Test
    public void totalObjects() throws SnapshotException
    {
        int no = 0;
        for (IClass cls : snapshot.getClasses())
        {
            no += cls.getNumberOfObjects();
        }
        int n = snapshot.getSnapshotInfo().getNumberOfObjects();
        assertEquals("Total objects", n, no);
    }

    @Test
    public void totalHeapSize() throws SnapshotException
    {
        long total = 0;
        for (IClass cls : snapshot.getClasses())
        {
            total += snapshot.getHeapSize(cls.getObjectIds());
        }
        long n = snapshot.getSnapshotInfo().getUsedHeapSize();
        assertEquals("Total heap size", n, total);
    }

    @Test
    public void objectSizes() throws SnapshotException
    {
        long total = 0;
        for (IClass cls : snapshot.getClasses())
        {
            long prev = -1;
            for (int o : cls.getObjectIds())
            {
                IObject obj = snapshot.getObject(o);
                long n = obj.getUsedHeapSize();
                long n2 = snapshot.getHeapSize(o);
                if (n != n2)
                {
                    assertEquals("snapshot object heap size / object heap size "+obj, n, n2);
                }
                total += n;
                if (prev >= 0)
                {
                    if (prev != n && !cls.isArrayType() && !(obj instanceof IClass))
                    {
                        // This might not be a problem as variable sized plain objects
                        // are now permitted using the array index to record the alternative sizes.
                        // However, the current dumps don't appear to have them, so test for it here.
                        // Future dumps may make this test fail.
                        assertEquals("Variable size plain objects " + cls + " " + obj, prev, n);
                    }
                }
                else if (!(obj instanceof IClass))
                {
                    // IClass objects are variably sized, so don't track those
                    prev = n;
                }
                assertEquals("All instance of a class must be of that type", cls, obj.getClazz());
            }
        }
        long n = snapshot.getSnapshotInfo().getUsedHeapSize();
        assertEquals("Total heap size", n, total);
    }

    @Test
    public void topComponents() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.lookup("component_report_top", snapshot);
        query.setArgument("aggressive", true);
        IResult result = query.execute(new CheckedProgressListener(collector));
        assertTrue(result != null);
    }


    @Test
    public void topReferenceLeak() throws SnapshotException
    {
        SnapshotQuery query = SnapshotQuery.parse("reference_leak java.lang.ref.WeakReference -include_subclasses -maxpaths 10 -factor 0.2", snapshot);
        IResult result = query.execute(new CheckedProgressListener(collector));
        assertTrue(result != null);
        if (result instanceof CompositeResult)
        {
            CompositeResult r = (CompositeResult)result;
            // Check each of the subresults
            for (CompositeResult.Entry e : ((CompositeResult) result).getResultEntries())
            {
                IResult r2 = e.getResult();
                assertNotNull(r2);
                // Check the trees have some selected rows and some are expanded
                if (r2 instanceof IResultTree)
                {
                    assertNotNull(e.getName());
                    IResultTree rt = (IResultTree)r2;
                    assertThat(rt.getElements().size(), greaterThan(0));
                    int selected = 0;
                    int expanded = 0;
                    for (Object o : rt.getElements())
                    {
                        if (rt instanceof ISelectionProvider)
                        {
                            ISelectionProvider ss = (ISelectionProvider)rt;
                            if (ss.isSelected(o))
                                ++selected;
                            if (ss.isExpanded(o))
                                ++expanded;
                        }
                        if (!rt.hasChildren(o))
                            break;
                        while (rt.hasChildren(o))
                        {
                            if (rt instanceof ISelectionProvider)
                            {
                                ISelectionProvider ss = (ISelectionProvider)rt;
                                if (ss.isSelected(o))
                                    ++selected;
                                if (ss.isExpanded(o))
                                    ++expanded;
                            }
                            // Has children, but zero of them?
                            if (rt.getChildren(o).size() == 0)
                                break;
                            o = rt.getChildren(o).get(0);
                        }
                    }
                    assertThat("selected", selected, greaterThan(0));
                    assertThat("expanded", expanded, greaterThan(0));
                }
            }
        }
    }

    @Test
    public void testMethods() throws SnapshotException
    {
        int methods = 0;
        int methodsWithObjects = 0;
        for (IClass cls : snapshot.getClasses())
        {
            if (cls.getName().contains("(") || cls.getName().equals("<stack frame>"))
            {
                ++methods;
                if (cls.getObjectIds().length > 0)
                    ++methodsWithObjects;
            }
        }
        if (hasMethods == Methods.ALL_METHODS)
        {
            assertTrue(methods > 0);
            assertTrue(methods > methodsWithObjects);
        }
        else if (hasMethods == Methods.RUNNING_METHODS)
        {
            assertTrue(methods > 0);
            assertEquals(methods, methodsWithObjects);
        }
        else if (hasMethods == Methods.FRAMES_ONLY)
        {
            assertEquals(1, methods);
            assertThat(methodsWithObjects, greaterThan(0));
        }
        else
        {
            assertEquals(0, methodsWithObjects);
            assertEquals(0, methods);
        }
    }

    @Test
    public void testClassLoaders() throws SnapshotException
    {
        assertThat(snapshot.getSnapshotInfo().getNumberOfClassLoaders(), greaterThan(1));
    }

    @Test
    public void testRegressionReport() throws SnapshotException, IOException
    {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.tests:regression", snapshot);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        checkHTMLResult(t);
    }

    @Test
    public void testPerformanceReport() throws SnapshotException, IOException
    {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.tests:performance", snapshot);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        checkHTMLResult(t);
    }

    @Test
    public void testLeakSuspectsReport() throws SnapshotException, IOException
    {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:suspects", snapshot);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        checkHTMLResult(t);
    }

    @Test
    public void testOverviewReport() throws SnapshotException, IOException
    {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:overview", snapshot);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        checkHTMLResult(t);
    }

    @Test
    public void testTopComponentsReport() throws SnapshotException, IOException
    {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:top_components", snapshot);
        IResult t = query.execute(new CheckedProgressListener(collector));
        assertNotNull(t);
        checkHTMLResult(t);
    }

    @Rule
    public ErrorCollector collector = new ErrorCollector();
    static class CheckedProgressListener extends VoidProgressListener
    {
        ErrorCollector collector = new ErrorCollector();
        public CheckedProgressListener(ErrorCollector collector)
        {
            this.collector = collector;
        }
        public void sendUserMessage(Severity severity, String message, Throwable exception)
        {
            if (exception != null && severity != Severity.INFO)
                collector.addError(exception);
            collector.checkThat(message, severity, lessThan(Severity.WARNING));
        }
    }; 

    @Test
    public void testAllQueriesReport() throws SnapshotException, IOException
    {
        IProgressListener checkListener = new CheckedProgressListener(collector);
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.tests:all", snapshot);
        IResult t = query.execute(checkListener);
        assertNotNull(t);
        checkHTMLResult(t);
    }

    public void checkHTMLResult(IResult r) throws IOException, SnapshotException
    {
        assertThat(r, instanceOf(DisplayFileResult.class));
        if (r instanceof DisplayFileResult)
        {
            DisplayFileResult d = (DisplayFileResult)r;
            File f = d.getFile();
            checkHTMLFile(f);
        }
    }

    /**
     * Recursively check an HTML file.
     * @param f
     * @throws IOException
     */
    public void checkHTMLFile(File f) throws IOException, SnapshotException
    {
        Set<File>seen = new HashSet<File>();
        checkHTMLFile(f, seen);
    }

    /**
     * Recursively check an HTML file, avoiding going
     * into files already seen.
     * @param f
     * @param seen Files already seen
     * @throws IOException
     */
    public void checkHTMLFile(File f, Set<File>seen) throws IOException, SnapshotException
    {
        // canonical needed to avoid problems with ..
        if (!seen.add(f.getCanonicalFile()))
            return;
        FileInputStream fis = new FileInputStream(f);
        try 
        {
            if (!f.getName().endsWith(".html"))
            {
                // Not HTML
                return;
            }
            String encoding = System.getProperty("file.encoding", "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
            try
            {
                // Convert to canonical form
                encoding = Charset.forName(encoding).name();
            }
            catch (IllegalCharsetNameException e)
            {
                // Ignore
            }
            // Read the file into a string
            InputStreamReader ir = new InputStreamReader(fis, encoding);
            char cbuf[] = new char[(int)f.length()];
            int l = ir.read(cbuf);
            String s = new String(cbuf, 0, l);

            /*
             *  All these checks are approximate and would be confused
             *  by false tags in attribute value string etc.
             */

            // Some basic checks
            assertThat("Expected charset", s, containsString("content=\"text/html;charset=" + encoding + "\""));
            assertThat("Possible double escaping <", s, not(containsString("&amp;lt;")));
            assertThat("Possible double escaping &", s, not(containsString("&amp;amp;")));

            /*
             * Rough test for bad tag - might indicate unescaped '<'.
             * Find a less-than sign
             *  Negative lookahead for:
             *   optional / or !
             *   series of letters
             *   then optional digits
             *   ending with a space or greater-than
             *   or !DOCTYPE
             *  then match all until next space or greater-than
             * We normally have lower case tags and no self-closed tags.
             */
            Pattern p = Pattern.compile("<(?!(/?[a-z]+[0-9]*)[ >]|!DOCTYPE )[^ >]*");
            Matcher m = p.matcher(s);
            String v;
            if (m.find())
            {
                v = m.group(0);
            }
            else
            {
                v = null;
            }
            assertThat("Bad tag in "+f, v, equalTo(null));

            /*
             * Rough test for bad entity or unescaped ampersand.
             * Negative lookahead for
             * entity name followed by semicolon
             * entity number preceded by # followed by semicolon 
             */
            p = Pattern.compile("&(?!([a-z]+;)|(#[0-9]+;))[^a-z#]+");
            m = p.matcher(s);
            if (m.find())
            {
                v = m.group(0);
            }
            else
            {
                v = null;
            }
            assertThat("Bad entity in "+f, v, equalTo(null));

            /*
             * Check for alt text for images.
             */
            p = Pattern.compile("<img (?![^>]*alt)[^>]*>");
            m = p.matcher(s);
            if (m.find())
            {
                v = m.group(0);
            }
            else
            {
                v = null;
            }
            assertThat("No alt for img in "+f, v, equalTo(null));

            /*
             * Rough check for nesting of tags.
             */
            Stack<String> stk = new Stack<String>();
            Stack<Integer> stki = new Stack<Integer>();
            // Matches tags
            p = Pattern.compile("</?[a-z]+[1-6]?");
            m = p.matcher(s);
            while (m.find())
            {
                String tag = m.group().substring(1);
                if (tag.startsWith("/"))
                {
                    // Closing tag
                    assertThat("Stack for "+tag, stk.size(), greaterThan(0));
                    tag = tag.substring(1);
                    String tag2 = stk.pop();
                    int si = stki.pop();
                    if (tag2.equals("p") && !tag.equals("a") && !tag.equals("p"))
                    {
                        // <p> closed by any outer tag except <a>
                        tag2 = stk.pop();
                        si = stki.pop();
                        assertThat("Stack for "+tag, stk.size(), greaterThan(0));
                    }
                    String range = s.substring(si, m.end());
                    assertThat("Tag closing at " + m.start()+" "+range+" "+f, tag, equalTo(tag2));
                }
                else
                {
                    // Self closing tag?
                    if (!(tag.equals("br") 
                                    || tag.equals("hr")
                                    || tag.equals("img")
                                    || tag.equals("link")
                                    || tag.equals("input")
                                    || tag.equals("meta")
                                    || tag.equals("area")))
                    {
                        // <p> is closed by following block tag
                        if (stk.size() >= 1 && stk.peek().equals("p") && (
                                        tag.equals("h1") ||
                                        tag.equals("h2") ||
                                        tag.equals("h3") ||
                                        tag.equals("h4") ||
                                        tag.equals("h5") ||
                                        tag.equals("h6") ||
                                        tag.equals("pre") ||
                                        tag.equals("ol") ||
                                        tag.equals("ul") ||
                                        tag.equals("div")))
                        {
                            // Close the <p> tag
                            stk.pop();
                            stki.pop();
                        }
                        stk.push(tag);
                        stki.push(m.start());
                    }
                }
            }
            assertThat("Stack should be empty", stk.size(), equalTo(0));

            // Look for references to other files
            for (int i = 0; i >= 0; )
            {
                String match = "href=\"";
                i = s.indexOf(match, i);
                if (i >= 0)
                {
                    int j = s.indexOf("\"", i + match.length());
                    String fn = s.substring(i + match.length(), j);
                    if (!fn.startsWith("/") && !fn.contains("#") && !fn.startsWith("http") && !fn.startsWith(QueryObjectLink.PROTOCOL))
                    {
                        File d = f.getParentFile();
                        File newf = new File(d, fn);
                        checkHTMLFile(newf, seen);
                    }
                    else if (fn.startsWith(QueryObjectLink.PROTOCOL))
                    {
                        QueryObjectLink link = QueryObjectLink.parse(fn);
                        if (link.getType() == QueryObjectLink.Type.OBJECT)
                        {
                            String t = link.getTarget();
                            assertNotNull(fn, t);
                            SnapshotQueryContext sc = new SnapshotQueryContext(snapshot);
                            int id = sc.mapToObjectId(t);
                        }
                        else if (link.getType() == QueryObjectLink.Type.QUERY)
                        {
                            String t = link.getTarget();
                            assertNotNull(fn, t);
                            SnapshotQuery q = SnapshotQuery.parse(t, snapshot);
                            String cmdname = q.getDescriptor().getIdentifier();
                            IResult r = q.execute(new CheckedProgressListener(collector));
                            if ((cmdname.equals("system_properties") || cmdname.equals("thread_overview")|| cmdname.equals("finalizer_thread"))
                                            && (snapshot.getSnapshotInfo().getProperty("$heapFormat").equals("DTFJ-PHD")
                                                            || snapshot.getSnapshotInfo().getProperty("$heapFormat")
                                                                            .equals("DTFJ-Javacore")))
                            {
                                // Might not return a result for PHD but
                                // shouldn't fail
                            }
                            else
                            {
                                assertNotNull(t, r);
                            }
                        }
                        else if (link.getType() == QueryObjectLink.Type.DETAIL_RESULT)
                        {
                            String t = link.getTarget();
                            assertNotNull(fn, t);
                        }
                        else
                        {
                            assertTrue("Unexpected link type "+link.getType()+" "+fn, false);
                        }
                    }
                    i = j;
                }
            }
            ir.close();
        }
        finally
        {
            fis.close();
        }
    }

    @Test
    public void listEntries() throws SnapshotException
    {
        Collection<IClass>tClasses = snapshot.getClassesByName("java.util.AbstractMap", true);
        if (tClasses != null) for (IClass thrdcls : tClasses)
        {
            for (int o : thrdcls.getObjectIds())
            {
                SnapshotQuery query = SnapshotQuery.parse("extract_list_values 0x"+Long.toHexString(snapshot.mapIdToAddress(o)), snapshot);
                try {
                    IResult t = query.execute(new CheckedProgressListener(collector));
                    assertNotNull(t);
                } catch (IllegalArgumentException e) {
                }
            }
        }
    }

    /**
     * Test exporting as HPROF
     * @param compress whether to compress the generated HPROF file
     * @throws SnapshotException
     * @throws IOException
     */
    public void exportHPROF(boolean compress, boolean redact, long segsize) throws SnapshotException, IOException
    {
        // Currently can't export PHD
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        // Currently can't export methods as classes properly
        assumeThat(hasMethods, equalTo(Methods.NONE));
        // HPROF parser can't handle adding links from classloader to classes for javacore
        File fn = new File(snapshot.getSnapshotInfo().getPrefix());
        assumeThat(fn.getName(), not(containsString("javacore")));
        File tmpdir = TestSnapshots.createGeneratedName(fn.getName(), null);
        File newSnapshotFile = new File(tmpdir, fn.getName() + (compress ? "hprof.gz" : "hprof"));
        File mapping = redact ? new File(tmpdir, fn.getName() + "_mapping.properties") : null;
        try {
            SnapshotQuery query = SnapshotQuery.parse("export_hprof -output "+newSnapshotFile.getPath() +
                            (compress ? " -compress" : "") +
                            (mapping != null ? " -redact NAMES -map "+mapping.getPath() : "") +
                            (segsize > 0 ? " -segsize "+segsize : ""), snapshot);
            IResult t = query.execute(new CheckedProgressListener(collector));
            assertNotNull(t);
            ISnapshot newSnapshot = SnapshotFactory.openSnapshot(newSnapshotFile, Collections.<String,String>emptyMap(), new VoidProgressListener());
            try {
                assertEquals("Snapshot prefix filename", new File(tmpdir, fn.getName()).getName(), new File(newSnapshot.getSnapshotInfo().getPrefix()).getName());
                SnapshotInfo oldInfo = snapshot.getSnapshotInfo();
                SnapshotInfo newInfo = newSnapshot.getSnapshotInfo();
                assertEquals("Classes", oldInfo.getNumberOfClasses(), newInfo.getNumberOfClasses());
                assertEquals("Objects", oldInfo.getNumberOfObjects(), newInfo.getNumberOfObjects());
                assertEquals("Classloaders", oldInfo.getNumberOfClassLoaders(), newInfo.getNumberOfClassLoaders());
                // Check number of (non-array) system classes not marked as GC root
                IObject boot = snapshot.getObject(0);
                int bootcls = 0;
                int systemclsroot = 0;
                if (boot instanceof IClassLoader)
                {
                    IClassLoader bootldr = ((IClassLoader)boot);
                    if (bootldr.getObjectAddress() == 0)
                    {
                        for (IClass cl : bootldr.getDefinedClasses())
                        {
                            if (cl.isArrayType())
                                continue;
                            ++bootcls;
                            GCRootInfo g[] = snapshot.getGCRootInfo(cl.getObjectId());
                            if (g != null && g.length >= 1)
                            {
                                // The class is a root for some reason
                                ++systemclsroot;
                            }
                        }
                    }
                }
                // Parsing new HPROF will make all classes loaded by boot loader as GC roots, so adjust the expected total
                // Only seems to apply for IBM 1.4.2 SDFF dumps with 'double', 'long' classes not as system class roots
                assertEquals("GC Roots", oldInfo.getNumberOfGCRoots() + (bootcls - systemclsroot), newInfo.getNumberOfGCRoots());
                if (redact)
                {
                    // Check redaction
                    String excluded = "java\\..*|(boolean|byte|char|short|int|long|float|double|void)(\\[\\])*";
                    for (IClass cl : snapshot.getClasses())
                    {
                        if (cl.getName().matches(excluded))
                            continue;
                        // Should not be any classes which match the original snapshot, apart from the excluded ones above
                        Collection<IClass>classes = newSnapshot.getClassesByName(cl.getName(), false);
                        assertThat("Class matching original snapshot", classes, anyOf(emptyCollectionOf(IClass.class), nullValue()));
                    }
                    // Check that no byte arrays match the old class names
                    Collection<IClass>cl1 = newSnapshot.getClassesByName("byte[]", false);
                    int nonnull1 = 0;
                    if (cl1 != null)
                    {
                        for (IClass c : cl1)
                        {
                            for (int o : c.getObjectIds())
                            {
                                IObject io = newSnapshot.getObject(o);
                                String name = io.getClassSpecificName();
                                if (name != null && !name.matches(excluded))
                                {
                                    Collection<IClass>classes = snapshot.getClassesByName(name, false);
                                    assertThat("byte[] matching classes in original snapshot", classes, anyOf(emptyCollectionOf(IClass.class), nullValue()));
                                }
                                if (name != null && !name.matches("\\.+"))
                                    ++nonnull1;
                            }
                        }
                    }
                    assertThat("Should be a non-empty byte[] somewhere", nonnull1, greaterThan(0));
                    // Check that no char arrays match the old class names
                    Collection<IClass>cl2 = newSnapshot.getClassesByName("char[]", false);
                    int nonnull2 = 0;
                    if (cl2 != null)
                    {
                        for (IClass c : cl2)
                        {
                            for (int o : c.getObjectIds())
                            {
                                IObject io = newSnapshot.getObject(o);
                                String name = io.getClassSpecificName();
                                if (name != null && !name.matches(excluded))
                                {
                                    Collection<IClass>classes = snapshot.getClassesByName(name, false);
                                    assertThat("char[] matching classes in original snapshot", classes, anyOf(emptyCollectionOf(IClass.class), nullValue()));
                                }
                                if (name != null && !name.matches("(\\\\u0000)+"))
                                    ++nonnull2;
                            }
                        }
                    }
                    assertThat("Should be a non-empty char[] somewhere", nonnull2, greaterThan(0));
                    File newSnapshotFile2 = File.createTempFile(fn.getName(), (compress ? ".hprof.gz" : ".hprof"), tmpdir);

                    // Try reversing the mapping, check the classes come back with the expected names
                    SnapshotQuery query2 = SnapshotQuery.parse("export_hprof -output "+newSnapshotFile2.getPath() +
                                    (compress ? " -compress" : "") +
                                    (mapping != null ? " -redact NONE -undo -map "+mapping.getPath() : "") +
                                    (segsize > 0 ? " -segsize "+segsize : ""), newSnapshot);
                    IResult t2 = query2.execute(new CheckedProgressListener(collector));
                    assertNotNull(t2);
                    ISnapshot newSnapshot2 = SnapshotFactory.openSnapshot(newSnapshotFile, Collections.<String,String>emptyMap(), new CheckedProgressListener(collector));
                    try {
                        for (IClass cl : snapshot.getClasses())
                        {
                            // Should be all the classes in the original snapshot
                            Collection<IClass>classes = newSnapshot2.getClassesByName(cl.getName(), false);
                            assertThat("Class matching original snapshot "+cl.getName(), classes, not(emptyCollectionOf(IClass.class)));
                        }
                    } finally {
                        SnapshotFactory.dispose(newSnapshot2);
                    }
                }
            } finally {
                SnapshotFactory.dispose(newSnapshot);
            }
        } finally {
            newSnapshotFile.delete();
            if (mapping != null)
                mapping.delete();
        }
    }

    /**
     * Test exporting as HPROF
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void exportHPROF() throws SnapshotException, IOException
    {
        exportHPROF(false, false, 0);
    }

    /**
     * Test exporting as HPROF
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void exportHPROFredact() throws SnapshotException, IOException
    {
        exportHPROF(false, true, 0);
    }

    /**
     * Test exporting as compressed HPROF
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void exportHPROFCompress() throws SnapshotException, IOException
    {
        exportHPROF(true, false, 0);
    }

    /**
     * Test exporting as HPROF
     * with small HPROF Heap Dump Segments
     * to test segment processing.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void exportHPROFSegments() throws SnapshotException, IOException
    {
        exportHPROF(false, false, 1000000);
    }

    /**
     * Test value of {@link java.lang.String}
     */
    @Test
    public void stringToString() throws SnapshotException
    {
        int objects = 0;
        int printables = 0;
        int escaped = 0;
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        Collection<IClass>tClasses = snapshot.getClassesByName("java.lang.String", true);
        for (IClass cls : tClasses)
        {
            for (int id : cls.getObjectIds()) {
                IObject o = snapshot.getObject(id);
                ++objects;
                String cn = o.getClassSpecificName();
                if (cn != null && cn.length() > 0)
                {
                    ++printables;
                    if (cn.matches(".*\\\\u[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f].*"))
                    {
                        escaped++;
                    }
                }
            }
        }
        // Check most ofthe strings are printable
        assertThat(printables, greaterThanOrEqualTo(objects * 2/ 3));
        // Check for at least one escape character if there are any Strings
        assertThat(escaped, either(greaterThan(0)).or(equalTo(objects)));
    }

    /**
     * Test value of {@link java.lang.StringBuilder}
     */
    @Test
    public void stringBuilderToString() throws SnapshotException
    {
        int objects = 0;
        int printables = 0;
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        Collection<IClass>tClasses = snapshot.getClassesByName("java.lang.StringBuilder", true);
        if (tClasses != null) for (IClass cls : tClasses)
        {
            for (int id : cls.getObjectIds()) {
                IObject o = snapshot.getObject(id);
                String cn = o.getClassSpecificName();
                if (cn != null && cn.length() > 0)
                {
                    ++printables;
                }
            }
        }
        assertThat(printables, greaterThanOrEqualTo(objects * 2 / 3));
    }

    /**
     * Test value of {@link java.lang.StringBuffer}
     */
    @Test
    public void stringBufferToString() throws SnapshotException
    {
        int objects = 0;
        int printables = 0;
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        Collection<IClass>tClasses = snapshot.getClassesByName("java.lang.StringBuffer", true);
        for (IClass cls : tClasses)
        {
            for (int id : cls.getObjectIds()) {
                IObject o = snapshot.getObject(id);
                String cn = o.getClassSpecificName();
                if (cn != null && cn.length() > 0)
                {
                    ++printables;
                }
            }
        }
        assertThat(printables, greaterThanOrEqualTo(objects * 2 / 3));
    }

    /**
     * Test value of {@link java.lang.StackTraceElement}
     */
    @Test
    public void stackFrameElementResolver() throws SnapshotException
    {
        int objects = 0;
        int printables = 0;
        assumeThat(snapshot.getSnapshotInfo().getProperty("$heapFormat"), not(equalTo((Serializable)"DTFJ-PHD")));
        Collection<IClass>tClasses = snapshot.getClassesByName("java.lang.StackTraceElement", true);
        assumeNotNull(tClasses);
        for (IClass cls : tClasses)
        {
            for (int id : cls.getObjectIds()) {
                IObject o = snapshot.getObject(id);
                String cn = o.getClassSpecificName();
                if (cn != null && cn.length() > 0)
                {
                    ++printables;
                }
            }
        }
        assertThat(printables, greaterThanOrEqualTo(objects * 2 / 3));
    }

    /**
     * Test caching of snapshots
     * @throws SnapshotException
     */
    @Test
    public void reload1() throws SnapshotException
    {
        String path = snapshot.getSnapshotInfo().getPath();
        File file = new File(path);
        ISnapshot sn2 = SnapshotFactory.openSnapshot(file, new CheckedProgressListener(collector));
        try
        {
            ISnapshot sn3 = SnapshotFactory.openSnapshot(file, new CheckedProgressListener(collector));
            try
            {
                assertSame(sn2, sn3);
            }
            finally
            {
                // Do not call ISnapshot.dispose()
                SnapshotFactory.dispose(sn3);
            }
            assertEquals(snapshot.getHeapSize(0), sn3.getHeapSize(0));
        }
        finally
        {
            SnapshotFactory.dispose(sn2);
        }
    }

    /**
     * Test aliasing of two dumps.
     * Find a dump with an absolute path and a relative path which is the same
     * when converted to absolute.
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void reload2() throws SnapshotException, IOException
    {
        // Get a path to a dump
        String path = snapshot.getSnapshotInfo().getPath();
        // Get the absolute path version
        File file1 = (new File(path)).getAbsoluteFile();
        // convert the absolute path to a relative path to the appropriate drive/current directory
        for (File root: File.listRoots()) {
            if (file1.getPath().startsWith(root.getPath())) {
                // Found a root e.g. C:\
                String rootPath = root.getPath();
                // Strip off the slash e.g. C:
                File drive = new File(rootPath.substring(0, rootPath.length() - 1));
                // Find the current directory for the drive e.g. C:\workspace\org.eclipse.mat.tests
                File current = drive.getAbsoluteFile();
                // e.g. C:\workspace
                current = current.getParentFile();
                // e.g. C:
                File newPrefix = drive;

                while (current != null) {
                    if (newPrefix == drive)
                        // e.g. C:..
                        newPrefix = new File(drive.getPath()+"..");
                    else
                        // e.g. C:..\..
                        newPrefix = new File(newPrefix, "..");
                    // e.g. C:\workspace
                    current = current.getParentFile();
                };
                // The relative path
                File newPath = new File(newPrefix, file1.getPath().substring(rootPath.length()));
                // The equivalent absolute path
                File f2 = newPath.getAbsoluteFile();
                /*
                 * Check that the complex path manipulations worked.
                 * This should be true for Windows and Linux.
                 */
                assumeTrue(newPath.exists());
                assumeTrue(f2.exists());
                ISnapshot sn2 = SnapshotFactory.openSnapshot(f2, new CheckedProgressListener(collector));
                try
                {
                    ISnapshot sn3 = SnapshotFactory.openSnapshot(newPath, new CheckedProgressListener(collector));
                    try
                    {
                        assertThat(sn3.getHeapSize(0), greaterThanOrEqualTo(0L));
                        // Do a complex operation which requires the dump still
                        // to be open and alive
                        assertNotNull(sn3.getObject(sn2.getClassOf(0).getClassLoaderId()).getOutboundReferences());
                    }
                    finally
                    {
                        SnapshotFactory.dispose(sn3);
                    }
                    assertThat(sn2.getHeapSize(0), greaterThanOrEqualTo(0L));
                    // Do a complex operation which requires the dump still to be open and alive.
                    assertNotNull(sn2.getObject(sn2.getClassOf(0).getClassLoaderId()).getOutboundReferences());
                }
                finally
                {
                    SnapshotFactory.dispose(sn2);
                }
            }
        }
    }
}

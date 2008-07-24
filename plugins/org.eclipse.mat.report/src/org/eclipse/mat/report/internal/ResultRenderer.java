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
package org.eclipse.mat.report.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.RendererRegistry;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.report.ITestResult.Status;
import org.eclipse.mat.util.FileUtils;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class ResultRenderer
{
    private interface Key
    {
        String IS_EXPANDABLE = "isExpandable";
        String ARTEFACT = "artefact";
    }

    /* package */class HtmlArtefact
    {
        File file;
        PrintWriter writer;

        private HtmlArtefact(HtmlArtefact parent, File file, String title) throws IOException
        {
            this.file = file;
            this.writer = new PrintWriter(file);

            artefacts.add(this);

            PageSnippets.beginPage(parent, this, title);
        }

        public HtmlArtefact append(String s)
        {
            writer.append(s);
            return this;
        }

        public void close()
        {
            try
            {
                PageSnippets.endPage(this);
            }
            catch (IOException ignore)
            {
                // $JL-EXC$ closing file anyway
            }
            finally
            {
                writer.close();
            }
        }

        @Override
        public String toString()
        {
            return file.getAbsolutePath();
        }
    }

    private TestSuite suite;
    private IOutputter html;

    private List<HtmlArtefact> artefacts = new ArrayList<HtmlArtefact>();
    private File directory;

    private Map<URL, String> icon2name = new HashMap<URL, String>();

    private boolean isClockingReportGeneration = false;

    public ResultRenderer()
    {
        html = RendererRegistry.instance().match("html", IResult.class);
    }

    public TestSuite getSuite()
    {
        return suite;
    }

    public void beginSuite(TestSuite suite, AbstractPart part) throws IOException
    {
        this.suite = suite;

        this.isClockingReportGeneration = suite.isClockingReportGeneration();

        prepareTempDirectory();

        File indexFile = new File(directory, "index.html");
        HtmlArtefact index = new HtmlArtefact(null, indexFile, part.spec().getName());
        suite.addResult(indexFile);

        part.putObject(Key.ARTEFACT, index);
    }

    public void endSuite(TestSuite suite, AbstractPart part) throws IOException
    {
        renderTableOfContents(part);

        renderTOCXml(part);

        for (HtmlArtefact artefact : artefacts)
            artefact.close();

        copyIcons();

        zipAndCopy();
    }

    private void copyIcons() throws IOException
    {
        if (!icon2name.isEmpty())
        {
            File iconDir = new File(directory, "icons");
            iconDir.mkdir();

            for (Map.Entry<URL, String> entry : icon2name.entrySet())
                copyResource(entry.getKey(), new File(iconDir, entry.getValue()));
        }
    }

    public void beginSection(SectionPart section) throws IOException
    {
        int order = 1;

        SectionPart p = section;
        while (p.getParent() != null)
        {
            p = p.getParent();
            order++;
        }

        HtmlArtefact srcArtefact = (HtmlArtefact) section.getObject(Key.ARTEFACT);
        if (srcArtefact == null)
            srcArtefact = (HtmlArtefact) section.getParent().getObject(Key.ARTEFACT);

        HtmlArtefact artefact = createNewFileIfNecessary(srcArtefact, section, order);

        // do not create expansion if
        // (a) it is the top-level element
        // (b) it is a new file (e.g. the top-level element for a sub-page)
        if (order == 1 || srcArtefact != artefact)
        {
            PageSnippets.heading(artefact, section, order, false);
        }
        else
        {
            PageSnippets.heading(artefact, section, order, true);
            PageSnippets.beginExpandableDiv(artefact, section);
            section.putObject(Key.IS_EXPANDABLE, true);
        }
    }

    public void endSection(SectionPart section) throws IOException
    {
        if (section.getObject(Key.IS_EXPANDABLE) != null)
        {
            HtmlArtefact artefact = (HtmlArtefact) section.getObject(Key.ARTEFACT);
            PageSnippets.endDiv(artefact);
        }
    }

    public void process(QueryPart test, IResult result, RenderingInfo rInfo) throws IOException
    {
        // determine output formatter
        String format = test.params().get(Params.FORMAT, "html");
        IOutputter outputter = html;

        if (result != null)
        {
            outputter = RendererRegistry.instance().match(format, result.getClass());
            if (outputter == null)
            {
                ReportPlugin.log(IStatus.WARNING, MessageFormat.format(
                                "No outputter found for format ''{0}'' and type ''{1}''", format, result.getClass()
                                                .getName()));
                outputter = html;
            }
        }

        if ("html".equals(format))
            doProcess(outputter, test, result, rInfo, true);
        else
            doProcessAlien(format, outputter, test, result, rInfo);
    }

    private void doProcessAlien(String format, IOutputter outputter, QueryPart test, IResult result, RenderingInfo info)
                    throws IOException
    {
        HtmlArtefact artefact = (HtmlArtefact) test.getObject(Key.ARTEFACT);
        if (artefact == null)
            artefact = (HtmlArtefact) test.getParent().getObject(Key.ARTEFACT);

        String filename = FileUtils.toFilename(test.spec().getName(), format);
        PageSnippets.linkedHeading(artefact, test, 5, filename);

        Writer writer = new FileWriter(new File(this.directory, filename));
        outputter.process(info, result, writer);
        writer.close();
    }

    private void doProcess(IOutputter outputter, QueryPart test, IResult result, RenderingInfo rInfo, boolean firstPass)
                    throws IOException
    {
        HtmlArtefact artefact = (HtmlArtefact) test.getObject(Key.ARTEFACT);
        if (artefact == null)
            artefact = (HtmlArtefact) test.getParent().getObject(Key.ARTEFACT);

        artefact = createNewFileIfNecessary(artefact, test, 5);

        String pattern = test.params().shallow().get(Params.Rendering.PATTERN);
        boolean isOverviewDetailsPattern = firstPass && Params.Rendering.PATTERN_OVERVIEW_DETAILS.equals(pattern);

        if (!isOverviewDetailsPattern)
        {
            PageSnippets.queryHeading(artefact, test);
            PageSnippets.beginExpandableDiv(artefact, test);
        }

        boolean isImportant = test.params().shallow().getBoolean(Params.Html.IS_IMPORTANT, false);
        if (isImportant)
        {
            artefact.append("<div class=\"important\">");
        }

        outputter.embedd(rInfo, result, artefact.writer);

        if (isOverviewDetailsPattern)
        {
            String filename = test.getId() + ".html";

            artefact.append("<div>");
            PageSnippets.link(artefact, filename, "Details &raquo;");
            artefact.append("</div>");

            // create new page for the details elements
            HtmlArtefact details = new HtmlArtefact(artefact, new File(directory, filename), test.getParent().spec()
                            .getName());

            // assign output page to all other children
            for (AbstractPart part : test.getParent().getChildren())
                part.putObject(Key.ARTEFACT, details);

            // process this child again (repeat on details page)
            doProcess(outputter, test, result, rInfo, false);
        }

        if (isImportant)
            artefact.append("</div>");

        if (!isOverviewDetailsPattern)
        {
            PageSnippets.endDiv(artefact);
        }
    }

    // //////////////////////////////////////////////////////////////
    // context interface
    // //////////////////////////////////////////////////////////////

    /* package */boolean isClockingReportGeneration()
    {
        return isClockingReportGeneration;
    }

    public String getRelativeIconLink(URL icon)
    {
        if (icon == null)
            return null;

        String name = icon2name.get(icon);
        if (name == null)
        {
            String f = icon.getFile();
            int p = f.lastIndexOf('.');

            String extension = p < 0 ? f : f.substring(p);
            icon2name.put(icon, name = "i" + icon2name.size() + extension);
        }

        return "icons/" + name;
    }

    public File getOutputDirectory()
    {
        return directory;
    }

    public IQueryContext getQueryContext()
    {
        return suite.getQueryContext();
    }

    // //////////////////////////////////////////////////////////////
    // private parts
    // //////////////////////////////////////////////////////////////

    private void prepareTempDirectory() throws IOException
    {
        directory = FileUtils.createTempDirectory("report", null);

        copyResource("/META-INF/html/styles.css", new File(directory, "styles.css"));
        copyResource("/META-INF/html/code.js", new File(directory, "code.js"));

        File imgDir = new File(directory, "img");
        imgDir.mkdir();
        copyResource("/META-INF/html/img/hide.gif", new File(imgDir, "hide.gif"));
        copyResource("/META-INF/html/img/open.gif", new File(imgDir, "open.gif"));
        copyResource("/META-INF/html/img/success.gif", new File(imgDir, "success.gif"));
        copyResource("/META-INF/html/img/warning.gif", new File(imgDir, "warning.gif"));
        copyResource("/META-INF/html/img/error.gif", new File(imgDir, "error.gif"));
    }

    private void copyResource(String resource, File target) throws FileNotFoundException, IOException
    {
        OutputStream out = new FileOutputStream(target);
        try
        {
            FileUtils.copy(getClass().getResourceAsStream(resource), out);
        }
        finally
        {
            out.close();
        }
    }

    private void copyResource(URL resource, File target) throws FileNotFoundException, IOException
    {
        OutputStream out = new FileOutputStream(target);
        InputStream in = null;
        try
        {
            in = resource.openStream();
            FileUtils.copy(in, out);
        }
        finally
        {
            try
            {
                in.close();
            }
            catch (IOException ignore)
            {
                // $JL-EXC$
            }
            try
            {
                out.close();
            }
            catch (IOException ignore)
            {
                // $JL-EXC$
            }
        }
    }

    private HtmlArtefact createNewFileIfNecessary(HtmlArtefact artefact, AbstractPart part, int order)
                    throws IOException
    {
        boolean isSeparateFile = part.params().shallow().getBoolean(Params.Html.SEPARATE_FILE, false);
        if (isSeparateFile)
        {
            String filename = FileUtils.toFilename(part.spec().getName() + part.getId(), "html");

            PageSnippets.linkedHeading(artefact, part, order, filename);

            artefact = new HtmlArtefact(artefact, new File(directory, filename), part.spec().getName());
        }

        part.putObject(Key.ARTEFACT, artefact);
        return artefact;
    }

    private void renderTableOfContents(AbstractPart part) throws IOException
    {
        HtmlArtefact toc = new HtmlArtefact(null, new File(directory, "toc.html"), "Table Of Contents");

        toc.append("<h1>Table of Contents</h1>\n");

        if (part instanceof SectionPart)
        {
            SectionPart sections = (SectionPart) part;
            renderResult(toc, sections);
        }
    }

    private void renderResult(HtmlArtefact toc, SectionPart sections)
    {
        toc.append("<ul>");
        for (AbstractPart part : sections.getChildren())
        {
            toc.append("<li>");

            if (part.getStatus() != null)
                toc.append("<img src=\"img/").append(part.getStatus().name().toLowerCase() + ".gif\"> ");

            HtmlArtefact page = (HtmlArtefact) part.getObject(Key.ARTEFACT);
            AbstractPart p = part;
            while (page == null)
                page = (HtmlArtefact) (p = p.getParent()).getObject(Key.ARTEFACT);

            if (page != null)
            {
                PageSnippets.beginLink(toc, page.file.getName() + "#" + part.getId());
                toc.append(part.spec().getName());
                PageSnippets.endLink(toc);
            }
            else
            {
                toc.append(part.spec().getName());
            }

            if (part instanceof SectionPart)
                renderResult(toc, (SectionPart) part);

            toc.append("</li>");
        }
        toc.append("</ul>");
    }

    private void renderTOCXml(AbstractPart part) throws IOException
    {
        PrintWriter out = null;

        try
        {
            out = new PrintWriter(new File(directory, "toc.xml"));

            SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
            TransformerHandler handler = tf.newTransformerHandler();

            Transformer xformer = handler.getTransformer();
            xformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            xformer.setOutputProperty(OutputKeys.INDENT, "yes");

            handler.setResult(new StreamResult(out));

            AttributesImpl atts = new AttributesImpl();

            handler.startDocument();
            renderTOCPart(handler, atts, part);
            handler.endDocument();
        }
        catch (TransformerConfigurationException e)
        {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
        catch (IllegalArgumentException e)
        {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
        catch (TransformerFactoryConfigurationError e)
        {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
        catch (SAXException e)
        {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
        finally
        {
            out.flush();
            out.close();
        }
    }

    private void renderTOCPart(TransformerHandler handler, AttributesImpl attrib, AbstractPart part)
                    throws SAXException
    {
        attrib.clear();
        
        String name = part.spec().getName();
        if (name == null)
            name = part.getId();
        attrib.addAttribute("", "", "name", "", name);

        Status status = part.getStatus();
        if (status != null)
            attrib.addAttribute("", "", "status", "", status.name());

        HtmlArtefact page = (HtmlArtefact) part.getObject(Key.ARTEFACT);
        AbstractPart p = part;
        while (page == null)
            page = (HtmlArtefact) (p = p.getParent()).getObject(Key.ARTEFACT);

        if (page != null)
            attrib.addAttribute("", "", "file", "", page.file.getName());

        if (part.queryExecutionTime > 0)
            attrib.addAttribute("", "", "query-time", "", String.valueOf(part.queryExecutionTime));

        if (part.totalExecutionTime > 0)
            attrib.addAttribute("", "", "time", "", String.valueOf(part.totalExecutionTime));

        handler.startElement("", "", "part", attrib);
        if (part instanceof SectionPart)
        {
            for (AbstractPart child : ((SectionPart) part).getChildren())
                renderTOCPart(handler, attrib, child);
        }
        handler.endElement("", "", "part");
    }

    private void zipAndCopy() throws IOException
    {
        File targetZip = suite.getOutput();
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip));

        try
        {
            zipDir(directory.getPath().length() + 1, directory, zos);
        }
        finally
        {
            zos.close();
        }

        suite.addResult(targetZip);
    }

    private void zipDir(int commonPath, File zipDir, ZipOutputStream zos) throws IOException
    {
        String[] dirList = zipDir.list();
        byte[] readBuffer = new byte[2156];
        int bytesIn = 0;

        for (int i = 0; i < dirList.length; i++)
        {
            File f = new File(zipDir, dirList[i]);
            if (f.isDirectory())
            {
                zipDir(commonPath, f, zos);
            }
            else
            {
                FileInputStream fis = new FileInputStream(f);
                try
                {
                    String path = f.getPath().substring(commonPath);
                    ZipEntry anEntry = new ZipEntry(path);
                    zos.putNextEntry(anEntry);
                    while ((bytesIn = fis.read(readBuffer)) != -1)
                    {
                        zos.write(readBuffer, 0, bytesIn);
                    }
                }
                finally
                {
                    fis.close();
                }
            }
        }
    }

}

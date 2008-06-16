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
package org.eclipse.mat.impl.test;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.mat.impl.test.html.FileUtils;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.test.Params;


public class ResultRenderer implements IOutputter.Context
{
    public static class RenderingInfo
    {
        public boolean[] visibleColumns;
        public int limit;
        public boolean showTotals = true;

        public RenderingInfo(int columnCount)
        {
            visibleColumns = new boolean[columnCount];
            for (int ii = 0; ii < visibleColumns.length; ii++)
                visibleColumns[ii] = true;

            limit = 25;
        }

        public boolean hasLimit()
        {
            return limit >= 0;
        }

        public boolean isVisible(int columnIndex)
        {
            return visibleColumns[columnIndex];
        }

        public boolean showTotals()
        {
            return showTotals;
        }
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

    TestSuite suite;
    IOutputter html;

    List<HtmlArtefact> artefacts = new ArrayList<HtmlArtefact>();
    File directory;

    Map<URL, String> icon2name = new HashMap<URL, String>();

    public ResultRenderer()
    {
        html = OutputterRegistry.instance().get("html");
    }

    public TestSuite getSuite()
    {
        return suite;
    }

    public void beginSuite(TestSuite suite) throws IOException
    {
        this.suite = suite;

        prepareTempDirectory();

        File indexFile = new File(directory, "index.html");
        HtmlArtefact index = new HtmlArtefact(null, indexFile, suite.part().spec().getName());
        suite.addResult(indexFile);

        suite.part().putObject(HtmlArtefact.class, index);
    }

    public void endSuite(TestSuite suite) throws IOException
    {
        renderTableOfContents();

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

        HtmlArtefact artefact = section.getObject(HtmlArtefact.class);
        if (artefact == null)
            artefact = section.getParent().getObject(HtmlArtefact.class);

        artefact = createNewFileIfNecessary(artefact, section, order);

        PageSnippets.heading(artefact, section, order);
    }

    public void endSection(SectionPart section) throws IOException
    {}

    public void process(QueryPart test, IResult result, RenderingInfo rInfo) throws IOException
    {
        // determine output formatter
        String type = test.params().get(Params.FORMAT, "html");
        IOutputter outputter = OutputterRegistry.instance().get(type);
        if (outputter == null)
        {
            Logger.getLogger(getClass().getName()).log(Level.WARNING,
                            MessageFormat.format("No outputter found for format ''{0}''", type));
            outputter = html;
        }

        if (outputter == html)
            doProcess(test, result, rInfo, true);
        else
            doProcessAlien(outputter, test, result, rInfo);
    }

    private void doProcessAlien(IOutputter outputter, QueryPart test, IResult result, RenderingInfo info)
                    throws IOException
    {
        HtmlArtefact artefact = test.getObject(HtmlArtefact.class);
        if (artefact == null)
            artefact = test.getParent().getObject(HtmlArtefact.class);

        String filename = FileUtils.toFilename(test.spec().getName(), outputter.getExtension());
        PageSnippets.linkedHeading(artefact, test, 5, filename);

        Writer w = new FileWriter(new File(this.directory, filename));
        outputter.process(this, test, result, info, w);
        w.close();
    }

    private void doProcess(QueryPart test, IResult result, RenderingInfo rInfo, boolean firstPass) throws IOException
    {
        HtmlArtefact artefact = test.getObject(HtmlArtefact.class);
        if (artefact == null)
            artefact = test.getParent().getObject(HtmlArtefact.class);

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

        html.embedd(this, test, result, rInfo, artefact.writer);

        if (isOverviewDetailsPattern)
        {
            String filename = test.getId() + ".html";

            artefact.append("<div>");
            PageSnippets.link(artefact, filename, "Details &raquo;");
            artefact.append("</div>");

            HtmlArtefact details = new HtmlArtefact(artefact, new File(directory, filename), test.getParent().spec()
                            .getName());
            test.getParent().putObject(HtmlArtefact.class, details);
            test.putObject(HtmlArtefact.class, details);
            doProcess(test, result, rInfo, false);
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

    public ISnapshot getSnapshot()
    {
        return suite.getSnapshot();
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

        part.putObject(HtmlArtefact.class, artefact);
        return artefact;
    }

    private void renderTableOfContents() throws IOException
    {
        HtmlArtefact toc = new HtmlArtefact(null, new File(directory, "toc.html"), "Table Of Contents");

        toc.append("<h1>Table of Contents</h1>\n");

        if (suite.part() instanceof SectionPart)
        {
            SectionPart sections = (SectionPart) suite.part();
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

            HtmlArtefact page = part.getObject(HtmlArtefact.class);
            AbstractPart p = part;
            while (page == null)
                page = (p = p.getParent()).getObject(HtmlArtefact.class);

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

    private void zipAndCopy() throws IOException
    {
        File snapshot = new File(suite.getSnapshot().getSnapshotInfo().getPath());

        String prefix = snapshot.getName();
        int p = prefix.lastIndexOf('.');
        if (p >= 0)
            prefix = prefix.substring(0, p);

        File targetDir = snapshot.getParentFile();
        File targetZip = new File(targetDir, prefix + "_" + FileUtils.toFilename(suite.part().spec().getName(), "zip"));

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

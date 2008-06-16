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
package org.eclipse.mat.impl.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SimpleServiceLoader<S> implements Iterable<S>
{
    private static Pattern CLASS_FILE_PATTERN = Pattern.compile(".*\\.class", Pattern.CASE_INSENSITIVE);

    public static <S> SimpleServiceLoader<S> load(File fileOrDirectory, Class<S> service, List<Exception> problems)
                    throws IOException
    {
        SimpleServiceLoader<S> answer = new SimpleServiceLoader<S>(fileOrDirectory);
        answer.findClasses(service, problems);
        return answer;
    }

    public static <S> SimpleServiceLoader<S> load(ClassLoader classLoader, Class<S> service, List<Exception> problems)
                    throws IOException
    {
        SimpleServiceLoader<S> answer = new SimpleServiceLoader<S>(null);
        answer.loadClasses(classLoader, service, problems);
        return answer;
    }

    private File fileOrDirectory;
    private List<S> instances;

    private SimpleServiceLoader(File fileOrDirectory)
    {
        this.fileOrDirectory = fileOrDirectory;
        this.instances = new ArrayList<S>();
    }

    public Iterator<S> iterator()
    {
        return instances.iterator();
    }

    public File getFileOrDirectory()
    {
        return fileOrDirectory;
    }

    // //////////////////////////////////////////////////////////////
    // static helpers
    // //////////////////////////////////////////////////////////////

    private void loadClasses(ClassLoader classLoader, Class<S> service, List<Exception> problems) throws IOException
    {
        InputStream in = classLoader.getResourceAsStream("META-INF/services/" + service.getName());

        instances = new ArrayList<S>();

        if (in != null)
        {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            try
            {
                String line = r.readLine();
                while (line != null)
                {
                    if (line.trim().length() > 0 && line.charAt(0) != '#')
                    {
                        try
                        {
                            Class<?> clazz = this.getClass().getClassLoader().loadClass(line);
                            instances.add(service.cast(clazz.newInstance()));
                        }
                        catch (Exception e)
                        {
                            String msg = "Error while creating ''{0}'' defined in META-INF/services/{1}";
                            problems.add(new Exception(MessageFormat.format(msg, line, service.getName()), e));
                        }
                    }
                    line = r.readLine();
                }
            }
            finally
            {
                try
                {
                    r.close();
                }
                catch (IOException ignore)
                {
                    // $JL-EXC$
                }
            }
        }

    }

    private void findClasses(Class<S> service, List<Exception> problems) throws IOException
    {
        List<String> classNames = new ArrayList<String>();

        if (fileOrDirectory.isDirectory())
            recursivelyListDir(classNames, fileOrDirectory, null);
        else if (isArchiveFile(fileOrDirectory))
            getClassNamesFromArchive(classNames, fileOrDirectory);

        if (classNames.isEmpty())
            return;

        URLClassLoader loader = new URLClassLoader(new URL[] { fileOrDirectory.toURL() }, getClass().getClassLoader());

        for (String className : classNames)
        {
            try
            {
                Class<?> resClass = loader.loadClass(className);
                if (service.isAssignableFrom(resClass))
                {
                    instances.add(service.cast(resClass.newInstance()));
                }
            }
            catch (ClassNotFoundException ignore)
            {
                // $JL-EXC$
            }
            catch (InstantiationException e)
            {
                problems.add(e);
            }
            catch (IllegalAccessException e)
            {
                problems.add(e);
            }
            catch (Exception e)
            {
                problems.add(e);
            }
            catch (Throwable t)
            {
                // $JL-EXC$
                // catch linkage and incomplete compile errors
                problems.add(new Exception(t));
            }
        }

    }

    private boolean isArchiveFile(File file)
    {
        String name = file.getName().toLowerCase();
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private void recursivelyListDir(List<String> classNames, File dir, String packageName)
    {
        for (File file : dir.listFiles())
        {
            String name = file.getName();
            if (file.isDirectory())
                recursivelyListDir(classNames, file, packageName != null ? packageName + "." + name : name);
            else if (CLASS_FILE_PATTERN.matcher(name).matches())
            {
                String className = name.substring(0, name.length() - 6);
                classNames.add(packageName != null ? packageName + "." + className : className);
            }
        }
    }

    private void getClassNamesFromArchive(List<String> classNames, File archive) throws IOException
    {
        ZipFile zip = new ZipFile(archive);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements())
        {
            ZipEntry element = entries.nextElement();
            if (CLASS_FILE_PATTERN.matcher(element.getName()).matches())
            {
                String name = element.getName().replace('/', '.');
                classNames.add(name.substring(0, name.length() - 6));
            }
        }
    }
}

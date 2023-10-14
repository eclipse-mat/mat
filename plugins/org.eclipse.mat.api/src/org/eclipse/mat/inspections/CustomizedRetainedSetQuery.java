/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - charset
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ExcludedReferencesDescriptor;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor;

@CommandName("customized_retained_set")
@Icon("/META-INF/icons/show_retained_set.gif")
@HelpUrl("/org.eclipse.mat.ui.help/concepts/shallowretainedheap.html")
public class CustomizedRetainedSetQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false, flag = "x")
    public String[] excludedReferences;

    @Argument(isMandatory = false, flag = "xfile")
    public File excludedReferencesListFile;

    public IResult execute(IProgressListener listener) throws Exception
    {
        SimpleMonitor monitor = new SimpleMonitor(MessageUtil.format(Messages.CustomizedRetainedSetQuery_QueryName, objects.getLabel()),
                        listener, new int[] { 10, 950, 50 });
        int[] retainedSet;

        if (excludedReferences == null && excludedReferencesListFile == null)
        {
            // normal retained set
            int objs[] = objects.getIds(monitor.nextMonitor());
            retainedSet = snapshot.getRetainedSet(objs, monitor.nextMonitor());
        }
        else
        {
            // retained set with excluded refs
            // read the file (if any)
            String[] fromFile = getLinesFromFile();
            if (fromFile != null && fromFile.length > 0)
            {
                if (excludedReferences != null)
                {
                    // merge from file and manually entered entries
                    String[] tmp = new String[fromFile.length + excludedReferences.length];
                    System.arraycopy(fromFile, 0, tmp, 0, fromFile.length);
                    System.arraycopy(excludedReferences, 0, tmp, fromFile.length, excludedReferences.length);
                    excludedReferences = tmp;
                }
                else
                {
                    excludedReferences = fromFile;
                }
            }
            ExcludedReferencesDescriptor[] excludedRefDescriptors = getExcludedReferenceDescriptors(excludedReferences);
            int objs[] = objects.getIds(monitor.nextMonitor());
            retainedSet = snapshot.getRetainedSet(objs, excludedRefDescriptors, monitor.nextMonitor());
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        Histogram histogram = snapshot.getHistogram(retainedSet, monitor.nextMonitor());

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(MessageUtil.format(Messages.CustomizedRetainedSetQuery_RetainedBy, objects.getLabel()));
        listener.done();
        return histogram;
    }

    private ExcludedReferencesDescriptor[] getExcludedReferenceDescriptors(String[] excludedRefs)
                    throws SnapshotException
    {
        ExcludedReferencesDescriptor[] result = new ExcludedReferencesDescriptor[excludedRefs.length];
        int i = 0;
        for (String s : excludedRefs)
        {
            // fields are separated by ":"
            StringTokenizer tokenizer = new StringTokenizer(s, ":"); //$NON-NLS-1$

            String objectsDescription = tokenizer.nextToken();
            ArrayIntBig objectIds = new ArrayIntBig();
            if (objectsDescription.startsWith("0x")) //$NON-NLS-1$
            {
                long objAddress = Long.parseLong(objectsDescription.substring(2), 16);
                objectIds.add(snapshot.mapAddressToId(objAddress));
            }
            else
            {
                Collection<IClass> classes = snapshot.getClassesByName(objectsDescription, true);
                if (classes != null)
                    for (IClass clazz : classes)
                    {
                        objectIds.addAll(clazz.getObjectIds());
                    }
            }
            Set<String> fields = null;
            if (tokenizer.hasMoreTokens())
            {
                fields = new HashSet<String>();
                StringTokenizer fieldTokenizer = new StringTokenizer(tokenizer.nextToken(), ","); //$NON-NLS-1$
                while (fieldTokenizer.hasMoreTokens())
                {
                    fields.add(fieldTokenizer.nextToken());
                }
            }

            ExcludedReferencesDescriptor desc = new ExcludedReferencesDescriptor(objectIds.toArray(), fields);
            result[i++] = desc;
        }

        return result;
    }

    private String[] getLinesFromFile() throws IOException
    {
        if (excludedReferencesListFile == null)
            return null;

        List<String> result = new ArrayList<String>();
        Charset cs;
        try
        {   
            String encoding = ResourcesPlugin.getEncoding();
            if (encoding != null)
                cs = Charset.forName(encoding);
            else
                cs = StandardCharsets.UTF_8;
        }
        catch (UnsupportedCharsetException e)
        {
            cs = StandardCharsets.UTF_8;
        }
        try (FileInputStream fis = new FileInputStream(excludedReferencesListFile);
             InputStreamReader isr = new InputStreamReader(fis, cs);
             BufferedReader in = new BufferedReader(isr))
        {
            String line = null;
            while ((line = in.readLine()) != null)
            {
                result.add(line);
            }
        }
        return result.toArray(new String[result.size()]);
    }

}

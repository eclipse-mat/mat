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
package org.eclipse.mat.inspections.query;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.mat.collect.ArrayIntBig;
import org.eclipse.mat.query.IHeapObjectArgument;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.HistogramResult;
import org.eclipse.mat.snapshot.ExcludedReferencesDescriptor;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.util.IProgressListener;


@Name("Customized Retained Set")
@Category("Java Basics")
@Icon("/META-INF/icons/show_retained_set.gif")
@Help("Calculate the retained set of a set of objects ignoring some other references.\n\n"
                + "The references which should be excluded should have the format <objectAddress | pattern>[:fieldName].\n"
                + "Example:\n" //
                + "\tjava.lang.ref.WeakReference:referent\n\n" //
                + "Entries with the same format can be loaded from a file (using the -xfile option).")
public class CustomizedRetainedSetQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    // @Argument(isMandatory = false, flag = "f")
    // @Help("List of field names")
    // public String[] fieldNames;

    @Argument(isMandatory = false, flag = "x")
    @Help("List of references to be excluded")
    public String[] excludedReferences;

    @Argument(isMandatory = false, flag = "xfile")
    @Help("File containing a list of references to be excluded")
    public File excludedReferencesListFile;

    public IResult execute(IProgressListener listener) throws Exception
    {
        int[] retainedSet;

        if (excludedReferences == null && excludedReferencesListFile == null)
        {
            /* normal retained set */
            retainedSet = snapshot.getRetainedSet(objects.getIds(listener), listener);
        }
        else
        // retained set with excluded refs
        {
            // read the file (if any)
            String[] fromFile = getLinesFromFile();
            if (fromFile != null && fromFile.length > 0)
            {
                if (excludedReferences != null) // merge from file and manually
                // entered entries
                {
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
            retainedSet = snapshot.getRetainedSet(objects.getIds(listener), excludedRefDescriptors, listener);
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        Histogram histogram = snapshot.getHistogram(retainedSet, listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        histogram.setLabel(MessageFormat.format("Retained by ''{0}''", new Object[] { objects.getLabel() }));
        return new HistogramResult(histogram);
    }

    private ExcludedReferencesDescriptor[] getExcludedReferenceDescriptors(String[] excludedRefs)
                    throws UnsupportedOperationException, SnapshotException
    {
        ExcludedReferencesDescriptor[] result = new ExcludedReferencesDescriptor[excludedRefs.length];
        int i = 0;
        for (String s : excludedRefs)
        {
            /* fields are separated by ":" */
            StringTokenizer tokenizer = new StringTokenizer(s, ":");

            String objectsDescription = tokenizer.nextToken();
            ArrayIntBig objectIds = new ArrayIntBig();
            if (objectsDescription.startsWith("0x"))
            {
                long objAddress = Long.parseLong(objectsDescription.substring(2), 16);
                objectIds.add(snapshot.mapAddressToId(objAddress));
            }
            else
            {
                Collection<IClass> classes = snapshot.getClassesByName(objectsDescription, true);
                for (IClass clazz : classes)
                {
                    objectIds.addAll(clazz.getObjectIds());
                }
            }
            Set<String> fields = null;
            if (tokenizer.hasMoreTokens())
            {
                fields = new HashSet<String>();
                StringTokenizer fieldTokenizer = new StringTokenizer(tokenizer.nextToken(), ",");
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

    private String[] getLinesFromFile() throws Exception
    {
        if (excludedReferencesListFile == null)
            return null;

        BufferedReader in = null;
        List<String> result = new ArrayList<String>();
        try
        {
            in = new BufferedReader(new FileReader(excludedReferencesListFile));
            String line = null;
            while ((line = in.readLine()) != null)
            {
                result.add(line);
            }
        }
        finally
        {
            if (in != null)
            {
                in.close();
            }
        }
        return result.toArray(new String[0]);
    }

}

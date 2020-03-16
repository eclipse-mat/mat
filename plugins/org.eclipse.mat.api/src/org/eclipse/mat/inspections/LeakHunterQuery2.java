/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.io.File;
import java.text.Format;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.eclipse.mat.query.BytesFormat;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

/**
 * Looks for leaks based on a delta in retained sizes of
 * the dominator tree from two snapshots. 
 */
@CommandName("leakhunter2")
@Icon("/META-INF/icons/leak.gif")
public class LeakHunterQuery2 extends LeakHunterQuery
{

    @Argument(advice = Advice.SECONDARY_SNAPSHOT)
    public ISnapshot baseline;

    @Argument(isMandatory = false)
    public String options = "-prefix"; //$NON-NLS-1$

    @Argument(isMandatory = false)
    public Pattern mask = Pattern.compile("\\s@ 0x[0-9a-f]+|^\\[[0-9]+\\]$|(?<=\\p{javaJavaIdentifierPart}\\[)\\d+(?=\\])"); //$NON-NLS-1$

    @Argument(isMandatory = false, flag = "x")
    public String[] extraReferences = new String[] {
                    "java.util.HashMap$Node:key", //$NON-NLS-1$
                    "java.util.Hashtable$Entry:key", //$NON-NLS-1$
                    "java.util.WeakHashMap$Entry:referent", //$NON-NLS-1$
                    "java.util.concurrent.ConcurrentHashMap$Node:key" //$NON-NLS-1$
    };

    @Argument(isMandatory = false, flag = "xfile")
    public File extraReferencesListFile;

    public IResult execute(IProgressListener listener) throws Exception
    {
        // Get a signed bytes formatter
        Histogram dummy = snapshot.getHistogram(new int[0], listener);
        dummy = dummy.diffWithBaseline(dummy);
        Format f = dummy.getColumns()[2].getFormatter();
        if (f instanceof BytesFormat)
            bytesFormatter = (BytesFormat)f;

        IResult res = super.execute(listener);
        if (res instanceof SectionSpec)
        {
            // Add in saved dominator tree
            QuerySpec spec = new QuerySpec(savedResult.getName());
            spec.setResult(savedResult.getResult());
            spec.setCommand(savedcmd);
            spec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());
            spec.set(Params.Rendering.SORT_COLUMN, "#5"); //$NON-NLS-1$
            spec.set(Params.Rendering.HIDE_COLUMN, "#7,#8,#9"); //$NON-NLS-1$
            ((SectionSpec) res).add(spec);
        }
        return res;
    }

    CompositeResult.Entry savedResult;
    String savedcmd;

    FindLeaksQuery.SuspectsResultTable callFindLeaks(IProgressListener listener) throws Exception
    {
        String querycmd = "find_leaks2"; //$NON-NLS-1$
        if (options != null)
            querycmd += " -options " + options; //$NON-NLS-1$
        StringBuilder cmd = new StringBuilder(querycmd);
        SnapshotQuery query = SnapshotQuery.parse(querycmd, snapshot)
                        .setArgument("threshold_percent", threshold_percent) //$NON-NLS-1$
                        .setArgument("max_paths", max_paths) //$NON-NLS-1$
                        .setArgument("baseline", baseline); //$NON-NLS-1$

        cmd.append(" -threshold_percent ").append(threshold_percent); //$NON-NLS-1$
        cmd.append(" -max_paths ").append(max_paths); //$NON-NLS-1$
        cmd.append(" -baseline ").append(baseline.getSnapshotInfo().getPath()); //$NON-NLS-1$
        if (mask != null)
        {
            query.setArgument("mask", mask); //$NON-NLS-1$
            cmd.append(" ").append("-mask ").append(escape(mask.pattern())); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (extraReferences != null)
        {
            query.setArgument("extraReferences", Arrays.asList(extraReferences)); //$NON-NLS-1$
            cmd.append(" ").append("-x "); //$NON-NLS-1$ //$NON-NLS-2$
            for (String e : extraReferences)
                cmd.append(" ").append(escape(e)); //$NON-NLS-1$
        }
        if (extraReferencesListFile != null)
        {
            query.setArgument("extraReferencesListFile", extraReferencesListFile); //$NON-NLS-1$
            cmd.append(" -xfile ").append(escape(extraReferencesListFile.getAbsolutePath())); //$NON-NLS-1$
        }

        savedcmd = cmd.toString();
        IResult ret = query.execute(listener);
        if (ret instanceof CompositeResult)
        {
            CompositeResult cr = (CompositeResult)ret;
            // The delta dominator tree, save for later.
            savedResult = cr.getResultEntries().get(0);
            // The leaks, pass back to LinkHunterQuery.
            return (FindLeaksQuery.SuspectsResultTable)cr.getResultEntries().get(1).getResult();
        }
        else
        {
            return (FindLeaksQuery.SuspectsResultTable)ret;
        }
    }

    static String escape(String s)
    {
        /*
         * abc\def -> abc\def
         * abc"def -> abc\"def
         * abc\\def -> abc\\\def
         * abc\"def -> abc\\\"def
         */
        s = s.replaceAll("\\\\(?=\\\\)|\\\\(?=\")|\"", "\\\\$0"); //$NON-NLS-1$ //$NON-NLS-2$
        if (s.indexOf(' ') >= 0)
            return "\"" + s + "\""; //$NON-NLS-1$ //$NON-NLS-2$
        return s;
    }

}

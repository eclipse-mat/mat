/*******************************************************************************
 * Copyright (c) 2022,2023 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram;
import org.eclipse.swt.widgets.Display;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Basic test of all the queries.
 */
@RunWith(value = Parameterized.class)
public class AllQueries
{
    QueryDescriptor qd;
    String command;

    @Parameters(name="{index}: Query={0} Command={1}")
    public static Collection<Object[]> data()
    {
        List<Object[]>parms = new ArrayList<Object[]>();
        for (QueryDescriptor qd : QueryRegistry.instance().getQueries())
        {
            parms.add(new Object[] {qd.getName(), qd, qd.getIdentifier()});
        }
        return parms;
    }
    
    public AllQueries(String query, QueryDescriptor qd, String command)
    {
        this.qd = qd;
        this.command = command;
    }

    @Test
    public void testHelp()
    {
        assertNotNull(qd.getHelp());
    }

    /**
     * Nearly everything should have a category.
     */
    @Test
    public void testCategory()
    {
        assumeThat(command, not(equalTo("comparetablesquery")));
        assumeThat(command, not(equalTo("export_hprof")));
        assumeThat(command, not(equalTo("show_retained_set")));
        assumeThat(command, not(equalTo("open_source_file")));
        assumeThat(command, not(equalTo("immediate_dominators")));
        assertNotNull(qd.getCategory());
    }

    /**
     * Should everything have help?
     */
    @Test
    public void testHelpAvailable()
    {
        if (qd.isHelpAvailable())
        {
            assertNotNull(qd.getHelp());
            assertThat(qd.getHelp(), not(equalTo("")));
        }
        else
            assertThat(qd.getHelp(), equalTo(nullValue()));
    }

    @Test
    public void testCommandType()
    {
        assertNotNull(qd.getCommandType());
        assertThat(qd.getCommandType().asSubclass(IQuery.class), not(equalTo(null)));
    }

    /**
     * Not really a URL - just a path into the help.
     * Like: /org.eclipse.mat.ui.help/tasks/runningleaksuspectreport.html
     * @throws MalformedURLException
     */
    @Test
    public void testHelpUrl() throws MalformedURLException, IOException
    {
        String helpUrl = qd.getHelpUrl();
        // Tests don't have help
        assumeThat(qd.getCommandType().getName(),not(startsWith("org.eclipse.mat.tests.")));
        // Not all queries have help yet, so skip rather than fail those.
        assumeThat("Normally should be some help", helpUrl, not(nullValue()));
        // All the help is currently in the help plugin
        assertThat(helpUrl, startsWith("/org.eclipse.mat.ui.help/"));
        URL url = new URL(new URL("platform:/plugin/"), helpUrl.substring(1));
        URL url2 = FileLocator.find(url);
        assertThat(url2, not(nullValue()));
        String encoding = StandardCharsets.UTF_8.name();
        String s;
        try (InputStream is = url2.openStream();
             InputStreamReader isr = new InputStreamReader(is, encoding);
             BufferedReader br = new BufferedReader(isr))
        {
            StringBuilder sb = new StringBuilder();
            String r;
            while ((r = br.readLine()) != null)
            {
                sb.append(r).append('\n');
            }
            s = sb.toString();
        }
        GeneralSnapshotTests.basicHTMLcheck(url2, s, encoding);
    }

    /**
     * All the supplied queries should have help for all the user
     * supplied arguments.
     */
    @Test
    public void testArguments()
    {
        List<ArgumentDescriptor> args = qd.getArguments();
        for (ArgumentDescriptor arg : args)
        {
            assertNotNull(arg.getField());
            assertNotNull(arg.getName());
            assertNotNull(arg.getType());
            if (qd.isHelpAvailable())
            {
                if (!qd.getCommandType().getName().startsWith("org.eclipse.mat.tests"))
                {
                    // Some argument types available from the context so don't need help
                    if ((arg.getType() != ISnapshot.class || Advice.SECONDARY_SNAPSHOT.equals(arg.getAdvice())) && 
                                    !IContextObject.class.isAssignableFrom(arg.getType())
                                    && !IQueryContext.class.isAssignableFrom(arg.getType())
                                    && !Display.class.isAssignableFrom(arg.getType())
                                    && !UnreachableObjectsHistogram.class.isAssignableFrom(arg.getType())
                                    && !SnapshotInfo.class.isAssignableFrom(arg.getType())
                                    )
                    {
                        assertNotNull(arg.getName()+" "+arg.getType(), arg.getHelp());
                    }
                }
            }
        }
    }

    /**
     * We don't currently supply translations, so everything
     * should come from annotations and be marked as English
     */
    @Test
    public void testHelpLocale()
    {
        assertNotNull(qd.getHelpLocale());
        if (!qd.getCommandType().getName().startsWith("org.eclipse.mat.tests."))
        {
            // Currently presume all MAT is tested and supplied in English
            assertThat(qd.getHelpLocale(), equalTo(Locale.ENGLISH));
        }
    }

    /**
     * All the supplied queries should have icons.
     */
    @Test
    public void testIcon()
    {
        assumeThat(qd.getCommandType().getName(),not(startsWith("org.eclipse.mat.tests.")));
        assertNotNull(qd.getIcon());
    }

    /**
     * All the supplied queries should have an identifier
     */
    @Test
    public void testIdentifier()
    {
        assertNotNull(qd.getIdentifier());
        assertThat(qd.getIdentifier(), equalTo(command));
    }
}

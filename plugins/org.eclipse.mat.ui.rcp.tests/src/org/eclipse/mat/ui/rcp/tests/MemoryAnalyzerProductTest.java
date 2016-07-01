/*******************************************************************************
 * Copyright (c) 2013 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.rcp.tests;

import static org.junit.Assert.assertTrue;

import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SWTBotJunit4ClassRunner.class)
public class MemoryAnalyzerProductTest
{

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        bot = new SWTWorkbenchBot();
        for (SWTBotView view : bot.views())
        {
            if ("Welcome".equals(view.getTitle()))
            {
                view.close();
                break;
            }
        }
    }

    @AfterClass
    public static void sleep()
    {
        bot.sleep(2000);
    }

    @Test
    public void testIfProxySettingsPreferenceIsAvailable()
    {
        bot.menu("Window").click().menu(new Matcher<MenuItem>()
        {
            public boolean matches(Object item)
            {
                if (item instanceof MenuItem)
                {
                    MenuItem mitem = (MenuItem) item;
                    String mitemText = mitem.getText().replaceAll("&", "");
                    if ("Preferences...".equals(mitemText) || "Preferences".equals(mitemText)) { return true; }
                }
                return false;
            }

            public void describeTo(Description description)
            {}

            public void describeMismatch(Object item, Description mismatchDescription)
            {}

            public void _dont_implement_Matcher___instead_extend_BaseMatcher_()
            {}
        }, false, 0).click();

        SWTBotShell shell = bot.shell("Preferences");
        shell.activate();

        bot.tree().expandNode("General").select("Network Connections");

        bot.button("Cancel").click();
    }

    @Test
    public void testIfInstallNewSoftwareIsAvailable()
    {
        SWTBotMenu installNewSoftwareMenu = bot.menu("Help").click().menu("Install New Software...");
        assertTrue("Could not find \"Install New Software...\" menu", installNewSoftwareMenu != null);
    }

    @Test
    public void testIfErrorLogViewIsAvailable()
    {
        SWTBotMenu errorLogMenu = bot.menu("Window").click().menu("Error Log", true, 0);
        assertTrue("Could not find \"Error Log\" menu", errorLogMenu != null);
    }

}

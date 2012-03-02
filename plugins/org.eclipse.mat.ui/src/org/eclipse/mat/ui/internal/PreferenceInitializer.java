/*******************************************************************************
 * Copyright (c) 2011,2012 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.internal.query.arguments.ArgumentsWizardPage;
import org.eclipse.mat.ui.snapshot.panes.oql.contentAssist.ColorProvider;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		IPreferenceStore store = MemoryAnalyserPlugin.getDefault().getPreferenceStore();
		store.setDefault(GettingStartedWizard.HIDE_WIZARD_KEY, false);
	    store.setDefault(PreferenceConstants.P_KEEP_UNREACHABLE_OBJECTS, false);
	    store.setDefault(ArgumentsWizardPage.HIDE_QUERY_HELP, false);
	    PreferenceConverter.setDefault(store, ColorProvider.COMMENT_COLOR_PREF, ColorProvider.COMMENT_COLOR);
        PreferenceConverter.setDefault(store, ColorProvider.KEYWORD_COLOR_PREF, ColorProvider.KEYWORD_COLOR);
	}

}

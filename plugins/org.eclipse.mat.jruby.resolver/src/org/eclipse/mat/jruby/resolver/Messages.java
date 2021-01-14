/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Kaloyan Raev - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.jruby.resolver;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	
	private static final String BUNDLE_NAME = "org.eclipse.mat.jruby.resolver.messages"; //$NON-NLS-1$
	
	public static String HeapDumpProxy_UnknownClass;
	public static String HeapDumpProxy_TypeMismatch;
	public static String HeapDumpProxy_NoSuchField;
	public static String HeapDumpProxy_NotImplemented;
	
	public static String JRubyScriptResolver_Summary;
	public static String JRubyScriptResolver_ResultHeader;
	public static String JRubyScriptResolver_ResultBody;
	public static String JRubyScriptResolver_ResultBody_RubyCall_Class;
	public static String JRubyScriptResolver_ResultBody_RubyCall_Method;
	public static String JRubyScriptResolver_ResultBody_PossibleSuspects;
	public static String JRubyScriptResolver_ResultBody_RubyScriptPath;
	
	public static String RubyStacktraceDumper_Summary;
	public static String RubyStacktraceDumper_ResultHeader;
	public static String RubyStacktraceDumper_UnknownThread;
	public static String RubyStacktraceDumper_StackTraceLine;
	public static String RubyStacktraceDumper_Unknown;
	public static String RubyStacktraceDumper_NoJRuby;
	public static String RubyStacktraceDumper_NoJRubyDetails;
	
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}

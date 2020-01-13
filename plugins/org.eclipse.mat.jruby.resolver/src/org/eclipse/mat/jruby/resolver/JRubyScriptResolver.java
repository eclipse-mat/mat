/*******************************************************************************
 * Copyright (c) 2010, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Dimitar Giormov - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - escapes
 *******************************************************************************/
package org.eclipse.mat.jruby.resolver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.util.HTMLUtils;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.osgi.util.NLS;

@Subject("org.jruby.runtime.ThreadContext")
public class JRubyScriptResolver implements IRequestDetailsResolver {

	public void complement(ISnapshot snapshot, IThreadInfo thread, int[] localVars, int currentVarId, IProgressListener listener) throws SnapshotException {
		
		boolean isOsgiBased = false;

		String shortJavaName = null;
		for (int i = 0; i < localVars.length; i++) {
			IObject object = snapshot.getObject(localVars[i]);
			if(snapshot.getObject(localVars[i]).getTechnicalName().startsWith("org.eclipse.core.runtime")){ //$NON-NLS-1$
				isOsgiBased = true;
			} else if(snapshot.getObject(localVars[i]).getTechnicalName().startsWith("sun.reflect.NativeMethodAccessorImpl")){ //$NON-NLS-1$
				IObject object2 = ((IObject)snapshot.getObject(localVars[i]).resolveValue("method")); //$NON-NLS-1$
				if (object2 != null){
					shortJavaName = object2.getClassSpecificName();
				}
			} else if (object.getTechnicalName().startsWith("org.jruby") //$NON-NLS-1$
					|| object.getTechnicalName().startsWith("java") //$NON-NLS-1$
					|| object.getTechnicalName().startsWith("ruby")){ //$NON-NLS-1$
				continue;
			}
		}
		List<String> possibleBundleSuspects = new ArrayList<String>();
		String realClassName = null; 
		if (shortJavaName != null) {
			realClassName = shortJavaName.substring(shortJavaName.indexOf(' ')).trim();
			String removedMethodName = realClassName.substring(0, realClassName.lastIndexOf('.'));
			
			
            Collection<IClass> classesByName = snapshot.getClassesByName(removedMethodName, true);
            if (classesByName != null)
            {
                for (IClass iClass : classesByName)
                {
                    possibleBundleSuspects.add(snapshot.getObject(iClass.getClassLoaderId()).getClassSpecificName());
                }
            }
		}
        CompositeResult result = new CompositeResult();
        
        String classSpecificName = ""; //$NON-NLS-1$
		List<NamedReference> outboundReferences = snapshot.getObject(currentVarId).getOutboundReferences();
		for (NamedReference namedReference : outboundReferences) {
			if ("file".equals(namedReference.getName())){ //$NON-NLS-1$
				classSpecificName = namedReference.getObject().getClassSpecificName();
				break;
			}
		}
		if (classSpecificName.length() > 0){
			String fileName = new File(classSpecificName).getName();
			String summary = NLS.bind(Messages.JRubyScriptResolver_Summary, HTMLUtils.escapeText(fileName));
			
			String rubyCallMessage = (shortJavaName == null)
					? NLS.bind(Messages.JRubyScriptResolver_ResultBody_RubyCall_Class, HTMLUtils.escapeText(fileName))
					: NLS.bind(Messages.JRubyScriptResolver_ResultBody_RubyCall_Method, HTMLUtils.escapeText(fileName), HTMLUtils.escapeText(realClassName));
			
			String possibleBundleSuspectsMessage = ""; //$NON-NLS-1$
			if (isOsgiBased && possibleBundleSuspects.size() > 0){
				thread.addKeyword("osgi"); //$NON-NLS-1$
				StringBuilder suspects = new StringBuilder();
				for (String possibleBundleSuspect : possibleBundleSuspects) {
					suspects.append(HTMLUtils.escapeText(possibleBundleSuspect)).append(' ');
				}
				possibleBundleSuspectsMessage = NLS.bind(Messages.JRubyScriptResolver_ResultBody_PossibleSuspects, suspects);
			}
			String rubyScriptPathMessage = NLS.bind(Messages.JRubyScriptResolver_ResultBody_RubyScriptPath, HTMLUtils.escapeText(classSpecificName));
			
			String resultBody = NLS.bind(Messages.JRubyScriptResolver_ResultBody, new Object[] { rubyCallMessage, possibleBundleSuspectsMessage, rubyScriptPathMessage });
			result.addResult(Messages.JRubyScriptResolver_ResultHeader, new TextResult(resultBody, true));
	 		thread.addRequest(summary, result);
	 		thread.addKeyword("Ruby");	 //$NON-NLS-1$
	 		thread.addKeyword("script"); //$NON-NLS-1$
	 		
		}
	}

}
